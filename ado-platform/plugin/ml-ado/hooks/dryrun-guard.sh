#!/usr/bin/env bash
# ml-ado :: PreToolUse hook. Blocks (exit 2) setting dryRun to false unless a message the USER typed in this session contains the word go-ahead.
exec "$(command -v python3 || command -v python)" "$(dirname "$0")/guard.py" dryrun
