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
| Frontend | Plain HTML5 / CSS3 / JavaScript (ES Modules) — **no build step** |
| Styling | Tailwind CSS via CDN |
| HTTP client | Axios via CDN |
| Charts | Chart.js via CDN |
| Backend | Spring Boot 3.x (Java 21), Maven |
| Database | PostgreSQL on Supabase |
| Auth | Custom BCrypt + self-issued JWT (HS256) |
| ML service | Python FastAPI (`AI/` directory, port 8001) |

---

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Java | 21 (LTS) | Maven wrapper (`./mvnw`) is included |
| Node.js | 18+ | Only for `npx serve` — zero extra installs needed |
| Python | 3.9+ | Only for the AI health advice service |

---

## One-Command Start (Windows)

```powershell
.\start.ps1
```

This opens **two terminal windows** simultaneously:

| Window | Service | URL |
|---|---|---|
| 1 | Spring Boot backend | http://localhost:8080 |
| 2 | Static frontend server | http://localhost:3000/pages/login.html |

> `npx serve` downloads itself on first run — no `npm install` required.

---

## Manual Start (alternative)

### Backend

```powershell
cd mytadika-backend
.\mvnw spring-boot:run
```

Verify it's up: `GET http://localhost:8080/api/auth/me` → `401` is correct (no token yet).

### Frontend

```powershell
cd mytadika-frontend
npx serve . --listen 3000
```

Open: **http://localhost:3000/pages/login.html**

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
│
├── mytadika-backend/                ← Spring Boot (port 8080)
│   └── src/main/java/com/mytadika/
│       ├── controller/              AuthController, StudentController,
│       │                            AcademicController, HealthController, AccountController
│       ├── service/                 AuthService, StudentService, AcademicService,
│       │                            HealthAdviceService, AccountService, EmailService
│       ├── model/                   Account, Student, Classroom, AcademicRecord,
│       │                            HealthRecord, AllergyProfile, PasswordResetToken
│       ├── repository/
│       ├── security/                JwtService, JwtAuthenticationFilter
│       └── config/                  SecurityConfig, CorsConfig
│
├── mytadika-frontend/               ← Plain HTML/JS (port 3000, no build)
│   ├── pages/                       login.html, create-account.html, dashboard-*.html,
│   │                                students.html, academic.html, health.html, profile.html …
│   ├── css/styles.css               design tokens (--color-primary, --color-bg …)
│   ├── js/
│   │   ├── api/                     axiosClient.js, authApi.js, studentApi.js,
│   │   │                            academicApi.js, healthApi.js
│   │   ├── auth/authGuard.js        session check + role guard on every page
│   │   └── pages/                   per-page JS modules
│   └── assets/images/
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
cd mytadika-backend
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
