# CLAUDE.md — MyTadika Development Guide
### Core Administrative & Health Analytics Module — HTML/JS + Spring Boot

> **Related docs:** `FYP1 Report (Ch 1–4)` = requirements/use-cases/ERD spec. `plan.md` = AI/ML deep-dive (Tasks 1–7 complete). This file = concrete build plan for whoever is writing code.

---

## Skills

Two project-level skills are installed under `.agents/skills/`. Read the relevant one **before** writing any code for that domain — don't rely on defaults.

| Skill | Path | When to read it |
|---|---|---|
| **frontend-design** | `.agents/skills/frontend-design/SKILL.md` | Before creating or restyling any HTML page, CSS, or JS UI component. Covers palette, typography, layout principles, and avoiding templated UI. The MyTadika design tokens in §8.4 were derived with this skill — use it to stay consistent when adding new UI. |

> `react-doctor` skill (`.agents/skills/react-doctor/`) is installed but suspended — frontend has moved to plain HTML/JS. Re-enable if React is reintroduced.

---

## 1. Project Context

MyTadika is a School-Parent Engagement Web App for Malaysian kindergartens. FYP split:

| Owner | Module |
|---|---|
| **You (this plan)** | Core Admin & Health Analytics — Auth, Student Profile, Academic Tracking, Health + AI Advice |
| Teammate (out of scope) | Parent Engagement & Communication — Chat, Gallery, Notifications, Admin Mgmt, AI Analytics |

Shared entities (`Account`, `Classroom`) are stubbed just enough to compile; full classroom features belong to teammate. See [Section 11](#11-out-of-scope--teammates-modules).

---

## 2. Tech Stack

| Layer | Technology |
|---|---|
| Frontend | **Plain HTML5 / CSS3 / JavaScript (ES Modules)** — multi-page, no build step required |
| HTTP client | Axios (loaded via CDN or npm) |
| Charts | Chart.js (replaces Recharts) |
| Styling | Tailwind CSS via CDN `<script>` tag; CSS custom properties for design tokens |
| Auth client | `@supabase/supabase-js` via CDN — sign up/in, Google/Facebook OAuth, session + token refresh |
| Backend | Spring Boot 3.x (Java 21 LTS), Maven |
| Persistence | Spring Data JPA + Hibernate (`PostgreSQLDialect`) |
| Database | **PostgreSQL hosted on Supabase** |
| Auth (backend) | `spring-boot-starter-oauth2-resource-server` — validates Supabase JWTs via JWKS |
| Validation | Jakarta Bean Validation |
| Boilerplate | Lombok |
| ML serving | Python FastAPI microservice (`AI/`) — Spring Boot calls it over HTTP, per `plan.md` |

---

## 3. Decisions Log

| Decision | Choice | Rationale |
|---|---|---|
| Database | **PostgreSQL on Supabase** | Already on Supabase. Spring Boot connects via standard JDBC/JPA — same as any Postgres instance. |
| DB connection mode | **Supavisor Session Pooler (port 5432)** | Direct connection needs IPv6 (paid add-on). Transaction pooler (6543) disables Hibernate prepared statements → cryptic errors. Session pooler is IPv4-compatible on all plans. Get string from Dashboard → Connect → "Session pooler." |
| Auth | **Supabase Auth + Spring Boot as OAuth2 Resource Server** | Keeps one source of truth. Google/Facebook login free via `signInWithOAuth`. Spring Boot only verifies tokens, doesn't issue them. Custom Spring Security JWT is still viable if you want auth logic in your own code — flag it. |
| Primary keys | **Auto-increment `Integer`** | `INTEGER` (~2.1B ceiling) is proportionate for a kindergarten app. `@GeneratedValue(strategy = GenerationType.IDENTITY)`. Use `Integer` everywhere: entity fields, DTOs, `@PathVariable`, `JpaRepository<Entity, Integer>`. |
| Academic raw scores | **Normalized `academic_score_item` table** (not JSON blob) | Queryable, indexable. |
| `Student.gender` | **Added field** (not in FYP1 data dictionary) | Required for WHO z-score sex-specific LMS lookup tables in the ML pipeline. |
| `AI_Report` entity | **Out of scope to build** — teammate's domain | May read from your `health_record`/`HealthAdvice` data (join on `student_id`). Agree on a read contract with teammate; don't build or write to it here. |
| Existing Supabase tables | `student` and `ai_report` exist with **varchar PKs** from an older ERD. No Java code depends on them. | **Action before Phase 1:** migrate to integer identity columns. One-time DB migration. |
| RLS | **Not the primary enforcement layer** | Spring Boot connects via JDBC (pooler creds), bypassing PostgREST/RLS. RBAC enforced in Service layer (§7.1). Add RLS as defense-in-depth later if wanted. |

---

## 4. Monorepo Structure

```text
MyTadika/
├── CLAUDE.md
├── mytadika-backend/
│   └── src/main/java/com/mytadika/backend/
│       ├── config/         SecurityConfig, CorsConfig, OpenApiConfig
│       ├── controller/     AuthController, StudentController, AcademicController, HealthController
│       ├── service/        AuthService, StudentService, AcademicService,
│       │                   HealthAdviceService, AiPredictionClient, GradeCalculationService
│       ├── repository/     one JpaRepository<Entity, Integer> per entity
│       ├── model/          JPA entities
│       ├── dto/            *RequestDTO / *ResponseDTO per entity
│       ├── security/       SupabaseJwtAuthConverter, AccountResolver
│       └── exception/      ResourceNotFoundException, UnauthorizedAccessException,
│                           InvalidInputException, GlobalExceptionHandler
└── mytadika-frontend/
    ├── pages/
    │   ├── login.html
    │   ├── dashboard-parent.html
    │   ├── dashboard-teacher.html
    │   ├── profile.html
    │   ├── students.html
    │   ├── academic.html
    │   └── health.html
    ├── css/
    │   └── styles.css              ← CSS custom property tokens + component classes
    ├── js/
    │   ├── supabaseClient.js       ← createClient() setup, exported as ES module
    │   ├── api/
    │   │   ├── axiosClient.js      ← axios instance + JWT interceptor
    │   │   ├── authApi.js
    │   │   ├── studentApi.js
    │   │   ├── academicApi.js
    │   │   └── healthApi.js
    │   ├── auth/
    │   │   └── authGuard.js        ← session check + role check on every page load
    │   └── pages/
    │       ├── login.js
    │       ├── dashboard.js
    │       ├── students.js
    │       ├── academic.js
    │       └── health.js
    └── assets/
        └── images/
```

---

## 5. System Architecture

```text
┌──────────────────────────────┐
│  HTML/JS Frontend            │  supabase-js handles login/signup/OAuth → Supabase JWT
└──────────────┬───────────────┘
               │ HTTPS REST — Authorization: Bearer <Supabase JWT>
┌──────────────▼───────────────┐
│  Spring Boot                  │  Controller → Service → Repository → Entity
│  OAuth2 Resource Server:      │  validates JWT via JWKS, resolves Account+role
│  business logic, RBAC scoping │
└──────┬──────────────┬─────────┘
       │ JDBC (session │ HTTP :8001
       │ pooler)       │
┌──────▼──────┐  ┌─────▼──────────────┐
│ PostgreSQL   │  │ Python FastAPI      │
│ (Supabase)   │  │ best_model.joblib   │
└─────────────┘  └────────────────────┘
```

**Rules:** No direct frontend→DB access. All business logic (grade avg, BMI, ML round-trip) stays server-side. Same backend serves role-scoped datasets for Parent and Teacher.

---

## 6. Database Design

### 6.1 Scope

- **Your tables:** `account`, `student`, `academic_record`, `academic_score_item`, `health_record`, `health_advice`, `allergy_profile`
- **Stub only (FK target):** `classroom`
- **Out of scope:** `gallery`, `notification`, `notification_recipient`, `message`, `ai_report`

### 6.2 ERD Overview

```text
Account (1)──(M) Student ──(M)──(1) Classroom [stub]
                 │
                 ├──(1:M)── AcademicRecord ──(1:M)── AcademicScoreItem
                 ├──(1:M)── HealthRecord ──(0:1)── HealthAdvice
                 └──(1:M)── AllergyProfile
```

### 6.3 Entity Definitions

#### `Account`
| Field | Type | Constraints |
|---|---|---|
| id | Integer | PK, auto-increment |
| authUserId | UUID | NOT NULL, UNIQUE — Supabase `auth.users.id` (JWT `sub` claim) |
| fullName | String(100) | NOT NULL |
| email | String(100) | NOT NULL, UNIQUE |
| role | Enum: `PARENT`,`TEACHER`,`ADMIN` | NOT NULL |
| phoneNumber | String(20) | nullable |
| address | String(500) | nullable |
| profileImageUrl | String(500) | nullable — Supabase Storage bucket `profile-images` |
| createdAt | LocalDateTime | NOT NULL, `@PrePersist` |

#### `Classroom` (stub)
| Field | Type | Constraints |
|---|---|---|
| id | Integer | PK |
| className | String(50) | NOT NULL |
| teacher | Account FK | ManyToOne NOT NULL |
| createdAt | LocalDateTime | NOT NULL |

#### `Student`
| Field | Type | Constraints |
|---|---|---|
| id | Integer | PK |
| parent | Account FK | ManyToOne NOT NULL |
| classroom | Classroom FK | ManyToOne nullable |
| fullName | String(100) | NOT NULL |
| dateOfBirth | LocalDate | NOT NULL |
| gender | Enum: `MALE`,`FEMALE` | NOT NULL — required for WHO z-score |
| medicalInfo | Text | nullable |
| emergencyContact | String(20) | NOT NULL |
| studentCode | String(20) | UNIQUE nullable — display ID e.g. `STU20001`, not a FK target |
| createdAt | LocalDateTime | NOT NULL |

#### `AcademicRecord` (UC003)
| Field | Type | Constraints |
|---|---|---|
| id | Integer | PK |
| student | Student FK | ManyToOne NOT NULL |
| academicTerm | String(50) | NOT NULL e.g. `"Term 1 - 2026"` |
| averageMark | Double | NOT NULL, computed server-side |
| finalGrade | String(2) | NOT NULL, computed server-side |
| createdAt | LocalDateTime | NOT NULL |

#### `AcademicScoreItem`
| Field | Type | Constraints |
|---|---|---|
| id | Integer | PK |
| academicRecord | AcademicRecord FK | ManyToOne NOT NULL |
| subjectName | String(50) | NOT NULL |
| score | Double | NOT NULL, `0 ≤ score ≤ 100` |

**Grading scale** (`GradeCalculationService` — Malaysia preschool standard):

| Range | Grade | Label |
|---|---|---|
| 80–100 | A | Excellent |
| 70–79 | B | Good |
| 60–69 | C | Satisfactory |
| 50–59 | D | Passing |
| 40–49 | E | Borderline |
| 0–39 | F | Unsatisfactory |

#### `HealthRecord` (UC004)
> Drop the orphaned `health_record` (singular) table from the older ERD once the `student` migration is done.

| Field | Type | Constraints |
|---|---|---|
| id | Integer | PK |
| student | Student FK | ManyToOne NOT NULL |
| heightCm | Double | NOT NULL |
| weightKg | Double | NOT NULL |
| muacCm | Double | nullable — WHO MUAC screening |
| calculatedBmi | Double | NOT NULL, computed: `weightKg / (heightCm/100)²` |
| bmiForAgeZ | Double | nullable — z-score feature engineering |
| nutritionStatus | Enum: `NORMAL`,`MODERATE`,`SEVERE` | nullable until ML runs |
| activityLevel | Enum: `SEDENTARY`,`NORMAL`,`HIGHLY_ACTIVE` | default `NORMAL` |
| recordedBy | Account FK | ManyToOne NOT NULL |
| dateRecorded | LocalDateTime | NOT NULL |

#### `HealthAdvice` (UC005)
| Field | Type | Constraints |
|---|---|---|
| id | Integer | PK |
| healthRecord | HealthRecord FK | OneToOne NOT NULL |
| dietaryAdvice | Text | JSON-encoded list |
| activityAdvice | Text | JSON-encoded list |
| allergyWarnings | Text | nullable — always rendered first |
| modelConfidence | Double | nullable |
| disclaimer | String(255) | default: `"This advice is generated from health guidelines and does not replace professional medical consultation."` |
| generatedAt | LocalDateTime | NOT NULL |

#### `AllergyProfile`
| Field | Type | Constraints |
|---|---|---|
| id | Integer | PK |
| student | Student FK | ManyToOne NOT NULL |
| allergenName | String(50) | NOT NULL |
| severity | Enum: `MILD`,`MODERATE`,`SEVERE` | NOT NULL |
| notes | Text | nullable |

### 6.4 Entity Annotation Pattern

```java
@Entity @Table(name = "account")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Account {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, columnDefinition = "uuid")
    private UUID authUserId; // JWT 'sub' claim → resolves to local role

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist void onCreate() { this.createdAt = LocalDateTime.now(); }
}
```

All other entities follow the same pattern: `Integer id`, `@ManyToOne(fetch = FetchType.LAZY)` for FK fields, `@Enumerated(EnumType.STRING)` for enums, `@Lob` for Text fields.

### 6.5 Supabase Connection Config

```yaml
# application.yml — Session Pooler (port 5432), NOT direct or transaction pooler
spring:
  datasource:
    url: jdbc:postgresql://aws-0-<region>.pooler.supabase.com:5432/postgres
    username: postgres.<project-ref>   # from Dashboard → Connect → Session pooler
    password: ${SUPABASE_DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: update   # switch to Flyway before submission
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
```

If you ever need the Transaction pooler (serverless deploy), add `prepareThreshold=0` to HikariCP data-source-properties to disable Hibernate prepared statements.

---

## 7. Backend Architecture (Spring Boot)

### 7.1 Layered Pattern

Controllers: request mapping + validation only. Business logic and RBAC scoping in Service layer.

```java
// Controller: thin
@GetMapping("/{id}")
public ResponseEntity<StudentResponseDTO> getStudent(
        @PathVariable Integer id,
        @AuthenticationPrincipal Account currentUser) {
    return ResponseEntity.ok(studentService.getStudentScoped(id, currentUser));
}

// Service: RBAC scope check (UC002 Constraint C1)
public StudentResponseDTO getStudentScoped(Integer id, Account currentUser) {
    Student student = studentRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Student not found"));
    if (currentUser.getRole() == Role.PARENT
            && !student.getParent().getId().equals(currentUser.getId()))
        throw new UnauthorizedAccessException("Cannot access another parent's child");
    return StudentResponseDTO.from(student);
}
```

### 7.2 Security & Auth

Auth split: Supabase issues/refreshes tokens. Spring Boot only verifies them.

```text
Login/OAuth → supabase-js → session { access_token JWT, refresh_token }
              supabase-js auto-refreshes; read via supabase.auth.getSession()

First login → POST /api/accounts/complete-profile (Bearer <JWT>)
            → Spring Boot creates Account row: authUserId = JWT 'sub', role from body

All requests → Spring Security OAuth2 Resource Server validates JWT signature
             → SupabaseJwtAuthConverter: findByAuthUserId(jwt.getSubject()) → Account
             → attaches ROLE_PARENT / ROLE_TEACHER / ROLE_ADMIN to SecurityContext
```

**Check first:** Dashboard → Project Settings → API → JWT Keys. If still HS256, migrate to asymmetric (one click, no downtime) before wiring `jwk-set-uri`.

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: https://<project-ref>.supabase.co/auth/v1/.well-known/jwks.json
```

```java
@Configuration @EnableMethodSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/accounts/complete-profile", "/swagger-ui/**").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(converter)));
        return http.build();
    }
}
```

**RBAC:**

| Role | Scope |
|---|---|
| `PARENT` | Own Account; Students where `student.parent.id == self`; read/write own children's health, read-only academic |
| `TEACHER` | All students; full read/write Academic & Health |
| `ADMIN` | Everything + staff account creation + soft-delete |

### 7.3 REST API Reference

**Auth / Account**

| Method | Endpoint | Access | Description |
|---|---|---|---|
| POST | `/api/accounts/complete-profile` | Public + valid JWT | First login: create local Account row from JWT `sub` |
| POST | `/api/accounts/register-staff` | ADMIN | Create Account row for existing Supabase user |
| GET | `/api/auth/me` | Authenticated | Current Account profile |
| PUT | `/api/accounts/me` | Authenticated | Update fullName / phoneNumber / address |
| POST | `/api/accounts/me/profile-image` | Authenticated | Upload to Supabase Storage → saves URL to `profileImageUrl` |

**Student Profile (UC002)**

| Method | Endpoint | Access | Description |
|---|---|---|---|
| GET | `/api/students` | TEACHER, ADMIN | List all (filter by classroomId) |
| GET | `/api/students/my-children` | PARENT | Own children |
| GET | `/api/students/{id}` | scoped | Get one |
| POST | `/api/students` | TEACHER, ADMIN | Create |
| PUT | `/api/students/{id}` | scoped | Update |
| DELETE | `/api/students/{id}` | ADMIN | Soft delete |

**Academic Tracking (UC003)**

| Method | Endpoint | Access | Description |
|---|---|---|---|
| GET | `/api/academic/students/{studentId}/records` | scoped | List records |
| POST | `/api/academic/students/{studentId}/records` | TEACHER | Submit scores → server computes average + grade |
| GET | `/api/academic/records/{id}` | scoped | Get one |
| PUT | `/api/academic/records/{id}` | TEACHER | Update / recalculate |

**Health & Nutrition + AI Advice (UC004, UC005)**

| Method | Endpoint | Access | Description |
|---|---|---|---|
| POST | `/api/health/students/{studentId}/records` | TEACHER, PARENT | Log height/weight/MUAC → server computes BMI |
| GET | `/api/health/students/{studentId}/records` | scoped | History |
| GET | `/api/health/students/{studentId}/records/latest` | scoped | Latest record + status |
| POST | `/api/health/records/{recordId}/generate-advice` | TEACHER | ML prediction (FastAPI) + rules engine → saves HealthAdvice |
| GET | `/api/health/students/{studentId}/advice/latest` | scoped | Latest advice for parent view |
| GET | `/api/health/allergies/{studentId}` | scoped | Get allergy profile |
| PUT | `/api/health/students/{studentId}/allergies` | TEACHER, PARENT | Update allergy profile |
| GET | `/api/health/students/{studentId}/growth-chart` | scoped | `{date, heightCm, weightKg, bmi}[]` |

> `generate-advice` is a separate endpoint (not auto-triggered on save) — mirrors UC005 where Teacher explicitly initiates advice generation.

### 7.4 Error Handling

`@RestControllerAdvice` returns `{ timestamp, status, error, message, path }`.

| Trigger | Message | Surfaced by |
|---|---|---|
| Wrong credentials (UC001 A1) | `Incorrect email or password. Please try again.` | Frontend (map from supabase-js error) |
| Incomplete profile (UC002 A1) | `Please complete all mandatory fields before saving.` | Spring Boot |
| Score 0–100 violation (UC003 A1) | `Invalid score entered. Scores must be between 0 and 100.` | Spring Boot |
| Invalid height/weight (UC004 A1) | `Please enter valid numerical values for height and weight.` | Spring Boot |
| FastAPI unreachable (UC005 A1) | `AI Service is currently unreachable. Please try again.` | Spring Boot |

### 7.5 AI/ML Integration

FastAPI payload (verified field-for-field against `AI/api/schemas.py`):

```java
Map.of(
    "child_id",   student.getId(),
    "age_months", Period.between(student.getDateOfBirth(), LocalDate.now()).toTotalMonths(),
    "gender",     student.getGender().name(),
    "weight_kg",  record.getWeightKg(),
    "height_cm",  record.getHeightCm(),
    "muac_cm",    record.getMuacCm(),
    "bmi",        record.getCalculatedBmi()
)
// Response: status / encoded / confidence / probabilities / flags / model_version
```

**`HealthAdviceService` business rules** (already implemented — check implementation against these):
- **Allergy guardrail:** dietary advice items mentioning an allergen in `AllergyProfile` are filtered out. Allergy warnings always render first.
- **Confidence caveat:** if `modelConfidence < 0.70`, append a caveat recommending professional confirmation.
- **Urgent referral:** if `nutritionStatus == SEVERE` or SAM flag raised, prepend "consult a healthcare professional" as the *first* advice item.

---

## 8. Frontend Architecture (HTML/JS)

### 8.1 Multi-Page Structure

Each `.html` file is a standalone page. No build step, no bundler. JS files use ES Modules (`type="module"`), loaded at the bottom of each page's `<body>`.

Every authenticated page includes two script tags in this order:
```html
<script type="module" src="../js/auth/authGuard.js"></script>
<script type="module" src="../js/pages/[page].js"></script>
```

CDN imports at the top of each HTML file:
```html
<script src="https://cdn.tailwindcss.com"></script>
<script src="https://cdn.jsdelivr.net/npm/axios/dist/axios.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
<script type="module" src="https://cdn.jsdelivr.net/npm/@supabase/supabase-js/dist/umd/supabase.js"></script>
```

### 8.2 Auth & Page Guard

```js
// js/auth/authGuard.js — add as first module script on every protected page
import { supabase } from '../supabaseClient.js';

const { data: { session } } = await supabase.auth.getSession();
if (!session) {
  window.location.href = '/pages/login.html';
}

// Role guard: fetch local Account profile, cache in sessionStorage
let profile = JSON.parse(sessionStorage.getItem('userProfile'));
if (!profile) {
  const res = await fetch('/api/auth/me', {
    headers: { Authorization: `Bearer ${session.access_token}` }
  });
  profile = await res.json();
  sessionStorage.setItem('userProfile', JSON.stringify(profile));
}

// Redirect wrong role (e.g. parent hitting teacher-only page)
const allowedRoles = document.body.dataset.roles?.split(',') ?? [];
if (allowedRoles.length && !allowedRoles.includes(profile.role)) {
  window.location.href = '/pages/login.html';
}

export { session, profile };
```

Mark each page with the allowed roles:
```html
<body data-roles="TEACHER,ADMIN">
```

```js
// js/api/axiosClient.js — reads token from supabase-js session
import { supabase } from '../supabaseClient.js';

const api = axios.create({ baseURL: 'http://localhost:8080/api' });
api.interceptors.request.use(async (config) => {
  const { data } = await supabase.auth.getSession();
  const token = data.session?.access_token;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});
export default api;
```

**Nav by role** — render the correct sidebar HTML server-side (via a shared JS function) based on `profile.role` read from `sessionStorage`:

| Parent | Teacher |
|---|---|
| Home, Academic Report, Health, Classroom*, Messages*, Memory Box*, Events*, Profile, Help | Home, Classroom*, Student Reports, Health, Messages*, Memory Box*, Profile |

(*links to a `coming-soon.html` stub until teammate's module lands.)

### 8.3 Data Fetching

Use `async/await` with the shared `axiosClient.js` instance directly in each `pages/*.js` file. No framework layer needed.

```js
// js/pages/health.js (example pattern)
import api from '../api/axiosClient.js';

const studentId = new URLSearchParams(location.search).get('id');
const { data: chart } = await api.get(`/health/students/${studentId}/growth-chart`);
renderGrowthChart(chart); // calls Chart.js
```

Read `studentId` (and other URL params) from `URLSearchParams` — this replaces React Router's `useParams`.

### 8.4 Design System

CSS custom properties in `css/styles.css` (also usable as Tailwind config overrides via the CDN `tailwind.config` global):

```css
:root {
  --color-primary:    #FFC727;  /* yellow — buttons, active nav */
  --color-bg:         #FFF8E8;  /* cream page background */
  --color-accent:     #FF9F43;  /* orange highlights */
  --color-success:    #4CAF50;  /* NORMAL status */
  --color-warning:    #FF9F43;  /* MODERATE */
  --color-danger:     #FF6B6B;  /* SEVERE / alerts */
  --color-ink:        #3D3D3D;  /* body text */
}
```

Cards: `border-radius: 1rem` + `box-shadow: 0 2px 8px rgba(0,0,0,.08)`. Status badges: green/amber/red = normal/moderate/severe.

Chart.js growth chart config note: use `type: 'line'`, dataset colour `#FFC727`, and `tension: 0.4` for the smooth curve shown in the wireframe.

### 8.5 Pages & Key JS Sections

| Page (`.html`) | Page script (`pages/*.js`) responsibilities |
|---|---|
| `login.html` | Role selector toggle, `supabase.auth.signInWithPassword`, `signInWithOAuth({provider:'google'})`, redirect after login |
| `profile.html` | Load & display `GET /api/auth/me`, handle Edit Profile form submit (`PUT /api/accounts/me`) |
| `dashboard-parent.html` | Greeting with child name, today's schedule list, quick-action buttons |
| `dashboard-teacher.html` | Classroom summary cards, latest updates feed |
| `students.html` | Student list table (`GET /api/students`), search/filter, link to individual profile |
| `academic.html` | Score input form (teacher), performance summary + download button (parent) |
| `health.html` | Measurement log form, Chart.js growth chart, AI Advice panel, allergy alert banner |

---

## 9. Use-Case Traceability

| UC | Backend | Frontend |
|---|---|---|
| UC001 Login | Supabase Auth (client-side) + `GET /api/auth/me` | LoginPage |
| UC002 Student Profile | `/api/students/**` | StudentProfilePage |
| UC003 Academic Tracking | `/api/academic/**` | AcademicTrackingPage |
| UC004 Health Records | `/api/health/students/{id}/records` | HealthTrackerPage → MeasurementForm |
| UC005 AI Advice | `/api/health/records/{id}/generate-advice` | HealthTrackerPage → AIAdvicePanel |

Cross-reference FYP1 Report Ch 4 use-case tables for exact basic/alternative flows.

---

## 10. Build Checklist

**Phases 0–6: all complete ✓** — backend fully scaffolded, all 5 use-case modules built and tested (28 tests passing), frontend wired end-to-end with role-based nav.

**Active items / known gaps:**
- [ ] **Rotate Supabase DB password** — old password is committed in plaintext on `origin/main` at teammate's commit `f2ab736`. Rotate from Dashboard before final submission.
- [ ] `AI/api/test_springboot.py` calls endpoints without a JWT — needs a real Supabase access token to run against the current auth-gated backend.
- [ ] Frontend is now plain HTML/JS — no `package.json` or build step needed. Confirm all CDN scripts (Tailwind, Axios, Chart.js, supabase-js) are loading correctly in each HTML page before writing page logic.
- [ ] Switch `ddl-auto` from `update` to Flyway migrations before final submission.
- [ ] Confirm Supabase JWT signing mode (Dashboard → API → JWT Keys): if still HS256, migrate to asymmetric before wiring `jwk-set-uri`.

---

## 11. Out of Scope (Teammate's Modules)

Don't build: `Classroom` full CRUD, `Gallery`, `Notification`/`Notification_Recipient`, `Message`, `AI_Report`, Admin Management.

If your `Account`/`Student` tables are needed as FK targets for their modules, agree on the schema contract with your teammate — don't decide unilaterally here.
