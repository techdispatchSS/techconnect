import boto3

ses = boto3.client("ses", region_name="eu-west-1")

SENDER = "no-reply@techconnect.app"          # must be a verified SES identity
RECIPIENTS = ["gerard.nagura@gmail.com"]      # add other maintainers here (must be verified too if SES is still in sandbox)

SUBJECT = "⚠️ AWS budget at 95% — techconnect project (action needed if you want to pause spend)"

BODY = """Hi team,

The techdispatch-monthly AWS budget has hit 95% of its $25/month threshold. This is a heads-up, not an outage — nothing is broken. But if you'd rather stop spend now instead of riding out the rest of the month, here's exactly what's costing money and how to shut it down.

WHAT'S RUNNING AND COSTING MONEY:
- Application Load Balancer (~$18/month if left running 24/7) — auto-created by the ECS Express Gateway service
- ECS Fargate tasks (2x, usage-based) — the backend containers themselves
- NAT Gateway, if present (~$32/month if left running 24/7) — outbound internet access for SES email
- RDS PostgreSQL (should be $0 under Free Tier) — holds real application data, DO NOT DELETE

IF YOU WANT TO STOP THE SPEND RIGHT NOW:

1. Delete the ECS Express service (removes the load balancer + running containers in one step):
   aws ecs delete-express-gateway-service --service-arn arn:aws:ecs:eu-west-1:440427555121:service/default/techdispatch-backend-0898
   Or via console: ECS -> Clusters -> default -> Express services -> techdispatch-backend-0898 -> Delete service.

2. Delete the NAT Gateway, if one exists: VPC console -> NAT Gateways -> select it -> Delete. Then release its Elastic IP: EC2 console -> Elastic IPs -> select the now-unattached one -> Release.

3. Leave RDS running. Its own cost is small, and deleting it risks real data loss. If you want to pause it too, take a manual snapshot first (RDS console -> your instance -> Actions -> Take snapshot), then delete the instance — do not delete without a snapshot.

TO RESUME LATER: re-run the same create-express-gateway-service command (and recreate the NAT Gateway if deleted) with the same settings used originally.

Questions? Reach out to Gerard (gerard.nagura@gmail.com).
"""

def handler(event, context):
    ses.send_email(
        Source=SENDER,
        Destination={"ToAddresses": RECIPIENTS},
        Message={
            "Subject": {"Data": SUBJECT},
            "Body": {"Text": {"Data": BODY}},
        },
    )
    return {"statusCode": 200}