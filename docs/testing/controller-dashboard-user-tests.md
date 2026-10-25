# Controller dashboard — user test report

| | |
|---|---|
| **Date** | 12 October 2026 |
| **Build under test** | `main` at `eca63af` (PR #42 merged), running as the local Docker stack (backend :8080, frontend :4200, Postgres, Mailpit) |
| **Role tested** | Controller (plus Technician, Manager and unauthenticated callers for access rules) |
| **Method** | 61 scripted API cases against the live backend + hands-on UI walkthrough in a browser, as a Controller |
| **Data** | Dedicated `uat.*@techconnect.test` accounts and `UAT …` incidents. All of it, and its test emails, was removed afterwards. Pre-existing data was not changed. |
| **Result** | **49 pass · 2 defects · 4 gaps · 6 observations** (API) · plus 1 UI defect and further observations below |
| **Status** | Defects **D-1 to D-3 are fixed in the same PR as this report** and covered by new automated tests (backend 136/136, frontend 125/125). They were not re-run by hand in the UI. The results in section 4 are as found, before the fix. |

---

## 1. Read this first: the requirements source

The code cites a **"PRD" with FR-01 … FR-09** (for example `PRD FR-04`, `PRD §8.2`), but **that document is not in the repository, the Downloads folder or the session history**, and Google Drive is not authorised for this assistant. The only FR *wording* that survives in the code is FR-09 ("audit records shall not be editable or deletable by any user").

So the FR mapping below is **reconstructed from how the code cites each FR**, not read from the PRD. It is good enough to test the behaviour that was built, but it **cannot reveal a requirement that nobody carried into the code**. Please send the PRD (or paste its FR table) and I will reconcile this report against the real wording and re-grade anything that moves.

| FR | Reconstructed meaning (from code citations) | Controller dashboard surface |
|---|---|---|
| FR-01 | Roles and access: Controller, Technician, Manager (Manager holds admin rights) | `/incidents` guarded to Controller; `/v1/controller/**` Controller-only |
| FR-02 | Incidents arrive from Freshdesk | Sync service, simulated client, queue |
| FR-03 | Incident queue: status, priority, SLA, logging directly | Queue, filters, KPIs, Log incident form |
| FR-04 | Dispatch: rank technicians, broadcast or assign, 10-minute window, first accept wins, expiry re-queues | Create dispatch, resend, technician answer endpoints |
| FR-05 / 06 / 07 | On-site job lifecycle: en route → on site → complete, parts, review & close | Ticket progress, Active jobs, Review & close |
| FR-08 | Outbound notifications | Technician dispatch email |
| FR-09 | Immutable audit trail | `admin_audit_log`, dispatch responses |

---

## 2. Headline findings

### Defects (D-1 to D-3 fixed in this PR)

| # | Severity | Finding | Where |
|---|---|---|---|
| **D-1** | Medium · *fixed* | **After logging an incident the queue shows a different ticket selected, with a "Dispatch again" button for it.** The link `?selected=<new id>` is only honoured if the incident is on page 1 of the list. A Medium-priority incident with the default 8 h SLA sorts behind urgent ones, lands on page 2, and the panel falls back to the first row (here a 690-hour-overdue ticket). A Controller who clicks the visible primary button dispatches the wrong job. The new-incident toast uses the same `?selected=` link, so it is likely affected the same way (not separately tested). | Frontend, `incident-queue.ts` preselect logic |
| **D-2** | Medium · *fixed* | **A manually logged incident cannot be found by the ticket reference shown in the table.** The search placeholder says "Search ticket, client, site…", but the query matches client name, site address and the Freshdesk ticket id only. Manual incidents display a derived `INC-xxxxxxxx` reference that is not searched. Typing `INC-21C62279` returns "No incidents in this view" while that row is on screen. | Backend, `IncidentSpecifications` |
| **D-3** | Low · *fixed* | **`%` and `_` in the search box act as wildcards** (typing `%` returns every incident). Not a security hole (injection returns nothing) but search is not literal. | Backend, `IncidentSpecifications` |

### Gaps (not built, or not enforced)

| # | Severity | Finding |
|---|---|---|
| **G-1** | **High** | **There is no way for a real technician to accept a dispatch.** The email tells them "Sign in to TechConnect to accept or decline", but a Technician who signs in lands on an *under construction* page. Accept/decline works only through the API and the Demo buttons on the progress page. FR-04's core loop is therefore not usable by the people it is for. |
| **G-2** | **High** | **Accepting a dispatch creates no job.** The technician's status stays `AVAILABLE`, the `jobs` table gets no row, and the incident sits at "In progress / Accepted" with nothing further: no en-route/on-site/complete, no parts, no review & close (FR-05/06/07). *Active jobs* and *Review & close* in the sidebar are disabled placeholders. |
| **G-3** | Medium | **One technician can hold several live jobs.** Because acceptance does not mark them busy, the same technician accepted a second dispatch while already holding one (DS-14), and they remain top of the ranking. |
| **G-4** | Medium | **Email is the only notification channel** (FR-08). For time-critical jobs a technician in the field needs SMS or push. |
| **G-5** | Medium | **Real Freshdesk is not connected.** Only the simulated client exists. The owners must still confirm domain/API key, the custom field names for site address and coordinates, the priority/status mapping, and whether TechConnect writes back. (Listed in `backend/README.md`.) |
| **G-6** | Medium | **Technician location is never updated** (no reporting from a technician app). Ranking trusts the last known point however old it is: a 3-day-old position still produced a distance and score, with no "stale" indication (DS-27). |

### Observations (works, but worth a decision)

| # | Finding |
|---|---|
| O-1 | **A Manager cannot open the Controller dashboard** (403). FR-01 says the Manager holds the admin rights, so supervising dispatch may be expected. Confirm the intent. |
| O-2 | **Dispatch email deadline is in UTC** ("Respond by: 2026-10-12 19:06 UTC"). South African technicians will read it as an hour that has already passed. Format as `Africa/Johannesburg`. |
| O-3 | **Dispatch actions are not in the audit trail.** Creator and each answer are stored, but a resend records only a count and time (not who), and none of it is in the append-only `admin_audit_log` that FR-09 describes. **Addressed:** each ticket now has an append-only history (`incident_events`, shown on the ticket progress page) recording who logged, dispatched, resent, answered and closed it. The wider `admin_audit_log` is unchanged. |
| O-4 | **Technicians who lost the race are shown as "No response"** on the progress page. That reads as a failure by the technician. "Job taken by X" would be fairer, and matters if response rates are ever reported. |
| O-5 | **Dispatch is allowed to Offline technicians and to technicians with none of the required skills** at the API level. The ranking shows both, but nothing stops or warns. (The UI path for selecting an offline technician was not exercised.) |
| O-6 | **Sidebar "User management / Reports / Integrations"** are inert text for a Controller; they look like navigation but do nothing, and User management is Manager-only. |
| O-7 | **Age and time display**: age reads `698h 2m`; "Logged" shows a time with no date; the progress page mixes `20:59` and `08:59 PM`. |
| O-8 | **Accessibility**: queue tabs have no `tab` role or `aria-selected`; table headers have no `scope`; the search box has only a placeholder, no accessible name. (Row selection *is* keyboard-operable via the ticket button with `aria-label`/`aria-pressed`.) |
| O-9 | **Mobile**: the new-incident toast overlays the page title and Freshdesk badge at 375 px; it also fires for an incident the same Controller just logged. No horizontal scrolling. |
| O-10 | **Demo mode is on by default in `docker-compose.yml`** (`TECHCONNECT_SIMULATION_ENABLED` defaults to `true`). It makes up incidents, so it must be off in any environment where real operations run. |

### What worked well

Access control (401/403 correct for every role tried); input validation (10 invalid-incident payloads all 400, no stack traces); first-accept-wins under a real simultaneous race (`[200, 409]`); two Controllers dispatching the same incident at once (one wins, one 409); the 10-minute window and the background expiry (re-queues the incident, refuses a late accept); everyone-declined ending the dispatch immediately; resend only to technicians who have not answered, with a 60-second cooldown; the post-send state on the queue, ticket and progress page ("Sent to 2 technicians — awaiting confirmation", *View dispatch status*, browser Back not returning to the create form); KPI tiles agreeing with their filters; markup in free text rendered as text; queue list in 34 ms.

---

## 3. FR traceability

| FR | Tested | Pass | Gap / defect / observation | Verdict |
|---|---|---|---|---|
| FR-01 Access | 7 | 6 | O-1 | Met (confirm Manager access) |
| FR-02 Freshdesk intake | 5 | 5 | G-5 (real client) | Met against the simulator only |
| FR-03 Incident queue and logging | 13 | 10 | D-2, D-3 (+ UI: D-1) | Mostly met |
| FR-04 Dispatch | 26 | 23 | G-3, G-6, O-5 | Back end met; **not usable by technicians (G-1)** |
| FR-05/06/07 Progress view and on-site lifecycle | 4 | 2 | G-2 | Progress view met; **on-site lifecycle not met** |
| FR-08 Notifications | 4 | 2 | G-4, O-2 | Partly met (email only) |
| FR-09 Audit | 1 | 0 | O-3 | Partly met (admin actions only) |
| Non-functional (response time) | 1 | 1 | — | Met (34 ms for 100 rows) |

---

## 4. Test cases

Result key: **PASS** as the requirement describes · **FAIL** defect · **GAP** not implemented · **OBS** works, owners should decide.

### 4.1 API cases (executed against the live backend)

| ID | FR | Test | Expected | Actual | Result |
|---|---|---|---|---|---|
| AC-01 | FR-01 | Unauthenticated request to the queue is refused | 401 | 401 | **PASS** |
| AC-02 | FR-01 | A Technician cannot read the Controller queue | 403 | 403 | **PASS** |
| AC-03 | FR-01 | A Manager (holds admin rights) can open the Controller queue | Manager has admin rights, so 200 expected | 403 - controller endpoints are CONTROLLER-only | **OBS** |
| AC-04 | FR-01 | A Controller cannot answer a dispatch as a technician | 403 | 403 | **PASS** |
| AC-05 | FR-01 | A malformed bearer token is refused | 401 | 401 | **PASS** |
| AC-06 | FR-01 | A disabled technician cannot sign in | 401/403 | 401 | **PASS** |
| AC-07 | FR-01 | Demo simulation endpoints are not reachable by technicians | 403 | 403 | **PASS** |
| IN-01 | FR-02 | Freshdesk status is reported honestly (simulated, not 'connected') | 200, mode SIMULATED while no real client exists | 200 {"mode": "SIMULATED", "simulation": true, "lastSyncAt": "2026-10-12T18:57:24.720329908Z", "lastSuccessAt": "2026-10-12T18:57:24.720401444Z", | **PASS** |
| IN-02 | FR-02 | A Freshdesk ticket becomes a NEW incident in the queue | Incident present, status NEW, ticket reference = Freshdesk id | ticket=SIM-MV5M6SJ73 found=True status=NEW ref=SIM-MV5M6SJ73 | **PASS** |
| IN-03 | FR-02 | Ticket fields map to incident details (client, site, contact, SLA, priority, geo) | all populated | missing: none | **PASS** |
| IN-04 | FR-02 | Re-syncing does not duplicate an already-imported ticket | exactly 1 incident for the ticket | 1 | **PASS** |
| IN-05 | FR-02 | An already-late ticket arrives flagged overdue | overdue=true | overdue=True slaDueAt=2026-10-12T18:27:41.992460Z | **PASS** |
| IN-06 | FR-03 | Controller can log an incident directly (201, NEW, SLA due = now + SLA hours) | 201, status NEW, ticketRef assigned, slaDueAt ~ now+6h | 201 status=NEW ref=INC-974B4065 due_ok=True | **PASS** |
| IN-07 | FR-03 | Invalid incident submissions are rejected with 400 and no stack leakage | all 10 invalid payloads -> 400 | unexpected: none; leaks: none | **PASS** |
| IN-08 | FR-03 | An incident logged without an SLA is accepted | Accepted; slaDueAt null so it can never become overdue | 201 slaDueAt=2026-10-12T22:57:42.216233365Z sla=4 hrs | **PASS** |
| IN-09 | FR-03 | Markup in free text is stored verbatim (output encoding is the UI's job) | 201, value returned unchanged | 201 <img src=x onerror=alert(1)> UAT XSS | **OBS** |
| QU-01 | FR-03 | Queue returns the standard paged envelope | content,page,size,totalElements,totalPages | 200 keys=['content', 'page', 'size', 'totalElements', 'totalPages'] | **PASS** |
| QU-02 | FR-03 | Queue is ordered by SLA due time, most urgent first | slaDueAt ascending, undated last | 31 dated of 31; ascending=True; undated_last=True | **PASS** |
| QU-03 | FR-03 | Filter by priority and by status | only matching rows | HIGH ok=True; NEW ok=True | **PASS** |
| QU-04 | FR-03 | Search finds an incident by client name, ticket reference, case-insensitively | found by all three | client=1 ref=0 lowercase=1 | **FAIL** |
| QU-05 | FR-03 | Paging parameters are bounded, never an error | size<=100, size>=1, negative page -> 0, huge page -> empty | size1000->100 size0->1 page-3->0 page99999->0 rows | **PASS** |
| QU-06 | FR-03 | KPI tiles agree with the rows behind them | overdueCount KPI == count of overdue rows | kpis={"newCount": 21, "unassignedCount": 23, "inProgressCount": 9, "overdueCount": 7} rows_total=31 overdue_rows=7 | **PASS** |
| QU-07 | FR-03 | Unknown / malformed ids and enum values return 4xx, not 500 | 404 / 400 / 400 | 404 / 400 / 400 | **PASS** |
| QU-08 | FR-03 | Search input is treated as text (injection, % and _ wildcards) | injection returns 0; '%' and '_' match only literal characters | inj=200/0 pct=200/31 us=200/31 | **FAIL** |
| DS-01 | FR-04 | Technician list excludes deactivated technicians | UAT Tech Disabled absent | ['John Smith', 'Mike Naidoo', 'Sarah Mokoena', 'Tyra Nair', 'UAT Tech Far', 'UAT Tech Near', 'UAT Tech NoLocation', 'UAT Tech Offline'] | **PASS** |
| DS-02 | FR-04 | Ranking puts the closest skilled technician first | Near scores higher than Far; distance shown | near=(100, 0.0) far=(96, 43.6) | **PASS** |
| DS-03 | FR-04 | A technician with no known location is still listed, ranked lower, with no distance | listed, distanceKm null, below Near | (35, None, 0) | **PASS** |
| DS-04 | FR-04 | Offline technicians are shown as offline (and not ranked above available ones) | listed with status OFFLINE, score <= available | ('OFFLINE', 70) | **PASS** |
| DS-05 | FR-04 | Broadcast to two technicians: dispatch PENDING, incident IN_PROGRESS, summary correct | 201; PENDING; IN_PROGRESS; invited 2, pending 2 | 201 dispatch=PENDING incident=IN_PROGRESS invited=2 pending=2 | **PASS** |
| DS-06 | FR-04 | Response window is 10 minutes | 10 | 10.000000033333333 | **PASS** |
| DS-07 | FR-04 | A second dispatch for an incident that already has a live one is refused | 409 with a plain-language reason | 409 This incident already has an active dispatch. Wait for it to be answered or expire. | **PASS** |
| NT-01 | FR-08 | Each invited technician is emailed the job | one email per invited technician | near=6 far=8 subject='New job: INC-515B2F15 · UAT Dispatch Main 6e82 (high)' | **PASS** |
| NT-02 | FR-08 | The notification carries what the technician needs (ticket, client, site, deadline) | contains ticket/client/site and response deadline | has_job_details=True; respond_by=True; has_site_contact_phone=False | **PASS** |
| NT-04 | FR-08 | The deadline in the email is expressed in the technician's local time | SAST (UTC+2) or an unambiguous local time | Shown as 'Respond by: ... UTC' | **OBS** |
| NT-03 | FR-08 | Notification channel | Email AND in-app/push/SMS so a technician in the field is actually reached | Email only; no push/SMS and no technician app to receive them | **GAP** |
| DS-08 | FR-04 | Resend is rate-limited (cooldown) | 409 with seconds to wait | 409 Technicians were notified moments ago. You can resend in 58 seconds. | **PASS** |
| DS-09 | FR-04 | Resend after the cooldown notifies the pending technicians and counts it | 200; notificationCount 2 | 200 {"notified": 2, "notificationCount": 2, "lastNotifiedAt": "2026-10-12T18:57:45.046754700Z"} | **PASS** |
| DS-10 | FR-04 | First technician to accept wins; the other becomes NO_RESPONSE | dispatch ACCEPTED; Near ACCEPTED; Far NO_RESPONSE | 200 status=ACCEPTED {'UAT Tech Near': 'ACCEPTED', 'UAT Tech Far': 'NO_RESPONSE'} | **PASS** |
| DS-11 | FR-04 | A late accept is refused (409); the winner repeating their accept is harmless (200, idempotent) | 409 / 200 | 409 / 200 | **PASS** |
| DS-12 | FR-04 | Resend after acceptance is refused | 409 | 409 A technician has already accepted this job. | **PASS** |
| DS-13 | FR-05 | Accepting a dispatch moves the technician to ON_JOB and creates the job record | technician status ON_JOB; a job row exists for the incident | technician status=['AVAILABLE']; jobs rows=['0'] | **GAP** |
| DS-14 | FR-04 | A technician who has already accepted a job can't silently accept a second at the same time | refused, or flagged to the Controller | dispatch=201; second accept=200 | **GAP** |
| DS-15 | FR-04 | When every technician declines, the dispatch ends immediately and the incident returns to NEW | mid-way PENDING; then EXPIRED; incident NEW | mid=PENDING then=EXPIRED incident=NEW | **PASS** |
| DS-16 | FR-04 | An expired incident can be dispatched again | 201 PENDING | 201 PENDING | **PASS** |
| DS-17 | FR-04 | A dispatch nobody answers lapses after the window and the incident is re-queued | EXPIRED; incident NEW; responses NO_RESPONSE | expired=True incident=NEW ['NO_RESPONSE'] | **PASS** |
| DS-18 | FR-04 | An accept after the window has closed is refused | 409 | 409 This dispatch has expired and can no longer be accepted. | **PASS** |
| DS-19 | FR-04 | Direct assign works for exactly one technician and is refused for two | two -> 400; one -> 201 | two=400 one=201 | **PASS** |
| DS-20 | FR-04 | Dispatch guards: disabled/unknown/non-technician, empty list, unknown or closed incident, oversize note | {"disabled": 400, "unknown tech": 400, "empty list": 400, "not a technician": 400, "unknown incident": 404, "… | {"disabled": 400, "unknown tech": 400, "empty list": 400, "not a technician": 400, "unknown incident": 404, "closed incident": 409, "note 4001 chars": 400} | **PASS** |
| DS-21 | FR-04 | A technician who was not invited cannot accept | 403/404 (never 200/500) | 404 Dispatch not found | **PASS** |
| DS-22 | FR-04 | Two technicians accepting at the same instant: exactly one wins | [200, 409] | [200, 409] | **PASS** |
| DS-23 | FR-04 | Dispatching to a technician who lacks the required skills | Controller is warned or it is blocked | 201 accepted without warning | **OBS** |
| QU-09 | FR-03 | KPI tiles agree with the New / In progress / Unassigned filters | newCount==NEW rows, inProgressCount==IN_PROGRESS rows, unassignedCount==unassigned rows | kpi={'newCount': 23, 'unassignedCount': 25, 'inProgressCount': 16, 'overdueCount': 7} rows new=23 inprog=16 unassigned=25 | **PASS** |
| DS-24 | FR-04 | Resend reaches only technicians who have not answered | notified == 2 (Near, NoLocation); the decliner gets nothing | 200 {"notified": 2, "notificationCount": 2, "lastNotifiedAt": "2026-10-12T18:58:02.910243380Z"}; far_mails_before=15 after=15 | **PASS** |
| DS-25 | FR-04 | Two Controllers dispatching the same incident at once produce one live dispatch | [201, 409]; exactly 1 PENDING dispatch | [201, 409] pending_rows=['1'] | **PASS** |
| DS-26 | FR-04 | Ranking for an unknown incident is a 404 | 404 | 404 | **PASS** |
| DS-27 | FR-04 | A technician's location that is days old is not treated as their live position | flagged as stale or distance withheld | distanceKm=43.6 (location 3 days old); no staleness field in response | **OBS** |
| PR-01 | FR-05 | Progress for an incident with no dispatch is a clear 404 | 404 | 404 No dispatch has been created for this incident yet | **PASS** |
| PR-02 | FR-05 | Progress carries dispatch detail, technician responses and who accepted | dispatch fields + responses + acceptedTechnicianName | acceptedTechnicianName=UAT Tech Near responses=2 | **PASS** |
| PR-03 | FR-05/06/07 | On-site progress (en route, on site, work complete, parts used, close-out) | Controller can follow the technician through the job and review & close it | Not available: no technician app, no job status updates, no review/close step | **GAP** |
| AU-01 | FR-09 | Dispatch decisions leave a durable, attributable record | who created/resent the dispatch and each technician's answer + time are recorded | dispatch_responses rows=['2']; created_by stored on dispatch; resend actor/time not stored; audit-log rows for dispatch actions=0 (['0']) | **OBS** |
| NF-01 | NFR | Queue list responds quickly | < 1000 ms for 100 rows | 36 ms | **PASS** |

### 4.2 UI cases (hands-on, Controller session)

| ID | FR | Test | Result | Evidence |
|---|---|---|---|---|
| UI-01 | FR-03 | Queue loads; KPI tiles, tab badges and row counts agree (New 25, Unassigned 27, In progress 19, Overdue 7) | **PASS** | All four tabs checked: badge = rows/"of N" |
| UI-02 | FR-03 | Search by client name | **PASS** | Also covered by QU-04 |
| UI-03 | FR-03 | Search by the ticket reference shown in the table | **FAIL** | `INC-21C62279` → "No incidents in this view" (D-2) |
| UI-04 | FR-03 | Selecting a row opens the detail panel with the right action for its state | **PASS** | Labels: Create dispatch / Dispatch again / View dispatch status |
| UI-05 | FR-04 | Create dispatch page: ranked list, distance, skills, map, selection count | **PASS** | 8 technicians listed; disabled one excluded |
| UI-06 | FR-04 | Broadcast to two technicians lands on ticket progress, replacing the form in history | **PASS** | "Sent to 2 technicians — awaiting confirmation · 2 yet to answer · Expires in 10m" |
| UI-07 | FR-04 | Browser Back from progress does not reopen the create form | **PASS** | Returned to the queue |
| UI-08 | FR-04 | Queue shows the sent state and the panel offers *View dispatch status* + *Resend* | **PASS** | Row chip "Sent to 2 · awaiting" |
| UI-09 | FR-04 | Immediate resend shows the cooldown reason; demo Accept shows "Assigned to …", the loser shows "No response", Resend disappears | **PASS** | "You can resend in 30 seconds." (O-4 for the label) |
| UI-10 | FR-03 | Log incident: required-field and phone validation, successful submit | **PASS** | "Client name is required." / "Use digits, spaces, + ( ) and - only." |
| UI-11 | FR-03 | After logging, the new incident is visible and selected | **FAIL** | Selected panel was INC-4587; new row on page 2 (D-1) |
| UI-12 | FR-03 | `<img onerror>` in a client name renders as text | **PASS** | Shown literally in the table |
| UI-13 | — | Keyboard: each row has a focusable ticket button with accessible name and pressed state | **PASS** | `aria-label="Select INC-4587, ABC Holdings"` |
| UI-14 | — | Tabs/table/search accessible semantics | **OBS** | O-8 |
| UI-15 | — | 375 px viewport: no horizontal page scroll; toast placement | **PASS / OBS** | O-9 |
| UI-16 | FR-04 | A technician who signs in can answer the dispatch | **GAP** | Route table: Technician home is an *under construction* page (G-1) |

---

## 5. Not covered

- The **PRD itself** (section 1). Anything it requires that the code never cited is untested.
- **Real Freshdesk** (does not exist yet), **SMS/push**, and the **technician app**.
- The 10-minute window was exercised by backdating the expiry in the database and letting the real 15-second sweep run, not by waiting 10 real minutes.
- Simulation **off** was not re-run live; it is covered by the automated `SimulationDisabledIntegrationTest`.
- One browser (the in-app Chromium pane), one viewport pair, no screen reader, no load or soak test, no multi-user UI concurrency (API-level concurrency was tested).
- The running containers were built about a day before testing; the commits since are dependency bumps and formatting.

## 6. Suggested order of work

1. **D-1, D-2** (small, user-visible, risk of dispatching the wrong job).
2. **G-1 / G-2 / G-3** together: the technician accept screen, creating the job and marking the technician busy on accept. This is the real remaining FR-04 → FR-07 loop.
3. **O-2** (UTC → local time in the email) and **G-4** (SMS/push) before any real technician is notified.
4. Owner decisions: **O-1** (Manager access), **G-5** (Freshdesk particulars), **O-3** (audit scope).
5. **D-3, O-4 … O-9** as polish.
