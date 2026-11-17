#!/usr/bin/env bash
# Builds and tests the service against a throwaway PostgreSQL. Needs Docker; no local JDK or Maven.
# The service depends on the shared library (com.platform:platform-security-commons), which is not in a package feed yet.
# Point COMMONS_DIR at a checkout of mls-commons-lib and it is built and installed into the Maven cache first:
#   COMMONS_DIR=../mls-commons-lib bash scripts/test.sh
# PGIMAGE chooses the PostgreSQL image (default postgres:16; postgres:18 also passes).
set -uo pipefail
root="${REPO_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
net="mls-tenants-$$"; db="mls-tenants-pg-$$"
cleanup() { docker rm -f "$db" >/dev/null 2>&1; docker network rm "$net" >/dev/null 2>&1; }
trap cleanup EXIT
docker network create "$net" >/dev/null
docker run -d --name "$db" --network "$net" -e POSTGRES_PASSWORD=postgres "${PGIMAGE:-postgres:16}" >/dev/null
for _ in $(seq 1 40); do docker exec "$db" pg_isready -U postgres >/dev/null 2>&1 && break; sleep 1; done
docker run --rm --network "$net" -v "$root:/work" ${COMMONS_DIR:+-v "$COMMONS_DIR:/commons"} -v mls-m2:/root/.m2 -w /work \
  -e TEST_PG_URL="jdbc:postgresql://$db:5432/postgres" -e TEST_PG_ADMIN_USER=postgres -e TEST_PG_ADMIN_PASSWORD=postgres \
  maven:3.9-eclipse-temurin-21 sh -c "${COMMONS_DIR:+mvn -B -ntp -q -f /commons/pom.xml -DskipTests install && }mvn -B -ntp ${*:-verify}"
