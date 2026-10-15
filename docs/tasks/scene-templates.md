# Scene templates

Status: implemented and independently reviewed (PASS). The user approved the design on 2026-10-15 with "lgtm" and approved the final specification on 2026-10-15: "Yes XML is the right choice, not html. The spec looks good to me. Start the implementation."

The approved scope and acceptance criteria are in [scene-templates.md](../specs/scene-templates.md). This approval record supersedes that document's historical planning-only and awaiting-approval statements. Preserve the approved specification unchanged.

## Scope

Implement the two predefined template factories with entrance-rate, per-approach turning-weight, and random-seed overrides, fixed topology and lights, validation, ownership, automated tests, and documentation as specified. Record XML as the chosen format for future declarative scene files and custom scene creation in `release-2.md`; a parser and file format remain future work.

## Workflow

Use branch `feat/scene-templates`. The review baseline is `25ef64a5a76ff057777da9577f1aa27d88b1e7f6` on `origin/main`. The branch was aligned with that merged commit before implementation; its tracked files were identical to the planning starting point. The approved specification's SHA-256 is `9879b90b67c15797d0a6c8729b8c0c448ef0f00486802c569a795b697c21a37e`.

Follow the explicitly invoked agentic workflow: builder implementation and required automated tests, independent review, and corrections if required. Allow at most three correction cycles. After PASS and all required checks, commit with a `feat:` message, push, and open a PR against the default branch. Never merge or deploy without approval.

## Verification

Builder verification passed on 2026-10-15 under JDK 21: `./mvnw clean verify` completed 159 tests (130 existing and 29 template tests), with zero failures, errors, or skipped tests. `./mvnw compile exec:java` passed and printed the existing readiness message. `git diff --check` passed, and the approved specification's hash is unchanged.

The first sandboxed verification attempt was blocked before tests because Kotlin's daemon could not create its marker outside the workspace. The escalated retry passed. No automated test failures occurred; zero of the three permitted correction cycles have been used.

Independent review passed on 2026-10-15 with no blocking or code-quality findings. The reviewer independently ran `./mvnw clean verify` under JDK 21 with all 159 tests passing and ran the entry-point smoke check successfully. The approved specification's hash remains unchanged, and the engine, domain contracts, dependencies, and entry point are unchanged.

Implementation, required verification, and independent review are complete. There are no accepted or unresolved findings, and zero correction cycles were used. Publication follows the agentic workflow above.
