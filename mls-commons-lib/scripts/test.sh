#!/usr/bin/env bash
# Runs the build and the tests against a throwaway PostgreSQL 16 container. Needs Docker; no local JDK needed.
# Usage: bash scripts/test.sh [maven arguments]      (default: verify)
set -uo pipefail
root="${REPO_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
net="mls-commons-$$"; db="mls-commons-pg-$$"
cleanup() { docker rm -f "$db" >/dev/null 2>&1; docker network rm "$net" >/dev/null 2>&1; }
trap cleanup EXIT
docker network create "$net" >/dev/null
docker run -d --name "$db" --network "$net" -e POSTGRES_PASSWORD=postgres postgres:16 >/dev/null
for _ in $(seq 1 40); do docker exec "$db" pg_isready -U postgres >/dev/null 2>&1 && break; sleep 1; done
docker run --rm --network "$net" -v "$root:/work" -v mls-m2:/root/.m2 -w /work \
  -e TEST_PG_URL="jdbc:postgresql://$db:5432/postgres" -e TEST_PG_ADMIN_USER=postgres -e TEST_PG_ADMIN_PASSWORD=postgres \
  maven:3.9-eclipse-temurin-21 mvn -B -ntp "${@:-verify}"
