# techconnect

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

For running the backend or frontend directly on the host instead (faster edit/reload loops),
see [backend/README.md](backend/README.md) and [frontend/README.md](frontend/README.md).
