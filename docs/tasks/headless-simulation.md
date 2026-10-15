# Headless simulation engine

Status: implemented and independently reviewed (PASS). The user approved the design on 2026-10-15 with "lgtm" and gave final approval of the implementation specification on 2026-10-15: "Yes it looks good. We can start building. Let the planner and the reviewer have extra high effort while the builder works on high."

The approved implementation scope, technical decisions, acceptance criteria, and required tests are in [headless-simulation.md](../specs/headless-simulation.md). This explicit final approval record supersedes that specification's historical awaiting-approval header. Preserve the approved specification content unchanged; implementation is authorized.

## Scope

Implement the complete headless Kotlin simulation through `Simulation.step()` and `Simulation.snapshot()` over the existing immutable domain model, including exact demand and buffers, simultaneous movement, independent lights, deterministic per-visit turns, crossing and receiving-space reservations, rotating admission priority, connected scenes, congestion, immutable snapshots, automated tests, and the README example required by the specification.

The approved specification remains the authoritative scope reference. Public scene templates, configuration input, frontend/analytics/export work, persistence, global gridlock recovery, and release-two behavior remain deferred as specified.

## Verification

Verified on 2026-10-15 with JDK 21: `./mvnw clean verify` passed with 130 tests (100 domain and 30 simulation), zero failures, errors, or skipped tests. `./mvnw compile exec:java` passed and printed the existing readiness message. The approved specification's hash is unchanged.

The initial sandboxed `./mvnw test` attempt was blocked before compilation or test execution because the Kotlin daemon needed to create a marker outside the workspace. The escalated command succeeded. No automated test failures occurred, and zero of the three permitted correction cycles have been consumed.

Independent review passed on 2026-10-15 with no blocking or code-quality findings. The reviewer independently ran `./mvnw clean verify` under JDK 21 with all 130 tests passing and ran the entry-point smoke check successfully.

A temporary public-API probe also passed 30,000 ticks across 60 seeded scenes, checking reproducibility, exact demand conservation, one-transition speed, signal gates, occupancy, receiving-space ownership, and reservation clearance. It included differing decimal scales and a tiny positive route weight. The probe harness was removed after verification.

No implementation or specification changes were made during review. There are no accepted findings, and zero of the three permitted correction cycles were consumed.

## Workflow

Use branch `feat/headless-simulation`. The review baseline is `e09755ff6007c1ed5c0403c69a857859bd0c4e53` on `origin/main`.

The explicitly invoked agentic workflow requires builder implementation and automated tests, followed by independent review and any required corrections. The user requested extra-high effort for the planner and reviewer and high effort for the builder. After a passing review and all required checks, create a `feat:` commit, push, and open a pull request. Return material requirement changes for user approval. Never merge or deploy without approval.

Implementation, required checks, and independent review are complete. The change is ready for commit, push, and pull-request creation; those publication steps are pending.
