#!/usr/bin/env python3
"""ml-ado guard hooks. Reads the tool call as JSON on stdin; exit 2 blocks the call and the message on stderr is shown to the assistant.

  guard.py token   : no token-shaped string may be written into a file or a commit
  guard.py dryrun  : dryRun may not be set to false unless the user typed the go-ahead in this session
The hooks never print the matched value.
"""
import json
import re
import sys

TOKENS = [
    re.compile(r"(?<![A-Za-z0-9])[A-Za-z0-9]{76}AZDO[A-Za-z0-9]{4}(?![A-Za-z0-9])"),  # Azure DevOps personal access token, new format
    re.compile(r"(?<![A-Za-z0-9])[a-z2-7]{52}(?![A-Za-z0-9])"),                       # old format
    re.compile(r"(?<![A-Z0-9])AKIA[0-9A-Z]{16}(?![A-Z0-9])"),
    re.compile(r"gh[pousr]_[0-9A-Za-z]{30,}"),
    re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----"),
    re.compile(r"eyJ[A-Za-z0-9_-]{15,}\.[A-Za-z0-9_-]{15,}\.[A-Za-z0-9_-]{10,}"),
]
WRITES = re.compile(r"(>>?\s*\S|\btee\b|\bsed\s+-i|\bgit\s+(add|commit|push)\b|\bcp\b|\bmv\b|<<)")
DRYRUN_FALSE = re.compile(r"""(?ix)
    dry[_-]?run["']?\s*[:=]\s*["']?(false|0|no)\b
  | --dry-?run[ =](false|0|no)\b
  | dry[_-]?run["']?\s*:\s*\{[^}]{0,60}value["']?\s*:\s*["']?(false|0|no)\b
""")
GO_AHEAD = re.compile(r"go-ahead", re.I)


def text_of(call):
    ti = call.get("tool_input") or {}
    name = call.get("tool_name", "")
    if name == "Bash":
        return name, ti.get("command", "")
    return name, "\n".join(str(ti.get(k, "")) for k in ("content", "new_string", "file_path"))


def user_messages(path):
    """Yields what the user typed (not tool results, not the assistant) from the session transcript."""
    try:
        with open(path, encoding="utf-8") as f:
            for line in f:
                try:
                    rec = json.loads(line)
                except ValueError:
                    continue
                if rec.get("type") != "user":
                    continue
                c = rec.get("message", {}).get("content")
                if isinstance(c, str):
                    yield c
                elif isinstance(c, list):
                    for part in c:
                        if isinstance(part, dict) and part.get("type") == "text":
                            yield part.get("text", "")
    except OSError:
        return


def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else ""
    try:
        call = json.load(sys.stdin)
    except ValueError:
        return 0
    name, text = text_of(call)
    if mode == "token":
        if not any(rx.search(text) for rx in TOKENS):
            return 0
        if name == "Bash" and not WRITES.search(text):
            return 0  # a credential set in the session environment writes nothing
        sys.stderr.write("ml-ado token-guard: this call would put a token-shaped string into a file or a commit. Blocked. Keep credentials in the environment (ADO_TOKEN) and never write them down.\n")
        return 2
    if mode == "dryrun":
        if not DRYRUN_FALSE.search(text):
            return 0
        path = call.get("transcript_path")
        if path and any(GO_AHEAD.search(m) for m in user_messages(path)):
            return 0
        sys.stderr.write("ml-ado dryrun-guard: setting dryRun to false needs your explicit go-ahead. Ask the delivery lead, record it on the Board, then type the word go-ahead with the definition name in the chat. Blocked.\n")
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
