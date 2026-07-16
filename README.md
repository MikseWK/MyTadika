# MyTadika — School-Parent Engagement Web App

Malaysian kindergarten management system. FYP split:

| Module | Owner |
|---|---|
| Core Admin & Health Analytics (Auth, Student Profile, Academic, Health + AI) | This repo |
| Parent Engagement & Communication (Chat, Gallery, Notifications) | Teammate |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Frontend | Plain HTML5 / CSS3 / JavaScript, no framework, **no build step** — source lives in top-level `frontend/`, Maven copies it into the backend's classpath at build time so Spring Boot serves it same-origin |
| Styling | Tailwind CSS via CDN |
| Charts | Chart.js via CDN |
| Backend | Spring Boot 3.5 (Java 21), Maven |
| Database | PostgreSQL on Supabase |
| Auth | Custom BCrypt + self-issued JWT (HS256), attached client-side via `frontend/components/auth-fetch.js` |
| Payments | Stripe (test mode) + ToyyibPay (sandbox) |
| ML service | Python FastAPI (`AI/` directory, port 8001) |

---

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Java | 21 (LTS) | Maven wrapper (`./mvnw`) is included |
| Python | 3.9+ | Only for the AI health advice service |

---

## One-Command Start (Windows)

```powershell
.\start.ps1
```

This opens **two terminal windows** simultaneously:

| Window | Service | URL |
|---|---|---|
| 1 | Spring Boot backend (serves the frontend too) | http://localhost:8080/login.html |
| 2 | FastAPI AI microservice | http://localhost:8001/health |

---

## Manual Start (alternative)

### Backend + Frontend

The frontend has no server of its own — Maven copies `frontend/` into the backend's classpath at build time, and Spring Boot serves it from the same origin/port as the API.

```powershell
cd backend
.\mvnw spring-boot:run
```

Open: **http://localhost:8080/login.html**

> Editing a file under `frontend/` requires re-running `mvn spring-boot:run` (or `mvn process-resources`) to pick it up — there's no live-reload across the Maven resource copy step.

### AI Service (optional — needed for health advice generation)

```powershell
cd AI/api
pip install -r requirements.txt
uvicorn main:app --reload --port 8001
```

The backend falls back to a rule-based estimate if this isn't running.

---

## Environment Variables

Set these before starting the backend (or add to a `.env` file / your shell profile):

```properties
# Required
SUPABASE_DB_PASSWORD=your_supabase_session_pooler_password
JWT_SECRET=/u0sfwn7U4HB/EbRH4U58r0m6F5MATjUCBt3gduWCoQ=

# Optional — only needed for forgot-password emails
MAIL_USERNAME=your_gmail@gmail.com
MAIL_PASSWORD=your_gmail_app_password
```

> **JWT_SECRET** — the value above is a generated default. Replace it for production.
> **MAIL_PASSWORD** — use a Gmail **App Password** (Google Account → Security → App passwords), not your login password.
> **SUPABASE_DB_PASSWORD** — get from Supabase Dashboard → Project Settings → Database → Connect → **Session pooler** (port 5432).

---

## Project Structure

```
MyTadika/
├── start.ps1                        ← one-command launcher (Windows)
├── README.md
├── docs/                            integration-plan.md (current), plan.md / system_development_plan.md (historical)
│
├── frontend/                        ← Plain HTML/JS, no build step — source of truth
│   ├── login.html, forgotpassword.html, resetpassword.html, createparentaccount.html
│   ├── components/                  auth-fetch.js, sidebar-loader.js, sidebar-*.html, topbar-*.html
│   ├── parent/                      parenthome.html, parentacademic.html, parenthealth.html,
│   │                                parentfees.html, parentmemory.html, parentclassroom.html …
│   ├── teacher/                     teacherhome.html, teacheracademic.html, teacherhealth.html …
│   └── admin/                       index.html, adminstudents.html, adminfees.html, admingallery.html …
│
├── backend/                         ← Spring Boot (port 8080). pom.xml copies ../frontend
│   └── src/main/java/com/mytadika/    into its classpath's static/ at build time.
│       ├── controller/              AuthController, StudentController, AcademicController,
│       │                            HealthController, ClassroomController, ChatController,
│       │                            EventController, FeeController, PaymentController,
│       │                            MemoryController, NotificationController, AdminController …
│       ├── service/                 one *Service per controller, plus GradeCalculationService,
│       │                            HealthAdviceService, StripePaymentService, ToyyibPayService …
│       ├── model/                   Account, Student, Classroom, AcademicRecord, HealthRecord,
│       │                            Fee, MemoryPost/Comment/Reaction, ChatMessage …
│       ├── repository/
│       ├── security/                JwtService, JwtAuthenticationFilter
│       └── config/                  SecurityConfig, CorsConfig, StripeConfig, WebConfig
│
└── AI/                              ← FastAPI ML microservice (port 8001)
```

---

## API Reference

All endpoints except auth require `Authorization: Bearer <jwt>`.

| Area | Method | Endpoint |
|---|---|---|
| **Auth** | POST | `/api/auth/login` |
| | POST | `/api/auth/register` |
| | POST | `/api/auth/forgot-password` |
| | POST | `/api/auth/reset-password` |
| | GET | `/api/auth/me` |
| **Students** | GET | `/api/students` (TEACHER/ADMIN) |
| | GET | `/api/students/my-children` (PARENT) |
| | GET/POST/PUT | `/api/students/{id}` |
| **Academic** | GET/POST | `/api/academic/students/{id}/records` |
| | GET/PUT | `/api/academic/records/{id}` |
| **Health** | POST | `/api/health/record` |
| | GET | `/api/health/history/{id}` |
| | GET | `/api/health/advice/{id}` |
| | GET/PUT | `/api/health/allergies/{id}` |
| **Account** | PUT | `/api/accounts/me` |
| | POST | `/api/accounts/me/profile-image` |
| | POST | `/api/accounts/register-staff` (ADMIN only) |

---

## Accounts & Roles

| Role | How to create |
|---|---|
| `PARENT` | Self-register via `POST /api/auth/register` or the Register page |
| `TEACHER` / `ADMIN` | An existing ADMIN calls `POST /api/accounts/register-staff` |

---

## Running Tests

```powershell
cd backend
.\mvnw test
```

28 tests — all passing (grading scale, BMI computation, RBAC scoping).

---

## Troubleshooting

**"AI Service is currently unreachable"**
The FastAPI service on port 8001 isn't running. Start it with the command above, or ignore it — the backend serves rule-based advice as fallback.

**Backend can't connect to database**
Check that `SUPABASE_DB_PASSWORD` is set and you're using the **Session pooler** connection string (port 5432), not the direct connection or Transaction pooler (port 6543).

**Login returns 401 after correct credentials**
Ensure the backend is running on port 8080. Check `JWT_SECRET` is set to the same value in the environment and in `application.properties`.
