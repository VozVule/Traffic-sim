# Traffic simulator

Kotlin/JVM project using Maven. Immutable domain entities describe roads, intersections, demand, lights, and snapshots. Network construction validates topology before use. Simulation advancement remains future work; the console entry point currently prints a readiness message.

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
```

Tile indices start at zero, so this road's final tiles have index nine. The same index in opposite lanes identifies distinct tiles. Entrances enter index zero; exits represent departure after the lane's final tile. Lights may occupy any road tile, default to red, and have separate positive red and green durations.

Construct rates and turning weights from decimal text with `BigDecimal`. Rates are nonnegative cars per tick and may exceed one. Each intersection's weights map incoming lane IDs to movement IDs and exact normalized fractions totaling one, including zero-weight legal movements. A movement connects incoming and outgoing lanes on different roads through its own connector lane. Declare nonempty crossing resource sets; merging paths must share a resource. Other physical conflicts and turn labels are supplied by the scene author because this model has no geometry.

Every road lane needs one starting endpoint (entrance or intersection) and one ending endpoint (exit or intersection), with reciprocal endpoints for its opposing lane. Closed loops and disconnected valid components are allowed. Collections are copied on construction and exposed as unmodifiable views. Snapshots check local identities, occupancy, and counts; contextual references are the future engine's responsibility. These entities do not advance time, move cars, generate arrivals, choose turns, or acquire crossing resources.

## Verify

```sh
./mvnw clean verify
```

Verification compiles the domain model and runs the JUnit Jupiter validation and ownership tests under JDK 21. The implementation scope is documented in [the basic entities specification](docs/specs/basic-domain-entities.md).
