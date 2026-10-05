# Deploying Carryon on AWS (console walkthrough)

Target: ~$40/month, or mostly free-tier the first year. **Set a budget alarm first** (Billing → Budgets → $10 monthly, email alert).
Use one region everywhere (e.g. `ap-south-1` Mumbai).

```
Browser ─► CloudFront ─┬─ default  ─► S3 (React build)
                       └─ /api/*   ─► ALB ─► ECS Fargate (Spring Boot) ─► RDS PostgreSQL
```

CloudFront serves the SPA and forwards `/api/*` to the API, so the browser sees **one origin**: no CORS and no frontend code changes (the app already calls `/api`).

## 0. Prerequisites
- AWS account, IAM Identity Center or an IAM user with admin for setup. **Never use the root user day to day; enable MFA.**
- Docker image builds locally: `docker build -t carryon-api backend`.

## 1. Network (use the default VPC for the MVP)
Create three security groups:
| SG | Inbound |
|---|---|
| `alb-sg` | 80 from anywhere (CloudFront will front it) |
| `api-sg` | 8080 **from `alb-sg` only** |
| `db-sg` | 5432 **from `api-sg` only** |

The database is never publicly reachable. That is the key security point to explain in interviews.

## 2. Database: RDS PostgreSQL
RDS → Create database → PostgreSQL 16 → **Free tier** template (db.t4g.micro), storage 20 GB gp3, **Public access: No**, security group `db-sg`, **encryption on**, automated backups 7 days. Database name `carryon`.
Let RDS manage the master password in **Secrets Manager** ("Manage master credentials").

## 3. Secrets
Secrets Manager (or SSM Parameter Store SecureString, which is free) → create:
- `carryon/jwt-secret`: 48+ random characters (`openssl rand -base64 48`)
- `carryon/admin-password`: a strong password

## 4. Container registry
ECR → create private repo `carryon-api`, then push (commands are shown under "View push commands"):
```bash
aws ecr get-login-password --region ap-south-1 | docker login --username AWS --password-stdin <acct>.dkr.ecr.ap-south-1.amazonaws.com
docker tag carryon-api:latest <acct>.dkr.ecr.ap-south-1.amazonaws.com/carryon-api:latest
docker push <acct>.dkr.ecr.ap-south-1.amazonaws.com/carryon-api:latest
```

## 5. API on ECS Fargate
1. ECS → Create cluster `carryon` (Fargate).
2. Task definition: Fargate, 0.5 vCPU / 1 GB, container `api` from the ECR image, port 8080, log driver `awslogs` (CloudWatch).
   Environment: `DB_URL=jdbc:postgresql://<rds-endpoint>:5432/carryon`, `DB_USER`, `CORS_ORIGIN=https://<cloudfront-domain>`, `ADMIN_EMAIL`.
   Secrets (from Secrets Manager/SSM): `DB_PASSWORD`, `JWT_SECRET`, `ADMIN_PASSWORD`.
   Task execution role must be allowed to read those secrets.
3. Load balancer: Application LB `carryon-alb`, `alb-sg`, listener 80 → target group (IP type, port 8080, **health check path `/actuator/health`**).
4. Service: 1 task, `api-sg`, attach to the target group.
5. Check: `http://<alb-dns>/actuator/health` returns `{"status":"UP"}`. Flyway creates the schema on first start.

## 6. Frontend: S3 + CloudFront
```bash
cd frontend && npm ci && npm run build
aws s3 sync dist s3://<bucket> --delete
```
1. S3 → new **private** bucket (Block Public Access ON).
2. CloudFront → distribution with origin S3 using **Origin Access Control** (let it update the bucket policy); default root object `index.html`.
3. Add a second origin: the ALB (HTTP only). Behaviour `/api/*` → ALB origin, cache policy **CachingDisabled**, origin request policy **AllViewerExceptHostHeader**, allowed methods **all**.
4. Error pages: 403/404 → `/index.html` with 200 (SPA routing).
5. Open `https://<id>.cloudfront.net`. Register and run the whole flow.

## 7. Observability (CloudWatch)
- Logs: `/ecs/carryon-api` (already wired).
- Alarms: ALB `HTTPCode_Target_5XX_Count` > 5 in 5 min; ECS CPU > 80% for 15 min; RDS CPU > 70% for 30 min; RDS free storage low. Send to an SNS email topic.

## 8. CI/CD (next step, optional)
Extend `.github/workflows/ci.yml`: on `main`, assume an AWS role via **GitHub OIDC** (no long-lived keys), push to ECR, then `aws ecs update-service --force-new-deployment`, and `aws s3 sync` + CloudFront invalidation for the frontend.

## Hardening before real users
- Real HTTPS to the ALB (ACM certificate) and a custom domain on CloudFront; add a `Content-Security-Policy` response header there.
- Change the default admin password; confirm `JWT_SECRET` is set (the dev default must never ship).
- AWS WAF rate-based rule on CloudFront (replaces the in-memory limiter once you run more than one task).
- Replace mock KYC and payments with real providers.
- RDS: enable deletion protection and Multi-AZ when real money flows.

## Cost notes
ALB (~$16–20) and Fargate (~$10–15) are the main costs; RDS and S3 are free-tier eligible for 12 months. Delete the ALB and stop the service when you are not demoing.
