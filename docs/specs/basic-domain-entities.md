# Basic domain entities and configuration

Status: proposed specification, awaiting final user approval. The design was approved on 2026-10-14. Application implementation must wait for separate approval of this document.

## Objective and scope

Implement the immutable Kotlin domain objects needed by the accepted first-release traffic simulator. This increment makes valid scenes and observable state representable and rejects structural configuration mistakes. It does not run a simulation.

The accepted domain rules and rationale remain in [SPEC.md](../../SPEC.md), [the phase-one design](../phase-1-design.md), [the glossary](../../GLOSSARY.md), and [the ADRs](../adr/). This document is the authoritative implementation scope for this increment; the broader specification's runnable R1 increments remain future work.

Include `Car`, `Road`, `Lane`, `Tile`, `Intersection`, `Movement`, `TrafficLight`, `Entrance`, `Exit`, `RoadNetwork`, `SimulationConfig`, and `SimulationSnapshot`, plus the `Turn` and `LightColor` enums. Include small useful lookup and validation methods, immutable collection ownership, construction documentation, and meaningful automated tests.

Exclude `Simulation`, tick advancement, car movement, arrival generation or accumulation, random turn selection, light cycling or timer state, reservation ownership or arbitration, template generation, a simulation runner, frontend, analytics, and release-two features. Retain the existing console entry point.

## Functional requirements

1. Each tile identifies a zero-based position within one lane. Equal indices in different lanes identify different positions.
2. Each lane has a stable identity and a positive number of positions, ordered in its travel direction. Road and connector lanes use the same lane type.
3. Each road contains exactly two distinct opposing lanes, one per direction. Forward and backward are declarations relative to that road, rather than compass directions or coordinates.
4. Each permitted intersection movement connects the end of an incoming road lane to the start of an outgoing road lane through one connector lane. Its turn is straight, left, or right; U-turn connections are invalid.
5. Each intersection declares physical crossing resources for its movements and a turning distribution for every incoming approach. The configuration contains these declarations; this increment does not acquire resources or select turns.
6. Each light records its road-lane tile, separately configurable positive red and green durations in ticks, and initial color, defaulting to red. Its lane identifies the controlled direction.
7. Each entrance records the road lane entered at index zero and an exact nonnegative decimal rate in cars per tick. Rates may be zero, fractional, or greater than one. Each exit identifies a road lane from which departure occurs after its final tile.
8. A network owns its roads, connector lanes, intersections, entrances, and exits, and validates their references and connections. It can represent boundary roads, roads connecting intersections, and loops.
9. Configuration combines a valid network, placed lights, and a random seed. It contains no running clock, random generator, arrivals buffer, or mutable light state.
10. A car records identity, current tile, and its optional selected movement for the current intersection visit. A snapshot records tick, cars, light colors, and pending entrance counts without owning mutable engine state.
11. Caller mutations and collection mutation attempts must not alter previously constructed domain objects, configuration, or snapshots.

## Technical design

### Package and implementation shape

Place the domain types in `trafficsimulator.domain`, under `src/main/kotlin/trafficsimulator/domain/`. Keep the existing Maven/Kotlin/JVM layout and Java 21 target. Prefer one main concept per file; small directly related enums may share a file with their owning concept.

Use concrete classes and value objects with `val` properties. Scalar-only values may be data classes. Collection-bearing classes must own their inputs; do not expose constructor collections or a generated copy path that bypasses ownership. Do not introduce inheritance, repositories, an event bus, a configurable policy framework, serialization, or a geometry engine.

### Common conventions

- Use nonblank, case-sensitive `String` identifiers. Store them verbatim rather than silently trimming or rewriting them. References use the target object's identifier.
- Identifiers must be unique within each entity kind. Lane identifiers are unique across all road and connector lanes; movement identifiers are unique across all intersections, so a car's chosen movement is unambiguous. Different entity kinds may reuse an identifier.
- Use `Int` for lane lengths, tile indices, and light durations; use `Long` for snapshot ticks, pending counts, and the random seed.
- Use `BigDecimal` for entrance rates and turning weights. Numeric comparisons must be scale-insensitive: `1`, `1.0`, and `1.00` are equivalent for validation. Construct documented example values from decimal text. No `Double` arrival accumulator or percentage parser belongs in this increment.
- Public constructors and checked lookup methods reject invalid arguments with `IllegalArgumentException`. Error messages identify the relevant field or offending identifier. An aggregated error framework or custom exception hierarchy is unnecessary.

### Domain contracts

The following properties define the public data contract. Constructor parameter order and private indexing details are implementation choices.

| Type | Properties and contract |
| --- | --- |
| `Tile` | `laneId: String`, `index: Int`. The identifier is nonblank and the index is nonnegative. Value equality includes both properties. The upper bound requires lane context. |
| `Lane` | `id: String`, `length: Int`. Positive length. `tile(index: Int): Tile` rejects indices outside `0 until length` and returns a tile for this lane. Do not allocate and retain a tile object for every position merely to store the length. |
| `Road` | `id: String`, `forwardLane: Lane`, `backwardLane: Lane`. Nonblank ID and distinct lane IDs. The pair declares opposing directions; no equal-length constraint or world coordinates are required. |
| `Car` | `id: String`, `position: Tile`, `chosenMovementId: String? = null`. The car ID and a present movement ID are nonblank. This is an immutable state record; changing position or choice belongs to the future engine. |
| `Turn` | Exactly `STRAIGHT`, `LEFT`, and `RIGHT`. No U-turn value. |
| `Movement` | `id: String`, `incomingLaneId: String`, `outgoingLaneId: String`, `connectorLaneId: String`, `turn: Turn`, `crossingResourceIds: Set<String>`. IDs and resource names are nonblank. Require at least one crossing resource for each movement. Cross-references are validated by its owning network. |
| `Intersection` | `id: String`, `movements: List<Movement>`, `turningWeights: Map<String, Map<String, BigDecimal>>`. The outer weight key is the incoming lane ID and the inner key is the movement ID. Require at least one movement. Own both map levels and the movement list. Validate distributions against the intersection's declared movements. |
| `LightColor` | Exactly `RED` and `GREEN`. |
| `TrafficLight` | `id: String`, `position: Tile`, `redDurationTicks: Int`, `greenDurationTicks: Int`, `initialColor: LightColor = RED`. Nonblank ID and positive durations. No elapsed timer or current running color property. |
| `Entrance` | `id: String`, `laneId: String`, `arrivalRate: BigDecimal`. Nonblank IDs and nonnegative rate. No buffer or accumulator property. |
| `Exit` | `id: String`, `laneId: String`. Nonblank IDs. The lane's final position is derived from its length rather than duplicated in the exit. |
| `RoadNetwork` | `roads: List<Road>`, `connectorLanes: List<Lane>`, `intersections: List<Intersection>`, `entrances: List<Entrance>`, `exits: List<Exit>`. Own every collection and validate the graph on construction. |
| `SimulationConfig` | `network: RoadNetwork`, `trafficLights: List<TrafficLight>`, `randomSeed: Long`. Own the light list, reject invalid placement and duplicates. Every seed value is valid. Rates and turning weights already live in the network's immutable definitions; do not duplicate or override them here. |
| `SimulationSnapshot` | `tick: Long`, `cars: List<Car>`, `lightColors: Map<String, LightColor>`, `pendingEntranceCounts: Map<String, Long>`. Map keys identify lights and entrances respectively. Own all collections. Apply the local state checks below. |

`RoadNetwork` provides checked `lane(laneId: String): Lane`, `tile(laneId: String, index: Int): Tile`, and `movement(movementId: String): Movement` lookups. A missing identifier throws `IllegalArgumentException`; tile lookup additionally checks bounds through the owning lane. Other private indexes or small public lookups are permitted when directly useful to the stated model, but are not a reason to expose engine behavior.

### Topology and connection validation

Network construction performs the following checks. A movement references road lanes for its incoming and outgoing endpoints and a connector lane for its path; these roles follow from network ownership, so `Lane` needs no redundant kind property.

1. Reject duplicate road, intersection, entrance, or exit IDs. Reject duplicate lane IDs across every road's two lanes and the connector lane list. Reject duplicate movement IDs across all intersections.
2. Every referenced lane exists. Entrance and exit lanes belong to roads. Movement incoming and outgoing lanes belong to roads; its connector belongs to the connector lane list.
3. Each connector lane is owned by exactly one movement. Reject unused connectors and connector sharing between movements.
4. A movement's incoming and outgoing lanes belong to different roads. Moving onto the opposite lane of the same road is a U-turn and is rejected. Reject duplicate incoming/outgoing lane pairs within one intersection, which would make a legal route ambiguous.
5. Determine a road lane's starting intersection from movements that name it as outgoing, and its ending intersection from movements that name it as incoming. Several movements at the same intersection may share that endpoint. A given start or end cannot belong to multiple intersections.
6. Each road lane has exactly one starting endpoint: an entrance or an intersection. Each has exactly one ending endpoint: an exit or an intersection. Reject missing endpoints, multiple entrances/exits for one lane, and an entrance/exit competing with an intersection at the same endpoint.
7. Road directions are reciprocal at an intersection. If a road's forward lane ends at intersection `I`, its backward lane starts at `I`; if its forward lane starts at `I`, its backward lane ends at `I`, and vice versa. This enforces the declared opposing directions without coordinates. Scene-boundary endpoint pairs follow from the same endpoint completeness rules.
8. Do not require the whole graph to be connected or require every component to have an entrance and exit. Complete closed loops and disconnected valid components are allowed. An empty network is also a valid empty scene.

There is no requirement that every intersection have four approaches: the two public four-way templates are future factories. The model accepts smaller valid fixtures and permits only the connections actually declared by a caller.

### Turning distributions and crossing resources

For each intersection, the outer turning-weight keys must match exactly the set of incoming lanes used by its movements. For each incoming lane, the inner keys must match exactly the permitted movement IDs from that lane, including any permitted movement assigned zero weight. Reject missing, foreign, or extra keys, negative weights, a total other than exactly one, and distributions without any positive choice.

The public representation uses normalized fractions, such as `0.25`, `0.50`, and `0.25`. Percentages are equivalent domain notation; a future input layer can convert them to this representation. Do not accept a weight total of `100` in this API or add input conversion functionality.

Crossing resource names are scoped to the owning intersection. Movements that physically conflict declare at least one resource in common. Disjoint sets declare compatibility. A movement that has no conflicts with other paths still declares its own resource, so its path is representable for the accepted conservative reservation rule later. Resource declarations contain no reservation owners or timers.

Two movements at one intersection that merge into the same outgoing lane must share a resource; reject a declaration that omits that known conflict. Other physical conflicts and correct straight/left/right labels are supplied by the scene author. Without geometry, this increment cannot infer whether those declarations accurately describe a physical arrangement. Future templates must declare opposing straight paths as compatible and verify their behavior through the later engine.

### Light configuration validation

`SimulationConfig` rejects duplicate light IDs and multiple lights on the same tile. Each light's lane must exist, belong to a road, and contain its index. A light at index zero, at the last index, or at any intervening road position is valid. Equal indices in opposite lanes are distinct placements and may have separate lights.

An empty light list is valid. Configuration validation does not run phases or enforce traffic flow.

### Snapshot validation and ownership

Snapshot construction rejects a negative tick, duplicate car IDs, two cars at the same tile, blank light/entrance map keys, and negative pending counts. Zero counts and empty collections are valid. Car and tile constructors already enforce their local scalar constraints.

Snapshots are independent records and do not contain a network or configuration reference. They therefore do not resolve lane bounds, chosen movement IDs, light IDs, entrance IDs, or complete status-map membership against a network. The later engine must produce contextually valid snapshots. Do not add a simulation validator, live-state mutation API, visit counter, reservation record, or special engine initialization behavior to satisfy this increment.

Copy collection inputs before exposing them, including both levels of the turning-weight map. Exposed lists, maps, and sets must reject mutation through JVM mutable interfaces, in addition to being Kotlin read-only types. Unmodifiable defensive copies are appropriate. Preserve input iteration order; do not use a copy operation that arbitrarily reorders collections. The later engine will define its deterministic draw and contention order.

All contained domain objects are themselves immutable, so retained nested object references cannot mutate a network or snapshot. The model need not implement deep cloning of immutable scalar values, global structural equality for collection-bearing classes, or persistence.

## Architecture decisions and trade-offs

- Lane-local tiles preserve distinct occupancy in opposing directions without introducing a global grid.
- String identifiers keep this prototype's interface concrete and small. Nonblank checks and unique ownership catch structural mistakes; no family of identifier wrappers is required.
- `BigDecimal` retains configured decimal values and avoids imposing binary floating-point tolerances on rates or normalized turning totals. There is no arrivals algorithm in this increment.
- Road/connector ownership, directed endpoint declarations, and crossing resource names represent topology without a geometry engine. They validate graph consistency but cannot prove a physical layout's accuracy.
- Immutable cars and snapshots let the future engine own mutable bookkeeping and publish safe records. Defensive collection copies add construction cost and provide predictable ownership.
- Entrance rates and approach weights stay with their accepted domain definitions, avoiding contradictory configuration copies. Changing them during a running simulation remains release-two work.
- Constructor validation fails before an invalid object can be used. Keep validation split into focused checks; a generic validation framework is unnecessary.

## Implementation tasks

1. Add scalar domain values and enums with local invariant checks and bounded lane tile lookup.
2. Add movements and intersections, including owned resource sets, nested turning distributions, and their local validation.
3. Add network construction, indexes, ownership checks, directed endpoint validation, and checked lookups.
4. Add configuration and immutable snapshots with the validation specified above.
5. Add Kotlin/JUnit Jupiter testing support and configure Maven test discovery. Keep existing Kotlin/JVM versions and Java 21 compilation unchanged; pin any added build dependencies/plugins.
6. Add meaningful tests using private test fixtures. Do not introduce public template factories or production test hooks.
7. Update construction documentation with a small manually built two-way road example, decimal-value conventions, zero-based positions, and the Maven verification command. State that simulation advancement remains future work.
8. Run `./mvnw clean verify` using JDK 21, and `./mvnw compile exec:java` to check the existing entry point. Record actual outcomes and resolve required failures before review.

## Acceptance criteria

| Criterion | Independently verifiable outcome |
| --- | --- |
| Complete entity scope | All listed public types compile and expose the specified immutable data contract. No excluded engine behavior has been added. |
| Lane-local bounds | Network tile lookup accepts first and last valid indices, rejects unknown lanes and out-of-range indices, and keeps matching indices in opposite lanes distinct. |
| Valid two-way roads | A manually constructed boundary road with one lane per direction, two entrances, and two exits constructs successfully. |
| Valid connected topology | Valid intersection fixtures with connector paths and a road connecting intersections construct successfully. A complete loop is accepted without forced boundary endpoints. |
| Invalid topology | Duplicate identities, unknown references, incorrect lane roles, unowned/shared connectors, duplicate routes, U-turns, missing/conflicting endpoints, and inconsistent opposing directions fail construction with useful errors. |
| Exact distributions | Scaled decimals that total one are valid; missing/extra approach or movement keys, negative weights, and incorrect totals are rejected. Zero-weight legal routes remain representable. |
| Crossing declarations | Owned resource sets describe compatible and conflicting paths; merging paths lacking a shared resource are rejected. No runtime reservations exist. |
| Exact demand | Zero, fractional, and rates above one are accepted as exact decimals; negative rates fail. No demand is generated or accumulated. |
| Light placement | Valid mid-road and boundary tiles are accepted. Opposite-lane lights at equal indices are valid. Duplicate IDs/tiles, connector placement, unknown lanes, invalid indices, and nonpositive durations fail. |
| State invariants | Snapshot construction rejects duplicate identities/occupancy, negative tick/counts, and blank status keys. Valid immutable state records construct successfully. |
| Collection ownership | Mutating original lists/maps/sets, including inner weight maps, leaves constructed objects unchanged. Mutation attempts through exposed mutable interfaces fail, including snapshot collections. |
| Build and entry point | Maven verification discovers and passes the required tests under JDK 21; the existing console entry point still runs. |
| Documentation | Construction conventions and limitations are documented with a usable manual example. |

## Required tests

Test behavior that can fail or defines ownership; do not test generated accessors or plain data holders simply to raise coverage.

- Bounded lane/network lookup at negative, first, last, and one-past-last indices; unknown lane and movement lookup.
- Valid boundary, intersection, connected-intersection, and loop fixtures. Assertions should verify useful lookup/construction outcomes rather than mirror private graph indexes.
- Focused invalid-network scenarios covering each distinct ownership, reference, route, and endpoint rule above. Parameterized cases are appropriate where they share a single rule.
- Turning distributions with scale differences, legal zero weights, negative entries, invalid totals, missing/extra approaches, and missing/extra/foreign movements.
- Crossing resource validation, including blank/empty resource declarations and missing shared resources for merges. A compatible-path fixture must remain valid.
- Configuration validation for rate/duration boundaries, valid opposing light placements, and each invalid light placement or duplicate category.
- Snapshot local state rejection, including lane-specific occupancy, and ownership of cars and status maps.
- Defensive ownership for network lists, intersection lists/nested maps, movement resource sets, configuration lights, and snapshot collections. Verify both mutation of retained constructor inputs and rejection of attempted mutation of exposed collections. Verify that iteration order is retained.

Do not introduce movement, timing, arrivals, seeded random, arbitration, or statistical tests. Those require the later simulation seam and are outside this task.

## Edge cases, risks, and assumptions

- A one-position road or connector lane is structurally valid. Lengths for the future multi-position public templates are chosen with those factories; this increment does not generate their dimensions.
- A road may connect two intersections or participate in a loop. Validation checks its directed endpoints and reciprocal lanes without a promise of reachability, eventual departure, or freedom from gridlock.
- Exact decimal validation intentionally rejects a rounded total such as `0.9999`; callers must supply an exact distribution.
- `BigDecimal` excludes nonfinite rates by representation. Future input adapters must reject or convert invalid external values before construction.
- Resource sets can validate names, ownership, and known merges. They cannot infer every physical conflict or verify turn geometry; these remain scene-author declarations and later template verification responsibilities.
- Snapshots enforce local state invariants and immutable ownership, while network-dependent runtime correctness remains the engine's responsibility.
- Pending counts use `Long` as records; this task defines no accumulation, overflow behavior, buffer capacity, or demand-loss policy.
- Dependency downloads may require network access. Report a download/environment failure separately from a failing test and never claim unexecuted verification passed.

## Delivery workflow

Use the existing `feat/basic-domain-entities` branch and preserve unrelated changes. The review baseline for this increment is `1848ed028512171f982cfa13fe0578d9f9bbfb76`.

The explicitly invoked agentic workflow governs delivery: design approval, final specification approval, builder implementation and tests, independent review/corrections, then a `feat:` commit, push, and pull request after a passing review. Never merge or deploy without user approval. The older local-only wording in the task record is superseded by the current requested workflow.
