# TechConnect

A dispatch and field-service platform: Controllers triage incoming incidents and dispatch them
to Technicians, who respond, accept, and work the job; Managers administer accounts and review
the audit log. Backend is a Spring Boot 4 / Java 21 API, frontend an Angular 21 (zoneless) PWA,
behind nginx in production/Docker.

## Prerequisites

- **Java 21** (backend)
- **Node.js 22** and npm (frontend — see `frontend/package.json` for the Angular CLI version)
- **Docker** and **Docker Compose** (Postgres, Mailpit, LocalStack, and optionally the full
  containerised stack below)

## Run the full stack

```bash
cp .env.example .env
```

Then open `.env` and set `TECHCONNECT_JWT_SECRET` — there is no default, and the backend
container refuses to start without one:

```bash
openssl rand -base64 48
```

```bash
docker compose up -d --build
```

This builds and runs every service: Postgres, [Mailpit](http://localhost:8025) (catches
activation/reset emails locally), LocalStack, the Spring Boot API, and the Angular frontend
behind nginx.

- App: http://localhost:4200
- API: http://localhost:8080/api (proxied through the frontend's nginx at `/api` too)
- Mailpit UI: http://localhost:8025

The backend has no user to sign in with until a Manager exists. If `TECHCONNECT_BOOTSTRAP_ADMIN_EMAIL`
is set (it is, in `.env.example`), the app creates one on first startup and logs a one-time
activation link — find it with:

```bash
docker compose logs backend | grep -A3 "Bootstrap administrator created"
```

Open that link at `http://localhost:4200/activate?token=...` to set a password and sign in.

`docker compose down` stops everything; add `-v` to also drop the Postgres and LocalStack
volumes for a clean slate.

## Run backend and frontend on the host instead

Faster edit/reload loops than rebuilding containers on every change. Only Postgres (and
Mailpit, for testing activation/reset emails) need to run in Docker; LocalStack is only needed
once Phase 2 AWS-backed features are wired up.

```bash
docker compose up -d postgres mailpit
```

```bash
cd backend
POSTGRES_PORT=5434 ./mvnw spring-boot:run
```

```bash
cd frontend
npm ci
npm start
```

See [backend/README.md](backend/README.md) and [frontend/README.md](frontend/README.md) for
the full set of environment variables, the first-run Manager activation flow, running tests,
and building for production.
