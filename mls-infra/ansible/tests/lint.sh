#!/usr/bin/env bash
# Lints the roles and playbooks with ansible-lint, run from a Python image.
# Usage: bash tests/lint.sh   (set ANSIBLE_DIR to the absolute path of the ansible folder if your shell rewrites paths)
set -euo pipefail

here="${ANSIBLE_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"

docker run --rm -v "$here:/ansible" -w /ansible -e ANSIBLE_ROLES_PATH=/ansible/roles python:3.12-slim   sh -c 'pip install -q ansible-core ansible-lint >/dev/null 2>&1 && ansible-lint roles playbooks tests/converge.yml'
