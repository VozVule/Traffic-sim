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

## Construct a scene

Domain types live in `trafficsimulator.domain`. A boundary road needs an entrance at the start and an exit after the end of each directed lane:

```kotlin
import java.math.BigDecimal
import trafficsimulator.domain.*
import trafficsimulator.simulation.Simulation

val road = Road("main", Lane("main-forward", 10), Lane("main-backward", 10))
val network = RoadNetwork(
    roads = listOf(road),
    entrances = listOf(
        Entrance("west-entry", road.forwardLane.id, BigDecimal("0.5")),
        Entrance("east-entry", road.backwardLane.id, BigDecimal("1.25")),
    ),
    exits = listOf(
        Exit("east-exit", road.forwardLane.id),
        Exit("west-exit", road.backwardLane.id),
    ),
)
val light = TrafficLight("west-light", network.tile("main-forward", 4), 20, 15)
val config = SimulationConfig(network, listOf(light), randomSeed = 42L)
val simulation = Simulation(config)
val initial = simulation.snapshot() // Tick zero, no cars or pending demand.
repeat(40) {
    val completed = simulation.step()
    println("Tick ${completed.tick}: ${completed.cars.size} active cars")
}
```

Tile indices start at zero, so this road's final tiles have index nine. The same index in opposite lanes identifies distinct tiles. Entrances enter index zero; exits represent departure after the lane's final tile. Lights may occupy any road tile, default to red, and have separate positive red and green durations.

Construct rates and turning weights from decimal text with `BigDecimal`. Rates are nonnegative cars per tick and may exceed one. Each intersection's weights map incoming lane IDs to movement IDs and exact normalized fractions totaling one, including zero-weight legal movements. A movement connects incoming and outgoing lanes on different roads through its own connector lane. Declare nonempty crossing resource sets; merging paths must share a resource. Other physical conflicts and turn labels are supplied by the scene author because this model has no geometry.

Every road lane needs one starting endpoint (entrance or intersection) and one ending endpoint (exit or intersection), with reciprocal endpoints for its opposing lane. Closed loops and disconnected valid components are allowed. Collections are copied on construction and exposed as unmodifiable views. The engine emits snapshots containing in-bounds positions and choices belonging to the configured network.

## Advance a simulation

Each `step()` completes one tick. Existing cars move at most one tile or boundary transition, and following cars can use tiles vacated in that same tick. At the end, each entrance admits at most one pending car onto its free first tile. Newly admitted cars first move on a later tick. A light gates departure from its tile, including an exit or connector entry; it permits arrival onto a red tile.

Demand uses exact decimal accumulation: rate `0.5` generates arrivals on ticks two, four, six; rate `0.1` first generates an arrival on tick ten. Blocked arrivals remain pending without a capacity or discard policy. Tick and pending counts use `Long`; an unrepresentable tick, due count, or pending total throws `ArithmeticException` before changing the run. A later admission cannot offset an overflowing intermediate total.

Lights independently use their initial color for its full phase duration. A completed snapshot reports the colors used for that tick, and `snapshot()` does not advance timers or randomness. Earlier snapshots remain immutable. Construct another `Simulation(config)` to restart; equal ordered configurations and seeds produce the same car identities, choices, and snapshot sequences. Serialize calls to each instance.

Cars choose once per intersection visit, retaining the seeded exact weighted choice while waiting and crossing. Entry reserves declared crossing resources and the first outgoing tile until the car finishes its connector. Conflicting entrants wait through the owner's clearance tick. Rotating approach priority skips infeasible requests and allows compatible crossings together. Conservative reservations and dependency-cycle rejection can reduce throughput or sustain gridlock; loops are supported without a global recovery guarantee.

Construct scenes directly with Kotlin domain types. Public scene templates, file/console input adapters, UI, analytics, and export remain deferred.

## Verify

```sh
./mvnw clean verify
```

Verification runs the domain validation/ownership suite and headless simulation scenarios under JDK 21. The implementation scope is documented in [the headless simulation specification](docs/specs/headless-simulation.md); domain contracts are documented in [the basic entities specification](docs/specs/basic-domain-entities.md).
