# ADR-0002: Firewall plugin deployed as an immutable bundle

**Status:** Accepted

## Context
Copying plugin files into each pod can leave pods on different builds, needs exec rights on pods, loses files if a pod is rescheduled, and a mismatched listener jar can stop a pod from starting.

## Decision
Package the plugin files with a SHA-256 manifest as a versioned bundle in S3. An init container pulls and verifies it. A Helm value (`plugin.version`) in Git drives a rolling restart, and the version is checked on every pod afterwards.

## Consequences
- Identical builds on every pod; rollback is a value change.
- The listener jar and plugin files come from one source.
- Needs the init container and values in Git before the first automated deploy.
