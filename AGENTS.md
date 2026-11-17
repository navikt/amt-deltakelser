# AGENTS.md — amt-deltakelser

<!-- TODO: Describe what this application does -->

## Build & Test Commands

Use JDK 25 and a running Docker daemon for Testcontainers. Copilot cloud sessions
prepare these and run baseline checks through `.github/workflows/copilot-setup-steps.yml`.
If Java 25 is missing in another environment, install it and set `JAVA_HOME` and
`PATH` before running Gradle. Do not change the project's Java target to bypass this.

```bash
# All Gradle modules: the same checks as CI, including tests and ktlint
./gradlew check --no-daemon --no-configuration-cache

# One changed application, for example amt-distribusjon
./gradlew check -p amt-distribusjon --no-daemon --no-configuration-cache

# Fix Kotlin formatting, then rerun check
./gradlew ktlintFormat -p amt-distribusjon --no-daemon --no-configuration-cache
```

Before creating or updating a PR, run `check` on the final changes for every
affected module. Run the full command for changes to shared modules (`amt-felles`,
`amt-lib`), `buildSrc`, or Gradle configuration. `test` alone is not sufficient:
it does not run ktlint, which also checks test source formatting. Test-name-only
changes still require `check`.

Setup checks run before edits and do not replace validation of the final diff.
Fix failures and rerun the checks before handing off. Do not skip tasks with `-x`
or weaken checks to get a passing result. If validation remains blocked, report
the exact command and blocker in the PR description and final response, and do
not claim validation passed.

## Project Structure

```text
amt-deltaker-bff/
amt-deltaker/
amt-distribusjon/
amt-felles/
amt-iac/
amt-lib/
amt-pdfgen/
amt-tiltaksarrangor-bff/
buildSrc/
docs/
gradle/
```

## Code Style

### Kotlin test names

Write Norwegian test names in backticks with correct Norwegian spelling, including
æ, ø and å. Keep code identifiers, endpoint names and English terms unchanged.
In API test names, use `@DisplayName` for the HTTP method and slash-prefixed path,
preserving the route spelling, for example `POST /pamelding`. Kotlin function
names cannot contain `/`.

### Minimal Editing

When fixing a bug or implementing a feature, change only what is necessary.
Do not rename variables, restructure working code, or refactor beyond the task at hand.
Keep diffs small and focused so they are easy to review.

## Git Workflow

<!-- TODO: Document your branching and merge strategy -->

## Boundaries

### ✅ Always

- Run tests after changes
- Follow existing code patterns in the project
- Preserve existing code structure — do not reorganize or refactor beyond the task
- Validate all external input

### ⚠️ Ask First

- Changing authentication mechanisms
- Adding new dependencies
- Modifying database schema

### 🚫 Never

- Commit secrets or credentials
- Skip input validation on external boundaries
