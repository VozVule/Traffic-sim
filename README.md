# Traffic simulator

Kotlin/JVM project using Maven. Immutable domain entities describe roads, intersections, demand, lights, and snapshots. The headless simulation advances a validated scene through deterministic logical ticks. The console entry point prints a readiness message.

## Requirements

Use JDK 21. The included Maven wrapper downloads Maven 3.9.15 on first use; a separate Maven installation is unnecessary. Dependency downloads require internet access.

On macOS, select your installed JDK 21 if needed:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

## Compile

```sh
./mvnw clean compile
```

## Run

```sh
./mvnw compile exec:java
```

The entry point is `src/main/kotlin/trafficsimulator/Main.kt`. Open `pom.xml` to import the project into an IDE. On Windows, use `mvnw.cmd` in place of `./mvnw`.

## Choose a scene template

Use `SceneTemplate.entries` to list the predefined scenes in catalog order. Templates are the supported way to create an application scene in this release:

| Template | Layout |
| --- | --- |
| `SINGLE_INTERSECTION` | One four-way intersection |
| `TWO_INTERSECTIONS` | Two four-way intersections joined by a two-way road |

Each template creates a ready-to-run `SimulationConfig`. Defaults are `0.1` cars per tick at every entrance, straight/left/right weights of `0.50`/`0.25`/`0.25` at every approach, and seed `42`. Roads have 10 tiles per lane; right/straight/left connectors have 1/2/3 tiles. Every incoming intersection approach has a light at its final tile, initially red, with independent 20-tick red and green phases. Layout and lights are fixed.

Override entrance rates, turning weights, and seed when creating a configuration:

```kotlin
import java.math.BigDecimal
import trafficsimulator.domain.Turn
import trafficsimulator.simulation.Simulation
import trafficsimulator.templates.SceneTemplate

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
val initial = simulation.snapshot() // Tick zero, no cars or pending demand.
repeat(40) {
    val completed = simulation.step()
    println("Tick ${completed.tick}: ${completed.cars.size} active cars")
}
```

Overrides are sparse across entrances and approaches: omitted entries keep their defaults. Each supplied approach must provide all three turns with nonnegative decimal weights totaling exactly one; zero-weight legal turns stay present. Values are not normalized and missing turns are not filled in. Rates are nonnegative cars per tick and may exceed one. Construct rates and weights from decimal text with `BigDecimal`. Any `Long` seed is accepted.

Entrance override keys are entrance IDs, and turning override keys are incoming lane IDs. Discover valid keys and available turns from a default configuration:

```kotlin
val template = SceneTemplate.TWO_INTERSECTIONS
println(template.displayName)
val defaults = template.create()
println(defaults.network.entrances.map { it.id })
for (intersection in defaults.network.intersections) {
    for ((incomingLaneId, movements) in intersection.movements.groupBy { it.incomingLaneId }) {
        println("$incomingLaneId: ${movements.map { it.turn }}")
    }
}
```

Single-intersection boundary roads are `north`, `east`, `south`, and `west`, giving entrance IDs such as `north-entry` and incoming lane IDs such as `north-in`. The two-intersection boundary roads are `west-north`, `west-south`, `west-west`, `east-north`, `east-east`, and `east-south`, with the same `-entry`/`-in` suffixes. Its internal approaches `connection-eastbound` and `connection-westbound` also accept turning overrides and have no boundary entrance.

Unknown or wrong-template IDs, negative values, incomplete distributions, and totals different from one throw `IllegalArgumentException` identifying the offending key or approach. Caller map order does not change configuration order or seeded runs. Configurations copy their collections and expose unmodifiable views; subsequent caller-map mutations and template calls do not affect an existing configuration.

Domain types remain available in `trafficsimulator.domain`. Each directed road lane has reciprocal endpoints, and each legal intersection movement has its own connector and crossing resources. Tile indices start at zero, so road final tiles have index nine. Entrances enter index zero; exits represent departure after the lane's final tile. The engine emits snapshots with in-bounds positions and choices belonging to the configured network.

## Advance a simulation

Each `step()` completes one tick. Existing cars move at most one tile or boundary transition, and following cars can use tiles vacated in that same tick. At the end, each entrance admits at most one pending car onto its free first tile. Newly admitted cars first move on a later tick. A light gates departure from its tile, including an exit or connector entry; it permits arrival onto a red tile.

Demand uses exact decimal accumulation: rate `0.5` generates arrivals on ticks two, four, six; rate `0.1` first generates an arrival on tick ten. Blocked arrivals remain pending without a capacity or discard policy. Tick and pending counts use `Long`; an unrepresentable tick, due count, or pending total throws `ArithmeticException` before changing the run. A later admission cannot offset an overflowing intermediate total.

Lights independently use their initial color for its full phase duration. A completed snapshot reports the colors used for that tick, and `snapshot()` does not advance timers or randomness. Earlier snapshots remain immutable. Construct another `Simulation(config)` to restart; equal ordered configurations and seeds produce the same car identities, choices, and snapshot sequences. Serialize calls to each instance.

Cars choose once per intersection visit, retaining the seeded exact weighted choice while waiting and crossing. Entry reserves declared crossing resources and the first outgoing tile until the car finishes its connector. Conflicting entrants wait through the owner's clearance tick. Rotating approach priority skips infeasible requests and allows compatible crossings together. Conservative reservations and dependency-cycle rejection can reduce throughput or sustain gridlock; loops are supported without a global recovery guarantee.

Custom scene creation and XML scene files belong to a future release. File/console input adapters, UI, analytics, and export remain deferred.

## Verify

```sh
./mvnw clean verify
```

Verification runs domain validation/ownership, headless simulation, and template contract/traversal scenarios under JDK 21. The template API is documented in [the scene templates specification](docs/specs/scene-templates.md); engine behavior in [the headless simulation specification](docs/specs/headless-simulation.md); and domain contracts in [the basic entities specification](docs/specs/basic-domain-entities.md).
