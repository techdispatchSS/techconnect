"""
Read-only capture of real Freshdesk API payloads, so the live client can be built and tested
against what the production account actually returns without touching it.

Safety:
  * The only HTTP call in this file is get(), which sends GET and nothing else, and only to
    paths under /api/v2/ on the allowlist below. Freshdesk GETs change no ticket, status,
    assignment or "seen" state and fire no automations.
  * Freshdesk's rate limit is per account, shared with whatever production uses. Calls are
    spaced out, the run stops as soon as fewer than RESERVE_FRACTION of the minute's budget
    is left, and a 429 ends the run (it is never retried).
  * The key is read from the environment and never written to disk.

Usage (PowerShell):
  $env:FRESHDESK_DOMAIN = "yourcompany.freshdesk.com"
  $env:FRESHDESK_API_KEY = "..."
  python scripts/freshdesk/capture.py --days 7 --pages 1
  Remove-Item Env:FRESHDESK_API_KEY

Output goes to scripts/freshdesk/captures/ (gitignored): raw/ holds the payloads as received,
redacted/ the same with names, emails, phones and free text masked, fit to use as fixtures.
"""

import argparse
import base64
import datetime as dt
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ALLOWED_PREFIXES = (
    "/api/v2/ticket_fields",
    "/api/v2/company_fields",
    "/api/v2/contact_fields",
    "/api/v2/tickets",
    "/api/v2/companies/",
    "/api/v2/contacts/",
)
RESERVE_FRACTION = 0.5  # leave at least half the per-minute budget for production
PAUSE_SECONDS = 1.5
KEPT_HEADERS = (
    "content-type", "link", "x-ratelimit-total", "x-ratelimit-remaining",
    "x-ratelimit-used-currentrequest", "retry-after", "x-request-id",
)

OUT = Path(__file__).resolve().parent / "captures"


class Stop(Exception):
    pass


class Freshdesk:
    def __init__(self, domain, key):
        self.base = f"https://{domain}"
        token = base64.b64encode(f"{key}:X".encode()).decode()
        self.headers = {"Authorization": f"Basic {token}", "Accept": "application/json"}
        self.calls = 0

    def get(self, path, query=None):
        if not path.startswith(ALLOWED_PREFIXES) or ".." in path:
            raise Stop(f"refusing non-allowlisted path {path}")
        url = self.base + path + ("?" + urllib.parse.urlencode(query) if query else "")
        if self.calls:
            time.sleep(PAUSE_SECONDS)
        self.calls += 1
        req = urllib.request.Request(url, headers=self.headers, method="GET")
        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                status, headers, raw = resp.status, resp.headers, resp.read()
        except urllib.error.HTTPError as e:
            status, headers, raw = e.code, e.headers, e.read()

        kept = {h: headers[h] for h in KEPT_HEADERS if headers.get(h) is not None}
        try:
            body = json.loads(raw) if raw else None
        except ValueError:
            body = raw.decode(errors="replace")
        record = {
            "request": {"method": "GET", "path": path, "query": query or {}},
            "response": {"status": status, "headers": kept, "body": body},
        }
        print(f"  GET {path} {query or ''} -> {status} "
              f"(remaining {kept.get('x-ratelimit-remaining', '?')}/{kept.get('x-ratelimit-total', '?')})")

        if status == 429:
            raise Stop("rate limited (429); stopping so production is not starved", record)
        if status in (401, 403):
            raise Stop(f"auth failed ({status}); check domain and key", record)
        total, left = kept.get("x-ratelimit-total"), kept.get("x-ratelimit-remaining")
        if total and left and int(left) < int(total) * RESERVE_FRACTION:
            raise Stop(f"rate budget down to {left}/{total}; stopping early", record)
        return record


# --- redaction -----------------------------------------------------------------------------

PII_KEYS = {
    "name", "email", "phone", "mobile", "twitter_id", "unique_external_id", "facebook_id",
    "address", "description", "description_text", "body", "body_text", "to_emails", "cc_emails",
    "fwd_emails", "reply_cc_emails", "bcc_emails", "ticket_cc_emails", "support_email",
    "from_email", "other_emails", "job_title", "domains", "notes",
    # Subjects carry client, site and technician names; attachment URLs carry file names.
    "subject", "attachment_url", "link",
}
EMAIL_RE = re.compile(r"[\w.+-]+@[\w-]+\.[\w.-]+")
# Not glued to letters, digits or dashes on either side, so ISO timestamps are left alone.
PHONE_RE = re.compile(r"(?<![\w-])\+?\d[\d ()-]{7,}\d(?![\w-])")
# Schema fields whose choices are people (agent names) or teams.
PEOPLE_FIELDS = {"agent", "group"}


def redact(value, key=None):
    if isinstance(value, dict):
        # Field schemas (ticket_fields etc.) describe fields called "name"/"description"; keep them.
        is_schema = "label" in value and "type" in value and "name" in value
        out = {}
        for k, v in value.items():
            if is_schema and k in ("name", "description"):
                out[k] = v
            elif is_schema and k == "choices" and value["name"] in PEOPLE_FIELDS and v:
                out[k] = f"<redacted:{len(v)} choices>"
            else:
                out[k] = redact(v, k)
        return out
    if isinstance(value, list):
        return [redact(v, key) for v in value]
    if isinstance(value, str):
        if key in PII_KEYS or (key or "").startswith("cf_"):
            return f"<redacted:{key}:{len(value)} chars>"
        if (key or "").endswith(("id", "_str")) or key == "path":
            return value
        return PHONE_RE.sub("<phone>", EMAIL_RE.sub("<email>", value))
    return value


# --- capture plan --------------------------------------------------------------------------

def save(name, record):
    for kind, data in (("raw", record), ("redacted", redact(record))):
        path = OUT / kind / f"{name}.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(data, indent=2, ensure_ascii=False), encoding="utf-8")


def run(fd, days, pages, detail):
    # Field schemas: which cf_* fields exist (site address?) and the status/priority choices.
    for name in ("ticket_fields", "company_fields", "contact_fields"):
        save(name, fd.get(f"/api/v2/{name}"))

    # Exactly what the sync will call: changed tickets, oldest first, requester embedded.
    since = (dt.datetime.now(dt.timezone.utc) - dt.timedelta(days=days)).strftime("%Y-%m-%dT%H:%M:%SZ")
    tickets = []
    for page in range(1, pages + 1):
        rec = fd.get("/api/v2/tickets", {
            "updated_since": since, "order_by": "updated_at", "order_type": "asc",
            "include": "requester,description", "per_page": 30, "page": page,
        })
        save(f"tickets_updated_since_page{page}", rec)
        body = rec["response"]["body"]
        if not isinstance(body, list):
            break
        tickets += body
        if "link" not in rec["response"]["headers"]:
            break

    # A few single tickets with company and stats, plus their conversations.
    for t in tickets[:detail]:
        save(f"ticket_{t['id']}", fd.get(f"/api/v2/tickets/{t['id']}", {"include": "requester,company,stats"}))
        save(f"ticket_{t['id']}_conversations", fd.get(f"/api/v2/tickets/{t['id']}/conversations"))

    company_id = next((t.get("company_id") for t in tickets if t.get("company_id")), None)
    if company_id:
        save(f"company_{company_id}", fd.get(f"/api/v2/companies/{company_id}"))

    # What an error looks like.
    save("ticket_not_found", fd.get("/api/v2/tickets/999999999999"))


def main():
    p = argparse.ArgumentParser(description="Read-only Freshdesk payload capture.")
    p.add_argument("--days", type=int, default=7, help="updated_since window (default 7)")
    p.add_argument("--pages", type=int, default=1, help="ticket list pages of 30 (default 1)")
    p.add_argument("--detail", type=int, default=3, help="tickets to fetch individually (default 3)")
    p.add_argument("--redact-only", action="store_true",
                   help="rebuild redacted/ from raw/ without calling Freshdesk")
    args = p.parse_args()

    if args.redact_only:
        for path in sorted((OUT / "raw").glob("*.json")):
            save(path.stem, json.loads(path.read_text(encoding="utf-8")))
        print(f"Re-redacted {OUT / 'raw'} into {OUT / 'redacted'}")
        return

    domain, key = os.environ.get("FRESHDESK_DOMAIN"), os.environ.get("FRESHDESK_API_KEY")
    if not domain or not key:
        sys.exit("Set FRESHDESK_DOMAIN (e.g. acme.freshdesk.com) and FRESHDESK_API_KEY first.")

    fd = Freshdesk(domain.removeprefix("https://").strip("/"), key)
    print(f"Capturing from {domain} (GET only) into {OUT}")
    try:
        run(fd, args.days, args.pages, args.detail)
    except Stop as stop:
        if len(stop.args) > 1:
            save(f"stopped_{fd.calls}", stop.args[1])
        print(f"Stopped: {stop.args[0]}")
    print(f"Done: {fd.calls} GET requests.")


if __name__ == "__main__":
    main()
