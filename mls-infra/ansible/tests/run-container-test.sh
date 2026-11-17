#!/usr/bin/env bash
# Runs the baseline twice in a container and fails if the second run changes anything (idempotence).
# Usage: bash tests/run-container-test.sh ubuntu:24.04 | amazonlinux:2023
set -euo pipefail

image="${1:?image, for example ubuntu:24.04 or amazonlinux:2023}"
here="${ANSIBLE_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"

case "$image" in
  ubuntu:*)
    setup='export DEBIAN_FRONTEND=noninteractive; apt-get update -qq && apt-get install -y -qq ansible-core openssh-server python3 >/dev/null' ;;
  amazonlinux:*)
    setup='dnf install -y -q ansible-core python3 openssh-server >/dev/null' ;;
  *)
    echo "unsupported image: $image" >&2; exit 2 ;;
esac

docker run --rm -v "$here:/ansible:ro" -w /ansible "$image" bash -c "
  set -euo pipefail
  $setup
  export ANSIBLE_ROLES_PATH=/ansible/roles ANSIBLE_NOCOWS=1
  echo '== syntax check'
  ansible-playbook -i localhost, -c local tests/converge.yml --syntax-check
  echo '== first run'
  ansible-playbook -i localhost, -c local tests/converge.yml
  echo '== second run (must change nothing)'
  ansible-playbook -i localhost, -c local tests/converge.yml | tee /tmp/second.log
  grep -E 'changed=0' /tmp/second.log >/dev/null && grep -E 'failed=0' /tmp/second.log >/dev/null
  echo 'idempotent: second run changed nothing'
"
