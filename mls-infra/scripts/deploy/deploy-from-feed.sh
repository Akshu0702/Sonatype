#!/usr/bin/env bash
# Run by the release pipeline (cd-mls-<service>) on the build agent. Takes the newest snapshot of a service from the Azure Artifacts feed
# mls-maven and, unless DRY_RUN is true, deploys it to the application host through Session Manager.
#
# DRY_RUN=true (the default) only downloads, checks and says what it would do. It makes no AWS call at all.
#
# Environment:
#   SERVICE        short name, e.g. tenants-service            GROUP, ARTIFACT   Maven coordinates in the feed
#   BASEVER        the snapshot line, e.g. 0.1.0-SNAPSHOT      PORT, HEALTH_PATH how the service is reached on the host
#   FEED_TOKEN     token that may read the feed (the release's System.AccessToken)
#   DRY_RUN        default true
#   Needed only when DRY_RUN is not true:
#   DEPLOY_BUCKET  an S3 bucket named mls-*, read by the application host's role      APP_HOST_ID  the host's instance id (i-...)
#   AWS_REGION     default ap-south-1; the agent's instance role supplies the credentials
set -euo pipefail

: "${SERVICE:?}" "${GROUP:?}" "${ARTIFACT:?}" "${BASEVER:?}" "${PORT:?}" "${FEED_TOKEN:?}"
HEALTH_PATH="${HEALTH_PATH:-/actuator/health}"
DRY_RUN="${DRY_RUN:-true}"
FEED="${FEED_URL:-https://pkgs.dev.azure.com/ML-AIML-COE/Sonatype-MLS/_packaging/mls-maven/maven/v1}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
base="$FEED/$(echo "$GROUP" | tr . /)/$ARTIFACT/$BASEVER"
auth=(-u "build:$FEED_TOKEN")

snap="$(curl -fsS "${auth[@]}" "$base/maven-metadata.xml" | python3 -c 'import sys,re;m=re.search(r"<extension>jar</extension>\s*<value>([^<]+)</value>",sys.stdin.read());print(m.group(1) if m else "")')"
[ -n "$snap" ] || { echo "No jar for $GROUP:$ARTIFACT $BASEVER in the feed." >&2; exit 1; }
out="${WORK_DIR:-$(mktemp -d)}"
curl -fsS "${auth[@]}" -o "$out/app.jar" "$base/$ARTIFACT-$snap.jar"
sha="$(sha256sum "$out/app.jar" | cut -d' ' -f1)"
echo "Feed version: $snap"
echo "sha256:       $sha  ($(wc -c < "$out/app.jar") bytes)"

if [ "$DRY_RUN" = "true" ]; then
  echo "DRY RUN: nothing was uploaded and nothing was changed on the host."
  echo "A real run would: put the jar in s3://${DEPLOY_BUCKET:-<bucket>}/deploy/$SERVICE/$snap/, then run host-deploy.sh on ${APP_HOST_ID:-<host>} (port $PORT, health $HEALTH_PATH)."
  exit 0
fi

: "${DEPLOY_BUCKET:?DEPLOY_BUCKET is required when DRY_RUN is not true}" "${APP_HOST_ID:?APP_HOST_ID is required when DRY_RUN is not true}"
region="${AWS_REGION:-ap-south-1}"
key="deploy/$SERVICE/$snap/app.jar"
aws s3 cp --region "$region" "$out/app.jar" "s3://$DEPLOY_BUCKET/$key" --only-show-errors

# The host fetches the jar with its own role, then runs host-deploy.sh (carried in the command, so the host needs no checkout).
script="$(base64 -w0 "$HERE/host-deploy.sh")"
# Ubuntu has no awscli package; the host installs the official v2 once if it is missing (no commas or braces: the value goes through the CLI shorthand parser).
cli="if ! command -v aws >/dev/null 2>&1; then cd /tmp; curl -fsSL https://awscli.amazonaws.com/awscli-exe-linux-\$(uname -m).zip -o awscliv2.zip; unzip -q -o awscliv2.zip; ./aws/install --update; rm -rf awscliv2.zip aws; fi"
cmd="set -e; $cli; aws s3 cp --region $region s3://$DEPLOY_BUCKET/$key /tmp/$SERVICE.jar --only-show-errors; echo $script | base64 -d > /tmp/host-deploy.sh; SERVICE=$SERVICE VERSION=$snap JAR=/tmp/$SERVICE.jar SHA256=$sha PORT=$PORT HEALTH_PATH=$HEALTH_PATH bash /tmp/host-deploy.sh; rm -f /tmp/$SERVICE.jar /tmp/host-deploy.sh"
id="$(aws ssm send-command --region "$region" --instance-ids "$APP_HOST_ID" --document-name AWS-RunShellScript \
      --comment "deploy $SERVICE $snap" --parameters "commands=[\"$cmd\"]" --query Command.CommandId --output text)"
echo "Session Manager command $id sent to $APP_HOST_ID"
for _ in $(seq 1 120); do
  status="$(aws ssm get-command-invocation --region "$region" --command-id "$id" --instance-id "$APP_HOST_ID" --query Status --output text 2>/dev/null || echo Pending)"
  case "$status" in Success|Failed|Cancelled|TimedOut) break ;; esac
  sleep 5
done
aws ssm get-command-invocation --region "$region" --command-id "$id" --instance-id "$APP_HOST_ID" --query '[StandardOutputContent,StandardErrorContent]' --output text || true
[ "$status" = "Success" ] || { echo "Deployment ended as: $status" >&2; exit 1; }
echo "Deployed $SERVICE $snap"
