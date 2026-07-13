# AGENTS.md — amt-deltakelser

<!-- TODO: Describe what this application does -->

## Build & Test Commands

```bash
# TODO: Add build and test commands
```

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
