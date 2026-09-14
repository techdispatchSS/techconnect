# Generates the AWS environment recreation runbook PDF.
from reportlab.lib.pagesizes import LETTER
from reportlab.lib.units import inch
from reportlab.lib import colors
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.enums import TA_LEFT
from reportlab.platypus import (
    SimpleDocTemplate, Paragraph, Spacer, Preformatted, PageBreak,
    Table, TableStyle, HRFlowable, ListFlowable, ListItem
)
from reportlab.pdfbase.pdfmetrics import registerFont
from reportlab.pdfbase.ttfonts import TTFont

OUT = r"C:\Users\User\Desktop\Indomisa\Repos\techdispatch\aws\techconnect-aws-runbook.pdf"

styles = getSampleStyleSheet()

title_style = ParagraphStyle("TitleX", parent=styles["Title"], fontSize=22, spaceAfter=4)
subtitle_style = ParagraphStyle("SubtitleX", parent=styles["Normal"], fontSize=11, textColor=colors.HexColor("#555555"), spaceAfter=18)
h1 = ParagraphStyle("H1X", parent=styles["Heading1"], fontSize=16, spaceBefore=18, spaceAfter=8, textColor=colors.HexColor("#1a1a1a"))
h2 = ParagraphStyle("H2X", parent=styles["Heading2"], fontSize=12.5, spaceBefore=12, spaceAfter=6, textColor=colors.HexColor("#c2410c"))
body = ParagraphStyle("BodyX", parent=styles["Normal"], fontSize=9.7, leading=14, spaceAfter=6)
note = ParagraphStyle("NoteX", parent=styles["Normal"], fontSize=9.2, leading=13, spaceAfter=8,
                       backColor=colors.HexColor("#fff7ed"), borderColor=colors.HexColor("#fdba74"),
                       borderWidth=0.75, borderPadding=8, leftIndent=2, rightIndent=2)
code_style = ParagraphStyle("CodeX", parent=styles["Code"], fontName="Courier", fontSize=8.3, leading=11,
                             backColor=colors.HexColor("#f4f4f5"), borderColor=colors.HexColor("#d4d4d8"),
                             borderWidth=0.5, borderPadding=6, spaceAfter=10)
step_style = ParagraphStyle("StepX", parent=body, leftIndent=14, spaceAfter=4)

story = []

def h1_(t):
    story.append(Paragraph(t, h1))

def h2_(t):
    story.append(Paragraph(t, h2))

def p(t):
    story.append(Paragraph(t, body))

def steps(items):
    story.append(ListFlowable(
        [ListItem(Paragraph(i, step_style), leftIndent=14) for i in items],
        bulletType="1", start=1, leftIndent=18, spaceBefore=2, spaceAfter=8
    ))

def code(t):
    story.append(Preformatted(t, code_style))

def note_(t):
    story.append(Paragraph(t, note))

def hr():
    story.append(HRFlowable(width="100%", thickness=0.6, color=colors.HexColor("#d4d4d8"), spaceBefore=4, spaceAfter=10))

# ---------------------------------------------------------------- Title page
story.append(Spacer(1, 60))
story.append(Paragraph("TechConnect — AWS Environment Runbook", title_style))
story.append(Paragraph("Step-by-step recreation guide for the production AWS deployment (ECS Express Mode, RDS, CloudFront). Every section gives the AWS Console path first, then the equivalent AWS CLI commands.", subtitle_style))

meta_table = Table([
    ["Account ID", "440427555121"],
    ["Region", "eu-west-1"],
    ["Application", "TechConnect (backend: Spring Boot / Java 21, frontend: Angular)"],
    ["Last verified", "September 2026"],
], colWidths=[110, 360])
meta_table.setStyle(TableStyle([
    ("FONTSIZE", (0, 0), (-1, -1), 9.5),
    ("FONTNAME", (0, 0), (0, -1), "Helvetica-Bold"),
    ("TEXTCOLOR", (0, 0), (0, -1), colors.HexColor("#c2410c")),
    ("BOTTOMPADDING", (0, 0), (-1, -1), 6),
    ("TOPPADDING", (0, 0), (-1, -1), 6),
    ("LINEBELOW", (0, 0), (-1, -1), 0.4, colors.HexColor("#e4e4e7")),
]))
story.append(meta_table)
story.append(Spacer(1, 24))

note_(
    "<b>Naming note:</b> resources created early in this project used the pre-rename product name "
    "<font face='Courier'>techdispatch</font>; the app itself was later renamed to "
    "<font face='Courier'>techconnect</font>. Existing resources that already exist under the old name "
    "(the RDS database/role, the S3 bucket, the ECR repository, the SSM parameter path) should be reused "
    "as-is rather than recreated under a new name — renaming an S3 bucket or ECR repository isn't possible "
    "in place. Anything genuinely new should use <font face='Courier'>techconnect-*</font> naming going forward."
)

story.append(Spacer(1, 8))
p("<b>Build order</b> (each step depends on the ones before it):")
steps([
    "RDS PostgreSQL database",
    "SSM Parameter Store secrets",
    "IAM roles for ECS",
    "ECR repository + Docker image",
    "ECS Express Gateway service",
    "Security group wiring (RDS &#8596; ECS)",
    "S3 bucket for the frontend build",
    "CloudFront distribution",
    "Post-deploy config update (app base URL)",
    "GitHub OIDC + CI/CD deploy role (optional)",
    "Cost budget + SNS + Lambda alert",
    "Verification checklist",
])

story.append(PageBreak())

# ============================================================== 1. RDS
h1_("1. RDS PostgreSQL Database")
p("A small, single-AZ Postgres instance in a private subnet — the only thing in this environment that holds real, "
  "hard-to-reproduce data, so it's the one resource in this guide you generally <b>don't</b> want to recreate casually.")

h2_("Console steps")
steps([
    "RDS console &rarr; <b>Databases</b> &rarr; <b>Create database</b>.",
    "Engine: <b>PostgreSQL</b>. Templates: <b>Free tier</b> (or Dev/Test if outside the free tier window).",
    "DB instance identifier: <font face='Courier'>techconnect-db</font>. Instance class: <font face='Courier'>db.t4g.micro</font> "
    "or <font face='Courier'>db.t3.micro</font> (free-tier eligible).",
    "Master username: <font face='Courier'>techconnect</font>. Master password: generate one and save it &mdash; "
    "it becomes the value you store in SSM in the next section.",
    "Storage: 20&nbsp;GB gp2 (within the free allowance).",
    "Connectivity: choose your VPC, <b>Public access: No</b>, create a new or existing security group "
    "(e.g. <font face='Courier'>techconnect-rds-sg</font> &mdash; you'll add its actual inbound rule in step 6, once "
    "the ECS service's security group exists).",
    "Additional configuration &rarr; Initial database name: <font face='Courier'>techconnect</font>.",
    "Create database. Wait for status <b>Available</b>, then note its <b>Endpoint</b> "
    "(Connectivity &amp; security tab) &mdash; you'll need it for the ECS task definition's "
    "<font face='Courier'>POSTGRES_HOST</font> env var.",
])

h2_("CLI alternative")
code("""aws rds create-db-instance \\
  --db-instance-identifier techconnect-db \\
  --db-instance-class db.t4g.micro \\
  --engine postgres \\
  --master-username techconnect \\
  --master-user-password "<choose-a-strong-password>" \\
  --allocated-storage 20 \\
  --db-name techconnect \\
  --vpc-security-group-ids <rds-sg-id> \\
  --no-publicly-accessible \\
  --region eu-west-1

# Wait for it to become available, then fetch the endpoint:
aws rds describe-db-instances --db-instance-identifier techconnect-db \\
  --query "DBInstances[0].Endpoint.Address" --output text --region eu-west-1""")

story.append(PageBreak())

# ============================================================== 2. SSM
h1_("2. SSM Parameter Store — Secrets")
p("Four SecureString parameters the ECS task pulls in at container start, instead of baking secrets into the "
  "task definition or the Docker image.")

h2_("Console steps")
steps([
    "Systems Manager console &rarr; <b>Parameter Store</b> &rarr; <b>Create parameter</b>, four times, one per secret below.",
    "For each: Tier <b>Standard</b>, Type <b>SecureString</b>, KMS key <b>alias/aws/ssm</b> (the default AWS-managed key "
    "is fine &mdash; no need for a customer-managed key).",
])
code("""/techdispatch/prod/db-password     -> the RDS master password from section 1
/techdispatch/prod/jwt-secret       -> openssl rand -base64 48   (generate fresh, never reuse a local dev value)
/techdispatch/prod/mail-username    -> your SES SMTP username
/techdispatch/prod/mail-password    -> your SES SMTP password""")
note_(
    "These keep the pre-rename <font face='Courier'>/techdispatch/prod/*</font> path because that's what "
    "already exists and what the IAM policy in the next section is scoped to. There's no functional reason "
    "to rename the path &mdash; only the product-facing name changed, not this internal identifier."
)

h2_("CLI alternative")
code("""aws ssm put-parameter --name "/techdispatch/prod/db-password" --type SecureString \\
  --value "<rds-master-password>" --region eu-west-1

aws ssm put-parameter --name "/techdispatch/prod/jwt-secret" --type SecureString \\
  --value "$(openssl rand -base64 48)" --region eu-west-1

aws ssm put-parameter --name "/techdispatch/prod/mail-username" --type SecureString \\
  --value "<ses-smtp-username>" --region eu-west-1

aws ssm put-parameter --name "/techdispatch/prod/mail-password" --type SecureString \\
  --value "<ses-smtp-password>" --region eu-west-1""")

story.append(PageBreak())

# ============================================================== 3. IAM roles
h1_("3. IAM Roles for ECS")
p("Two roles ECS Express Mode needs: an execution role (lets the container agent pull the image, read secrets, "
  "write logs) and an infrastructure role (lets the Express Mode control plane manage the ALB/target group on your behalf).")

h2_("Console steps")
steps([
    "IAM console &rarr; <b>Roles</b> &rarr; <b>Create role</b> &rarr; Trusted entity: <b>AWS service</b> &rarr; "
    "Use case: <b>Elastic Container Service</b> &rarr; <b>Elastic Container Service Task</b>.",
    "Name it <font face='Courier'>ecsTaskExecutionRole</font>. Attach managed policy "
    "<font face='Courier'>AmazonECSTaskExecutionRolePolicy</font>.",
    "On the same role, add an inline policy named <font face='Courier'>techdispatch-ssm-read</font> "
    "(JSON below) so it can read the four parameters from section 2.",
    "Add a second inline policy for KMS &mdash; SecureString parameters need <font face='Courier'>kms:Decrypt</font> "
    "in addition to <font face='Courier'>ssm:GetParameters</font>, or the task will fail with "
    "AccessDenied on startup even though the SSM permission looks correct.",
    "Create a second role, same trusted-entity flow, named "
    "<font face='Courier'>ecsInfrastructureRoleForExpressServices</font>, and attach the managed policy "
    "<font face='Courier'>AmazonECSInfrastructureRoleforExpressGatewayServices</font>.",
    "Double-check the <b>Trust relationships</b> tab on both roles shows <font face='Courier'>ecs-tasks.amazonaws.com</font> "
    "(execution role) / <font face='Courier'>ecs.amazonaws.com</font> (infrastructure role) as the trusted principal &mdash; "
    "a role created through a different console flow can end up with no trust policy at all, which fails "
    "with \"cannot be assumed\" the first time anything tries to use it.",
])

h2_("SSM read policy (attach to ecsTaskExecutionRole)")
code("""{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": "ssm:GetParameters",
    "Resource": "arn:aws:ssm:eu-west-1:440427555121:parameter/techdispatch/prod/*"
  }]
}""")

h2_("KMS decrypt policy (attach to ecsTaskExecutionRole)")
code("""{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": "kms:Decrypt",
    "Resource": "arn:aws:kms:eu-west-1:440427555121:key/*",
    "Condition": {
      "StringEquals": { "kms:ViaService": "ssm.eu-west-1.amazonaws.com" }
    }
  }]
}""")

h2_("CLI alternative")
code("""aws iam create-role --role-name ecsTaskExecutionRole \\
  --assume-role-policy-document '{
    "Version": "2012-10-17",
    "Statement": [{"Effect": "Allow", "Principal": {"Service": "ecs-tasks.amazonaws.com"}, "Action": "sts:AssumeRole"}]
  }'

aws iam attach-role-policy --role-name ecsTaskExecutionRole \\
  --policy-arn arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy

aws iam put-role-policy --role-name ecsTaskExecutionRole \\
  --policy-name techdispatch-ssm-read --policy-document file://ssm-read-policy.json

aws iam put-role-policy --role-name ecsTaskExecutionRole \\
  --policy-name techdispatch-kms-decrypt --policy-document file://kms-decrypt-policy.json

aws iam create-role --role-name ecsInfrastructureRoleForExpressServices \\
  --assume-role-policy-document '{
    "Version": "2012-10-17",
    "Statement": [{"Effect": "Allow", "Principal": {"Service": "ecs.amazonaws.com"}, "Action": "sts:AssumeRole"}]
  }'

aws iam attach-role-policy --role-name ecsInfrastructureRoleForExpressServices \\
  --policy-arn arn:aws:iam::aws:policy/service-role/AmazonECSInfrastructureRoleforExpressGatewayServices""")

story.append(PageBreak())

# ============================================================== 4. ECR
h1_("4. ECR Repository &amp; Docker Image")

h2_("Console steps")
steps([
    "ECR console &rarr; <b>Repositories</b> &rarr; <b>Create repository</b> &rarr; name "
    "<font face='Courier'>techdispatch-backend</font> (reuse the existing repo if it's still there &mdash; "
    "don't create a second one under a different name, or every reference to it elsewhere in this guide "
    "needs updating).",
    "Click into the repository &rarr; <b>View push commands</b> for the exact login/tag/push sequence for your OS.",
    "From the <font face='Courier'>backend/</font> directory of the repo, build and push the image "
    "(see CLI block &mdash; the console's own \"push commands\" panel gives you the same thing pre-filled "
    "with your account ID).",
])

h2_("CLI alternative")
code("""cd backend
aws ecr get-login-password --region eu-west-1 | \\
  docker login --username AWS --password-stdin 440427555121.dkr.ecr.eu-west-1.amazonaws.com

docker build -t 440427555121.dkr.ecr.eu-west-1.amazonaws.com/techdispatch-backend:latest .
docker push 440427555121.dkr.ecr.eu-west-1.amazonaws.com/techdispatch-backend:latest""")
note_(
    "If <font face='Courier'>aws ecr get-login-password</font> fails with an OAuth2/authorization-grant "
    "error, your AWS CLI credentials have expired or the active profile is misconfigured &mdash; run "
    "<font face='Courier'>aws sts get-caller-identity</font> first to confirm you have a working session "
    "before troubleshooting anything ECR-specific."
)

story.append(PageBreak())

# ============================================================== 5. ECS Express
h1_("5. ECS Express Gateway Service")
p("This single command provisions the Fargate task, an Application Load Balancer, a target group, and wires "
  "them together &mdash; it's the replacement for AWS App Runner, which stopped accepting new customers on "
  "2026-04-30.")

h2_("Console steps")
steps([
    "ECS console &rarr; <b>Clusters</b> &rarr; <b>default</b> (or create a cluster first if none exists) &rarr; "
    "<b>Express services</b> tab &rarr; <b>Create</b>.",
    "Container image: the ECR image URI from section 4. Container port: <font face='Courier'>8080</font>.",
    "Environment variables: see the table below.",
    "Secrets: map each to its SSM parameter from section 2 (use \"Value from\" &mdash; the parameter's "
    "<b>name or ARN</b>, never the parameter's decrypted value itself; pasting the actual secret value here "
    "instead of a reference is a real mistake that's easy to make and causes AccessDenied errors that look "
    "like an IAM problem but aren't).",
    "Health check path: <font face='Courier'>/api/actuator/health</font>.",
    "Execution role: <font face='Courier'>ecsTaskExecutionRole</font>. Infrastructure role: "
    "<font face='Courier'>ecsInfrastructureRoleForExpressServices</font>.",
    "Networking: choose the same VPC as the RDS instance from section 1 (a mismatch here is a common, "
    "hard-to-diagnose failure &mdash; ECS Express Mode can silently pick a different default VPC than "
    "your database lives in, and security groups can't reference each other across VPCs).",
    "Create, and wait for the deployment to reach steady state. Note the <b>Application URL</b> "
    "(<font face='Courier'>https://&lt;random&gt;.ecs.eu-west-1.on.aws</font>) &mdash; this is the value "
    "you'll point CloudFront's second origin at in section 8, and it changes every time the service is "
    "deleted and recreated.",
])

h2_("Environment variables")
env_table = Table([
    ["POSTGRES_HOST", "<RDS endpoint from section 1>"],
    ["POSTGRES_PORT", "5432"],
    ["POSTGRES_DB", "techconnect"],
    ["POSTGRES_USER", "techconnect"],
    ["MAIL_HOST", "email-smtp.eu-west-1.amazonaws.com"],
    ["MAIL_PORT", "587"],
    ["TECHCONNECT_MAIL_FROM", "<a verified SES sender address>"],
    ["TECHCONNECT_BOOTSTRAP_ADMIN_EMAIL", "<the first admin's email>"],
    ["SPRING_PROFILES_ACTIVE", "prod"],
    ["TECHCONNECT_APP_BASE_URL", "<filled in after section 8 &mdash; see section 9>"],
], colWidths=[190, 280])
env_table.setStyle(TableStyle([
    ("FONTSIZE", (0, 0), (-1, -1), 8.3),
    ("FONTNAME", (0, 0), (0, -1), "Courier"),
    ("FONTNAME", (1, 0), (1, -1), "Courier"),
    ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
    ("TOPPADDING", (0, 0), (-1, -1), 5),
    ("GRID", (0, 0), (-1, -1), 0.4, colors.HexColor("#e4e4e7")),
    ("BACKGROUND", (0, 0), (0, -1), colors.HexColor("#f4f4f5")),
]))
story.append(env_table)
story.append(Spacer(1, 8))

note_(
    "<b>MAIL_HOST region matters:</b> this must match whatever region you actually verified your SES "
    "sending identity in &mdash; a mismatched region here produces a <font face='Courier'>java.net."
    "UnknownHostException</font> at runtime, not an obvious config error."
)
note_(
    "<b>Mail health check:</b> Spring Boot Actuator auto-registers a health indicator that actively "
    "probes SMTP connectivity. If SES/mail isn't reachable, <font face='Courier'>/api/actuator/health</font> "
    "reports 503 even though the app itself is completely fine &mdash; which fails the ALB's health check "
    "and blocks every deployment. Add <font face='Courier'>management.health.mail.enabled=false</font> to "
    "<font face='Courier'>application-prod.properties</font> before building the image in section 4, since "
    "mail delivery is designed to be best-effort in this app (a failed send is logged, not fatal)."
)

h2_("CLI alternative")
code("""aws ecs create-express-gateway-service \\
  --service-name techdispatch-backend \\
  --execution-role-arn arn:aws:iam::440427555121:role/ecsTaskExecutionRole \\
  --infrastructure-role-arn arn:aws:iam::440427555121:role/ecsInfrastructureRoleForExpressServices \\
  --health-check-path "/api/actuator/health" \\
  --cpu 256 --memory 512 \\
  --primary-container '{
    "image": "440427555121.dkr.ecr.eu-west-1.amazonaws.com/techdispatch-backend:latest",
    "containerPort": 8080,
    "environment": [
      {"name": "POSTGRES_HOST", "value": "<rds-endpoint>"},
      {"name": "POSTGRES_PORT", "value": "5432"},
      {"name": "POSTGRES_DB", "value": "techconnect"},
      {"name": "POSTGRES_USER", "value": "techconnect"},
      {"name": "MAIL_HOST", "value": "email-smtp.eu-west-1.amazonaws.com"},
      {"name": "MAIL_PORT", "value": "587"},
      {"name": "TECHCONNECT_MAIL_FROM", "value": "<verified-ses-sender>"},
      {"name": "TECHCONNECT_BOOTSTRAP_ADMIN_EMAIL", "value": "<admin-email>"},
      {"name": "SPRING_PROFILES_ACTIVE", "value": "prod"},
      {"name": "TECHCONNECT_APP_BASE_URL", "value": "placeholder-filled-in-after-section-8"}
    ],
    "secrets": [
      {"name": "POSTGRES_PASSWORD", "valueFrom": "arn:aws:ssm:eu-west-1:440427555121:parameter/techdispatch/prod/db-password"},
      {"name": "TECHCONNECT_JWT_SECRET", "valueFrom": "arn:aws:ssm:eu-west-1:440427555121:parameter/techdispatch/prod/jwt-secret"},
      {"name": "MAIL_USERNAME", "valueFrom": "arn:aws:ssm:eu-west-1:440427555121:parameter/techdispatch/prod/mail-username"},
      {"name": "MAIL_PASSWORD", "valueFrom": "arn:aws:ssm:eu-west-1:440427555121:parameter/techdispatch/prod/mail-password"}
    ]
  }' \\
  --network-configuration '{"awsvpcConfiguration":{"subnets":["<rds-vpc-private-subnet-ids>"]}}' \\
  --monitor-resources

# Note the returned serviceArn and Application URL from the output.""")

story.append(PageBreak())

# ============================================================== 6. SG wiring
h1_("6. Security Group Wiring (RDS &#8596; ECS)")
p("RDS sits in a private subnet and accepts nothing by default. The ECS task needs an explicit inbound rule "
  "on the RDS security group &mdash; sourced from the ECS task's own security group, not a CIDR block.")

h2_("Console steps")
steps([
    "ECS console &rarr; your Express service &rarr; <b>Configuration and networking</b> (or the "
    "<b>Networking</b> tab) &rarr; note the <b>Security group</b> ID attached to the running task "
    "(not the load balancer's own security group &mdash; check the task's actual network interface if "
    "more than one security group is listed, since Express Mode often creates one for the gateway/ALB "
    "and a separate one for the task itself).",
    "EC2 console &rarr; <b>Security Groups</b> &rarr; the RDS instance's security group &rarr; "
    "<b>Edit inbound rules</b> &rarr; <b>Add rule</b>: Type <b>PostgreSQL</b>, Port <b>5432</b>, "
    "Source &rarr; search for and select the ECS task's security group ID (a value starting "
    "<font face='Courier'>sg-</font>, never a rule ID starting <font face='Courier'>sgr-</font> &mdash; "
    "picking a rule ID by mistake from the autocomplete produces a "
    "\"CIDR block, a security group ID or a prefix list has to be specified\" error).",
    "Save rules.",
])
note_(
    "If saving produces \"You have specified two resources that belong to different networks,\" the ECS "
    "service and the RDS instance are in different VPCs &mdash; security group references only work "
    "within the same VPC. The fix is deleting and recreating the ECS Express service pointed at RDS's "
    "own VPC/subnets (section 5), not adjusting this rule."
)

h2_("CLI alternative")
code("""aws ec2 authorize-security-group-ingress \\
  --group-id <rds-security-group-id> \\
  --protocol tcp --port 5432 \\
  --source-group <ecs-task-security-group-id> \\
  --region eu-west-1""")

story.append(PageBreak())

# ============================================================== 7. S3
h1_("7. S3 Bucket for the Frontend Build")

h2_("Console steps")
steps([
    "S3 console &rarr; <b>Create bucket</b> &rarr; name it something globally unique, e.g. "
    "<font face='Courier'>techdispatch-frontend-&lt;suffix&gt;</font> (reuse the existing bucket if it's "
    "still there).",
    "Block all public access &mdash; CloudFront reaches it via Origin Access Control, not a public bucket "
    "policy, so this should stay fully blocked.",
    "Build and upload the Angular app:",
])
code("""cd frontend
npm ci
npm run build -- --configuration production
aws s3 sync dist/frontend/browser s3://techdispatch-frontend-<suffix> --delete --region eu-west-1""")

h2_("CLI alternative (bucket creation)")
code("""aws s3 mb s3://techdispatch-frontend-<suffix> --region eu-west-1

aws s3api put-public-access-block \\
  --bucket techdispatch-frontend-<suffix> \\
  --public-access-block-configuration \\
  BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true""")

story.append(PageBreak())

# ============================================================== 8. CloudFront
h1_("8. CloudFront Distribution")
p("One distribution, two origins: S3 for the static Angular build, and the ECS Express service for "
  "<font face='Courier'>/api/*</font>. This is what makes the browser see everything as one origin, so "
  "there's no CORS configuration needed anywhere in the app.")

h2_("Console steps")
steps([
    "CloudFront console &rarr; <b>Origin access</b> &rarr; <b>Create control setting</b> &rarr; name it "
    "<font face='Courier'>techconnect-oac</font>.",
    "<b>Distributions</b> &rarr; <b>Create distribution</b>.",
    "<b>Origin 1</b>: choose the S3 bucket from section 7 from the dropdown, attach the origin access "
    "control just created (accept the console's offer to update the bucket policy for you).",
    "Default cache behavior: <b>Viewer protocol policy &rarr; Redirect HTTP to HTTPS</b> (not \"HTTP and "
    "HTTPS\"), cache policy <b>CachingOptimized</b>.",
    "Settings: default root object <font face='Courier'>index.html</font>.",
    "Create the distribution, then go back in and add the second origin and behavior (CloudFront's "
    "create wizard only lets you fully configure one origin cleanly at a time; adding the rest after is "
    "completely normal and doesn't require recreating anything).",
    "<b>Origins</b> tab &rarr; <b>Create origin</b> &rarr; type the ECS Express service's Application URL "
    "hostname directly into the origin domain field as free text &mdash; it won't appear in the "
    "autocomplete dropdown (that list only surfaces resource types CloudFront recognizes natively, like "
    "S3 buckets or load balancers &mdash; a custom domain like this is still fully valid, just not suggested). "
    "Protocol: <b>HTTPS only</b>, port 443.",
    "<b>Do not</b> point this origin at the raw Application Load Balancer's own "
    "<font face='Courier'>.elb.amazonaws.com</font> hostname even if you can find it &mdash; that ALB "
    "typically has no valid HTTPS listener of its own; TLS termination happens at the Express service's "
    "managed <font face='Courier'>.on.aws</font> domain, which is what you must use here.",
    "<b>Behaviors</b> tab &rarr; <b>Create behavior</b> &rarr; path pattern <font face='Courier'>/api/*</font> "
    "&rarr; origin = the ECS origin just added &rarr; cache policy <b>CachingDisabled</b> &rarr; origin "
    "request policy <b>AllViewer</b> &rarr; confirm this behavior's precedence is <b>0</b> (ahead of the "
    "default <font face='Courier'>(*)</font> behavior at precedence 1) so it's actually evaluated first.",
    "<b>Error pages</b> tab &rarr; <b>Create custom error response</b>, twice: HTTP error code "
    "<b>403</b> &rarr; Customize response &rarr; response page path <font face='Courier'>/index.html</font> "
    "&rarr; HTTP response code <b>200</b>. Repeat for error code <b>404</b>. This is what makes refreshing "
    "the page on any Angular route (not just <font face='Courier'>/</font>) work instead of 403/404-ing, "
    "since those paths don't exist as real files in S3.",
])
note_(
    "<b>Watch out for:</b> the 403/404 &rarr; 200 override above applies distribution-wide, to any origin's "
    "error response &mdash; not just S3's. If the ECS origin itself ever returns a genuine 403 or 404 "
    "(for example, from Spring Security), this same rule will mask it behind a 200 response containing "
    "the Angular app's HTML instead of the real error, which is confusing to debug. If API calls seem to "
    "silently fail, check the raw response body, not just the status code."
)
note_(
    "Missing the <font face='Courier'>/api/*</font> behavior entirely produces a very similar-looking "
    "symptom: every API call quietly returns the Angular <font face='Courier'>index.html</font> with a "
    "200 status instead of hitting the backend at all, because the request falls through to the default "
    "S3 behavior and then gets caught by the same error-page override above."
)

h2_("CLI alternative")
p("CloudFront's CLI interface for a multi-origin, multi-behavior distribution is a single large JSON "
  "document rather than incremental commands, which makes the console meaningfully faster for this "
  "specific resource. If you do want the CLI form:")
code("""aws cloudfront create-origin-access-control \\
  --origin-access-control-config Name=techconnect-oac,SigningProtocol=sigv4,SigningBehavior=always,OriginAccessControlOriginType=s3

# Then build a full distribution-config.json (two origins, two cache behaviors, two custom
# error responses) and run:
aws cloudfront create-distribution --distribution-config file://distribution-config.json

# To modify an existing distribution instead:
aws cloudfront get-distribution-config --id <distribution-id> > current-config.json
# edit the ETag-stamped config, then:
aws cloudfront update-distribution --id <distribution-id> \\
  --distribution-config file://updated-config.json --if-match <etag-from-get>""")

story.append(PageBreak())

# ============================================================== 9. Post-deploy config
h1_("9. Post-Deploy Config Update")
p("Once the CloudFront distribution exists, you have its domain &mdash; feed that back into the backend "
  "so activation/reset links point at the right place.")

h2_("Console steps")
steps([
    "CloudFront console &rarr; your distribution &rarr; note the <b>Distribution domain name</b> "
    "(<font face='Courier'>dxxxxxxxxxxxxx.cloudfront.net</font>).",
    "ECS console &rarr; your Express service &rarr; <b>Update service</b> &rarr; edit the "
    "<font face='Courier'>TECHCONNECT_APP_BASE_URL</font> environment variable to "
    "<font face='Courier'>https://dxxxxxxxxxxxxx.cloudfront.net</font> &rarr; deploy.",
])

h2_("CLI alternative")
code("""aws ecs update-express-gateway-service \\
  --service-arn <service-arn-from-section-5> \\
  --primary-container '{"environment":[{"name":"TECHCONNECT_APP_BASE_URL","value":"https://dxxxxxxxxxxxxx.cloudfront.net"}]}'""")

story.append(PageBreak())

# ============================================================== 10. GitHub OIDC
h1_("10. GitHub OIDC + CI/CD Deploy Role (optional)")
p("Lets GitHub Actions push new images and redeploy without a long-lived AWS access key ever existing as "
  "a GitHub secret. Skip this section until the manual flow above is confirmed working end-to-end at "
  "least once.")

h2_("Console steps")
steps([
    "IAM console &rarr; <b>Identity providers</b> &rarr; <b>Add provider</b> &rarr; Provider type "
    "<b>OpenID Connect</b> &rarr; Provider URL <font face='Courier'>https://token.actions.githubusercontent.com</font> "
    "&rarr; Audience <font face='Courier'>sts.amazonaws.com</font>.",
    "<b>Roles</b> &rarr; <b>Create role</b> &rarr; Trusted entity type <b>Web identity</b> &rarr; select the "
    "provider just created &rarr; Audience <font face='Courier'>sts.amazonaws.com</font> &rarr; GitHub "
    "organization/repository: your actual repo (e.g. <font face='Courier'>your-org/your-repo</font>).",
    "Name it <font face='Courier'>techconnect-gha-deploy</font>. Attach an inline policy scoped to exactly: "
    "ECR push to the one repo, S3 sync to the one bucket, CloudFront invalidation on the one distribution, "
    "and <font face='Courier'>ecs:UpdateExpressGatewayService</font> on the one service &mdash; not broad "
    "account-wide permissions.",
    "In GitHub: repo <b>Settings &rarr; Secrets and variables &rarr; Actions</b> &rarr; add secret "
    "<font face='Courier'>AWS_DEPLOY_ROLE_ARN</font>, and variables <font face='Courier'>AWS_REGION</font>, "
    "<font face='Courier'>ECR_REPOSITORY_NAME</font>, <font face='Courier'>EXPRESS_SERVICE_ARN</font>, "
    "<font face='Courier'>S3_BUCKET_NAME</font>, <font face='Courier'>CLOUDFRONT_DISTRIBUTION_ID</font>.",
])

h2_("CLI alternative")
code("""aws iam create-open-id-connect-provider \\
  --url https://token.actions.githubusercontent.com \\
  --client-id-list sts.amazonaws.com \\
  --thumbprint-list 6938fd4d98bab03faadb97b34396831e3780aea1

aws iam create-role --role-name techconnect-gha-deploy \\
  --assume-role-policy-document file://trust-policy.json

aws iam put-role-policy --role-name techconnect-gha-deploy \\
  --policy-name techconnect-deploy-perms --policy-document file://permissions-policy.json""")
note_(
    "<font face='Courier'>trust-policy.json</font> needs a "
    "<font face='Courier'>token.actions.githubusercontent.com:sub</font> condition scoped to "
    "<font face='Courier'>repo:&lt;org&gt;/&lt;repo&gt;:*</font> &mdash; without it, any GitHub Actions "
    "workflow anywhere could assume this role."
)

story.append(PageBreak())

# ============================================================== 11. Budget
h1_("11. Cost Budget + SNS + Lambda Alert")
p("A plain AWS Budgets email notification can't carry custom instructions &mdash; only a fixed AWS-worded "
  "template. To get an actual custom message (what's running, what it costs, exact commands to shut it "
  "down) you need Budget &rarr; SNS topic &rarr; Lambda &rarr; SES send.")

h2_("Console steps")
steps([
    "SNS console &rarr; <b>Topics</b> &rarr; <b>Create topic</b> &rarr; Standard &rarr; name "
    "<font face='Courier'>techconnect-budget-alert</font>.",
    "Lambda console &rarr; <b>Create function</b> &rarr; Author from scratch &rarr; name "
    "<font face='Courier'>techconnect-budget-alert</font>, runtime Python 3.13.",
    "Execution role: create a new role (or reuse an existing one) trusted by "
    "<font face='Courier'>lambda.amazonaws.com</font> &mdash; check its <b>Trust relationships</b> tab "
    "explicitly if you created the role through a different console flow, since a role created for another "
    "purpose can end up with no Lambda trust statement at all, which fails with \"cannot be assumed by "
    "Lambda\" the moment you try to attach it.",
    "Attach <font face='Courier'>AWSLambdaBasicExecutionRole</font> (CloudWatch Logs) and an inline policy "
    "granting <font face='Courier'>ses:SendEmail</font>.",
    "Paste in the custom send-email code (see <font face='Courier'>lambda_function.py</font> alongside "
    "this document), deploy.",
    "SNS console &rarr; the topic &rarr; <b>Create subscription</b> &rarr; protocol <b>AWS Lambda</b> "
    "&rarr; select the function (this also grants SNS permission to invoke it automatically).",
    "Billing console &rarr; <b>Budgets</b> &rarr; your budget &rarr; <b>Edit</b> &rarr; <b>Notifications</b> "
    "&rarr; add a threshold at <b>95%</b> of actual cost &rarr; recipient type <b>Amazon SNS topic</b> "
    "&rarr; select the topic above. Keep a separate, simpler 80% plain-email notification if you want an "
    "earlier heads-up too.",
])
note_(
    "SES sandbox mode requires <b>both</b> the sender and every recipient address to be individually "
    "verified identities, or the Lambda's send call fails silently &mdash; check "
    "<font face='Courier'>/aws/lambda/techconnect-budget-alert</font> in CloudWatch Logs if no email "
    "arrives."
)
note_(
    "AWS Budgets' own \"actual spend\" figure lags the Billing dashboard / Cost Explorer by up to 24 "
    "hours. A budget showing $0 right after creating or editing it, while the dashboard shows real spend, "
    "is normal and not a sign of misconfiguration."
)

h2_("CLI alternative")
code("""aws sns create-topic --name techconnect-budget-alert --region eu-west-1

zip function.zip lambda_function.py
aws lambda create-function --function-name techconnect-budget-alert \\
  --runtime python3.13 --handler lambda_function.handler \\
  --role arn:aws:iam::440427555121:role/techconnect-budget-alert-role \\
  --zip-file fileb://function.zip --region eu-west-1

aws sns subscribe --topic-arn arn:aws:sns:eu-west-1:440427555121:techconnect-budget-alert \\
  --protocol lambda \\
  --notification-endpoint arn:aws:lambda:eu-west-1:440427555121:function:techconnect-budget-alert

aws lambda add-permission --function-name techconnect-budget-alert \\
  --statement-id sns-invoke --action lambda:InvokeFunction \\
  --principal sns.amazonaws.com \\
  --source-arn arn:aws:sns:eu-west-1:440427555121:techconnect-budget-alert

# Test without waiting for a real threshold:
aws sns publish --topic-arn arn:aws:sns:eu-west-1:440427555121:techconnect-budget-alert \\
  --message "test" --region eu-west-1""")

story.append(PageBreak())

# ============================================================== 12. Verification
h1_("12. Verification Checklist")
steps([
    "<font face='Courier'>curl https://&lt;express-service&gt;.ecs.eu-west-1.on.aws/api/actuator/health</font> "
    "returns <font face='Courier'>{\"status\":\"UP\"}</font> &mdash; confirms the backend, DB connection, "
    "and IAM/secrets wiring are all correct, independent of CloudFront.",
    "Open the CloudFront domain in a browser &mdash; the Angular app loads.",
    "Submit the sign-in form (or any API-calling action) and confirm, in the browser's network tab, that "
    "the request actually reaches <font face='Courier'>/api/*</font> and returns real JSON &mdash; not the "
    "SPA's own <font face='Courier'>index.html</font> in disguise (see the note in section 8).",
    "CloudWatch Logs for the ECS task shows the bootstrap admin's activation link (logged regardless of "
    "whether the email itself sends successfully). Open it, activate the account, log in.",
    "<font face='Courier'>psql</font> into RDS directly (temporarily allow your IP on its security group, "
    "then revert) and confirm the expected tables/rows exist.",
])

story.append(Spacer(1, 20))
hr()
story.append(Paragraph(
    "Generated for the TechConnect project &mdash; account 440427555121, region eu-west-1. "
    "Treat every password/secret value in this document as a placeholder to be generated fresh, never reused "
    "from a prior environment.",
    ParagraphStyle("Footer", parent=styles["Normal"], fontSize=8, textColor=colors.HexColor("#888888"))
))

doc = SimpleDocTemplate(
    OUT, pagesize=LETTER,
    topMargin=54, bottomMargin=54, leftMargin=56, rightMargin=56,
    title="TechConnect AWS Environment Runbook",
    author="Gerard Nagura",
)
doc.build(story)
print("wrote", OUT)
