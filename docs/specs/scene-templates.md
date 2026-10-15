# Scene templates

Status: design approved on 2026-10-15; final specification approval pending. This task is planning only. Application implementation requires a subsequent explicit request.

## Objective and scope

Provide a small Kotlin API for listing and selecting predefined scenes and creating a valid, ready-to-run `SimulationConfig`. Templates are the supported application scene-creation path for this release; user-created scenes belong to the next release.

Provide exactly two templates: one four-way intersection and two four-way intersections in sequence. Allow overrides for entrance rates, per-approach turning weights, and random seed. Topology, road/connector lengths, crossing resources, and lights remain fixed within each template.

Do not add a console flow, change `Main.kt`, introduce UI or file formats, implement custom scenes, or change the engine or existing domain contracts. Existing public domain constructors remain available to callers and tests. The broader release-one settings entry point, including configurable lights, remains a separate task.

## Functional requirements and public API

Add `SceneTemplate` in `trafficsimulator.templates`, with these enum entries and display names:

| Entry | Display name |
| --- | --- |
| `SINGLE_INTERSECTION` | Single four-way intersection |
| `TWO_INTERSECTIONS` | Two four-way intersections |

`SceneTemplate.entries` supplies the fixed catalog in the order above. Each entry exposes `val displayName: String` and this factory method:

```kotlin
fun create(
    entranceRates: Map<String, BigDecimal> = emptyMap(),
    turningWeights: Map<String, Map<Turn, BigDecimal>> = emptyMap(),
    randomSeed: Long = 42L,
): SimulationConfig
```

Use the existing `java.math.BigDecimal` and `trafficsimulator.domain.Turn`. No settings object, string-selection API, registration mechanism, or factory interface is needed. For example:

```kotlin
val config = SceneTemplate.SINGLE_INTERSECTION.create(
    entranceRates = mapOf("north-entry" to BigDecimal("0.2")),
    turningWeights = mapOf(
        "north-in" to mapOf(
            Turn.STRAIGHT to BigDecimal("0.75"),
            Turn.LEFT to BigDecimal("0.25"),
            Turn.RIGHT to BigDecimal.ZERO,
        ),
    ),
    randomSeed = 7L,
)
val simulation = Simulation(config)
```

### Defaults and overrides

| Setting | Default |
| --- | --- |
| Every boundary entrance rate | `BigDecimal("0.1")` cars per tick |
| Every incoming approach's straight/left/right weights | `0.50` / `0.25` / `0.25`, constructed from decimal text |
| Random seed | `42L` |
| Every road lane, including the connecting road | 10 tiles |
| Right/straight/left connector lengths | 1 / 2 / 3 tiles |
| Light placement | Final tile, index 9, of every incoming intersection approach |
| Every light's red/green duration and initial color | 20 / 20 ticks; `LightColor.RED` |

Lights use the existing independent full-phase timing semantics. There are four lights in the single-intersection template and eight in the two-intersection template. There are no additional lights. Light placement, durations, and colors have no factory overrides in this task.

Entrance override keys are entrance IDs. Turning override keys are incoming road-lane IDs, including the connecting road's approaches. A caller can discover these keys by creating a default config and reading `network.entrances` and each intersection's `movements` (`incomingLaneId` and `turn`). README must show this discovery and the example above.

Overrides are sparse across entrances and approaches: omitted entries retain their defaults. Each supplied approach replaces its complete three-turn distribution and must contain exactly `STRAIGHT`, `LEFT`, and `RIGHT`. Values must be nonnegative and total exactly one using decimal comparison; zero-weight legal turns remain present. Do not normalize values or merge an incomplete distribution with defaults.

Reject unknown entrance IDs, unknown incoming approach IDs, incomplete turn distributions, negative rates/weights, and distributions whose sum differs from one. Throw `IllegalArgumentException` with a message identifying the offending key or approach. Reuse existing domain validation where applicable; validate override keys and turn-key completeness at the template boundary. Passing IDs belonging only to the other template is an error. Empty override maps use all defaults; an empty distribution for a specified approach is invalid. Any `Long` seed is valid and is copied unchanged to `SimulationConfig.randomSeed`.

Caller map iteration order must not change template ordering or seeded behavior. Factories must not retain mutable caller-owned maps or expose mutable collections. An override must not affect an earlier config or a later default factory call.

## Technical design

Implement ordinary construction functions behind the enum's factory method, returning existing domain objects. Use a private helper for the common four-way junction definition so the two templates share their route and conflict declarations. Do not introduce a runtime `Scene`, general geometry engine, public template hierarchy, or dynamic registry. `Simulation(config)` remains the run boundary and owns all mutable simulation state.

### Fixed topology and identifiers

All roads are two-way with one lane per direction. Boundary roads use their inward lane as `forwardLane` and outward lane as `backwardLane`. Their IDs follow this convention:

| Entity | ID |
| --- | --- |
| Boundary road | The road name listed below |
| Its incoming/outgoing lanes | `<road>-in` / `<road>-out` |
| Its entrance/exit | `<road>-entry` / `<road>-exit` |
| Movement | `<junction>-<from-arm>-to-<to-arm>` |
| Movement connector lane | `<movement-id>-path` |
| Approach light | `<junction>-<arm>-light` |

Arm names are `north`, `east`, `south`, and `west`; they describe the side of the junction from which an incoming car arrives. Each permitted movement connects that arm's incoming lane to its destination arm's outgoing lane. Every movement owns its own connector. No U-turn exists.

The single-intersection template has intersection ID `junction` and boundary roads `north`, `east`, `south`, `west`, in that order. It contains four roads, eight road lanes, four entrances, four exits, twelve movements, and twelve connector lanes.

The two-intersection template has intersections `west-junction` and `east-junction`, in that order. Its road order is `west-north`, `west-south`, `west-west`, `connection`, `east-north`, `east-east`, `east-south`. All except `connection` are boundary roads; entrance and exit lists follow boundary-road order. It contains seven roads, fourteen road lanes, six entrances, six exits, twenty-four movements, and twenty-four connector lanes.

The connecting road has ID `connection`, forward lane `connection-eastbound`, and backward lane `connection-westbound`. It has no entrance or exit. Junction arm bindings are:

| Junction | North | East | South | West |
| --- | --- | --- | --- | --- |
| `west-junction` | `west-north` boundary road | Incoming `connection-westbound`, outgoing `connection-eastbound` | `west-south` boundary road | `west-west` boundary road |
| `east-junction` | `east-north` boundary road | `east-east` boundary road | `east-south` boundary road | Incoming `connection-eastbound`, outgoing `connection-westbound` |

For example, `west-junction-west-to-east` connects `west-west-in` to `connection-eastbound`; `east-junction-west-to-east` connects `connection-eastbound` to `east-east-out`. The seed and probabilistic overrides do not change identifiers or topology.

Within each junction, declare incoming approaches in north/east/south/west order and movements within each approach in straight/left/right order. Connector and light lists follow their corresponding movement and approach orders. Turning distributions retain movement order after applying overrides. These stable orders preserve reproducible arbitration, sampling, and configuration output.

### Turns and crossing resources

Use the following right-hand-traffic route table at every junction. Resource IDs are local to the intersection in the existing engine; reuse `north-west`, `north-east`, `south-east`, and `south-west` at each junction. The table supplies the `Movement.turn`, destination arm, and complete `crossingResourceIds` set.

| From | Turn | To | Crossing resources |
| --- | --- | --- | --- |
| North | Straight | South | `north-west`, `south-west` |
| North | Left | East | `north-west`, `south-west`, `south-east` |
| North | Right | West | `north-west` |
| East | Straight | West | `north-east`, `north-west` |
| East | Left | South | `north-east`, `north-west`, `south-west` |
| East | Right | North | `north-east` |
| South | Straight | North | `south-east`, `north-east` |
| South | Left | West | `south-east`, `north-east`, `north-west` |
| South | Right | East | `south-east` |
| West | Straight | East | `south-west`, `south-east` |
| West | Left | North | `south-west`, `south-east`, `north-east` |
| West | Right | South | `south-west` |

Opposing straight paths have disjoint resources. Perpendicular straights conflict, and every pair merging into the same outgoing lane shares a resource. Apply existing conservative crossing reservations unchanged. These declarations model conflicts without introducing geometric coordinates.

Translate each override's turn-keyed distribution into the existing `Intersection.turningWeights` map keyed by movement IDs for that approach. Construct `RoadNetwork` and `SimulationConfig` through their existing validation and ownership contracts.

## Architecture decisions and trade-offs

- A fixed enum and ordinary factories match the predefined catalog and keep selection explicit. Adding another built-in template requires a code change.
- Existing domain IDs provide override keys without a second metadata model. Callers inspect a default config or use documented IDs.
- Requiring a complete triplet for an overridden approach avoids guessing how omitted probabilities should be redistributed. Sparse outer maps still make changing only one approach straightforward.
- Fixed dimensions, resources, and lights keep this feature limited to selecting layouts and changing probability-related settings. Broader configuration remains separate.
- Exact decimal defaults and existing validation preserve demand and probability semantics without new dependencies.

## Implementation tasks

1. Add the enum/catalog, display names, ordinary factories, and private shared four-way construction helper in `trafficsimulator.templates`.
2. Author the two fixed topologies, stable IDs/orders, routes, connectors, resources, and default lights as specified.
3. Apply and validate entrance/turning/seed overrides, translating turns to movement IDs and preserving collection ownership and ordering.
4. Add focused template tests and representative integrations through the existing `Simulation` API. Preserve the existing small engine test fixtures; do not migrate them merely to use public templates.
5. Update README to document selection, defaults, override keys/discovery, validation, and a Kotlin construction/stepping example. Replace the statement that public templates are deferred. Add custom scene creation to `release-2.md`; leave previously approved historical specifications unchanged.
6. Run the full Maven verification with JDK 21 and the unchanged entry-point smoke check. No new dependencies or testing framework are required.

These are future implementation tasks. This planning request changes only this specification and does not authorize their execution.

## Acceptance criteria and required tests

Use existing JUnit Jupiter conventions. Exercise the public factory, existing domain observations, and `Simulation`; do not add state injection or require specific private helper decomposition.

| Scenario | Independently verifiable outcome |
| --- | --- |
| Catalog and defaults | Exactly two entries exist in the specified order with the specified display names. Both default configs construct successfully and have the documented entity counts, IDs, lengths, boundary endpoints, weights, seed, and lights. |
| Junction routes and conflicts | Every approach has exactly its legal straight/left/right destinations and connector lengths; no U-turn exists. Opposing straights are compatible, perpendicular straights conflict, and all merges share a resource. The connection is reciprocal and has no boundary entrance/exit. |
| Sparse entrance overrides | An overridden entrance uses its supplied exact rate, including zero or a valid rate above one; omitted rates and all unrelated settings retain defaults. |
| Per-approach turning overrides | A complete distribution changes exactly the selected approach, including a connecting-road approach. Other approaches retain defaults, and zero weights remain legal. Different caller map orders produce the same ordered configuration and run. |
| Rejection | Unknown entrance/approach keys, other-template keys, incomplete/empty distributions, negative rates/weights, and totals below or above one fail clearly during factory construction. No partially valid config is returned. |
| Seed | Default and supplied seeds are propagated unchanged, including `Long.MIN_VALUE` and `Long.MAX_VALUE`. Do not require distinct seeds to produce different short runs. |
| Ownership and independence | Mutating supplied maps after creation cannot change the config. Produced collections honor existing unmodifiable ownership contracts. Overrides cannot alter a previously returned config or a subsequent default config. |
| Real template traversal | With other boundary rates set to zero and selected approaches forced through complete distributions, cars follow the configured turns and leave at the expected boundary lanes. Include straight, left, and right choices and a run that traverses both junctions via `connection-eastbound`, choosing at each junction. Account for the fixed default lights when choosing the run length. |
| Run integration and reproducibility | Both templates advance with valid in-bounds, nonoverlapping car positions. Matching template/overrides/seed produce matching snapshot fields over representative runs; retained configs/snapshots stay unchanged. Existing domain and simulation suites continue to pass. |
| Build and documentation | `./mvnw clean verify` passes with JDK 21; `./mvnw compile exec:java` retains the existing readiness behavior. README documents the actual template API and deferred custom-scene scope. |

Force distributions for exact route assertions and compare snapshot fields using existing test patterns. Do not use flaky statistical thresholds or duplicate the engine's full timing/reservation suite; existing tests already cover those rules.

## Edge cases, risks, and assumptions

- All-zero entrance rates are valid and produce an empty advancing run. Zero turn weights are valid when the complete distribution totals one. Finite decimal values with differing scales follow existing exact numeric validation.
- Lights intentionally begin with identical independent phases. The engine resolves conflicting greens safely; these presets do not coordinate lights or promise maximum throughput.
- Sustained demand can produce queues or gridlock under the existing conservative engine rules. The factory does not add recovery, new capacity limits, or runtime setting changes.
- IDs are the public keys for overrides and must remain stable within this feature. Wrong-template or misspelled IDs fail rather than being silently ignored.
- No geometry validation is added. Correctness of the authored route/resource table must be checked by template tests and review.
- The approved design settles this feature's scope and defaults. Final specification approval remains pending; implementation and its builder/reviewer workflow have not begun.
