# CLAUDE.md — MyTadika Development Guide
### Core Administrative & Health Analytics Module — React + Spring Boot Build Plan


>
> **Relationship to existing docs:**
> - `FYP1 Report (Chapters 1–4)` — the academic source of truth for *requirements*,
>   use cases, and the original ERD/UI mockups. Treat it as the spec, not the
>   implementation guide.
> - `plan.md` — the deep-dive for the **AI/ML side** of the Health & Nutrition
>   module (Tasks 1–7: data, model training, rules engine). It already commits
>   to Spring Boot + a relational DB + a Python FastAPI microservice. This file
>   takes that decision and runs with it across the *whole* system, and fully
>   operationalizes plan.md's Tasks 8–9 (Spring Boot + React integration).
> - `system_development_plan.md` — an earlier React+Spring Boot integration plan,
>   now **superseded by this file** for frontend/backend planning. Its concrete
>   details worth keeping (the academic grading scale, the B1–B4/F1–F4 phase
>   checklists) have been folded into Sections 6.3 and 10 below. Kept only for
>   historical reference — don't treat it as current.
> - **This file** — the concrete build plan: architecture, schema, API
>   contracts, folder structure, and an ordered task checklist for whoever
>   (you or an IDE assistant) is writing the code.

---

## 1. Project Context

MyTadika is a School-Parent Engagement Web Application for Malaysian kindergartens. The
FYP is split between two students:

| Owner | Module | Status |
|---|---|---|
| **You (this plan)** | Core Administrative & Health Analytics — Auth & User Management, Student Profile, Academic Performance Tracking, Health & Nutrition + AI Advice | Health/AI data-science work in progress (`plan.md` Tasks 1–7). Spring Boot + React build not yet started. |
| Teammate (out of scope here) | Parent Engagement & Communication — Chat, Memorable Moments Gallery, Notifications, Admin Management, general AI Analytics | Not covered by this plan |

This document **only designs your modules**. Shared entities that your modules
depend on via foreign key (`Account`, `Classroom`) are stubbed just enough to
compile and integrate; full classroom-management features belong to your
teammate. See [Section 11](#11-out-of-scope--teammates-modules).

---

## 2. Tech Stack

| Layer | Technology | Notes |
|---|---|---|
| Frontend | React 18+ (Vite) | Vite over CRA — faster dev server, current standard |
| Routing | React Router v6 | |
| Server state | TanStack Query (React Query) | caching, refetch, loading/error states |
| HTTP client | Axios | single instance with JWT interceptor |
| Forms/validation | React Hook Form + Zod | |
| Styling | Tailwind CSS | matches the soft, rounded kindergarten visual style in the wireframes faster than hand-rolled CSS |
| Charts | Recharts | growth line chart, performance radar, bar charts |
| Icons | lucide-react | matches wireframe icon style |
| Auth client | `@supabase/supabase-js` | frontend-side sign up/in, Google/Facebook OAuth, session + token refresh |
| Backend | Spring Boot 3.x (Java 21 LTS) | Maven |
| Web layer | Spring Web (REST) | |
| Persistence | Spring Data JPA + Hibernate (`PostgreSQLDialect`) | |
| Database | **PostgreSQL, hosted on Supabase** | corrected from the original MySQL recommendation — see rationale below |
| Auth | **Supabase Auth**, validated in Spring Boot via `spring-boot-starter-oauth2-resource-server` (JWKS) | see rationale below |
| Validation | Jakarta Bean Validation (`spring-boot-starter-validation`) | |
| Boilerplate | Lombok | `@Data`, `@Builder`, `@RequiredArgsConstructor` |
| API docs | springdoc-openapi (Swagger UI) | optional but cheap to add, useful for FYP demo |
| ML serving | Python FastAPI microservice (already decided in `plan.md`) | unchanged — Spring Boot calls it over HTTP |

---

## 3. Decisions & Assumptions Log

These were either explicitly chosen by you or are this plan's recommendation
where you said "not sure." Revisit any of these freely — nothing here is
load-bearing for the rest of the document if you change your mind, but
changing one *does* ripple into the schema/API sections below.

| Decision | Choice | Rationale |
|---|---|---|
| Relational DB | **PostgreSQL, hosted on Supabase** (supersedes earlier MySQL recommendation) | You corrected this after the first draft — you're already on Supabase. Functionally this is just "managed Postgres": Spring Boot connects via standard JDBC + Spring Data JPA, the same way it would to any Postgres instance. Supabase's other features (Storage, Realtime, PostgREST) aren't required for your modules, though your teammate's Chat/Gallery features may be a natural fit for Supabase Realtime/Storage — worth a conversation with them. |
| DB connection mode | **Supavisor Session Pooler** (port 5432), not the raw direct connection or the Transaction pooler | Direct connection requires IPv6 (or a paid IPv4 add-on) — most dev machines and budget hosts are IPv4-only. The Transaction pooler (port 6543) disables server-side prepared statements, which Hibernate relies on by default and which causes the cryptic "prepared statement already exists" errors that show up in nearly every "Spring Boot + Supabase" troubleshooting thread. Session pooler is IPv4-compatible on every plan and behaves like a normal Postgres connection. Get the exact string from Supabase Dashboard → Project Settings → Database → Connect → "Session pooler." |
| Auth strategy | **Supabase Auth** (was: custom Spring Security + JWT) | Revised for the same reason as the DB change: since Supabase is already the database, using Supabase Auth too keeps one source of truth (your `account` table just references the Supabase-managed user via `authUserId`), and it cheaply re-enables the wireframe's Google/Facebook buttons — `supabase.auth.signInWithOAuth({ provider: 'google' })` is a few lines on the frontend, versus building Spring Security's OAuth2 Client flow by hand. Spring Boot's role is now "verify the JWT Supabase issued," not "issue tokens" — see Section 7.3. If you'd rather have full custom auth logic visible in your own Spring Boot code for grading/demo purposes, that's still a reasonable call; flag it and I'll swap this section back. |
| Entity primary keys | **Auto-increment `Long`** (your choice) | Standard Spring Data JPA default (`@GeneratedValue(strategy = GenerationType.IDENTITY)`). The report's display-style IDs (`STU20001`) can still exist as a separate human-readable `studentCode` field if you want it for UI/printouts — it's just not the FK-bearing key anymore. |
| Academic raw scores | Normalized child table instead of a JSON blob column | The report's `RawScore: JSON` field was a NoSQL/Firestore-friendly shape. Postgres has a native `jsonb` type if you wanted to keep it, but a `academic_score_item` child table (one row per subject) is more queryable/indexable and avoids re-litigating "JSON vs. relational" inside a single column. |
| `Student.gender` | **Added field**, not in original data dictionary | `plan.md`'s WHO z-score features (weight-for-age, height-for-age, BMI-for-age) require sex-specific LMS lookup tables. Without it the ML pipeline can't compute those features. Flagging this explicitly since it's a schema change beyond the FYP1 report. |
| `AI_Report` entity | **Out of scope to build, but not isolated** | Re-reading Chapter 1: this entity belongs to the "AI & Analytics Module" listed under the *Parent Engagement & Communication* module (your teammate's), covering general academic/behavioral insights — distinct from the BMI-driven nutritional advice that's actually yours. Don't build or write to it. Your teammate's `ai_report` may *read* from your `HealthAdvice`/`health_record` data (e.g. joining on `student_id`) to surface health insights inside their general analytics — that's fine, it's a one-way read dependency, not a duplicate store. The exact read contract (which endpoint or columns they're allowed to join against) is a conversation to have with them, not something to decide unilaterally here. See [Section 11](#11-out-of-scope--teammates-modules). |
| Existing Supabase tables predate this plan | `student` and `ai_report` already exist in Supabase with **varchar PKs** (`student_id`, `report_id`, `account_id`), built earlier against `system_development_plan.md`'s ERD — before any `Account`/`Student`/`Classroom` JPA entity existed in code (confirmed: none exist as of 2026-06-18, only the health module — `HealthRecord`, `AllergyProfile` — is built). **Action required before Phase 1**: migrate these tables' PK/FK columns to `bigint` identity columns to match the Long-PK decision above. Since zero code depends on the varchar format today, this is a one-time DB migration, not a design conflict to resolve in Java. |

---

## 4. Monorepo Structure

```text
MyTadika/
├── CLAUDE.md                      ← this file
├── mytadika-backend/              ← Spring Boot (Maven)
│   └── src/main/java/com/mytadika/backend/
│       ├── config/                ← SecurityConfig, CorsConfig, OpenApiConfig
│       ├── controller/
│       ├── service/
│       ├── repository/
│       ├── model/                 ← JPA entities
│       ├── dto/                   ← request/response DTOs
│       ├── security/              ← SupabaseJwtAuthConverter, AccountResolver
│       └── exception/             ← custom exceptions + @RestControllerAdvice
├── mytadika-frontend/             ← React (Vite)
│   └── src/
│       ├── pages/
│       ├── components/
│       ├── layouts/
│       ├── hooks/
│       ├── services/api/
│       ├── context/
│       └── routes/
└── AI/                             ← Python FastAPI ML microservice (already exists per plan.md)
    ├── api/                        ← main.py, predictor.py, schemas.py
    ├── models/                     ← best_model.joblib
    └── rules_engine/               ← reference JS, ported into Java HealthAdviceService
```

This matches the structure `plan.md` Section 8.5 already committed to — `AI/`
and `mytadika-backend/` are unchanged; `mytadika-frontend/` is added here.

---

## 5. System Architecture

The report originally specced a Firebase-flavored MVC (View/Controller/Model/Service/Database).
Translated 1:1 into Spring Boot + React, the layers map like this:

```text
┌──────────────────────────┐
│  React SPA (View)        │  pages/, components/ — role-aware UI (Parent/Teacher)
│  uses supabase-js for    │
│  login/signup/OAuth →    │
│  gets a Supabase JWT     │
└────────────┬──────────────┘
             │ HTTPS / REST (JSON, Supabase JWT in Authorization header)
┌────────────▼──────────────┐
│  Spring Boot Backend       │
│  ┌─────────────────────┐  │
│  │ Controller layer     │  │  @RestController — request mapping, validation, no business logic
│  ├─────────────────────┤  │
│  │ Service layer        │  │  business rules: grade calc, BMI calc, RBAC scoping,
│  │                       │  │  AiPredictionClient (calls FastAPI), HealthAdviceService
│  ├─────────────────────┤  │
│  │ Repository layer     │  │  Spring Data JPA interfaces
│  ├─────────────────────┤  │
│  │ Entity / Model layer │  │  JPA entities — mirrors the report's "Model" layer
│  └─────────────────────┘  │
│  validates the Supabase    │
│  JWT as an OAuth2          │
│  Resource Server (JWKS) —  │
│  doesn't issue its own     │
│  tokens                    │
└──────┬───────────────┬────┘
       │ JDBC           │ HTTP (localhost:8001)
       │ (Supavisor      │
       │ session pooler) │
┌──────▼──────────┐  ┌──▼────────────────────┐
│ PostgreSQL        │  │ Python FastAPI (AI/)  │
│ (Supabase)        │  │ loads best_model.joblib│
│ Student, Account, │  │ returns nutrition_status│
│ AcademicRecord,    │  │ + confidence            │
│ HealthRecord, ...  │  │                         │
└────────────────────┘  └─────────────────────────┘
```

Key fidelity points carried over from the report's architecture rationale
(Section 4.2.1):

- **No direct frontend→database access** (rejecting the Two-Tier model the
  report explicitly ruled out) — the React app only ever talks to the Spring
  Boot REST API.
- **Heavy logic stays server-side**: grade averaging, BMI calculation, and the
  ML prediction round-trip all happen in the Service layer, never in the
  browser, for the same reasons the report cites (security, consistency,
  enabling later changes without touching the client).
- **Role-based view composition**: the same backend serves both a
  Teacher-facing dataset and a Parent-facing (scoped) dataset, matching the
  report's "diverse, role-specific interfaces" requirement.

---

## 6. Database Design

### 6.1 Scope

In scope (built by you): `account`, `student` (extended), `academic_record`,
`academic_score_item`, `health_record`, `health_advice`, `allergy_profile`.

Stub only (teammate owns the full feature, you only need the FK target to
exist so `student.classroom_id` compiles): `classroom`.

Out of scope entirely: `gallery`, `notification`, `notification_recipient`,
`message`, `ai_report`.

### 6.2 Entity-Relationship Overview

```text
Account (1)───(M) Student ──(M)──(1) Classroom [stub]
                  │
                  ├──(1:M)── AcademicRecord ──(1:M)── AcademicScoreItem
                  ├──(1:M)── HealthRecord ──(0:1)── HealthAdvice
                  └──(1:M)── AllergyProfile
```

### 6.3 Entity Definitions

#### `Account` (shared core)

| Field | Type | Constraints |
|---|---|---|
| id | Long | PK, auto-increment |
| authUserId | UUID | NOT NULL, UNIQUE — references Supabase `auth.users.id` (the `sub` claim of the JWT) |
| fullName | String(100) | NOT NULL |
| email | String(100) | NOT NULL, UNIQUE |
| role | Enum: `PARENT`, `TEACHER`, `ADMIN` | NOT NULL, `@Enumerated(STRING)` |
| phoneNumber | String(20) | nullable |
| address | String(500) | nullable |
| profileImageUrl | String(500) | nullable — set via `POST /api/accounts/me/profile-image`, uploaded to Supabase Storage bucket `profile-images` |
| createdAt | LocalDateTime | NOT NULL, default now |

#### `Classroom` (stub — teammate's full domain)

| Field | Type | Constraints |
|---|---|---|
| id | Long | PK |
| className | String(50) | NOT NULL |
| teacher | `Account` FK | ManyToOne, NOT NULL |
| createdAt | LocalDateTime | NOT NULL |

#### `Student`

| Field | Type | Constraints |
|---|---|---|
| id | Long | PK |
| parent | `Account` FK | ManyToOne, NOT NULL |
| classroom | `Classroom` FK | ManyToOne, nullable until assigned |
| fullName | String(100) | NOT NULL |
| dateOfBirth | LocalDate | NOT NULL |
| gender | Enum: `MALE`, `FEMALE` | **added** — required for WHO z-score lookups |
| medicalInfo | Text | nullable |
| emergencyContact | String(20) | NOT NULL |
| studentCode | String(20) | optional, UNIQUE — human-readable display ID (e.g. `STU20001`), generated server-side, **not** an FK target |
| createdAt | LocalDateTime | NOT NULL |

#### `AcademicRecord` (UC003)

| Field | Type | Constraints |
|---|---|---|
| id | Long | PK |
| student | `Student` FK | ManyToOne, NOT NULL |
| academicTerm | String(50) | NOT NULL, e.g. `"Term 1 - 2026"` |
| averageMark | Double | NOT NULL, computed server-side |
| finalGrade | String(2) | NOT NULL, computed server-side |
| createdAt | LocalDateTime | NOT NULL |

#### `AcademicScoreItem` (replaces the report's `RawScore` JSON blob)

| Field | Type | Constraints |
|---|---|---|
| id | Long | PK |
| academicRecord | `AcademicRecord` FK | ManyToOne, NOT NULL |
| subjectName | String(50) | NOT NULL |
| score | Double | NOT NULL, `0 ≤ score ≤ 100` (UC003 alt-flow A1) |

**Grading scale** (`GradeCalculationService`, from `system_development_plan.md` Task B3 — Malaysia preschool standard, not in the original FYP1 report):

| Range | Grade | Label |
|---|---|---|
| 80–100 | A | Excellent |
| 70–79 | B | Good |
| 60–69 | C | Satisfactory |
| 50–59 | D | Passing |
| 40–49 | E | Borderline |
| 0–39 | F | Unsatisfactory |

#### `HealthRecord` (UC004, merges report's Health_Record + `plan.md`'s feature set)

> Supabase also has an orphaned `health_record` (singular) table from
> `system_development_plan.md`'s older `Health_Record` ERD entity (`HealthID` PK,
> `AIAdvice` text column). Confirmed nothing in code references it — drop it once
> the `student` table migration above is done.

| Field | Type | Constraints |
|---|---|---|
| id | Long | PK |
| student | `Student` FK | ManyToOne, NOT NULL |
| heightCm | Double | NOT NULL |
| weightKg | Double | NOT NULL |
| muacCm | Double | nullable — WHO MUAC screening |
| calculatedBmi | Double | NOT NULL, computed server-side: `weightKg / (heightCm/100)^2` (UC004 C1) |
| bmiForAgeZ | Double | nullable — populated once z-score feature engineering lands |
| nutritionStatus | Enum: `NORMAL`, `MODERATE`, `SEVERE` | nullable until ML prediction runs |
| activityLevel | Enum: `SEDENTARY`, `NORMAL`, `HIGHLY_ACTIVE` | default `NORMAL` |
| recordedBy | `Account` FK | ManyToOne, NOT NULL — audit trail of who entered it |
| dateRecorded | LocalDateTime | NOT NULL |

#### `HealthAdvice` (UC005, this is `plan.md`'s `advice_history` table, made concrete)

| Field | Type | Constraints |
|---|---|---|
| id | Long | PK |
| healthRecord | `HealthRecord` FK | OneToOne, NOT NULL |
| dietaryAdvice | Text | JSON-encoded list of advice strings |
| activityAdvice | Text | JSON-encoded list |
| allergyWarnings | Text | nullable, always rendered first/prominently per `plan.md` 7.2 |
| modelConfidence | Double | nullable |
| disclaimer | String(255) | default: `"This advice is generated from health guidelines and does not replace professional medical consultation."` |
| generatedAt | LocalDateTime | NOT NULL |

#### `AllergyProfile` (one row per allergen, per `plan.md` 8.4)

| Field | Type | Constraints |
|---|---|---|
| id | Long | PK |
| student | `Student` FK | ManyToOne, NOT NULL |
| allergenName | String(50) | NOT NULL |
| severity | Enum: `MILD`, `MODERATE`, `SEVERE` | NOT NULL |
| notes | Text | nullable |

### 6.4 Sample Entity Code

```java
// model/Account.java
@Entity
@Table(name = "account")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Supabase auth.users.id — the JWT 'sub' claim. This is how a validated
    // token gets resolved back to your local Account/role.
    @Column(nullable = false, unique = true, columnDefinition = "uuid")
    private UUID authUserId;

    @Column(nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role; // PARENT, TEACHER, ADMIN

    private String phoneNumber;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() { this.createdAt = LocalDateTime.now(); }
}
```

```java
// model/Student.java
@Entity
@Table(name = "student")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Student {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", nullable = false)
    private Account parent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "classroom_id")
    private Classroom classroom;

    @Column(nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gender gender;

    @Lob
    private String medicalInfo;

    @Column(nullable = false, length = 20)
    private String emergencyContact;

    @Column(unique = true, length = 20)
    private String studentCode;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
```

### 6.5 Connecting Spring Boot to Supabase

Use the **Session Pooler** string (Supabase Dashboard → Project Settings →
Database → Connect → "Session pooler"), not the raw direct connection and not
the Transaction pooler — see the rationale in Section 3.

```yaml
# application.yml
spring:
  datasource:
    url: jdbc:postgresql://aws-0-<region>.pooler.supabase.com:5432/postgres
    username: postgres.<project-ref>
    password: ${SUPABASE_DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: update   # switch to Flyway before final deployment
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
```

Get the exact `<region>` and `<project-ref>` values from the dashboard
rather than guessing — they're project-specific. Keep the password out of
version control (`${SUPABASE_DB_PASSWORD}` env var).

**Why not the other two options:** the raw direct connection requires IPv6
(or a paid IPv4 add-on) and most local/dev/budget-host networks are
IPv4-only; the Transaction pooler (port 6543) disables server-side prepared
statements by default, which is what Hibernate uses, and is the single most
common cause of "Spring Boot can't connect to Supabase" reports. If you ever
do need the Transaction pooler (e.g. deploying to a serverless platform),
disable prepared statement caching explicitly
(`spring.datasource.hikari.data-source-properties.prepareThreshold=0`).

**Row Level Security (RLS):** Spring Boot connects directly via JDBC using
the pooler credentials above, not through Supabase's PostgREST/Data API.
That means RLS policies are **not** the enforcement mechanism for your
backend's queries — they only apply to traffic going through PostgREST/the
Supabase client SDK. The Service-layer RBAC scoping from Section 7.2 is what
actually protects the data here. You can still add RLS later as
defense-in-depth, but it isn't required for this architecture and an IDE
assistant generating RLS policies as the *primary* access control would be
solving the wrong layer.

---

## 7. Backend Architecture (Spring Boot)

### 7.1 Package Structure

```text
com.mytadika.backend
├── config/        SecurityConfig, CorsConfig, OpenApiConfig
├── controller/     AuthController, StudentController, AcademicController, HealthController
├── service/        AuthService, StudentService, AcademicService,
│                   HealthAdviceService, AiPredictionClient, GradeCalculationService
├── repository/      one Spring Data JPA interface per entity
├── model/           JPA entities
├── dto/             *RequestDTO / *ResponseDTO per entity
├── security/        SupabaseJwtAuthConverter, AccountResolver
└── exception/        ResourceNotFoundException, UnauthorizedAccessException,
                      InvalidInputException, GlobalExceptionHandler
```

### 7.2 Layered Pattern — Controller / Service / Repository

Controllers do request mapping + validation only; all business logic and RBAC
scoping lives in the Service layer so it's testable independent of HTTP.

```java
@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;

    @GetMapping("/{id}")
    public ResponseEntity<StudentResponseDTO> getStudent(
            @PathVariable Long id,
            @AuthenticationPrincipal Account currentUser) {
        return ResponseEntity.ok(studentService.getStudentScoped(id, currentUser));
    }
}
```

```java
@Service
@RequiredArgsConstructor
public class StudentService {

    private final StudentRepository studentRepository;

    public StudentResponseDTO getStudentScoped(Long id, Account currentUser) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found"));

        if (currentUser.getRole() == Role.PARENT
                && !student.getParent().getId().equals(currentUser.getId())) {
            throw new UnauthorizedAccessException("Cannot access another parent's child");
        }
        return StudentResponseDTO.from(student);
    }
}
```

This is the concrete implementation of UC002's Constraint C1
("Parents are strictly restricted to viewing... only their own linked child's
profile") and the report's general RBAC requirement (3.5.2).

### 7.3 Security & Auth Design

Auth now happens in two places with a clean split: Supabase issues and
refreshes tokens, Spring Boot only verifies them.

```text
Sign up / Login / Google OAuth → handled entirely client-side by supabase-js
  → supabase.auth.signUp() / signInWithPassword() / signInWithOAuth({provider:'google'})
  → Supabase returns a session { access_token (JWT), refresh_token }
  → supabase-js stores + auto-refreshes the session; your code just reads
    supabase.auth.getSession() when you need the current token

First login only → frontend calls POST /api/accounts/complete-profile
  (Authorization: Bearer <supabase JWT>) → Spring Boot creates the local
  `account` row: authUserId = JWT 'sub' claim, fullName/role from the request body

Every subsequent request → Authorization: Bearer <supabase JWT>
  → Spring Security (OAuth2 Resource Server) validates the signature via
    Supabase's JWKS endpoint, no shared secret needed in Spring Boot
  → a converter resolves the local Account by authUserId and attaches
    its role as a Spring Security authority
  → @PreAuthorize("hasRole('TEACHER')") etc. works exactly as before
```

**Before wiring this up**, check Supabase Dashboard → Project Settings →
API → JWT Keys to see whether the project is on the legacy shared-secret
(HS256) system or the newer asymmetric signing-keys system (ES256/RSA +
JWKS). New projects increasingly default to asymmetric; if yours is still
legacy, migrating to the new system (one click, no downtime, per Supabase's
docs) is worth doing now since it's what `jwk-set-uri` below expects.

```yaml
# application.yml — only valid for the asymmetric/JWKS signing-keys system
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: https://<project-ref>.supabase.co/auth/v1/.well-known/jwks.json
```

```java
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/accounts/complete-profile", "/swagger-ui/**").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(supabaseJwtAuthConverter())));
        return http.build();
    }

    @Bean
    public Converter<Jwt, AbstractAuthenticationToken> supabaseJwtAuthConverter() {
        // Custom converter: look up Account by jwt.getSubject() (the
        // Supabase auth.users UUID), then return its role as a
        // SimpleGrantedAuthority("ROLE_" + account.getRole()). This is
        // where Spring Security finds out the user is a TEACHER/PARENT/ADMIN —
        // the JWT itself only proves *who* they are, not their app role.
        return new SupabaseJwtAuthConverter(accountRepository);
    }
}
```

This is meaningfully less custom code than rolling your own login/JWT
issuance — `spring-boot-starter-oauth2-resource-server` handles JWKS
fetching, caching, and signature verification for you. The only thing you
write is the small converter that bridges "validated Supabase JWT" to "which
local `Account`, with which role."

RBAC summary (unchanged from before — this is enforced in the Service
layer, see 7.2):

| Role | Scope |
|---|---|
| `PARENT` | Own `Account`; only `Student` rows where `student.parent.id == self`; read/write own children's health logs, read-only academic records |
| `TEACHER` | All students (classroom-scoped once teammate's Classroom CRUD exists); full read/write on Academic & Health modules |
| `ADMIN` | Everything, plus account creation for staff and soft-delete |

### 7.4 REST API Reference

**Auth / Account**

Login, registration, and Google/Facebook OAuth no longer go through Spring
Boot at all — they're `supabase-js` calls made directly from the frontend.
Spring Boot only needs the following:

| Method | Endpoint | Access | Description |
|---|---|---|---|
| POST | `/api/accounts/complete-profile` | Public, but requires a valid Supabase JWT | First-login only: creates the local `account` row (authUserId, fullName, role) from the validated token's `sub` |
| POST | `/api/accounts/register-staff` | ADMIN | Create the `account` profile row for a teacher/admin whose Supabase user already exists (created via Supabase invite or normal sign-up) |
| GET | `/api/auth/me` | Authenticated | Current user's local `account` profile, resolved from the JWT |
| PUT | `/api/accounts/me` | Authenticated | Update own `fullName`/`phoneNumber`/`address` |
| POST | `/api/accounts/me/profile-image` | Authenticated | Multipart upload; stores image in Supabase Storage via `SupabaseStorageService`, saves the public URL to `profileImageUrl` |

**Student Profile (UC002)**

| Method | Endpoint | Access | Description |
|---|---|---|---|
| GET | `/api/students` | TEACHER, ADMIN | List all (optionally filter by classroomId) |
| GET | `/api/students/my-children` | PARENT | List own linked children |
| GET | `/api/students/{id}` | scoped | Get one |
| POST | `/api/students` | TEACHER, ADMIN | Create profile |
| PUT | `/api/students/{id}` | scoped | Update (parent: limited fields; teacher: full) |
| DELETE | `/api/students/{id}` | ADMIN | Soft delete |

**Academic Tracking (UC003)**

| Method | Endpoint | Access | Description |
|---|---|---|---|
| GET | `/api/academic/students/{studentId}/records` | scoped | List records for a student |
| POST | `/api/academic/students/{studentId}/records` | TEACHER | Submit raw scores; server computes average + grade |
| GET | `/api/academic/records/{id}` | scoped | Get one record |
| PUT | `/api/academic/records/{id}` | TEACHER | Update / recalculate |

**Health & Nutrition + AI Advice (UC004, UC005 — bridges `plan.md` Task 8.1)**

| Method | Endpoint | Access | Description |
|---|---|---|---|
| POST | `/api/health/students/{studentId}/records` | TEACHER, PARENT | Log height/weight/MUAC; server computes BMI |
| GET | `/api/health/students/{studentId}/records` | scoped | History (growth chart source) |
| GET | `/api/health/students/{studentId}/records/latest` | scoped | Latest record + status |
| POST | `/api/health/records/{recordId}/generate-advice` | TEACHER | Triggers ML prediction (via FastAPI) + rules engine → saves `HealthAdvice` |
| GET | `/api/health/students/{studentId}/advice/latest` | scoped | Latest advice for parent view |
| PUT | `/api/health/students/{studentId}/allergies` | TEACHER, PARENT | Update allergy profile |
| GET | `/api/health/students/{studentId}/growth-chart` | scoped | `{date, heightCm, weightKg, bmi}[]` for charting |

> Note the `generate-advice` step is deliberately a separate endpoint, not
> automatic on record save — this mirrors UC005's basic flow exactly
> ("Teacher initiates the AI nutritional advice generation" as its own step,
> distinct from saving the raw measurement in UC004).

### 7.5 Error Handling Convention

Use a `@RestControllerAdvice` returning a consistent shape:

```json
{ "timestamp": "...", "status": 400, "error": "Bad Request", "message": "...", "path": "/api/..." }
```

Reuse the exact user-facing strings the report already specifies in its use
cases, so the app stays traceable back to the documented UML. The login
error (UC001 A1) now comes back from `supabase.auth.signInWithPassword()`
on the frontend rather than from Spring Boot — catch it there and map it to
the same wording rather than showing Supabase's raw error string:

| Trigger | Message (from report) | Surfaced by |
|---|---|---|
| Wrong login credentials (UC001 A1) | `Incorrect email or password. Please try again.` | Frontend, mapped from the supabase-js error |
| Incomplete profile save (UC002 A1) | `Please complete all mandatory fields before saving.` | Spring Boot |
| Score outside 0–100 (UC003 A1) | `Invalid score entered. Scores must be between 0 and 100.` | Spring Boot |
| Invalid height/weight (UC004 A1) | `Please enter valid numerical values for height and weight.` | Spring Boot |
| FastAPI unreachable (UC005 A1) | `AI Service is currently unreachable. Please try again.` | Spring Boot |

### 7.6 AI/ML Microservice Integration

Unchanged from `plan.md` 8.0/8.2 — Spring Boot remains the only thing the
frontend talks to; it calls the FastAPI microservice internally.

```java
// service/AiPredictionClient.java
@Service
@RequiredArgsConstructor
public class AiPredictionClient {

    private final RestTemplate restTemplate; // or WebClient
    private static final String AI_SERVICE_URL = "http://localhost:8001/predict";

    public PredictionResult predict(HealthRecord record, Student student) {
        var payload = Map.of(
            "age_months", Period.between(student.getDateOfBirth(), LocalDate.now()).toTotalMonths(),
            "gender", student.getGender().name(),
            "weight_kg", record.getWeightKg(),
            "height_cm", record.getHeightCm(),
            "muac_cm", record.getMuacCm()
        );
        try {
            return restTemplate.postForObject(AI_SERVICE_URL, payload, PredictionResult.class);
        } catch (RestClientException e) {
            throw new AiServiceUnavailableException("AI Service is currently unreachable. Please try again.");
        }
    }
}
```

`HealthAdviceService.java` then takes the `PredictionResult` (nutrition
status + confidence) and runs the rules-engine logic ported from
`AI/rules_engine/healthAdviceEngine.js`, exactly as `plan.md` 8.6 describes —
that mapping/porting work doesn't change here, this just gives it a concrete
caller. The concrete rules it must enforce (pulled forward from `plan.md`
Tasks 7.2–7.4 and 8.4 so this is self-contained without re-reading the
ML-focused doc):

- **Allergy guardrails always win**: if the child has a recorded allergy, any
  dietary advice item mentioning that allergen is filtered out before the
  response is built — guardrails are never overridden by dietary
  recommendations, and allergy warnings render first/most prominently.
- **Confidence caveat**: if `modelConfidence < 0.70`, append a caveat
  recommending professional confirmation rather than presenting the
  prediction as certain.
- **Urgent referral**: if `nutritionStatus == SEVERE` (or a SAM flag is
  raised), prepend a "consult a healthcare professional" recommendation as
  the *first* advice item, ahead of dietary/activity advice.

This logic already exists in `mytadika-backend/src/main/java/com/mytadika/service/HealthAdviceService.java`
— treat the above as the spec to check that implementation against, not a
new feature to design from scratch.

---

## 8. Frontend Architecture (React)

### 8.1 Folder Structure

```text
src/
├── pages/
│   ├── auth/        LoginPage.jsx, RegisterPage.jsx
│   ├── profile/     ProfilePage.jsx
│   ├── dashboard/   ParentDashboardPage.jsx, TeacherDashboardPage.jsx
│   ├── students/    StudentListPage.jsx, StudentProfilePage.jsx
│   ├── academic/    AcademicTrackingPage.jsx
│   └── health/      HealthTrackerPage.jsx
├── components/
│   ├── common/      Button, Card, Modal, Badge, Sidebar, Navbar
│   ├── students/    StudentCard, StudentSearchBar
│   ├── academic/    PerformanceRadarChart, ScoreInputForm, GradeTable
│   └── health/      HealthSummaryCard, GrowthChart, AIAdvicePanel,
│                     AllergyAlertBanner, MeasurementForm, HistoryTimeline
├── layouts/         ParentLayout.jsx, TeacherLayout.jsx, AuthLayout.jsx
├── hooks/           useAuth.js, useStudents.js, useHealthRecords.js, useAcademicRecords.js
├── services/api/    axiosClient.js, authApi.js, studentApi.js, academicApi.js, healthApi.js
├── context/         AuthContext.jsx
└── routes/          AppRouter.jsx, ProtectedRoute.jsx
```

(`components/health/*` names are taken directly from `plan.md` 9.1 — no
renaming needed when you wire up the AI advice UI later.)

### 8.2 Routing & Role-Based Layout

```jsx
// routes/ProtectedRoute.jsx
export function ProtectedRoute({ allowedRoles, children }) {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" />;
  if (allowedRoles && !allowedRoles.includes(user.role)) return <Navigate to="/" />;
  return children;
}
```

Sidebar nav differs by role per the wireframes — scaffold both, even though
Chat/Memory Box/Events route to teammate's pages for now:

| Parent nav | Teacher nav |
|---|---|
| Home, Academic Report, Health, Classroom*, Messages*, Memory Box*, Events*, Profile, Help | Home, Classroom*, Student Reports (Academic), Health, Messages*, Memory Box*, Profile |

(*items marked with an asterisk render a placeholder until your teammate's
module lands — keep the route and nav item present so layout doesn't shift
later.)

### 8.3 Data Fetching Convention

```js
// services/api/axiosClient.js
import { supabase } from "../supabaseClient";

const api = axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL });
api.interceptors.request.use(async (config) => {
  const { data } = await supabase.auth.getSession();
  const token = data.session?.access_token;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});
```

`supabase-js` already persists and silently refreshes the session — there's
no manual `localStorage` token management or a `/refresh` endpoint to build
on the frontend.

```js
// hooks/useHealthRecords.js
export function useGrowthChart(studentId) {
  return useQuery({
    queryKey: ["growth-chart", studentId],
    queryFn: () => healthApi.getGrowthChart(studentId),
  });
}
```

All server state goes through React Query hooks like this — no manual
`useEffect` + `fetch` for data that the cache can own.

### 8.4 Design System

Colors lifted directly from the wireframes (warm, kindergarten-friendly):

```js
// tailwind.config.js (excerpt)
colors: {
  primary:   "#FFC727", // sunny yellow — buttons, active nav
  background:"#FFF8E8", // cream page background
  accent:    "#FF9F43", // orange highlights
  success:   "#4CAF50", // healthy/normal status
  warning:   "#FF9F43", // moderate concern
  danger:    "#FF6B6B", // severe concern / alerts
  ink:       "#3D3D3D", // body text
}
```

Cards use generous rounding (`rounded-2xl`) and soft shadows; status badges
use the traffic-light convention `plan.md` 9.2 already specifies (green /
amber / red for normal / moderate / severe).

### 8.5 Page-by-Page Component Breakdown

| Page (wireframe) | Key components |
|---|---|
| Login | `RoleSelector`, `LoginForm`, `GoogleLoginButton`/`FacebookLoginButton` (now live — `supabase.auth.signInWithOAuth`, no longer deferred) |
| Profile | `ProfileCard`, `LinkedChildrenList`, `AccountPreferencesGrid`, `LogoutButton` |
| Parent Home | `GreetingBanner`, `TodaySchedule`, `MemoryBoxPreview*`, `UpcomingReminders*`, `QuickActions*` |
| Academic Tracking (parent) | `PerformanceRadarChart`, `MonthlyGrowthBadge`, `AttendanceBadge`, `AlertCard`, `TeacherCommentCard`, `DownloadReportButton` |
| Academic Tracking (teacher) | `StudentReportGrid`, `ScoreInputForm`, `ClassroomMilestones*` |
| Health & AI Advice | `HealthSummaryCard`, `AIAdvicePanel`, `AllergyAlertBanner`, `GrowthChart`, `MeasurementForm`, `HistoryTimeline` |

(*teammate's data — render with mock/empty state until that module exists)

### 8.6 Sample Page Skeleton

```jsx
// pages/health/HealthTrackerPage.jsx
export default function HealthTrackerPage() {
  const { studentId } = useParams();
  const { data: latest } = useLatestHealthRecord(studentId);
  const { data: chartData } = useGrowthChart(studentId);
  const { data: advice } = useLatestAdvice(studentId);

  return (
    <div className="space-y-4 p-4">
      <AllergyAlertBanner studentId={studentId} />
      <AIAdvicePanel advice={advice} />
      <HealthSummaryCard record={latest} />
      <GrowthChart data={chartData} />
      <MeasurementForm studentId={studentId} />
      <HistoryTimeline studentId={studentId} />
    </div>
  );
}
```

---

## 9. Use-Case Traceability (sanity check against the FYP UML)

| UC | Backend endpoint(s) | Frontend page |
|---|---|---|
| UC001 Login | Supabase Auth (client-side) + `GET /api/auth/me` | LoginPage |
| UC002 Manage Student Profile | `/api/students/**` | StudentProfilePage |
| UC003 Manage Academic Tracking | `/api/academic/**` | AcademicTrackingPage |
| UC004 Manage Health Records | `/api/health/students/{id}/records` | HealthTrackerPage → MeasurementForm |
| UC005 Generate AI Advice | `/api/health/records/{id}/generate-advice` | HealthTrackerPage → AIAdvicePanel |

If a coding assistant is implementing a use case, point it at the matching
row here plus the corresponding use-case table in FYP1 Report Chapter 4 for
the exact basic/alternative flow.

---

## 10. Build Phases / Task Checklist

Work top to bottom — each phase depends on the entities/auth from the one
before it.

> **Actual state as of 2026-06-20** (confirmed by reading the codebase, not
> assumed): **Phases 0–3 and Phase 5's backend half are built.**
> `Account`/`Student`/`Classroom`/`Gender`/`Role` entities, `SecurityConfig`
> (OAuth2 resource server), `SupabaseJwtAuthConverter`, `AccountResolver`,
> `AccountController`/`AuthController`/`StudentController`, and the
> `AccountService`/`StudentService` RBAC-scoped logic all exist under
> `mytadika-backend/src/main/java/com/mytadika/`, alongside the already-built
> `HealthRecord`/`AllergyProfile`/`HealthController`/`HealthAdviceService`/
> `AiPredictionClient` (Phase 5). The Maven wrapper (`mvnw`) is in place and
> the backend compiles clean (`mvn clean compile`, 37 source files). On the
> frontend, `mytadika-frontend` is scaffolded with `supabaseClient.js`,
> `AuthContext`, `axiosClient` (Supabase-session interceptor),
> `ProtectedRoute`, and working `LoginPage`/`RegisterPage`/
> `ParentDashboard`/Student pages — not stubs. **Update (2026-06-21): Phase 4
> is now also built** — `AcademicRecord`/`AcademicScoreItem` entities,
> `GradeCalculationService`, `AcademicService`/`AcademicController`
> (`/api/academic/students/{id}/records`, `/api/academic/records/{id}`), and
> a frontend `AcademicTrackingPage` (`/students/:studentId/academic`, linked
> from `StudentProfilePage`) all compile/build clean. **Update (2026-06-21,
> later same day): Phase 5 is now also fully built and closed out** —
> `HealthTrackerPage` + the `components/health/*` set already existed on
> disk; the two open items (confirm the FastAPI↔`AiPredictionClient`
> contract, fix the missing RBAC on `HealthController`) are both done — see
> Phase 5's checklist below for what was verified/changed. **Update
> (2026-06-21, same day): Phase 6 is now also done** — role-based sidebar/nav
> (`layouts/AuthenticatedLayout.jsx`, `components/common/Sidebar.jsx`,
> `ProfilePage`, `ComingSoonPage`, `TeacherDashboard`), the one missing
> loading/error state (`AllergyAlertBanner`), 28 passing backend unit tests
> across 4 new test classes, and deployment prep — see Phase 6's checklist
> below, including a real credential-exposure finding from the deployment
> prep pass (Supabase DB password leaked on `origin/main`'s history, not this
> branch) that still needs the user to actually rotate the password. **All
> six phases are now built**; nothing left on the original checklist.
>
> **Teammate integration (2026-06-21):** teammate pushed a divergent backend
> directly to `origin/main` (commit `f2ab736`, branched from the same
> `0cf1e06` base this branch also forked from) — a server-rendered app with
> its own BCrypt/form-login `Account` (String PK, password field, static
> HTML pages under `static/`), `RestAuthController`/`AuthService`/
> `EmailService`/password-reset flow. That auth/profile/HTML stack
> contradicts the Supabase Auth + React decision in Section 3 and was **not**
> merged. What *was* cherry-picked into this branch: `SupabaseStorageService`
> (Supabase Storage upload via service-role key) and an `address` +
> `profileImageUrl` pair added to `Account`, now exposed through
> `PUT /api/accounts/me` and `POST /api/accounts/me/profile-image` (JWT-scoped
> to the caller, unlike the teammate's unauthenticated `accountId`-path-param
> version). Their `HealthAdviceService`/`AiPredictionClient`/`HealthRecord`/
> `AllergyProfile` changes on `origin/main` were diffed and found to be the
> same logic as this branch's versions (package-move + Lombok-removal only;
> no functional changes), so nothing further to port there. One open item:
> their `AI/api/predictor.py` (in the untracked `teammate latest/` folder)
> differs substantially from this branch's recently-fixed `AI/api/predictor.py`
> (commit `49650ee`) — not yet compared in detail; don't assume either is
> stale without checking. Also flagging pre-existing issue, unrelated to this
> integration: `mytadika-backend/src/main/resources/application.properties`
> has the Supabase DB password committed in plaintext — worth moving to an
> env var before final submission.

**Phase 0 — Scaffolding**
- [x] Supabase project created; note the project ref, region, and DB password; confirm legacy vs. asymmetric JWT signing keys (Dashboard → API → JWT Keys)
- [x] Spring Initializr project: Web, JPA, PostgreSQL Driver, OAuth2 Resource Server, Validation, Lombok
- [x] Vite React app; install axios, react-router-dom, @tanstack/react-query, @supabase/supabase-js, tailwindcss, recharts, lucide-react, react-hook-form, zod
- [x] `application.yml` datasource pointed at the Supabase **session pooler** string (Section 6.5)
- [x] CORS config (allow `http://localhost:5173`), global exception handler skeleton, base `ApiResponse<T>` wrapper

**Phase 1 — Shared Core Schema**
- [x] `Account` entity (with `authUserId`) + repository
- [x] `Classroom` stub entity + repository
- [x] `Student` entity (with `gender`) + repository
- [x] Verify schema generation against Supabase (`ddl-auto=update` for now)

**Phase 2 — Authentication**
- [x] Enable Email/Password + Google (and Facebook, if wanted) providers in Supabase Dashboard → Authentication → Providers
- [x] `SecurityConfig` with `oauth2ResourceServer().jwt()` + `SupabaseJwtAuthConverter` + `AccountResolver`
- [x] `AccountController`: `complete-profile`, `register-staff`; `AuthController`: `me`
- [x] Frontend: `supabaseClient.js`, `LoginPage`/`RegisterPage` using `supabase-js`, `AuthContext` wrapping `onAuthStateChange`, axios interceptor reading the Supabase session, `ProtectedRoute`

**Phase 3 — Student Profile**
- [x] `StudentController`/`Service`/DTOs with RBAC scoping
- [x] Frontend: `StudentListPage` (teacher), `StudentProfilePage`

**Phase 4 — Academic Performance Tracking**
- [x] `AcademicRecord` + `AcademicScoreItem` entities, `GradeCalculationService`
- [x] `AcademicController` endpoints
- [x] Frontend: `AcademicTrackingPage` (table view with grade badges; teacher gets an add/edit score form, parent gets read-only — no radar chart, since `recharts` was never actually added to this project's `package.json`)

**Phase 5 — Health & Nutrition + AI Advice**
- [x] `HealthRecord`, `AllergyProfile`, `HealthAdvice` entities/repositories
- [x] `HealthController`, `HealthAdviceService` (port rules engine), `AiPredictionClient` — added one missing endpoint, `GET /api/health/allergies/{studentId}`, so the frontend edit form has something to load (only `PUT` existed before)
- [x] Confirm FastAPI request/response contract matches `AiPredictionClient` — verified field-for-field against `AI/api/schemas.py`/`predictor.py` (child_id/age_months/weight_kg/height_cm/muac_cm/bmi/gender in, status/encoded/confidence/probabilities/flags/model_version out) and ran both live: started the FastAPI service against the real `best_model.joblib` and ran `AI/api/test_fastapi.py` (10/10 passed), then booted Spring Boot against the dev Supabase DB to confirm it starts clean with that contract wired in.
- [x] Frontend: `HealthTrackerPage` (`/students/:studentId/health`, linked from `StudentProfilePage`) + `AllergyAlertBanner`/`HealthSummaryCard`/`AIAdvicePanel`/`MeasurementForm`/`HistoryTimeline` in `components/health/`. **Fixed the RBAC gap noted previously:** `HealthController` now injects `AccountResolver` + `StudentRepository` and applies the same scoping pattern as `AcademicController`/`AcademicService` — `@PreAuthorize("hasAnyRole('TEACHER','PARENT','ADMIN')")` on the write/record endpoints, plus an `assertCanAccessStudent` ownership check (PARENT can only touch their own child's `studentId`) on every endpoint that reads or writes a specific student's health/allergy data. Verified live: an unauthenticated `GET /api/health/history/1` against the running app now returns `401` instead of the data. `AI/api/test_springboot.py` predates this auth layer (calls endpoints with no JWT) and will need a real Supabase access token to exercise going forward — not something fixable without live user credentials.

**Phase 6 — Integration & Polish**
- [x] Role-based sidebar/nav matching wireframes for both portals — added `layouts/AuthenticatedLayout.jsx` (wraps every authenticated route in a flex shell with `<Outlet/>`) and `components/common/Sidebar.jsx`, which renders role-specific nav (`ParentSidebar`/`TeacherSidebar`/`AdminSidebar`) per Section 8.2's tables. Parent's "Academic Report"/"Health" links resolve straight to that child's page when there's exactly one linked child, otherwise fall back to the dashboard. Teammate's modules (Classroom/Messages/Memory Box/Events) got a shared `ComingSoonPage` so the nav item and route exist without faking functionality; added a real `ProfilePage` (`PUT /api/accounts/me` was already built, just unused) and a static `HelpPage`. `PlaceholderDashboard` is now Admin-only; Teacher got a real `TeacherDashboard` with a student count + quick link. Note: `tailwind`/`lucide-react` were never actually added to `package.json` (same gap Phase 4 flagged for `recharts`) — nav styling uses the existing plain-CSS variable system in `index.css`, not Tailwind utility classes as Section 2/8.4 describe.
- [x] Loading/empty/error states across all pages — audited every data-fetching page/component; most already had `isLoading`/`isError`/empty handling (`StudentListPage`, `StudentProfilePage`, `AcademicTrackingPage`, `HealthTrackerPage`, `HealthSummaryCard`, `AIAdvicePanel`, `HistoryTimeline`). Fixed the one real gap: `AllergyAlertBanner` ignored `isError` from `useAllergies` and would silently render "None on file" on a network failure — it now shows an explicit error message instead.
- [x] Unit tests: `GradeCalculationService`, BMI calculation, RBAC scoping checks — added `GradeCalculationServiceTest` (all grade boundaries + average + label mapping), `StudentServiceTest` and `AcademicServiceTest` (parent-ownership scoping, teacher/admin bypass), and `HealthControllerTest` (RBAC on the `assertCanAccessStudent` check added this session, plus a BMI-from-weight/height assertion via an `ArgumentCaptor` on the saved `HealthRecord`). 28 tests total, all passing (`mvnw test`).
- [x] Deployment prep — verified both build scripts (`mvnw compile`/`test`, `npm run build`/`lint`) are clean. **Found a real credential leak while doing this:** the Supabase DB password is *not* committed anywhere on this branch (`application.properties` is correctly gitignored and untracked here), but it **is** committed in plaintext on `origin/main` at the teammate's commit `f2ab736` (`spring.datasource.password=...`). That's in shared remote history now — rewriting it would need a coordinated force-push, which wasn't done unilaterally. **Recommend rotating the Supabase DB password from the dashboard before final submission/grading**, since anyone with read access to the repo can see the old one in `origin/main`'s history regardless of what this branch does. Also switched this branch's local (untracked) `application.properties` from a bare literal to `${SUPABASE_DB_PASSWORD:<existing value>}`, matching the env-var pattern Section 6.5 already documents and the one already used for `SUPABASE_SERVICE_ROLE_KEY` — verified the app still boots against the live DB with that placeholder syntax. Flyway adoption skipped — still correctly deferred per [Section 12](#12-open-questions-for-later).

---

## 11. Out of Scope (Teammate's Modules)

Do not build these here — they belong to the Parent Engagement &
Communication module:

- `Classroom` full CRUD (you only have the read-only FK stub)
- `Gallery` (Memorable Moments)
- `Notification` / `Notification_Recipient`
- `Message` (Teacher–Parent chat)
- `AI_Report` (general academic/behavioral insights — distinct from your BMI-driven `HealthAdvice`)
- Admin Management module (system-wide configuration)

If integration work is needed (e.g., your `Account`/`Student` tables being
the FK target for their `Message`/`Gallery` tables), that's a schema
contract to agree on with your teammate, not something to build unilaterally
here.

**`AI_Report` specifically**: don't build it, don't write to it, and don't
duplicate your health advice into it. It's fine for it to *read* from your
`HealthAdvice`/health data (e.g. a join on `student_id`) so the teammate's
general AI analytics can surface your health insights alongside theirs —
that's a one-way dependency in their favor, not a merge of the two concepts.
Agree the exact read contract (endpoint vs. direct table read, which
columns) with them directly; don't decide it unilaterally in this file.

---

## 12. Open Questions for Later

- Whether to also use Supabase Storage/Realtime for your teammate's
  Gallery/Chat/Notification modules — not your call alone, but worth raising
  with them since you're already on the platform.
- Whether `studentCode` (the report's `STU20001`-style display ID) is
  actually needed anywhere in the UI, or whether the auto-increment `id` is
  fine to show as-is.
- Flyway migration adoption timing — recommended before final
  deployment/submission, not necessary during active development.
- If the Supabase project is still on the legacy HS256 JWT secret rather
  than the new asymmetric signing keys, decide whether to migrate (recommended,
  needed for the `jwk-set-uri` approach in 7.3) or instead validate manually
  with the shared secret.
