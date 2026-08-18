#!/bin/sh
# Bootstraps baseline AWS resources in LocalStack on container startup.
# Runs automatically via the /etc/localstack/init/ready.d hook.
set -eu

REGION="${AWS_DEFAULT_REGION:-us-east-1}"
ENDPOINT="http://localhost:4566"

awslocal --region "$REGION" s3 mb "s3://techdispatch-local" || true

awslocal --region "$REGION" sqs create-queue --queue-name techdispatch-local-queue || true

awslocal --region "$REGION" sns create-topic --name techdispatch-local-topic || true

awslocal --region "$REGION" ses verify-email-identity --email-address no-reply@techdispatch.local || true

awslocal --region "$REGION" cognito-idp create-user-pool --pool-name techdispatch-local-pool || true

echo "LocalStack bootstrap complete (endpoint: $ENDPOINT)"
