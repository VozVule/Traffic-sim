package trafficsimulator.domain

import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue

internal fun decimal(value: String): BigDecimal = BigDecimal(value)

internal fun road(id: String, length: Int = 3): Road =
    Road(id, Lane("$id+", length), Lane("$id-", length))

internal fun movement(
    id: String,
    incoming: String,
    outgoing: String,
    connector: String = "$id-path",
    resources: Set<String> = setOf("crossing"),
): Movement = Movement(id, incoming, outgoing, connector, Turn.STRAIGHT, resources)

internal fun intersection(id: String, movements: List<Movement>): Intersection {
    val weights = movements.groupBy { it.incomingLaneId }.mapValues { (_, choices) ->
        val weight = BigDecimal.ONE.divide(BigDecimal(choices.size))
        choices.associate { it.id to weight }
    }
    return Intersection(id, movements, weights)
}

internal data class SceneParts(
    val roads: MutableList<Road>,
    val connectors: MutableList<Lane>,
    val intersections: MutableList<Intersection>,
    val entrances: MutableList<Entrance>,
    val exits: MutableList<Exit>,
) {
    fun network(): RoadNetwork = RoadNetwork(roads, connectors, intersections, entrances, exits)
}

internal fun sceneParts(roads: List<Road>, intersections: List<Intersection>): SceneParts {
    val movements = intersections.flatMap { it.movements }
    val starts = movements.map { it.outgoingLaneId }.toSet()
    val ends = movements.map { it.incomingLaneId }.toSet()
    val lanes = roads.flatMap { listOf(it.forwardLane, it.backwardLane) }
    return SceneParts(
        roads.toMutableList(),
        movements.map { Lane(it.connectorLaneId, 1) }.toMutableList(),
        intersections.toMutableList(),
        lanes.filter { it.id !in starts }.map { Entrance("enter-${it.id}", it.id, decimal("0.5")) }.toMutableList(),
        lanes.filter { it.id !in ends }.map { Exit("exit-${it.id}", it.id) }.toMutableList(),
    )
}

internal fun boundaryParts(): SceneParts = sceneParts(listOf(road("a")), emptyList())

internal fun junctionParts(): SceneParts = sceneParts(
    listOf(road("a"), road("b")),
    listOf(intersection("i", listOf(movement("ab", "a+", "b+"), movement("ba", "b-", "a-")))),
)

internal fun connectedParts(): SceneParts = sceneParts(
    listOf(road("a"), road("b"), road("c")),
    listOf(
        intersection("i", listOf(movement("ab", "a+", "b+"), movement("ba", "b-", "a-"))),
        intersection("j", listOf(movement("bc", "b+", "c+"), movement("cb", "c-", "b-"))),
    ),
)

internal fun loopParts(): SceneParts = sceneParts(
    listOf(road("a"), road("b")),
    listOf(
        intersection("i", listOf(movement("ab", "a+", "b+"), movement("ba", "b-", "a-"))),
        intersection("j", listOf(movement("ba2", "b+", "a+"), movement("ab2", "a-", "b-"))),
    ),
)

internal fun rejected(field: String, action: () -> Unit) {
    val error = assertThrows(IllegalArgumentException::class.java, action)
    assertTrue(error.message?.contains(field, ignoreCase = true) == true, "Expected '$field' in: ${error.message}")
}
