#!/usr/bin/env python3
"""Refuses a Terraform plan that would do more than it was allowed to.

Usage: plan-guard.py plan.json [--allow create] [--expect-region ap-south-1]

Reads the output of `terraform show -json <plan>`. It fails (exit 1) when:
  * any resource change has an action outside --allow (default: only "create"). An update, a delete or a
    replace (delete + create) is therefore refused unless explicitly allowed;
  * the plan imports an existing resource (an import block), because the test must never adopt existing resources;
  * any AWS provider configuration has no region pinned, or a region other than --expect-region.
Reads and no-ops are always accepted. It does not look at what is created, only at what happens to what exists.
"""
import argparse
import json
import sys

ALWAYS_OK = {"no-op", "read"}


def resolve_region(cfg, variables):
    expr = (cfg.get("expressions") or {}).get("region")
    if not expr:
        return None
    if "constant_value" in expr:
        return expr["constant_value"]
    for ref in expr.get("references", []):
        if ref.startswith("var."):
            name = ref.split(".", 1)[1]
            if name in variables:
                return variables[name].get("value")
    return None


def check(plan, allow, expect_region):
    problems = []
    rows = []
    for rc in plan.get("resource_changes", []):
        ch = rc.get("change", {})
        actions = set(ch.get("actions", []))
        if rc.get("mode") == "data":
            continue
        if ch.get("importing"):
            problems.append(f"{rc['address']}: imports an existing resource, which is never allowed here")
        extra = actions - ALWAYS_OK - allow
        if extra:
            problems.append(f"{rc['address']}: action {'+'.join(ch.get('actions', []))} is not allowed (allowed: {', '.join(sorted(allow))})")
        if actions - ALWAYS_OK:
            rows.append((rc["address"], "+".join(ch.get("actions", []))))
    variables = plan.get("variables", {})
    provider_cfg = (plan.get("configuration") or {}).get("provider_config", {})
    aws_cfgs = {k: v for k, v in provider_cfg.items() if v.get("name") == "aws"}
    if expect_region:
        if not aws_cfgs:
            problems.append("no AWS provider configuration found, so the region cannot be checked")
        for key, cfg in aws_cfgs.items():
            region = resolve_region(cfg, variables)
            if region is None:
                problems.append(f"provider {key}: region is not pinned in the configuration")
            elif region != expect_region:
                problems.append(f"provider {key}: region is {region}, expected {expect_region}")
    return problems, rows


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("plan_json")
    ap.add_argument("--allow", default="create", help="comma-separated actions: create,update,delete")
    ap.add_argument("--expect-region", default=None)
    a = ap.parse_args(argv)
    allow = {x.strip() for x in a.allow.split(",") if x.strip()}
    bad = allow - {"create", "update", "delete"}
    if bad:
        print(f"unknown action(s) in --allow: {sorted(bad)}")
        return 2
    plan = json.load(open(a.plan_json, encoding="utf-8"))
    problems, rows = check(plan, allow, a.expect_region)
    print(f"Plan guard: {len(rows)} change(s), allowed actions: {', '.join(sorted(allow))}")
    for addr, act in rows:
        print(f"  {act:14} {addr}")
    if problems:
        print("\nPLAN REFUSED:")
        for p in problems:
            print("  - " + p)
        return 1
    print("Plan accepted.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
