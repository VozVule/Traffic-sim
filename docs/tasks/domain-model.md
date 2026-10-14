# Domain entities and configuration

Status: implemented and independently reviewed (PASS). The user approved the design and gave final approval of the implementation specification on 2026-10-14: "Approved. We can start with the implementation." Depends on the completed Kotlin/JVM Maven project setup.

Implement the data model in SPEC.md and docs/phase-1-design.md. The user's latest request limits this task to the entities needed by the simulation, without implementing the simulation itself. The approved task scope and acceptance criteria are in [basic-domain-entities.md](../specs/basic-domain-entities.md). Its proposal-status header is superseded by this explicit approval record; preserve the approved specification content unchanged.

## Scope

- Car, Road, Lane, Tile, Intersection, Movement, TrafficLight, Entrance, explicit scene exits, RoadNetwork, SimulationConfig, and SimulationSnapshot, plus supporting values where they clarify the model.
- Lane-local tile identity, two opposing road lanes, connector lanes, permitted turns, per-approach weights, crossing-conflict declarations, exact decimal arrival rates, independent light settings, chosen movement per car visit, seed, and snapshot data.
- Immutable ownership of topology, configuration and snapshot collections.
- Validate structural invariants, cross-references, legal connections, light placement and turning distributions. Keep constructors and useful model methods small and concrete.
- Document construction conventions and compile the result using Maven.

## Exclusions

No Simulation class, tick advancement, arrivals generation, car movement, random turn selection, light cycling, reservation arbitration, template generation, frontend, or release-two behavior. Keep internal engine bookkeeping for the later engine task.

## Verification

Do not test plain data holders, generated accessors or trivial properties. Test meaningful validation or helper behavior added by this task, especially network/configuration validation and defensive collection ownership. Run Maven verification and retain a working console entry point.

Verified on 2026-10-14 with JDK 21: `./mvnw clean verify` passed with 100 tests, zero failures, errors, or skipped tests; `./mvnw compile exec:java` printed the existing readiness message. Independent review passed with no correctness or code-quality findings. No correction cycles were required.

## Workflow

Use `feat/basic-domain-entities`; its review baseline is `1848ed028512171f982cfa13fe0578d9f9bbfb76`. No external issue tracker is configured. The user's explicitly invoked agentic workflow now requires an approved specification, builder implementation/tests, and independent review before committing, pushing, and opening a pull request. This supersedes the earlier local-only workflow. Never merge or deploy without approval.
