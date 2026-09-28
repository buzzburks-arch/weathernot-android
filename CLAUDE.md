# CLAUDE.md

## Git workflow

When working on an issue:

1. Create a branch from `dev` named `feat-<issue#>` (e.g. `feat-42`).
2. Move the issue to **In Progress** as soon as work on it starts:
   `gh issue edit <issue#> --add-label "In Progress" --remove-label "Backlog"`
3. When finished, commit, push the branch, and open a pull request against `dev`.
4. Once the work is committed, move the issue to **in review**:
   `gh issue edit <issue#> --add-label "in review" --remove-label "In Progress"`

## Local deployment

Whenever a change is made, deploy it to the local environment so it can be
tested by hand. Deploy once the change is complete and its checks pass, not after
every file edit, then say where to test it.

For this app, the local environment is a running Android emulator. Build,
install, and launch the app with:

```bash
./scripts/build-and-run.sh   # first attached emulator/device
```

The app talks to the weathernot API on the host's `localhost:8000`
(`http://10.0.2.2:8000` from the emulator) -- the rancher-desktop k8s
backend (`k8s/scripts/deploy-backend.sh` in the weathernot repo) or
`scripts/dev-api.sh`. If the change also touches weathernot, deploy that
side first (see weathernot's CLAUDE.md).
