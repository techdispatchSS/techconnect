# Data retention: job audit trail

**For whoever sets up the production database's backups and retention.**

## The requirement

Every job status change must be kept for at least **2 years**, and must not be editable or deletable by any user (PRD FR-09, §9.5).

## What the application does

- Every status change, including closing a job, writes a row to `job_status_history`: who made the change, when, the previous status, the new status, and any reason.
- The table is append-only. The repository has no update or delete method, and database triggers refuse `UPDATE`, `DELETE` and `TRUNCATE` for every connected user (migration `V17`).
- `incident_events`, the ticket history, is append-only in the same way (`V15`), though its triggers don't cover `TRUNCATE`.
- **Nothing purges these tables.** There is no scheduled job or endpoint that removes old rows, so they stay in the live database indefinitely.

## What this leaves to you

The application can't protect against losing the database itself, so backups need to be set with the 2-year minimum in mind:

1. **Keep long-lived backups.** RDS automated backups can be retained for 35 days at most, so they don't meet 2 years alone. Use manual snapshots or AWS Backup with a retention of at least 2 years.
2. **Don't drop or rebuild the tables** during maintenance without first checking the rows are preserved. A superuser can drop the triggers, so restrict that access.
3. **Test a restore** and confirm `job_status_history` comes back with its triggers.

## Removing data after 2 years

Two years is a minimum, and nothing requires removing data afterwards. If the business decides to remove old rows, that is a deliberate change: it needs its own migration that lifts the triggers for the purge and puts them back, and it needs sign-off from whoever owns the FR-09 requirement.

## Who can read the trail

| Role | How |
|---|---|
| Controller | `GET /api/v1/controller/jobs/{id}/history`, and the timeline on the job page |
| Manager | `GET /api/v1/admin/jobs/{id}/history` |
| Technician | Their own jobs only, on the job screen |
