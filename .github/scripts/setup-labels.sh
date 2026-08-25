#!/usr/bin/env bash
# Apply the label set in .github/labels.yml to the repo.
# Run this from your own machine with `gh auth login` already done there
# (this sandbox's GitHub proxy blocks writes to repos outside its allowlist,
# so it must be run locally, not from the Claude cloud session).
#
# Requires: gh CLI, yq (https://github.com/mikefarah/yq)
#   brew install gh yq   (or apt/choco equivalent)
#
# Usage: ./setup-labels.sh [owner/repo]
set -euo pipefail

REPO="${1:-techdispatchSS/techdispatch}"
LABELS_FILE="$(dirname "$0")/../labels.yml"

echo "Applying labels from $LABELS_FILE to $REPO ..."

count=$(yq '. | length' "$LABELS_FILE")
for i in $(seq 0 $((count - 1))); do
  name=$(yq -r ".[$i].name" "$LABELS_FILE")
  color=$(yq -r ".[$i].color" "$LABELS_FILE")
  desc=$(yq -r ".[$i].description // \"\"" "$LABELS_FILE")

  if gh label list --repo "$REPO" --json name -q '.[].name' | grep -qxF "$name"; then
    echo "Updating: $name"
    gh label edit "$name" --repo "$REPO" --color "$color" --description "$desc" >/dev/null
  else
    echo "Creating: $name"
    gh label create "$name" --repo "$REPO" --color "$color" --description "$desc" >/dev/null
  fi
done

echo "Done. $count labels applied to $REPO."
