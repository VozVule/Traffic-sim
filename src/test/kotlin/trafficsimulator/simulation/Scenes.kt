package trafficsimulator.simulation

import java.math.BigDecimal
import trafficsimulator.domain.*

internal fun road(id: String, length: Int = 2, backwardLength: Int = length): Road =
    Road(id, Lane("$id+", length), Lane("$id-", backwardLength))

internal fun move(
    id: String,
    incoming: String,
    outgoing: String,
    resources: Set<String> = setOf("center"),
    turn: Turn = Turn.STRAIGHT,
): Movement = Movement(id, incoming, outgoing, "$id-path", turn, resources)

internal fun junction(
    id: String,
    movements: List<Movement>,
    weights: Map<String, String> = emptyMap(),
): Intersection = Intersection(id, movements, movements.groupBy { it.incomingLaneId }.mapValues { (_, choices) ->
    choices.associate { it.id to BigDecimal(weights[it.id] ?: if (it == choices.first()) "1" else "0") }
})

internal fun scene(
    roads: List<Road>,
    intersections: List<Intersection> = emptyList(),
    rates: Map<String, String> = emptyMap(),
    connectorLength: Int = 1,
    lights: List<TrafficLight> = emptyList(),
    seed: Long = 42,
): SimulationConfig {
    val movements = intersections.flatMap { it.movements }
    val starts = movements.map { it.outgoingLaneId }.toSet()
    val ends = movements.map { it.incomingLaneId }.toSet()
    val lanes = roads.flatMap { listOf(it.forwardLane, it.backwardLane) }
    val network = RoadNetwork(
        roads, movements.map { Lane(it.connectorLaneId, connectorLength) }, intersections,
        lanes.filter { it.id !in starts }.map { Entrance("entry-${it.id}", it.id, BigDecimal(rates[it.id] ?: "0")) },
        lanes.filter { it.id !in ends }.map { Exit("exit-${it.id}", it.id) },
    )
    return SimulationConfig(network, lights, seed)
}

internal fun boundary(
    length: Int = 2,
    rate: String = "1",
    backwardRate: String = "0",
    lights: List<TrafficLight> = emptyList(),
): SimulationConfig = scene(listOf(road("a", length)), rates = mapOf("a+" to rate, "a-" to backwardRate), lights = lights)

internal fun crossing(
    compatible: Boolean = false,
    length: Int = 1,
    connectorLength: Int = 1,
    rates: Map<String, String> = mapOf("a+" to "1", "b-" to "1"),
    lights: List<TrafficLight> = emptyList(),
): SimulationConfig = scene(
    listOf(road("a", length), road("b", length)),
    listOf(junction("i", listOf(
        move("ab", "a+", "b+", setOf("first")),
        move("ba", "b-", "a-", setOf(if (compatible) "second" else "first")),
    ))), rates, connectorLength, lights,
)

internal fun fourWay(
    weights: Map<String, String> = emptyMap(),
    rates: Map<String, String> = mapOf("n+" to "1", "s+" to "1", "e+" to "1", "w+" to "1"),
    lights: List<TrafficLight> = emptyList(),
): SimulationConfig {
    val routes = listOf(
        Triple("ns", Turn.STRAIGHT, setOf("nw", "sw")), Triple("nw", Turn.RIGHT, setOf("nw")),
        Triple("ne", Turn.LEFT, setOf("nw", "sw", "se")),
        Triple("sn", Turn.STRAIGHT, setOf("se", "ne")), Triple("se", Turn.RIGHT, setOf("se")),
        Triple("sw", Turn.LEFT, setOf("se", "ne", "nw")),
        Triple("ew", Turn.STRAIGHT, setOf("ne", "nw")), Triple("en", Turn.RIGHT, setOf("ne")),
        Triple("es", Turn.LEFT, setOf("ne", "nw", "sw")),
        Triple("we", Turn.STRAIGHT, setOf("sw", "se")), Triple("ws", Turn.RIGHT, setOf("sw")),
        Triple("wn", Turn.LEFT, setOf("sw", "se", "ne")),
    )
    val movements = routes.map { (id, turn, resources) -> move(id, "${id[0]}+", "${id[1]}-", resources, turn) }
    return scene(listOf("n", "s", "e", "w").map { road(it) }, listOf(junction("i", movements, weights)), rates, lights = lights)
}

internal fun connected(lights: List<TrafficLight> = emptyList(), rate: String = "1"): SimulationConfig = scene(
    listOf(road("a", 1), road("b", 2), road("c", 1)),
    listOf(
        junction("i", listOf(move("ab", "a+", "b+"), move("ba", "b-", "a-"))),
        junction("j", listOf(move("bc", "b+", "c+"), move("cb", "c-", "b-"))),
    ), mapOf("a+" to rate), lights = lights,
)

internal fun loop(rate: String = "0.1", closed: Boolean = false, allowExit: Boolean = true): SimulationConfig {
    val loopRoads = listOf(road("a", 3), road("b", 3))
    val first = mutableListOf(move("ba", "b+", "a+"), move("ab-reverse", "a-", "b-"))
    if (!closed) {
        first.add(move("xa", "x+", "a+"))
        first.add(move("bx", "b+", "x-"))
    }
    return scene(
        if (closed) loopRoads else loopRoads + road("x", 1),
        listOf(
            junction("i", first, when {
                closed -> emptyMap()
                allowExit -> mapOf("ba" to "0.5", "bx" to "0.5")
                else -> mapOf("ba" to "1", "bx" to "0")
            }),
            junction("j", listOf(move("ab", "a+", "b+"), move("ba-reverse", "b-", "a-"))),
        ), mapOf("x+" to rate), seed = 6,
    )
}

internal fun fields(snapshot: SimulationSnapshot): List<Any> =
    listOf(snapshot.tick, snapshot.cars, snapshot.lightColors, snapshot.pendingEntranceCounts)

internal fun Simulation.steps(count: Int): List<SimulationSnapshot> = (1..count).map { step() }
