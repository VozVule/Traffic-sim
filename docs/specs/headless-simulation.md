# Headless simulation engine

Status: proposed specification, awaiting final user approval. The user approved the design on 2026-10-15 with "lgtm". Application implementation must wait for separate approval of this document.

## Objective and scope

Implement the complete headless Kotlin simulation over the existing immutable domain model. A simulation owns the state of one scene and advances deterministically through the public `step()` and `snapshot()` interface.

The accepted behavior and rationale are recorded in [SPEC.md](../../SPEC.md), [the phase-one design](../phase-1-design.md), [the glossary](../../GLOSSARY.md), and [the crossing-reservation ADR](../adr/0005-reserve-crossings-and-outgoing-space.md). The domain entities are already implemented under [their approved specification](basic-domain-entities.md). This document defines the current implementation scope and resolves the engine details needed to implement those accepted rules.

Include roads and exits, exact deterministic arrivals, entrance buffers, simultaneous movement, independent timed lights, seeded per-visit turns, multi-position intersection crossings, conservative crossing and receiving-space reservations, rotating admission priority, connected scenes, congestion, immutable snapshots, and automated tests.

The user selected the complete engine rather than only the first road increment. Scenes are constructed through the existing Kotlin domain constructors. Public template factories and console/file configuration are deferred. Internal test fixtures may represent one or two four-way intersections, smaller valid junctions, and loops without becoming public templates.

Exclude frontend/analytics/export work and release-two behavior: coordinated signals, live demand/configuration changes, richer lane arrangements, roundabouts, and variable drivers. No persistence or global gridlock recovery is added.

## Functional requirements

1. Construct an empty, independent run from immutable `SimulationConfig`. `step()` completes one logical tick; `snapshot()` observes without changing time or randomness. Constructing another instance restarts a run.
2. Preserve the accepted one-transition speed, simultaneous following, exact decimal demand, persistent entrance buffers, and end-of-tick admission. Newly admitted cars cannot move again that tick.
3. Implement independent directional lights and one retained seeded movement choice per intersection visit.
4. Secure crossing resources and the first outgoing tile before entry, hold reservations through crossing, admit compatible paths concurrently, and rotate priority among feasible competitors.
5. Handle valid empty/disconnected scenes, one-tile lanes, connected intersections, and loops safely. Conservative dependency resolution may leave cyclic chains stopped; global progress or eventual departure is not guaranteed.
6. Produce contextually valid immutable snapshots. Checked numeric overflow fails before changing any run state.

## Technical design

### Public API and module boundary

Add `src/main/kotlin/trafficsimulator/simulation/Simulation.kt`:

```kotlin
package trafficsimulator.simulation

import trafficsimulator.domain.SimulationConfig
import trafficsimulator.domain.SimulationSnapshot

class Simulation(config: SimulationConfig) {
    fun step(): SimulationSnapshot
    fun snapshot(): SimulationSnapshot
}
```

Use the current `trafficsimulator.domain` types without changing their public contracts. The constructor accepts arbitrary networks already accepted by `RoadNetwork` and `SimulationConfig`; it must not require a public template, four approaches per intersection, equal opposing lane lengths, an entrance in every component, or an eventual route to an exit.

Keep mutable engine helpers internal or private to the simulation package. Prefer small concrete components for directly used responsibilities; do not put every responsibility into one large method or add public policy interfaces for future controllers. No public car injection, state mutation, reset, checkpoint/restore, or reservation inspection API is required. Snapshots are observations rather than restorable checkpoints.

Callers serialize calls to an individual instance. Thread safety, wall-clock pacing, background execution, and real-time scheduling are outside this contract. The existing console entry point remains usable; implementing a simulation CLI is outside this change.

### State ownership and indexes

The simulation retains the immutable configuration/network and owns all mutable run state:

- Tick number, active car records, and occupancy.
- Each entrance's fractional demand remainder and pending count.
- Each light's next movement color and remaining phase duration, plus the colors in the last completed snapshot.
- The run's seeded random generator and deterministic identity/creation ordering.
- Crossing owners, reserved receiving tiles, and each intersection's rotating priority cursor.

Build private lookup indexes from the validated topology as needed: road lanes, incoming-lane intersections, connector ownership, movement-to-intersection ownership, exit lanes, and lights by tile. Do not repeat network validation or expose engine indexes as new domain state.

Crossing-resource identity is `(intersectionId, declaredResourceId)`. Different intersections may reuse the same resource names without blocking each other. Receiving reservations identify actual outgoing `Tile` values. Each connector is owned by exactly one movement, as already enforced by `RoadNetwork`.

Maintain a single coherent occupancy/reservation model. Snapshot construction validates local duplicate occupancy but cannot validate lane bounds or movement ownership; producing valid runtime state remains the engine's responsibility.

### Initial state and snapshot contract

Initialize tick, entrance remainders, and pending counts to zero; cars and reservations to empty; each approach cursor to its first declared approach; and the random generator from `randomSeed` without choosing routes. Lights begin their initial full phases.

The tick-zero snapshot contains no cars, all configured light IDs mapped to initial colors, and all entrance IDs mapped to zero. Empty scenes have empty collections and still advance their clock.

Later snapshots contain the completed tick number; every active car exactly once at an existing in-bounds tile, with a movement valid for its current incoming lane/connector visit; every light's color used for that tick; and every entrance's pending count after admission, including zeros.

Use the existing immutable `Car` and defensively owned `SimulationSnapshot` records. Earlier snapshots must remain unchanged after future steps, and attempted JVM collection mutations must not alter engine state. Returning the same immutable snapshot object on repeated reads is permitted; object identity is not part of the API.

### Deterministic identity and ordering

Use declared configuration order consistently:

- Entrances and lights follow their list order.
- Intersections follow `network.intersections` order.
- An intersection's approach order is the first occurrence of each incoming lane in its movement list; choices within an approach follow that movement list.
- Cars follow stable admission order: admission tick, then entrance list order. Removing a car does not reorder the remaining cars.

Assign unique, reproducible car IDs at admission, never when demand merely becomes pending. An ID derived from the entrance ID and admission tick is sufficient because each entrance admits at most one car per tick; include an unambiguous separator and do not reuse IDs after departure. The exact text format is an implementation convention rather than a caller parsing interface. It must not introduce a separate unchecked numeric counter.

Use stable car order for new route choices and snapshots, and preserve light/entrance list order in status maps. Do not use hash iteration to decide winners. Identical ordered configuration and seed must produce equal observable fields, including IDs and list order. Reordering otherwise equivalent configuration may change the run. `SimulationSnapshot` needs no new structural `equals` implementation.

### Tick sequence

For advancement from completed tick `t` to `t + 1`:

1. Preflight checked tick and demand arithmetic for every entrance into temporary values. If any representational limit would be exceeded, throw without changing any run state.
2. Determine the light colors used for this movement tick. Apply the preflight demand results to the entrance buffers/remainders.
3. In stable car order, choose movements for cars at their final incoming road tile that have no current choice. Construct at most one transition request per existing car.
4. Resolve directional light gates, occupancy dependencies, crossing/receiving reservations, and competing admissions. Accept only complete feasible dependencies; reject conservative dependency cycles.
5. Commit accepted moves together, remove exited cars, and release reservations for cars that completed their connector-to-road transition. Update approach cursors based on actual accepted admissions.
6. In entrance list order, admit at most one pending car to each unoccupied first road tile. This includes a tile vacated during this tick. Decrease the corresponding pending count without giving the new car another transition.
7. Complete light timer bookkeeping for the next movement tick and publish the new immutable snapshot with the colors used in this tick.

The order is observable at boundaries: new cars cannot move twice; a route is chosen before an existing car requests connector entry; an entrance can reuse a successfully vacated tile; and snapshot colors describe movement that just happened.

### Exact demand, buffers, and arithmetic failure

For each entrance, maintain an exact `BigDecimal` remainder `r` with `0 <= r < 1`. Before each tick compute:

```text
total = r + configuredArrivalRate
due = floor(total)
nextRemainder = total - due
nextPending = currentPending + due
```

Use decimal/integer arithmetic without a `Double` accumulator, rounding context, or repeated conversion from binary floating point. With rate `0.5`, the first due arrivals occur on ticks two, four, and six. With rate `0.1`, the first is due on tick ten. Rates of zero or above one remain valid.

Pending arrivals are counts of otherwise identical off-scene demand; no individual `Car` or route choice exists until admission. There is no application buffer capacity, timeout, or demand-discard policy. Admission requires only pending demand and a free entrance tile. A red light on that first tile does not stop admission; it controls later departure from the tile.

Ticks and pending counts use the existing `Long` representation. Before mutating any state, check `t + 1`, exact conversion of each whole-number due count, and addition to every pending count. Use checked conversions/addition and throw `ArithmeticException` with the affected field or entrance identified if a value cannot be represented. Check the pending total before the later admission subtraction; admission is not used to justify an overflowing intermediate count. Never wrap, clamp, silently drop demand, or return negative values.

All entrances must pass this preflight before changing cars, remainders, buffers, timers, random state, reservations, cursors, or the published snapshot. A failed step leaves the run unchanged and repeated attempts fail from that same state. Stable IDs avoid a separate late counter overflow. Light timers must not add two `Int` durations into an overflowing `Int`; use remaining-phase counters or safe wider arithmetic.

Test pending/due overflow with large rates over a few public steps. No test-only tick setter or impractical run to `Long.MAX_VALUE` is required. The checked tick addition must still be implemented.

### Road movement, exits, and dependencies

Within a lane, a requested transition goes from index `i` to `i + 1`. At a final road tile, it either requests entry into its chosen connector or departs through that lane's configured exit. A car on a connector advances through its positions, then requests the first tile of its movement's outgoing road lane. Every boundary consumes one full transition.

Apply a light gate only to the road tile being departed. A red final-tile light prevents connector entry or departure through an exit. A red light on an outgoing receiving tile does not prevent arrival onto that tile. Connector positions have no lights.

Requests depend on the current occupants of destination tiles and, for a new crossing reservation, the first outgoing tile. A dependency succeeds only if that occupant has an accepted departure in this same tick. A gated, rejected, or blocked departure does not make its tile available. Commit moves together so a following chain can advance without temporary duplicate occupancy.

Resolve dependencies with explicit visiting/resolved state or an equivalent finite graph traversal. A closed chain that would require proving itself vacated is rejected conservatively, together with moves that require that chain. Do not hang, overflow the call stack on cyclic topology, or optimistically accept a cycle because a car was encountered twice. This policy may leave a fully occupied loop stopped even when a simultaneous rotation could theoretically be safe. No global deadlock recovery is added.

Do not infer that every topological loop is blocked. A populated loop with sufficient space must permit ordinary progression and new per-visit choices. Populate revisit fixtures through a boundary branch into a loop using the existing constructors. A closed component with no entrance naturally remains empty in a newly constructed run.

### Light timing

Each light independently uses its configured initial color for that color's full duration. For an initially red light with red duration twenty, movement ticks one through twenty use red; movement tick twenty-one uses green. For red duration two and green duration three, initially red colors are `RED, RED, GREEN, GREEN, GREEN, RED, ...`.

The tick-zero snapshot reports the initial color. A completed snapshot reports the color just used even if private bookkeeping has already prepared the next phase. An initially green light likewise uses its full green duration first. Repeated snapshot reads must not decrement durations, switch colors, or affect movement.

Distinct lights, including equal indices on opposing lanes, remain independent. A car that has departed a light's tile is unaffected by later changes at that tile. Crossing safety still applies when incompatible approaches both have green.

### Per-visit turning

A car becomes eligible for a new choice when it is at the last tile of a road lane ending at an intersection at the beginning of a movement tick. A car that reaches that tile during this tick, including a newly admitted car on a one-tile incoming lane, chooses on the next tick. Being stopped by the final-tile light or losing contention does not prevent recording that one choice.

Choose according to that approach's declared legal movements and exact normalized weights. Zero-weight legal choices must never be selected. Use one `java.util.Random` seeded from `randomSeed` for the run, consumed only for new choices in stable car order.

For weighted sampling, convert positive finite decimal weights to integer weights using a common nonnegative decimal scale. Sample uniformly below their integer total using `BigInteger` rejection sampling with the seeded generator, then select from cumulative integer weights in movement-list order. This avoids rounding away small positive weights. A sole positive choice may be returned directly without consuming randomness. No sampling dependency is needed.

Retain `chosenMovementId` while waiting on the incoming final tile and throughout the connector. Clear it when committing departure from the connector into the outgoing road. At a later intersection, including a revisit, select afresh. If the outgoing road has one tile and ends at another intersection, the next visit begins on the following movement tick, without a second transition in the current tick.

Tests use forced weights for exact route assertions and fixed seeds for reproducibility. Do not test private random-number draws or require different seeds to produce different outcomes in a short finite run.

### Crossing admission and reservation lifetime

A new connector entry requires a permitted departure light, an available first connector tile, all required crossing resources unreserved, and an unreserved first outgoing tile that is free or vacated by an accepted move this tick.

Acquire the crossing resources and receiving tile atomically for an accepted entrant. A failed candidate must not leave tentative ownership behind. Another connector entry cannot consume any resource or receiving tile already secured by a prior accepted entrant. Tile identity and crossing-resource identity are separate safety checks.

The admitted car retains its reservations while moving through the connector. Its first outgoing tile remains secured even while physically empty; other entrants cannot take it. On connector departure, the car occupies the outgoing tile and its crossing/receiving reservations are released after the movement commit. Ordinary occupancy then protects that tile.

Existing reservations are treated as held throughout the current resolution phase, including when their owner is expected to finish crossing this tick. A conflicting or same-connector entrant therefore waits until a later tick to reuse those resources. This makes the accepted following restriction and release timing explicit. Occupied outgoing tiles, separately, can qualify through a successful same-tick departure.

### Feasible arbitration and rotating priority

For each intersection, keep the declared approach order and a cursor initially at its first approach. An approach has at most one entry candidate, the car at its final incoming road tile.

Scan approaches cyclically from the cursor. Consider only candidates whose actual transition and required occupancy dependencies can succeed. Skip red, blocked, currently reserved, cyclic, or otherwise infeasible candidates without redrawing their choices or retaining tentative reservations. Grant feasible candidates greedily in this rotated order; continue the scan to admit later candidates compatible with the grants already made.

Requests whose feasibility depends on another existing car's departure must be resolved together with that dependency before a grant is final. If the required departure is rejected, reject the dependent grant, release its tentative claims, and allow the next feasible competing candidate to be considered. A rejected request must not consume capacity or win merely because it appeared earlier. Deterministic processing uses intersection-list order when otherwise independent intersections need a traversal order; resource names remain local to each intersection.

After the final accepted set is established, move the cursor to the approach immediately after the first approach actually admitted in that tick's rotated scan. Leave it unchanged if no approach was admitted. Compatible later admissions do not move it again. Base rotation on accepted moves rather than failed speculative attempts.

This gives each continuously feasible contender first priority in turn when the relevant resources become available. It does not guarantee progress for a red, downstream-blocked, or otherwise infeasible approach. Conservative full-crossing reservations favor straightforward safety over maximum throughput.

## Architecture decisions and trade-offs

- Keep runtime bookkeeping behind the approved interface and use concrete internal components, existing domain contracts, and JDK arithmetic/randomness.
- Declared topology/resources avoid a geometry engine but depend on accurate scene-author conflict declarations.
- Simultaneous movement preserves ordinary following. Full-crossing reservations and conservative cycle rejection prioritize straightforward safety and can reduce throughput or sustain gridlock.
- Stable declared ordering and rotating priority provide reproducibility without a scheduling framework.
- Checked `Long` preflight matches existing snapshot types and fails atomically at representational limits.
- Deferring templates/input adapters keeps this change focused on movement correctness.

## Implementation tasks

1. Add the simulation facade, initial immutable snapshot, private runtime state, and directly useful topology indexes. Preserve existing public domain contracts and the current Maven/JDK layout.
2. Implement overflow preflight, exact arrival remainders, pending accounting, reproducible admission identities, and stable car/status ordering.
3. Implement one-transition requests, simultaneous road/connector movement, exits, occupancy dependencies, and conservative cycle handling.
4. Implement independent signal phases and movement-tick snapshot colors with safe duration arithmetic.
5. Implement exact seeded per-visit weighted choices and choice retention/clearing.
6. Implement intersection-scoped crossing resources, receiving reservations, transactional feasible admission, reservation release timing, and rotating approach priority.
7. Add focused test scenes and scenario tests through `Simulation`. Use private test helpers rather than production template APIs or state injection. Existing helper distributions that divide one by the number of choices need explicit finite weights for three-way choices.
8. Update README with a small Kotlin construction/stepping example, snapshot timing and demand conventions, numeric limits, conservative congestion behavior, and remaining deferred template/input work. Keep historical approved specifications unchanged.
9. Run the full Maven verification and existing entry-point smoke check with JDK 21. Report actual outcomes and resolve required failures before independent review.

These tasks may be implemented internally in increments, but the delivered change must include the complete agreed engine and its integrated tests.

## Acceptance criteria and required tests

Exercise externally observable behavior through construction, `step()`, and `snapshot()`. Compare snapshot fields rather than adding equality solely for tests. Existing domain validation/ownership tests must continue to pass. Do not mirror helper decomposition, private reservation containers, or random-generator bookkeeping.

| Scenario | Independently verifiable outcome |
| --- | --- |
| Initial observation | Tick zero is empty, all configured light/entrance entries exist, initial colors and zero counts are correct, and repeated reads change nothing. Empty scenes advance safely. |
| Independent runs | Two simulations sharing one configuration produce equal snapshot sequences for equal advancement; stepping one does not advance the other. |
| Movement and identity | Active IDs are unique and reproducible; opposite-lane matching indices remain distinct; every existing car makes at most one transition per tick and no tile has two cars. |
| Following and exits | A chain follows a successfully departing leader in the same tick, including a scene exit; a blocked leader prevents dependent moves. One-tile roads work without an extra admission move. |
| Exact demand | Rates `0`, `0.5`, and `0.1` have the exact expected due schedule; a fractional rate above one retains the correct integer backlog and remainder over multiple ticks. |
| Admission and congestion | At most one car enters per entrance per tick; a vacated first tile can admit after movement; blocked demand accumulates without loss. Verify conservation as due demand equals pending plus admitted demand, accounting for observed departures. |
| Overflow atomicity | A huge due value and a later pending-addition overflow throw clear `ArithmeticException`s before any run state changes. A multi-entrance case places the failing entrance later in declaration order. Prior snapshot fields remain equal, repeat attempts fail from the same tick, and no test-only setters are added. |
| Signal phase boundaries | Initially red for twenty ticks remains red through tick twenty and first permits departure on tick twenty-one. Unequal durations and initial green use their full phases; large valid durations do not overflow arithmetic. |
| Signal placement and independence | Cars reach a red light tile but cannot leave; first/final-tile lights gate departure appropriately; opposite lanes and distinct lights act independently; cars beyond an upstream light keep moving. |
| Weighted choice | Each approach obeys its own forced legal straight/left/right choices. Valid decimal distributions with differing scales run reproducibly; zero-weight choices never occur. Waiting and contention retain the selected movement. Code review verifies exact integer sampling rather than requiring a finite test run to hit a very rare route. |
| Connector traversal | A car visibly occupies successive connector positions and spends separate ticks on road-to-connector and connector-to-road transitions; choice clears on crossing completion. Include a one-tile connector boundary. |
| Compatible crossings | Correctly declared opposing straight movements occupy their connectors concurrently. |
| Physical conflicts and merges | Incompatible movements cannot overlap their held crossing resources despite distinct connector tiles; merging entries cannot secure the same outgoing tile. Simultaneous greens preserve safety. |
| Receiving-space dependencies | A blocked outgoing tile keeps a car outside. Its accepted departure permits same-tick admission; a merely requested but blocked departure does not. A blocked high-priority candidate does not deny a feasible competing candidate its grant. |
| Reservation lifetime | An admitted car retains secured outgoing space until connector departure. A follower and conflicting entrant wait while its reservation is held, including the owner's clearance tick, and can enter on a later feasible tick. |
| Rotating priority | Sustained feasible competition grants each conflicting approach over successive crossings. Infeasible approaches are skipped; losing candidates keep their turns. Do not require progress when downstream conditions make it impossible. |
| Resource scoping | Two intersections using the same declared resource name can cross independently when their own roads and paths are available. |
| Connected scenes | Traffic traverses two connected intersections, chooses again on the second visit, queues safely on the connection under downstream red/blockage, preserves pending demand, and leaves at boundary exits. |
| Loops and cyclic dependencies | A boundary-fed loop with space permits a car to revisit and obtain a new per-visit choice. A closed dependency cycle terminates safely without overlapping cars or leaked reservations and may remain stationary. A closed component without entrances remains empty. |
| Reproducibility | Matching configurations/seeds produce equal full snapshot sequences over representative single- and connected-intersection runs, including car IDs, positions, choices, colors, counts, and ordering. |
| Snapshot ownership/context | Retained snapshots remain unchanged after later ticks; mutation attempts cannot affect the engine; every emitted position/choice belongs to the configured network. |
| Integration/build | The full domain and simulation suite passes under JDK 21, and the existing console entry point still runs. README demonstrates the public engine without requiring templates or file input. |

For route-choice retention/revisit checks, use controlled topology and weights to make the externally observed choices meaningful; do not infer a fresh draw merely because a PRNG was called or impose flaky statistical thresholds. A representative four-way fixture must exercise legal turning and crossing declarations, while smaller fixtures should keep individual timing assertions readable.

Exercise cycles using states reachable through valid traffic and public configuration. If reservation invariants make a proposed fully closed request cycle unreachable, document that invariant and inspect finite cycle handling during code review rather than adding state-injection hooks solely for that test.

Run with JDK 21:

```sh
./mvnw clean verify
./mvnw compile exec:java
```

Use the existing Kotlin 2.2.21, JUnit Jupiter, Maven wrapper, and Java 21 target. No new runtime or testing dependency is required by this design. Report dependency/environment failures separately from failing tests and never claim unexecuted checks passed.

## Edge cases, risks, and assumptions

- Network/configuration construction already rejects structural mistakes. Existing constructor validation remains the rejection point for invalid topology, references, distributions, or light placement; the engine adds contextual runtime correctness rather than another validation framework.
- Valid networks can have unequal opposing lane lengths, disconnected components, one-position lanes, no lights, no entrances, or no exits. Do not silently reject these supported model cases.
- Crossing declarations cannot prove real physical safety without geometry. Tests verify engine compliance with declarations and use correctly authored representative intersections.
- Full-crossing reservations and conservative cycle rejection can reduce throughput or sustain gridlock. There is no overtaking, spontaneous rerouting, or global recovery algorithm.
- Demand is retained within the existing representable counters. Extremely large configured demand eventually causes explicit atomic arithmetic failure; the engine does not truncate it or promise arbitrary-size snapshot counts.
- Exact decimal arithmetic and rejection sampling use ordinary JVM resources. The prototype introduces no arbitrary precision, scene-size, or buffer-capacity configuration limits beyond existing scalar representations; pathological inputs remain bounded by available process resources.
- No initial-car API is introduced. Closed loops need a boundary-connected route to receive traffic in tests or caller-built scenes.
- Snapshot fields remain operational observations. Queue lengths, waiting times, throughput reports, export formats, and persistence are not added implicitly.

## Delivery workflow

Use the existing `feat/headless-simulation` branch and preserve unrelated changes. The review baseline is `e09755ff6007c1ed5c0403c69a857859bd0c4e53` on `origin/main`.

The explicitly invoked agentic workflow requires final user approval of this specification before application implementation, builder implementation and automated tests, independent review and any required corrections, then a `feat:` commit, push, and pull request after a passing review. Do not modify the approved specification silently during implementation. Return material requirement changes for user approval. Never merge or deploy without approval.
