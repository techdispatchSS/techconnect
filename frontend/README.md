# TechConnect frontend

The web app for TechConnect: the Controller dashboard (incident queue, dispatch, ticket
progress), the Manager's admin portal, the public technician application, and the sign-in and
account-recovery pages. Angular 21, standalone components and signals, zoneless change detection.

It talks to the Spring Boot API in `../backend` (`/api/v1/...`). See the root `README.md` for
running the whole stack with Docker Compose.

## Running it

```bash
npm ci
npm start          # ng serve on http://localhost:4200, proxying /api to localhost:8080
```

`proxy.conf.json` sends `/api` to the backend, so the dev server and the API share an origin the
way they do behind nginx and CloudFront.

## Checks

```bash
npm test                 # unit tests (Vitest), once: npm test -- --watch=false
npm run lint             # ESLint
npm run format:check     # Prettier (use `format` to fix)
npm run build            # production build into dist/
```

CI runs lint, the format check, the tests, the build and a dependency audit on every pull
request. On Windows, run Prettier with `--end-of-line auto` so it leaves line endings alone.

## Layout

| Path | What lives there |
|---|---|
| `src/app/core` | Auth (session, guards, interceptor), theme, Google Places, shared models |
| `src/app/features/auth` | Sign-in, activation, password reset |
| `src/app/features/controller` | Controller dashboard: queue, create dispatch, ticket progress, log incident |
| `src/app/features/admin` | Manager's portal: users, applications, audit log |
| `src/app/features/apply` | Public technician application |
| `src/app/shared` | Reusable pieces: logo, street search, dialogs, skeletons |
| `public/` | Static files served at the site root (icons, manifest, link-preview image, brand artwork) |

## Branding

The logo is drawn by `<app-logo>` (`src/app/shared/logo`), inline, so the wordmark uses the page's
Barlow font and its grey follows the surface it sits on: set `--tc-logo-ink` on a dark surface.
Use `variant="mark"` for the circle mark alone and `variant="lockup"` for mark plus wordmark.

Static artwork in `public/`:

| File | Used for |
|---|---|
| `favicon.svg`, `favicon.ico` | Browser tab icon. Both have no background; the SVG lightens its grey in dark tabs, the ICO uses a mid-grey that reads on either |
| `apple-touch-icon.png` | iOS home screen |
| `icon-192.png`, `icon-512.png`, `icon-maskable-512.png`, `manifest.webmanifest` | Install icons for Android and desktop |
| `og-image.png` | Link previews (WhatsApp, Slack, LinkedIn, iMessage); 1200 x 630 |
| `brand/techconnect-mark.svg`, `brand/techconnect-lockup.svg` | The original artwork |
| `brand/techconnect-email-logo.png` | Header image in HTML emails (the backend links to it) |

The home-screen and install PNGs are renders of the mark on the brand's dark tile (`#17181c`),
because those surfaces need a solid background; the tab icons are transparent. If the
artwork changes, re-render them rather than editing by hand, and keep `favicon.svg` and
`<app-logo>` in step.

## Environment

`src/environments/environment.ts` holds `googleMapsApiKey`, left blank in the repository. The
deploy workflow injects it from the `GOOGLE_MAPS_API_KEY` repository variable; without it,
address search is disabled and the address fields accept typed input only.
