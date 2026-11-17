#!/usr/bin/env bash
# Builds and tests the portal API. Needs Docker; no local JDK or Maven. No database is needed.
# The service depends on the shared library (com.platform:platform-security-commons), which is not in a package feed yet.
# Point COMMONS_DIR at a checkout of mls-commons-lib and it is built and installed into the Maven cache first:
#   COMMONS_DIR=../mls-commons-lib bash scripts/test.sh
set -uo pipefail
root="${REPO_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
docker run --rm -v "$root:/work" ${COMMONS_DIR:+-v "$COMMONS_DIR:/commons"} -v mls-m2:/root/.m2 -w /work maven:3.9-eclipse-temurin-21 \
  sh -c "${COMMONS_DIR:+mvn -B -ntp -q -f /commons/pom.xml -DskipTests install && }mvn -B -ntp ${*:-verify}"
