#!/usr/bin/env bash
# Stops before anything is planned if the credentials point at the wrong account or region.
# Needs: EXPECTED_ACCOUNT, EXPECTED_REGION, AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY in the environment.
set -euo pipefail

: "${EXPECTED_ACCOUNT:?EXPECTED_ACCOUNT is not set}"
: "${EXPECTED_REGION:?EXPECTED_REGION is not set}"
: "${AWS_ACCESS_KEY_ID:?AWS_ACCESS_KEY_ID is not set (variable group missing?)}"
: "${AWS_SECRET_ACCESS_KEY:?AWS_SECRET_ACCESS_KEY is not set (variable group missing?)}"

if [ "${AWS_DEFAULT_REGION:-}" != "$EXPECTED_REGION" ] || [ "${AWS_REGION:-$EXPECTED_REGION}" != "$EXPECTED_REGION" ]; then
  echo "##vso[task.logissue type=error]Region is not $EXPECTED_REGION. Refusing to continue."
  exit 1
fi

AWS_IMAGE="${AWS_CLI_IMAGE:-amazon/aws-cli:2.17.0}"
identity="$(docker run --rm -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY -e AWS_SESSION_TOKEN -e AWS_DEFAULT_REGION "$AWS_IMAGE" sts get-caller-identity --query '[Account,Arn]' --output text)"
account="$(echo "$identity" | awk '{print $1}')"
arn="$(echo "$identity" | awk '{print $2}')"
echo "Deploying as: $arn (account $account), region $EXPECTED_REGION"
if [ "$account" != "$EXPECTED_ACCOUNT" ]; then
  echo "##vso[task.logissue type=error]Account is $account, expected $EXPECTED_ACCOUNT. Refusing to continue."
  exit 1
fi
case "$arn" in
  *:user/mls-deploy|*:role/mls-deploy*) ;;
  *) echo "##vso[task.logissue type=error]Credentials belong to $arn, not the mls-deploy identity. Refusing to continue."; exit 1 ;;
esac
