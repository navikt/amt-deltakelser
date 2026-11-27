# Copilot Instructions for amt-deltakelser

<!-- This file captures repository-specific context.
     Nav-wide language and framework conventions are provided by installed Copilot instructions. -->

## Repository Overview

<!-- TODO: Describe what this application does, who uses it, and key architecture decisions -->

## Tech Stack

<!-- TODO: List technologies used -->

## Validation before pull requests

Follow the build and validation instructions in `AGENTS.md`. Run Gradle `check`
on the final changes before creating or updating a PR, including test-name-only
changes. `test` does not run the ktlint checks required by CI. Resolve missing
Java 25 or Docker prerequisites instead of skipping validation.

## Read-only code reviews

Do not run builds, tests or linters during read-only PR reviews unless the user
explicitly requests them. Review the diff and relevant code, and use existing
GitHub Actions results when available. If CI results are missing or pending,
state that limitation without starting local validation or waiting for CI.
The validation requirements above apply when making changes, not read-only reviews.

## Key Patterns

<!-- TODO: Document project-specific patterns, e.g.:
     - Authentication flow
     - Data access patterns
     - API conventions -->

## Minimal Editing

When fixing a bug or implementing a feature, change only what is necessary.
Do not rename variables, restructure working code, or refactor beyond the task at hand.
Keep diffs small and focused so they are easy to review.
