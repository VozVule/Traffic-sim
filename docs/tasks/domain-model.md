# Domain entities and configuration

Status: in progress. Depends on the completed Kotlin/JVM Maven project setup.

Implement the data model in SPEC.md and docs/phase-1-design.md. The user's latest request limits this task to the entities needed by the simulation, without implementing the simulation itself.

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

## Workflow

Use a local integration branch. The review baseline is f07d57de45803f561fb116cc983363b33864e9dc, the project state before this task. No external issue tracker is configured, and no PR or push is part of this task.
