# MyTadika Frontend — Running & Testing

## Test it now (frontend only)

```bash
cd mytadika-frontend
npm install     # first time only
npm run dev
```

Open the URL Vite prints (usually `http://localhost:5173`).

What works today: the `/login` page only. Pick a role (Parent / Teacher /
Admin), enter any email/password, and submit — there's no real backend auth
yet, so it just routes you to a placeholder dashboard for that role
(`/parent/dashboard`, `/teacher/dashboard`, `/admin/dashboard`). Anything you
type "succeeds."

## Test the full system (once backend/AI are wired up)

This isn't functional yet — Phases 0–2 in `CLAUDE.md` (Supabase project,
Spring Boot scaffolding, auth wiring) haven't landed. Once they have, start
things in this order:

1. **AI microservice** (`AI/`):
   ```bash
   cd AI/api
   uvicorn main:app --reload --port 8001
   ```
2. **Backend** (`mytadika-backend/`):
   ```bash
   cd mytadika-backend
   mvn spring-boot:run
   ```
   Runs on `http://localhost:8080` by default. Needs DB connection details
   (Supabase session pooler string) configured first — see `CLAUDE.md`
   Section 6.5.
3. **Frontend** (`mytadika-frontend/`):
   ```bash
   npm run dev
   ```

Start the AI service and backend first so the frontend has something to
talk to. Update this section as auth and real API calls come online.
