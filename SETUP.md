# TechConnect project management setup

Files in this bundle, and what to do with each.

## 1. Commit the files as-is
Copy the `.github/` folder into the root of `techdispatchSS/techdispatch` and push from your own machine (normal `git add .github && git commit && git push` — uses your own local git credentials, not this sandbox):

- `.github/ISSUE_TEMPLATE/user_story.yml`, `bug.yml`, `task.yml`, `config.yml` — the three ticket types (Story/Bug/Task) show up as structured forms when someone clicks "New issue."
- `.github/PULL_REQUEST_TEMPLATE.md` — auto-fills on every new PR.
- `.github/labels.yml` — the label set (type, phase, status, priority, area) referenced by the templates above.
- `.github/scripts/setup-labels.sh` — one-time script to create/update those labels on the repo.
- `.github/workflows/sprint-report.yml` + `.github/scripts/generate-sprint-report.sh` — the biweekly report automation.

## 2. Apply the labels (one-time, run locally)
```
brew install gh yq        # or your platform's equivalent
gh auth login              # your own GitHub account, on your own machine
./.github/scripts/setup-labels.sh techdispatchSS/techdispatch
```

## 3. Set your sprint start date
Open `.github/workflows/sprint-report.yml` and set `SPRINT_START_DATE` to the Monday your first sprint starts. The report then fires automatically every 14 days from that date, and you can also trigger it any time from the Actions tab ("Run workflow") if you want an ad-hoc report.

## 4. Create the Project board (manual — 5 minutes)
This part genuinely can't be scripted from outside GitHub's UI/GraphQL, so do it once by hand:

1. Go to `https://github.com/orgs/techdispatchSS/projects` (or the repo's Projects tab) → **New project** → **Board** template.
2. Add a custom field: **+ New field** → name it `Sprint` → type **Iteration** → duration **2 weeks** → set the start date to match `SPRINT_START_DATE` above.
3. Add a `Status` field if not already present (To do / In progress / In review / Done) — matches the `status:` labels above so you can filter either way.
4. Repo settings → **Workflows** in the Project's `...` menu → enable "Item added to project" and "Item closed" automations so new issues/PRs land on the board automatically and closed ones move to Done.
5. Link the repo to the project: Project → `...` → **Manage access** / **Link a repository** → select `techdispatchSS/techdispatch`.

## 5. Claude ticket creation (the "nice to have")
This cloud session's GitHub access is blocked by a sandbox proxy regardless of credentials — that's specific to this environment, not GitHub or your token. Two ways to still get Claude creating tickets for you:

- Run **Claude Code locally** (the CLI, on your own machine) inside the repo — it uses your own local `gh`/git credentials directly, with no proxy in the way, so `gh issue create` from Claude there works normally.
- Or connect **Linear** as a chat connector here (it showed up as available in the connector registry) if you'd rather have Claude create/manage tickets directly from this chat without the local-machine step.
