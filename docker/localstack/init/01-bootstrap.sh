#!/bin/sh
# Bootstraps baseline AWS resources in LocalStack on container startup.
# Runs automatically via the /etc/localstack/init/ready.d hook.
set -eu

REGION="${AWS_DEFAULT_REGION:-us-east-1}"
ENDPOINT="http://localhost:4566"

awslocal --region "$REGION" s3 mb "s3://techconnect-local" || true

awslocal --region "$REGION" sqs create-queue --queue-name techconnect-local-queue || true

awslocal --region "$REGION" sns create-topic --name techconnect-local-topic || true

awslocal --region "$REGION" ses verify-email-identity --email-address no-reply@techconnect.app || true

# cognito-idp is LocalStack Pro-only; this silently no-ops on Community edition
# (set LOCALSTACK_AUTH_TOKEN + switch to localstack/localstack-pro to enable it).
awslocal --region "$REGION" cognito-idp create-user-pool --pool-name techconnect-local-pool || true

echo "LocalStack bootstrap complete (endpoint: $ENDPOINT)"
