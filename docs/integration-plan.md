# Integration Plan: Merge teammate's backend modules + adopt teammate's frontend

> Status: **executed — all phases (A–D) complete.** Tracked the merge of `mytadika-backend(teammate)/` into `mytadika-backend/`, and consolidation onto teammate's static-HTML frontend. `mytadika-backend(teammate)/` has been deleted; all 28 backend tests pass (`mvn test`).

## Context

The repo currently has two full backend copies:

- **`mytadika-backend/`** (git-tracked, canonical) — has already absorbed most of the teammate's business modules (Chat, Classroom, Events, Fees, Memory, Notifications, Admin) in earlier work, and has since **evolved past** the teammate's copy: it has real stateless JWT auth (`security/JwtService.java`, `security/JwtAuthenticationFilter.java`), a locked-down `SecurityConfig` (only `/api/auth/**` + static assets are public, everything else requires a valid Bearer token), a full exception hierarchy (`exception/*`, 7 files), `CorsConfig`, validated DTOs, and the only existing test suite (`HealthControllerTest`, `AcademicServiceTest`, `GradeCalculationServiceTest`, `StudentServiceTest`).
- **`mytadika-backend(teammate)/`** (untracked, a fresh unmodified snapshot of the teammate's contribution, previously under `teammate contribution/MyTadika-main/mytadika-backend/`) — has two things `mytadika-backend` doesn't yet have: a **payment feature** (Stripe + ToyyibPay: `StripeConfig`, `PaymentController`, `StripePaymentService`, `ToyyibPayService`) and **memory comments/reactions** (`MemoryComment`, `MemoryReaction` + repos). Its `SecurityConfig` permits `/api/**` entirely (no auth enforced) and it has no test directory — this copy is behind, not ahead, on auth/safety.
- Its `src/main/resources/static/` is also the **actual frontend** the user wants: a complete role-based static HTML/JS site (admin/parent/teacher pages + shared `components/`) served directly by Spring Boot. A stale copy of this was already bulk-copied into `mytadika-backend/static/` but is missing 3 newer admin pages (`adminacademiclist.html`, `admingallery.html`, `adminhealthlist.html`) and every shared file differs from teammate's latest version.
- Separately, `mytadika-frontend/` (a plain multi-page ES-module SPA matching CLAUDE.md's original documented architecture) and the ad hoc `bridge.html` (a cross-origin localStorage token handoff page) represent an **abandoned parallel frontend effort** — confirmed stale by `docs/system_development_plan.md`, which describes a React/Vite SPA that was never actually built (CLAUDE.md already notes `react-doctor` is "suspended").

Goal: end up with **one backend** (`mytadika-backend`, keeping its safer auth/error-handling/tests) serving **one frontend** (teammate's static HTML site, refreshed to the latest copy), with the teammate's payment and memory-social features ported in. Retire the parallel SPA attempt.

## Decisions

1. **Canonical backend = `mytadika-backend`.** Do not switch base folders — port teammate's *missing* pieces into it, don't overwrite its auth/security/exception work.
2. **Canonical frontend = teammate's static site**, refreshed into `mytadika-backend/src/main/resources/static/`, replacing the stale copy already there.
3. **Keep `components/auth-fetch.js`** (user-only file, not in teammate's copy) — it patches `window.fetch` to attach `Authorization: Bearer <token>`, which is now *required* since `mytadika-backend`'s `SecurityConfig` actually enforces auth (teammate's pages were written assuming no enforcement). Every refreshed page must keep including it.
4. **Retire `mytadika-frontend/` and `bridge.html`** — once there's a single same-origin frontend, the cross-origin bridge and the separate SPA serve no purpose. Recommend deleting both (they're not referenced by the canonical frontend); confirm before deleting since it removes a chunk of prior work rather than just merging it.
5. **Keep `mytadika-backend`'s standalone `model/Role.java` enum**, not teammate's nested `Account.RoleType` — same values, but `Role` is what the current `JwtService`/`SecurityConfig`/`AuthController` already depend on. Ported files must not reintroduce `Account.RoleType`.
6. **Skip porting `AcademicScoreItemRepository`** (teammate-only, confirmed via grep to be unused — `mytadika-backend` manages score items via cascade off `AcademicRecord`).
7. **`pom.xml`**: keep `mytadika-backend`'s Spring Boot `3.5.15` + `jjwt` (self-issued JWT, replacing the abandoned Supabase-JWKS plan) and add teammate's `com.stripe:stripe-java:29.2.0`. Do not downgrade to teammate's `3.2.5`.
8. **New payment endpoints must be wired into the real `SecurityConfig`**, not left permit-all like teammate's copy — decide per-endpoint (e.g. a Stripe webhook likely needs to stay public but signature-verified; parent-facing payment endpoints need `.authenticated()`).
9. **Secrets**: teammate's `application.properties` has Stripe test keys and ToyyibPay sandbox keys committed in plaintext. Bring them over for now (needed to run), but add them to the existing "rotate before submission" checklist item in CLAUDE.md alongside the DB password.

## Steps

### Phase A — Frontend consolidation ✅ Done
1. Replace `mytadika-backend/src/main/resources/static/{admin,parent,teacher,components}/*` and root `login.html` / `forgotpassword.html` / `resetpassword.html` / `createparentaccount.html` with the latest versions from `mytadika-backend(teammate)/src/main/resources/static/`, **except** keep `components/auth-fetch.js` as-is (user-only).
2. Add the 3 new admin pages teammate has that the stale copy lacks: `adminacademiclist.html`, `admingallery.html`, `adminhealthlist.html`.
3. Re-insert the `<script src="components/auth-fetch.js">` include on every refreshed page (teammate's originals don't have it, since they assumed session/cookie auth) — this is the key step that makes the refreshed pages actually work against the real, auth-enforcing backend.
4. Spot-check a parent page, a teacher page, and an admin page's JS to confirm their `fetch()` calls target endpoints that exist in `mytadika-backend`'s controllers (not just `login.html`, which was already verified).
5. Delete `bridge.html` and `mytadika-frontend/` (pending confirmation).

### Phase B — Port teammate-only backend modules ✅ Done
6. Copy `model/MemoryComment.java`, `model/MemoryReaction.java`, `repository/MemoryCommentRepository.java`, `repository/MemoryReactionRepository.java` into `mytadika-backend`; wire into the existing `MemoryController`/`MemoryService` (reuse teammate's wiring there as reference for what endpoints call these repos).
7. Copy `config/StripeConfig.java`, `controller/PaymentController.java`, `service/StripePaymentService.java`, `service/ToyyibPayService.java` into `mytadika-backend`, same package paths.
8. Update `pom.xml`: add `com.stripe:stripe-java:29.2.0`.
9. Update `application.properties`: add `stripe.secret-key` / `stripe.publishable-key` / `stripe.webhook-secret` and `toyyibpay.base-url` / `toyyibpay.user-secret-key` / `toyyibpay.category-code` from teammate's file.

### Phase C — Auth correctness pass on the ported code ✅ Done
10. Update `SecurityConfig` to add explicit rules for the new payment endpoints (don't inherit teammate's `permitAll` on `/api/**`).
11. Grep the newly-ported files for `Account.RoleType` / `RoleType` and replace with `mytadika-backend`'s standalone `Role` enum.

### Phase D — Cleanup ✅ Done
12. Delete `mytadika-backend(teammate)/` (untracked scratch copy) once everything needed has been pulled from it.
13. Update `CLAUDE.md`: fix the stale "Integer auto-increment PK" decision (actual PK is a `String accountId`, already renegotiated per code comments), rewrite §4/§8 to describe the real static-HTML-in-Spring-Boot frontend architecture instead of the abandoned ES-module SPA plan, and note `docs/plan.md` / `docs/system_development_plan.md` as historical/stale rather than current design docs.

## Verification
- [x] `mvn test` in `mytadika-backend` — the 4 existing test classes must stay green through all phases. **28/28 tests pass, BUILD SUCCESS.**
- [ ] Start the backend, open `login.html` at `localhost:8080`, log in as each of PARENT/TEACHER/ADMIN, and click through every refreshed page (chat, memory/gallery incl. new comment/reaction UI, fees/payment, classroom, events, notifications), confirming `auth-fetch.js` attaches the Bearer token and calls return 200s, not 401/403. — **not yet done manually, recommend before final submission.**
- [ ] Exercise the Stripe/ToyyibPay flow against sandbox keys to confirm the ported payment endpoints respond correctly. — **not yet done manually.**
- [x] Confirm no remaining page references the old `mytadika-frontend` origin or `bridge.html`. — both were removed as part of Phase A/git merge; `mytadika-backend(teammate)/` scratch copy also deleted (Phase D).

## Addendum — follow-up folder restructure

After this plan's phases completed, `mytadika-backend/` was renamed to top-level `backend/`, and its `src/main/resources/static/` frontend was pulled out into a new top-level `frontend/` folder as the frontend's source of truth. `backend/pom.xml` now declares `frontend/` as an extra Maven resource directory (`targetPath=static`), so it's still copied into the classpath and served same-origin by Spring Boot at build/run time — behavior is unchanged, only the source layout is cleaner. See CLAUDE.md §4 for the current structure. `mvn test` (28/28) and a live `spring-boot:run` smoke test (`GET /login.html` → 200) both passed against the new layout.
