# MyTadika — Running the Full Stack Locally

Three services make up the app. All six build phases in `CLAUDE.md` are
done, so this is the real flow — Supabase Auth (email/password + Google
OAuth), role-based dashboards, Student/Academic/Health modules, and AI
nutrition advice all work end-to-end.

```text
mytadika-frontend/   React (Vite)         → http://localhost:5173
mytadika-backend/    Spring Boot (Maven)  → http://localhost:8080
AI/api/              FastAPI microservice → http://localhost:8001
```

## Prerequisites

- Node.js 18+
- JDK 17+ (the bundled Maven wrapper handles the rest)
- Python 3.10+
- A Supabase project (PostgreSQL + Auth) — see `CLAUDE.md` Section 6 if you
  need to create one from scratch

## One-time setup

### 1. Frontend environment

```bash
cd mytadika-frontend
npm install
cp .env.example .env
```

Edit `.env` with your Supabase project's values (Dashboard → Project
Settings → API):

```ini
VITE_SUPABASE_URL=https://<project-ref>.supabase.co
VITE_SUPABASE_ANON_KEY=<anon-or-publishable-key>
VITE_API_BASE_URL=http://localhost:8080/api
```

### 2. Backend configuration

`mytadika-backend/src/main/resources/application.properties` is gitignored
(it holds DB/JWT secrets) — create it if it doesn't exist yet:

```properties
server.port=8080

spring.datasource.url=jdbc:postgresql://<pooler-host>.pooler.supabase.com:5432/postgres?sslmode=require
spring.datasource.username=postgres.<project-ref>
spring.datasource.password=${SUPABASE_DB_PASSWORD:<your-db-password>}
spring.datasource.driver-class-name=org.postgresql.Driver
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false
spring.jpa.open-in-view=false

ai.service.url=http://localhost:8001/api/predict

# Use the Session Pooler connection string (port 5432), not the Transaction
# pooler (6543) or the raw direct connection — see CLAUDE.md §3/§6.5 for why.
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://<project-ref>.supabase.co/auth/v1/.well-known/jwks.json

app.cors.allowed-origins=http://localhost:5173

supabase.url=https://<project-ref>.supabase.co
supabase.service-role-key=${SUPABASE_SERVICE_ROLE_KEY:}
supabase.storage.bucket=profile-images
```

Get `<pooler-host>`, `<project-ref>`, and the DB password from Supabase
Dashboard → Project Settings → Database → Connect → **Session pooler**.
`SUPABASE_SERVICE_ROLE_KEY` is only needed for profile-image uploads
(Dashboard → Project Settings → API → service_role key) — leave it blank to
skip that feature.

### 3. AI microservice dependencies

```bash
cd AI/api
pip install -r requirements.txt
```

Trained model files already live in `AI/models/` — nothing else to set up.

## Boot order

Start these in order, each in its own terminal:

**1. AI microservice** (optional but recommended — the backend falls back to
a rule-based estimate if this isn't reachable):

```bash
cd AI/api
uvicorn main:app --reload --port 8001
```

Verify: `curl http://localhost:8001/health` → `{"status":"healthy",...}`

**2. Backend:**

```bash
cd mytadika-backend
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

Verify: `curl http://localhost:8080/api/auth/me` → `401` (expected without a
token — it means the server is up).

**3. Frontend:**

```bash
cd mytadika-frontend
npm run dev
```

Open the URL Vite prints (usually `http://localhost:5173`).

## Using the app

- **Sign up** as a parent from `/register` (email/password) or sign in with
  **Google** from `/login` — both go through Supabase Auth directly, not the
  backend. Staff accounts (Teacher/Admin) are provisioned via
  `POST /api/accounts/register-staff` by an existing Admin, not self-service.
- First login auto-creates your local profile row (`PARENT` role) — this
  requires the **backend to be running**; if it isn't, you'll land on a
  blank page after login instead of your dashboard (see Troubleshooting).
- Role-based nav (sidebar) routes to Student/Academic/Health/Profile pages;
  Classroom/Messages/Memory Box/Events show a "coming soon" placeholder —
  those belong to the teammate's Parent Engagement module.

## Troubleshooting

**Blank page after Google/email login, URL ends in a stray `#`.**
The backend isn't running (or isn't reachable at `VITE_API_BASE_URL`). The
frontend fetches your profile right after login; if that call fails with a
connection error rather than a 404, it gives up silently. Start the backend
and refresh.

**Sign-up shows "check your inbox" but no email arrives.**
That email is probably already registered (e.g. you signed in with Google
using it earlier). Supabase intentionally no-ops repeat sign-ups instead of
erroring, to avoid leaking which emails exist — check
Dashboard → Authentication → Users, or just sign in instead of signing up.

**"AI Service is currently unreachable" / nutrition predictions look generic.**
The FastAPI microservice (port 8001) isn't running — the backend's
`AiPredictionClient` falls back to a simple BMI-threshold rule rather than
the trained model. Start `AI/api` and retry.

**Backend won't connect to the database / "prepared statement already exists".**
You're probably using the Transaction pooler (port 6543) instead of the
Session pooler (port 5432) — see `CLAUDE.md` Section 6.5.

## Running tests

```bash
cd mytadika-backend && ./mvnw test     # 28 unit tests: grading, BMI, RBAC scoping
cd mytadika-frontend && npm run lint && npm run build
```
