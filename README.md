# actions-testbed

A testbed for [`gradle/actions`](https://github.com/gradle/actions). It exercises
the actions against real GitHub-hosted runners using end-to-end scenarios that
seed a cache and then verify restore behaviour.

The current focus is the **configuration-cache support** being added to
`gradle/actions/setup-gradle` (the `prerelease/config-cache-support` ref). Each
scenario seeds a cache with a write-only build and then runs a read-only build
to verify how the configuration cache and the Gradle User Home cache are
restored.

## Layout

- **`no-build-logic/`** — the Gradle project under test. A small Java library on
  the Gradle 9.5.1 wrapper, applying `com.gradle.develocity` 4.4.2 with
  `org.gradle.configuration-cache=true`.
- **`.github/workflows/scenario.yml`** — a **reusable** workflow
  (`workflow_call`). Runs one scenario as a `seed-build` (cache-write-only)
  followed by a `restore-build` (cache-read-only). Its inputs are the
  per-scenario knobs: `develocity-url`, `cache-encryption-key`,
  `gradle-version`, `gradle-command`, `restore-cc-enabled`, `dv-plugin-version`,
  `working-directory`, and a required `name`.
  - `name` is set as `GRADLE_BUILD_ACTION_CACHE_KEY_JOB` on both jobs, so the
    seed and restore builds share a Gradle User Home cache key while different
    scenarios stay isolated from one another.
  - `DEVELOCITY_STORE_CONFIGURATION_CACHE` (the config-cache opt-in) is set from
    `restore-cc-enabled` on the **Setup Gradle** step.
- **`.github/workflows/no-build-logic.yml`** — the **single entry point**. Each
  scenario is a job that calls `scenario.yml` with its inputs.

## Scenarios

| Scenario | What it varies |
|---|---|
| `success` | Baseline — everything configured correctly. |
| `no-optin` | `restore-cc-enabled: false` (config-cache opt-in disabled). |
| `no-develocity-url` | Empty `develocity-url`. |
| `no-access-key` | Empty Develocity access key. |
| `invalid-access-key` | A bogus Develocity access key. |
| `old-develocity-plugin` | Older `com.gradle.develocity` plugin version (`4.3.3`). |
| `no-encryption-key` | Empty `cache-encryption-key`. |
| `gradle-8-5` | Provisions Gradle 8.5 and builds with `gradle build`. |

## Running the scenarios

The `no-build-logic` workflow runs on `push` and can also be triggered manually:

```bash
gh workflow run no-build-logic.yml -R gradle/actions-testbed --ref main
```

It requires a `DEVELOCITY_ACCESS_KEY` secret for the
`ge.solutions-team.gradle.com` Develocity server.

## Configuration-cache report

After a run completes, a consolidated report can be generated showing, per
scenario: the configuration-cache hit/miss on the restore build, the project
content status from the Job Summary, and the project entry (save/restore) status
for the `Project: no-build-logic` cache entry. See [`CLAUDE.md`](CLAUDE.md) for
the extraction script and details.

> **Note:** setup-gradle writes the caching report to `$GITHUB_STEP_SUMMARY`,
> which is not in the job logs by default and has no public API. For the
> content/entry columns to be extractable, the run must echo the summary into
> the logs.
