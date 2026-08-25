#!/usr/bin/env bash
# Builds sprint-report.md from issues closed in the last 14 days.
# Runs inside GitHub Actions (GH_TOKEN + GH_REPOSITORY already set there).
set -euo pipefail

REPO="${GH_REPOSITORY:-${GITHUB_REPOSITORY:-techdispatchSS/techdispatch}}"
SINCE=$(date -u -d '14 days ago' +%Y-%m-%d)
TODAY=$(date -u +%Y-%m-%d)

closed=$(gh issue list --repo "$REPO" --state closed --search "closed:>=$SINCE" \
  --json number,title,labels,closedAt,assignees --limit 200)

still_open_high_priority=$(gh issue list --repo "$REPO" --state open \
  --label "priority: critical,priority: high" \
  --json number,title,labels --limit 200)

{
  echo "# Sprint Report: $SINCE to $TODAY"
  echo
  echo "## Closed this sprint ($(echo "$closed" | jq 'length'))"
  echo
  echo "$closed" | jq -r '.[] | "- #\(.number) \(.title) — " + ([.labels[].name] | join(", "))'
  echo
  echo "## Still open — critical/high priority ($(echo "$still_open_high_priority" | jq 'length'))"
  echo
  echo "$still_open_high_priority" | jq -r '.[] | "- #\(.number) \(.title) — " + ([.labels[].name] | join(", "))'
  echo
  echo "## By type"
  echo
  echo "$closed" | jq -r '[.[] | .labels[].name | select(startswith("type:"))] | group_by(.) | map("- \(.[0]): \(length)") | .[]'
  echo
  echo "_Generated automatically. Add sprint goals/retro notes to this issue by hand before closing it out._"
} > sprint-report.md

echo "Wrote sprint-report.md:"
cat sprint-report.md
