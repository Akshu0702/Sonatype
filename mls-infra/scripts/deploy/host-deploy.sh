#!/usr/bin/env bash
# Runs ON the application host (sent there by deploy-from-feed.sh through Session Manager). Puts one service jar into a container,
# starts it, waits for it to be healthy, and puts the previous version back if it is not.
#
# Environment (all required unless a default is shown):
#   SERVICE      short name, e.g. tenants-service
#   VERSION      what is being deployed (the feed version), recorded in the log and the image tag
#   JAR          path of the jar on this host
#   SHA256       expected checksum of the jar (the build's, checked again here)
#   PORT         the port the service listens on
#   HEALTH_PATH  default /actuator/health
#   ENV_FILE     default /opt/mls/<service>/env   (optional file of KEY=VALUE settings; never in the pipeline)
#   WAIT_SECONDS default 90
set -euo pipefail

: "${SERVICE:?}" "${VERSION:?}" "${JAR:?}" "${SHA256:?}" "${PORT:?}"
HEALTH_PATH="${HEALTH_PATH:-/actuator/health}"
BASE="${DEPLOY_BASE:-/opt/mls}"
ENV_FILE="${ENV_FILE:-$BASE/$SERVICE/env}"
WAIT_SECONDS="${WAIT_SECONDS:-90}"
TAG="$(printf '%s' "$VERSION" | sed 's/[^A-Za-z0-9_.-]/-/g')"
IMAGE="mls/$SERVICE:$TAG"

actual="$(sha256sum "$JAR" | cut -d' ' -f1)"
if [ "$actual" != "$SHA256" ]; then
  echo "Checksum mismatch for $JAR: expected $SHA256, got $actual. Nothing was changed." >&2
  exit 2
fi

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
cp "$JAR" "$work/app.jar"
cat > "$work/Dockerfile" <<'DOCKER'
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 app
WORKDIR /app
COPY app.jar app.jar
USER app
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
DOCKER
mkdir -p "$BASE/$SERVICE"
chmod 0750 "$BASE/$SERVICE"
docker build -q -t "$IMAGE" "$work" >/dev/null

previous=""
if docker inspect "$SERVICE" >/dev/null 2>&1; then
  previous="$SERVICE-previous"
  docker rm -f "$previous" >/dev/null 2>&1 || true
  docker stop "$SERVICE" >/dev/null
  docker rename "$SERVICE" "$previous"
fi

args=(--name "$SERVICE" --restart unless-stopped -p "$PORT:$PORT" -e "PORT=$PORT" --label "mls.version=$VERSION" --label "mls.sha256=$SHA256")
[ -f "$ENV_FILE" ] && args+=(--env-file "$ENV_FILE")
docker run -d "${args[@]}" "$IMAGE" >/dev/null

healthy=""
for _ in $(seq 1 "$WAIT_SECONDS"); do
  if curl -fsS "http://127.0.0.1:$PORT$HEALTH_PATH" >/dev/null 2>&1; then healthy=yes; break; fi
  sleep 1
done

log="$BASE/deployments.log"
if [ -n "$healthy" ]; then
  [ -n "$previous" ] && docker rm -f "$previous" >/dev/null 2>&1
  echo "$(date -u +%FT%TZ) $SERVICE $VERSION sha256=$SHA256 deployed" >> "$log"
  echo "$SERVICE $VERSION is healthy on port $PORT"
  exit 0
fi

echo "$SERVICE $VERSION did not become healthy within ${WAIT_SECONDS}s. Last log lines:" >&2
docker logs --tail 30 "$SERVICE" >&2 || true
docker rm -f "$SERVICE" >/dev/null 2>&1 || true
if [ -n "$previous" ]; then
  docker rename "$previous" "$SERVICE"
  docker start "$SERVICE" >/dev/null
  echo "$(date -u +%FT%TZ) $SERVICE $VERSION FAILED, previous version restarted" >> "$log"
  echo "The previous version was started again." >&2
else
  echo "$(date -u +%FT%TZ) $SERVICE $VERSION FAILED, nothing to go back to" >> "$log"
fi
exit 1
