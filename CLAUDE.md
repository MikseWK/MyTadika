# CLAUDE.md — MyTadika Development Guide
### Core Administrative & Health Analytics Module — HTML/JS + Spring Boot

> **Related docs:** `FYP1 Report (Ch 1–4)` = requirements/use-cases/ERD spec. `docs/plan.md` and `docs/system_development_plan.md` are the teammate's original AI/ML and React/Vite SPA planning docs — **historical/stale**: the AI/ML pipeline they describe was superseded by the rules-engine approach actually shipped, and the React/Vite SPA was never built (frontend is plain static HTML/JS served by Spring Boot, per §8). `docs/integration-plan.md` records the actual merge of the teammate's backend modules and frontend into this repo — treat it as the current source of truth for what shipped. This file = concrete build plan for whoever is writing code.

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
| Auth client | `components/auth-fetch.js` — patches `window.fetch` to attach `Authorization: Bearer <token>` from `localStorage`; no Supabase JS client in the shipped frontend |
| Backend | Spring Boot 3.5.15 (Java 21 LTS), Maven |
| Persistence | Spring Data JPA + Hibernate (`PostgreSQLDialect`) |
| Database | **PostgreSQL hosted on Supabase** (JDBC only — Supabase Auth/PostgREST are not used) |
| Auth (backend) | Self-issued stateless JWT (`security/JwtService.java`, `security/JwtAuthenticationFilter.java`) — **not** Supabase OAuth2/JWKS; that approach was abandoned. Spring Boot issues, signs, and validates its own tokens against the `accounts` table. |
| Validation | Jakarta Bean Validation |
| Boilerplate | Lombok |
| ML serving | Python FastAPI microservice (`AI/`) — Spring Boot calls it over HTTP, per `plan.md` |

---

## 3. Decisions Log

| Decision | Choice | Rationale |
|---|---|---|
| Database | **PostgreSQL on Supabase** | Already on Supabase. Spring Boot connects via standard JDBC/JPA — same as any Postgres instance. |
| DB connection mode | **Supavisor Session Pooler (port 5432)** | Direct connection needs IPv6 (paid add-on). Transaction pooler (6543) disables Hibernate prepared statements → cryptic errors. Session pooler is IPv4-compatible on all plans. Get string from Dashboard → Connect → "Session pooler." |
| Auth | **Self-issued Spring Security JWT** (superseded the original Supabase Auth plan) | `AuthController`/`AuthService` verify email+password against the `accounts` table (BCrypt) and issue a JWT via `JwtService`; `JwtAuthenticationFilter` validates it on every request. No Supabase Auth, no Google/Facebook OAuth, no JWKS — Supabase is used purely as a Postgres host (JDBC) and for Storage (profile images, memory photos). Frontend attaches the token via `components/auth-fetch.js`, not a Supabase JS session. |
| Primary keys | **`Account.accountId`: 28-char app-generated `String`** (UUID-without-dashes, truncated); **all other entities: auto-increment `Long`** via `@GeneratedValue(strategy = GenerationType.IDENTITY)` | Renegotiated with the teammate so both modules share one `accounts` table — their auth/account-creation flow generates the id client-side before insert. Other entities (`Student`, `AcademicRecord`, `HealthRecord`, `MemoryPost`, `Fee`, etc.) kept plain auto-increment `Long`, not `Integer`. FK columns referencing `Account` are typed `String` (often `xAccountId` fields rather than `@ManyToOne` object references — see e.g. `Classroom.teacherAccountId`, `MemoryPost.authorAccountId`). |
| Academic raw scores | **Normalized `academic_score_item` table** (not JSON blob) | Queryable, indexable. |
| `Student.gender` | **Added field** (not in FYP1 data dictionary) | Required for WHO z-score sex-specific LMS lookup tables in the ML pipeline. |
| `AI_Report` entity | **Out of scope to build** — teammate's domain | May read from your `health_record`/`HealthAdvice` data (join on `student_id`). Agree on a read contract with teammate; don't build or write to it here. |
| Existing Supabase tables | `student` and `ai_report` exist with **varchar PKs** from an older ERD. No Java code depends on them. | **Action before Phase 1:** migrate to integer identity columns. One-time DB migration. |
| RLS | **Not the primary enforcement layer** | Spring Boot connects via JDBC (pooler creds), bypassing PostgREST/RLS. RBAC enforced in Service layer (§7.1). Add RLS as defense-in-depth later if wanted. |

---

## 4. Monorepo Structure

Two top-level folders: `frontend/` (static HTML/JS source of truth) and `backend/` (Spring Boot). They deploy as **one unit** — `backend/pom.xml` adds `frontend/` as an extra Maven resource directory with `targetPath=static`, so at build time (`mvn compile`/`test`/`package`/`spring-boot:run`) Maven copies it into the classpath's `static/` folder and Spring Boot's default static-resource handler serves it same-origin (no CORS, no bridge page). **Editing a file under `frontend/` requires re-running Maven (`mvn process-resources` or restarting `spring-boot:run`) to pick it up** — there's no live-reload across that copy step. The earlier plan of a separate `mytadika-frontend/` ES-module SPA was abandoned and removed; the earlier merged layout (frontend physically inside `mytadika-backend/src/main/resources/static/`) was itself restructured into this frontend/backend split — see `docs/integration-plan.md` for how the consolidation happened.

```text
MyTadika/
├── CLAUDE.md
├── start.ps1                        ← one-command dev launcher (Windows)
├── docs/
│   ├── integration-plan.md          ← current source of truth for the merge/consolidation
│   ├── plan.md                      ← historical (teammate's AI/ML plan, superseded)
│   └── system_development_plan.md   ← historical (teammate's React/Vite SPA plan, never built)
│
├── frontend/                        ← static HTML/JS source of truth, no build step
│   ├── login.html, forgotpassword.html, resetpassword.html, createparentaccount.html
│   ├── components/    auth-fetch.js (Bearer-token fetch patch), sidebar-*.html,
│   │                  topbar-*.html, sidebar-loader.js — shared across all role pages
│   ├── parent/        parenthome.html, parentacademic.html, parenthealth.html,
│   │                  parentfees.html, parentmemory.html, parentclassroom.html, ...
│   ├── teacher/       teacherhome.html, teacheracademic.html, teacherhealth.html,
│   │                  teachermemory.html, teacherclassroom.html, ...
│   └── admin/         index.html, adminstudents.html, adminaccounts.html,
│                      adminfees.html, admingallery.html, adminhealth*.html, ...
│
└── backend/
    ├── pom.xml                      ← adds ../frontend as an extra resource dir → classpath static/
    └── src/main/
        ├── java/com/mytadika/
        │   ├── config/       SecurityConfig, CorsConfig, WebConfig, StripeConfig, DbMigrationRunner, HolidayDataInitializer
        │   ├── controller/   AuthController, AccountController, StudentController, AcademicController,
        │   │                 HealthController, ClassroomController, ChatController, EventController,
        │   │                 FeeController, PaymentController, MemoryController, NotificationController,
        │   │                 AdminController, ProfileController, PresenceController, UploadController
        │   ├── service/      one *Service per controller, plus GradeCalculationService, HealthAdviceService,
        │   │                 AiPredictionClient, EmailService, SupabaseStorageService,
        │   │                 StripePaymentService, ToyyibPayService, CustomUserDetailsService
        │   ├── repository/   one JpaRepository<Entity, Long|String> per entity
        │   ├── model/        JPA entities — Account.accountId is String; everything else is Long id
        │   ├── dto/          *RequestDTO / *ResponseDTO per entity
        │   ├── security/     JwtService, JwtAuthenticationFilter (self-issued JWT, not Supabase)
        │   └── exception/    ResourceNotFoundException, UnauthorizedAccessException,
        │                     InvalidInputException, ConflictException, ExternalServiceException,
        │                     GlobalExceptionHandler, ErrorResponse
        └── resources/
            └── application.properties   ← DB, mail, Supabase Storage, Stripe, ToyyibPay config
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

> **Superseded.** The flow below (Supabase-issued JWT, JWKS verification, `SupabaseJwtAuthConverter`) was the original plan and was never built this way — see §2/§3. What actually shipped: `AuthController`/`AuthService` check email+password against `accounts` (BCrypt), `JwtService` signs and issues its own JWT, and `JwtAuthenticationFilter` validates it on every request (`SecurityConfig` at `backend/src/main/java/com/mytadika/config/SecurityConfig.java` — currently permits `/api/auth/**` login/register/password-reset, `/api/payments/webhook`, `/api/payments/toyyibpay/callback`, and all static asset paths; everything else requires a valid Bearer token). The frontend attaches that token via `components/auth-fetch.js` (§8.2), not a Supabase session. Keep the rest of this subsection for the RBAC scoping intent (role → data scope), not the literal auth mechanism.

Auth split (as originally planned — not what shipped): Supabase issues/refreshes tokens. Spring Boot only verifies them.

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

> This section describes what's actually shipped, not the original ES-module SPA plan (see the historical-docs note in the header). Every page below lives under top-level `frontend/` and is served same-origin by Spring Boot (via the Maven resource copy into `backend`'s classpath, §4) — no separate frontend server, no CORS handshake for page loads.

### 8.1 Multi-Page Structure

Each `.html` file is a standalone page, no build step, no bundler, no ES modules — plain `<script>` tags. Pages are split by role into `parent/`, `teacher/`, `admin/`, plus shared entry pages at the static root (`login.html`, `forgotpassword.html`, `resetpassword.html`, `createparentaccount.html`).

CDN imports at the top of each HTML file:
```html
<script src="https://cdn.tailwindcss.com?plugins=forms,container-queries"></script>
<link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;600;700;800&display=swap" rel="stylesheet">
<link href="https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined:wght,FILL@100..700,0..1&display=swap" rel="stylesheet">
<script id="tailwind-config">
  tailwind.config = { darkMode: "class", theme: { extend: { colors: { /* §8.4 */ } } } };
</script>
```
Chart.js is added via CDN only on pages that render charts (health growth chart).

Every authenticated page includes, near the end of `<body>`:
```html
<script src="/components/auth-fetch.js"></script>
<script src="/components/sidebar-loader.js"></script>
<script>
  loadSidebar('parent', 'health');   // role + which nav item to highlight
  loadTopbar('parent');
  // page-specific inline <script> below calls the REST API directly with fetch()
</script>
```

### 8.2 Auth & Page Guard

There is **no Supabase JS client and no session object** in the shipped frontend. Login (`POST /api/auth/login`) returns a self-issued JWT (see §3/§7.2); the page stores it plus `accountId`, `fullName`, and `roleType` in `localStorage` (or `sessionStorage` for "remember me" off) and redirects to the role's home page.

`components/auth-fetch.js` is loaded on every protected page and monkey-patches `window.fetch` to attach `Authorization: Bearer <token>` to same-origin requests automatically — this exists because the pages themselves were written assuming the browser would send auth on its own, and it patches that gap without touching every call site. There is no separate `authGuard.js`/role-redirect module; role gating happens implicitly because each role only has links into its own `parent/`, `teacher/`, `admin/` folder, and `sidebar-loader.js`'s `enforceProfileComplete()` redirects parents/teachers with an incomplete profile to their edit-profile page.

**Nav by role** — `sidebar-loader.js`'s `loadSidebar(role, activeNav)` fetches `/components/sidebar-<role>.html` and `topbar-<role>.html` and injects them into the page, highlights the active nav item, and polls unread-message/notification/fee badges every few seconds. `logout()` (also in `sidebar-loader.js`) clears `localStorage`/`sessionStorage` and redirects to `/login.html`.

### 8.3 Data Fetching

No axios, no shared API client module — each page's inline `<script>` calls `fetch('/api/...')` directly (the `auth-fetch.js` patch means the Bearer token is attached automatically). Example pattern (from `parent/parenthealth.html`):

```js
const studentId = new URLSearchParams(location.search).get('studentId');
const res = await fetch(`/api/health/students/${studentId}/growth-chart`);
const chart = await res.json();
renderGrowthChart(chart); // Chart.js
```

Read `studentId` and other params from `URLSearchParams`, same as originally planned.

### 8.4 Design System

Defined per-page via the CDN `tailwind.config` block (§8.1), not a shared `css/styles.css` file — copy the block verbatim into new pages rather than reinventing colors:

```js
colors: {
  "primary": "#FFD700",            // KinderJoy Sun Yellow
  "primary-dim": "#E6C200",
  "secondary": "#FF8C42",          // Playful Orange
  "secondary-container": "#FFF4E1",
  "tertiary": "#6BCB77",           // Garden Green
  "background": "#FFFDF5",         // Soft Cream
  "surface": "#FFFFFF",
  "on-surface": "#4A3F35",         // Warm Charcoal
  "on-surface-variant": "#857668",
  "outline": "#E8E2D9",
  "surface-container-low": "#FFF9EB",
  "surface-container-lowest": "#FFFFFF",
  "surface-container-high": "#F7F2E9",
  "surface-container-highest": "#EFE9DD"
}
```
Font: `Plus Jakarta Sans` for headline/body/label. Icons: Material Symbols Outlined (`FILL` toggled on the active nav icon). Cards generally use rounded-2xl/3xl + soft shadow; status badges follow green/amber/red = normal/moderate/severe, consistent with the original plan's `--color-success/warning/danger`.

Chart.js growth chart: `type: 'line'`, dataset colour matches `primary`, `tension: 0.4` for a smooth curve.

### 8.5 Shared Components & Notable Pages

| File | Responsibility |
|---|---|
| `components/auth-fetch.js` | Patches `window.fetch` to attach the JWT — include on every protected page |
| `components/sidebar-loader.js` | `loadSidebar()`, `loadTopbar()`, unread/notification/fee badge polling, `initNotificationsPage()`, image lightbox (`openImageLightbox`), `logout()` |
| `components/sidebar-*.html`, `topbar-*.html` | Per-role nav markup, injected by `sidebar-loader.js` |
| `login.html` | Email/password form → `POST /api/auth/login`, stores JWT + profile fields, redirects by role |
| `*/*editprofile.html` | `GET`/`PUT /api/profile/{accountId}` — the page `enforceProfileComplete()` redirects incomplete profiles to |
| `*/*academic*.html` | Score input (teacher) / performance view (parent) — `/api/academic/**` |
| `*/*health*.html` | Measurement log, Chart.js growth chart, AI advice panel, allergy banner — `/api/health/**` |
| `*/*memory.html`, `admin/admingallery.html` | Photo feed with reactions/comments — `/api/memory/**` (§ Phase B) |
| `*/*fees.html` | Fee list + Stripe/ToyyibPay checkout — `/api/fees/**`, `/api/payments/**` |
| `*/*classroom.html` | Roster/classroom view — `/api/classroom/**` |
| `*/*chatwith*.html`, `admin/adminmessages.html` | Chat — `/api/chat/**` |
| `*/*events.html` | School events/holidays — `/api/events/**` |
| `*/*notifications.html` | Full notification list, backed by `initNotificationsPage()` in `sidebar-loader.js` |

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
