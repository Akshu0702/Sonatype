#!/usr/bin/env bash
# ml-ado :: PreToolUse hook. Blocks (exit 2) a Write, Edit or Bash call that would put a token-shaped string into a file or a commit.
# A token in an export or an inline variable of a command that writes nothing is allowed (that is how a session is given a credential).
exec "$(command -v python3 || command -v python)" "$(dirname "$0")/guard.py" token
