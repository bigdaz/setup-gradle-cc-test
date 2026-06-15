# CLAUDE.md

Test-bed for the configuration-cache support being added to
`gradle/actions/setup-gradle` (the `prerelease/config-cache-support` ref).
Each test is a GitHub Actions scenario that seeds a cache and then verifies
restore behaviour.

## Layout

- `no-build-logic/` — the Gradle project under test (Java lib, Gradle 9.5.1
  wrapper, `com.gradle.develocity` 4.4.2, `org.gradle.configuration-cache=true`).
- `.github/workflows/scenario.yml` — **reusable** workflow (`workflow_call`).
  Runs one scenario as a `seed-build` (cache-write-only) followed by a
  `restore-build` (cache-read-only). Inputs are the per-scenario knobs
  (`develocity-url`, `cache-encryption-key`, `gradle-version`, `gradle-command`,
  `restore-cc-enabled`, `dv-plugin-version`, …) plus a required `name`.
  - `name` is set as `GRADLE_BUILD_ACTION_CACHE_KEY_JOB` on both jobs, so seed
    and restore share a Gradle User Home cache key while scenarios stay isolated
    from each other.
  - `DEVELOCITY_STORE_CONFIGURATION_CACHE` (the config-cache opt-in) is set from
    `restore-cc-enabled` on the **Setup Gradle** step.
- `.github/workflows/no-build-logic.yml` — **single entry point**. Each scenario
  is a job that calls `scenario.yml` with its inputs.

### Scenarios

`success`, `no-optin`, `no-develocity-url`, `no-access-key`,
`invalid-access-key`, `old-develocity-plugin`, `no-encryption-key`, `gradle-8-5`.

## Generating the configuration-cache report

The report shows, per scenario:
1. **Configuration cache hit/miss** on the restore build.
2. **Project content status** line from the Job Summary, for the seed and
   restore jobs.
3. **Project entry status** (save/restore) of the `Project: no-build-logic`
   cache entry, for the seed and restore jobs.

### Prerequisites

- A completed run of the `no-build-logic` workflow.
- The **Job Summary content must be present in the job logs** for that run.
  setup-gradle writes the caching report to `$GITHUB_STEP_SUMMARY`, which is
  *not* in the logs by default and has no public API — the run must echo the
  summary into the logs for the content/entry columns to be extractable.

### Trigger a run (optional)

```bash
R=bigdaz/setup-gradle-cc-test
gh workflow run no-build-logic.yml -R "$R" --ref main   # needs the workflow on the default branch
```

### Extract the data

```bash
R=bigdaz/setup-gradle-cc-test
# latest run, or set RUN_ID explicitly:
RUN_ID=$(gh run list -R "$R" --workflow no-build-logic.yml -L1 --json databaseId --jq '.[0].databaseId')

scenarios=(success no-optin no-develocity-url no-access-key invalid-access-key \
           old-develocity-plugin no-encryption-key gradle-8-5)

# scenario job -> job id
gh run view "$RUN_ID" -R "$R" --json jobs \
  --jq '.jobs[] | select(.name | test("/ (seed|restore)-build$")) | "\(.name)\t\(.databaseId)"' \
  > /tmp/jobs.txt

strip() { sed -E 's/^.*[0-9T:.Z-]{20,} //'; }                       # drop "job / step  <ts> " prefix

cc_verdict() {  # 1=restore job id  -> HIT|MISS
  gh run view --job "$1" -R "$R" --log 2>/dev/null \
    | grep -qiE 'reusing configuration cache|configuration cache entry reused' && echo HIT || echo MISS; }

content() {     # 1=job id  -> the project-state summary line (blank if absent)
  gh run view --job "$1" -R "$R" --log 2>/dev/null | strip \
    | grep -iE 'project state \(build-logic and configuration cache\)' | sort -u | head -1; }

entry() {       # 1=job id  2=seed|restore  -> the save/restore "(Entry …)" status (blank if no entry)
  gh run view --job "$1" -R "$R" --log 2>/dev/null | strip \
  | awk -v mode="$2" '
      /Entry: Project: no-build-logic/ {inblk=1}
      inblk && /Saved +Key/            {saved=1}
      inblk && /\(Entry / {
        if (mode=="restore" && !saved && rr=="") {gsub(/^[ \t]+/,""); rr=$0}
        if (mode=="seed"    &&  saved && sr=="") {gsub(/^[ \t]+/,""); sr=$0}
      }
      inblk && /^[^ ]/ && !/Entry: Project/ {inblk=0}
      END { print (mode=="restore" ? rr : sr) }'; }

for s in "${scenarios[@]}"; do
  sid=$(grep -P "^\Q$s\E / seed-build\t"    /tmp/jobs.txt | cut -f2)
  rid=$(grep -P "^\Q$s\E / restore-build\t" /tmp/jobs.txt | cut -f2)
  printf '## %s\n'                "$s"
  printf '  CC restore      : %s\n' "$(cc_verdict "$rid")"
  printf '  content seed    : %s\n' "$(content "$sid")"
  printf '  content restore : %s\n' "$(content "$rid")"
  printf '  entry seed      : %s\n' "$(entry   "$sid" seed)"
  printf '  entry restore   : %s\n' "$(entry   "$rid" restore)"
done
```

### Present it

Render a single table, one row per scenario, columns:
`Scenario | CC hit (restore) | Content seed | Content restore | Entry seed | Entry restore`.
A blank `content` means no project-state line in that summary; a blank `entry`
means the `Project: no-build-logic` entry was not listed for that job.

## How to request this report

Ask, e.g.:

> "Generate the config-cache scenario report."

Optionally scope it:

> "Generate the config-cache scenario report for the latest run."
> "Generate the config-cache scenario report for run <RUN_ID>."

Claude will (trigger and) wait for the run to complete, run the extraction above,
and produce the consolidated table.
