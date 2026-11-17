// A small local interpreter for the part of Amazon States Language these state machines use
// (JSONata mode: Pass, Task, Choice, Wait, Succeed, Fail, Assign, Arguments, Output, Retry, Catch).
//
// It is NOT the AWS service. It runs the real definition files with the real JSONata library, with every Task answered by a
// mock, so it proves the expressions and the flow. It cannot prove that the service accepts the definition or that EC2 accepts
// the arguments: scripts/ci/statemachine-validate-aws.sh does the first once the agent may call the service, and the first
// real execution does the second.
import fs from "node:fs";
import crypto from "node:crypto";
import jsonata from "jsonata";

const isTemplate = (v) => typeof v === "string" && v.startsWith("{%") && v.endsWith("%}");

export class Failure extends Error {
  constructor(error, cause) {
    super(`${error}: ${cause}`);
    this.error = error;
    this.cause = cause;
  }
}

async function evalExpr(text, scope, clock) {
  const expr = jsonata(text.slice(2, -2).trim());
  expr.registerFunction("now", () => clock);
  expr.registerFunction("random", () => 0); // always the first subnet, so runs are repeatable
  expr.registerFunction("base64encode", (s) => Buffer.from(String(s), "utf8").toString("base64"));
  expr.registerFunction("uuid", () => crypto.randomUUID());
  try {
    return await expr.evaluate(undefined, scope);
  } catch (e) {
    throw new Failure("States.QueryEvaluationError", `${text}: ${e.message}`);
  }
}

async function ev(node, scope, clock) {
  if (isTemplate(node)) return evalExpr(node, scope, clock);
  if (Array.isArray(node)) return Promise.all(node.map((n) => ev(n, scope, clock)));
  if (node && typeof node === "object") {
    const out = {};
    for (const [k, v] of Object.entries(node)) {
      const r = await ev(v, scope, clock);
      if (r !== undefined) out[k] = r; // an undefined field is left out, as JSONata does for an object
    }
    return out;
  }
  return node;
}

const plain = (v) => (v === undefined ? undefined : JSON.parse(JSON.stringify(v)));

// definition: parsed ASL. run: { input, clock, executionName, mocks: { StateName: [ {return}|{error,cause} ] } }
export async function execute(definition, run) {
  if (definition.QueryLanguage !== "JSONata") throw new Error("the emulator only supports QueryLanguage JSONata");
  const vars = {};
  const trace = [];
  const waits = [];
  const calls = {};
  const queues = Object.fromEntries(Object.entries(run.mocks || {}).map(([k, v]) => [k, [...v]]));
  const exec = run.executionName || "exec-1";
  const context = {
    Execution: { Name: exec, Id: "arn:aws:states:::execution:test:" + exec, Input: run.input },
    StateMachine: { Name: "test" },
  };
  let name = definition.StartAt;
  let input = plain(run.input);
  let steps = 0;

  const scopeFor = (extra) => ({ states: { input, context, ...extra }, ...vars });
  const assign = async (state, extra) => {
    if (!state.Assign) return;
    const scope = scopeFor(extra);
    const produced = {};
    for (const [k, v] of Object.entries(state.Assign)) {
      const r = await ev(v, scope, run.clock);
      if (r === undefined) throw new Failure("States.QueryEvaluationError", `Assign ${k} in ${name} evaluated to undefined`);
      produced[k] = plain(r);
    }
    Object.assign(vars, produced); // every expression saw the old values, as in the service
  };

  try {
    for (;;) {
      if (++steps > 2000) throw new Error("more than 2000 steps: the run loops");
      const state = definition.States[name];
      if (!state) throw new Error("no state " + name);
      trace.push(name);

      if (state.Type === "Fail") {
        const scope = scopeFor({});
        throw new Failure(await ev(state.Error, scope, run.clock), await ev(state.Cause, scope, run.clock));
      }
      if (state.Type === "Succeed") {
        const out = state.Output === undefined ? input : plain(await ev(state.Output, scopeFor({}), run.clock));
        return { status: "SUCCEEDED", output: out, trace, waits, calls, vars };
      }
      if (state.Type === "Choice") {
        let next = state.Default;
        for (const c of state.Choices) {
          const r = await ev(c.Condition, scopeFor({}), run.clock);
          if (typeof r !== "boolean") {
            throw new Failure("States.QueryEvaluationError", `Condition in ${name} did not give true or false: ${JSON.stringify(r)}`);
          }
          if (r) {
            next = c.Next;
            await assign(c, {});
            break;
          }
        }
        if (!next) throw new Failure("States.NoChoiceMatched", name);
        name = next;
        continue;
      }
      if (state.Type === "Pass") {
        const result = state.Output === undefined ? input : plain(await ev(state.Output, scopeFor({}), run.clock));
        await assign(state, { result: input });
        input = result;
        name = state.Next;
        continue;
      }
      if (state.Type === "Wait") {
        const ts = await ev(state.Timestamp, scopeFor({}), run.clock);
        if (typeof ts !== "string" || !/^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(\.\d+)?Z$/.test(ts)) {
          throw new Failure("States.Runtime", `Wait in ${name} needs an RFC 3339 timestamp, got ${JSON.stringify(ts)}`);
        }
        waits.push({ state: name, until: ts });
        if (ts > run.clock) run.clock = ts; // time passes while the execution waits
        name = state.Next;
        continue;
      }
      if (state.Type === "Task") {
        let attempt = 0;
        for (;;) {
          const args = plain(await ev(state.Arguments || {}, scopeFor({}), run.clock));
          (calls[name] ||= []).push({ resource: state.Resource, arguments: args });
          const q = queues[name];
          if (!q || q.length === 0) throw new Error(`no mock left for task ${name}`);
          const m = q.shift();
          if (m.return !== undefined) {
            const result = plain(m.return);
            const out = state.Output === undefined ? result : plain(await ev(state.Output, scopeFor({ result }), run.clock));
            await assign(state, { result });
            input = out;
            name = state.Next;
            break;
          }
          // the mock answers with an error: Retry first, then Catch
          const matches = (list) => list.some((e) => e === m.error || e === "States.ALL" || (e === "States.TaskFailed" && m.error !== "States.Timeout"));
          const retry = (state.Retry || []).find((r) => matches(r.ErrorEquals));
          if (retry && attempt < (retry.MaxAttempts ?? 3)) {
            attempt++;
            continue;
          }
          const catcher = (state.Catch || []).find((c) => matches(c.ErrorEquals));
          if (!catcher) throw new Failure(m.error, m.cause || "");
          const errorOutput = { Error: m.error, Cause: m.cause || "" };
          await assign(catcher, { errorOutput });
          input = errorOutput;
          name = catcher.Next;
          break;
        }
        continue;
      }
      throw new Error("state type not supported by the emulator: " + state.Type);
    }
  } catch (e) {
    if (e instanceof Failure) return { status: "FAILED", error: e.error, cause: e.cause, trace, waits, calls, vars };
    throw e;
  }
}

// ---- assertions
function subset(actual, expected, path = "") {
  if (expected !== null && typeof expected === "object") {
    if (Array.isArray(expected)) {
      if (!Array.isArray(actual) || actual.length !== expected.length) {
        return `${path}: expected an array of ${expected.length}, got ${JSON.stringify(actual)}`;
      }
      for (let i = 0; i < expected.length; i++) {
        const r = subset(actual[i], expected[i], `${path}[${i}]`);
        if (r) return r;
      }
      return null;
    }
    if (actual === null || typeof actual !== "object") return `${path}: expected an object, got ${JSON.stringify(actual)}`;
    for (const [k, v] of Object.entries(expected)) {
      const r = subset(actual[k], v, `${path}.${k}`);
      if (r) return r;
    }
    return null;
  }
  return actual === expected ? null : `${path}: expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual)}`;
}

export function check(result, expect) {
  const problems = [];
  if (expect.status && result.status !== expect.status) {
    problems.push(`status ${result.status}, expected ${expect.status}` + (result.error ? ` (${result.error}: ${result.cause})` : ""));
  }
  if (expect.error && result.error !== expect.error) problems.push(`error ${result.error}, expected ${expect.error}`);
  if (expect.causeContains && !String(result.cause || "").includes(expect.causeContains)) {
    problems.push(`cause "${result.cause}" lacks "${expect.causeContains}"`);
  }
  if (expect.path) {
    const r = subset(result.trace, expect.path, "path");
    if (r) problems.push(r + ` (trace: ${result.trace.join(" > ")})`);
  }
  for (const s of expect.notVisited || []) if (result.trace.includes(s)) problems.push(`state ${s} must not run`);
  if (expect.waits) {
    const r = subset(result.waits.map((w) => w.until), expect.waits, "waits");
    if (r) problems.push(r);
  }
  if (expect.output) {
    const r = subset(result.output, expect.output, "output");
    if (r) problems.push(r);
  }
  for (const [state, spec] of Object.entries(expect.calls || {})) {
    const list = result.calls[state] || [];
    if (spec.count !== undefined && list.length !== spec.count) problems.push(`${state} called ${list.length} times, expected ${spec.count}`);
    if (spec.arguments) {
      const r = subset(list[0]?.arguments, spec.arguments, `${state}.arguments`);
      if (r) problems.push(r);
    }
    if (spec.lastArguments) {
      const r = subset(list.at(-1)?.arguments, spec.lastArguments, `${state}.lastArguments`);
      if (r) problems.push(r);
    }
  }
  return problems;
}

// ---- command line: node emulator.mjs <rendered dir> <cases.json>
if (process.argv[1] && process.argv[1].endsWith("emulator.mjs")) {
  const [dir, casesFile] = process.argv.slice(2);
  const cases = JSON.parse(fs.readFileSync(casesFile, "utf8"));
  let failed = 0;
  for (const c of cases) {
    const def = JSON.parse(fs.readFileSync(`${dir}/${c.machine}.json`, "utf8"));
    let problems;
    try {
      const r = await execute(def, { input: c.input, clock: c.now, executionName: c.executionName, mocks: c.mocks });
      problems = check(r, c.expect);
    } catch (e) {
      problems = ["test harness error: " + e.message];
    }
    if (problems.length) {
      failed++;
      console.log(`FAIL ${c.machine} / ${c.name}`);
      problems.forEach((p) => console.log("     " + p));
    } else {
      console.log(`ok   ${c.machine} / ${c.name}`);
    }
  }
  console.log(failed ? `${failed} of ${cases.length} cases FAILED` : `all ${cases.length} cases passed`);
  process.exit(failed ? 1 : 0);
}
