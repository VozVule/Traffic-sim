package trafficsimulator.templates

import java.math.BigDecimal
import trafficsimulator.domain.*

enum class SceneTemplate(val displayName: String) {
    SINGLE_INTERSECTION("Single four-way intersection"),
    TWO_INTERSECTIONS("Two four-way intersections");

    fun create(
        entranceRates: Map<String, BigDecimal> = emptyMap(),
        turningWeights: Map<String, Map<Turn, BigDecimal>> = emptyMap(),
        randomSeed: Long = 42L,
    ): SimulationConfig = when (this) {
        SINGLE_INTERSECTION -> singleIntersection(entranceRates, turningWeights, randomSeed)
        TWO_INTERSECTIONS -> twoIntersections(entranceRates, turningWeights, randomSeed)
    }
}

private data class Approach(val incomingLaneId: String, val outgoingLaneId: String)

private data class Junction(val id: String, val approaches: Map<String, Approach>)

private data class Route(val from: String, val turn: Turn, val to: String, val resources: Set<String>)

private val routes = listOf(
    Route("north", Turn.STRAIGHT, "south", setOf("north-west", "south-west")),
    Route("north", Turn.LEFT, "east", setOf("north-west", "south-west", "south-east")),
    Route("north", Turn.RIGHT, "west", setOf("north-west")),
    Route("east", Turn.STRAIGHT, "west", setOf("north-east", "north-west")),
    Route("east", Turn.LEFT, "south", setOf("north-east", "north-west", "south-west")),
    Route("east", Turn.RIGHT, "north", setOf("north-east")),
    Route("south", Turn.STRAIGHT, "north", setOf("south-east", "north-east")),
    Route("south", Turn.LEFT, "west", setOf("south-east", "north-east", "north-west")),
    Route("south", Turn.RIGHT, "east", setOf("south-east")),
    Route("west", Turn.STRAIGHT, "east", setOf("south-west", "south-east")),
    Route("west", Turn.LEFT, "north", setOf("south-west", "south-east", "north-east")),
    Route("west", Turn.RIGHT, "south", setOf("south-west")),
)

private val defaultWeights = mapOf(
    Turn.STRAIGHT to BigDecimal("0.50"),
    Turn.LEFT to BigDecimal("0.25"),
    Turn.RIGHT to BigDecimal("0.25"),
)

private fun boundaryRoad(id: String): Road = Road(id, Lane("$id-in", 10), Lane("$id-out", 10))

private fun Road.approach(): Approach = Approach(forwardLane.id, backwardLane.id)

private fun singleIntersection(
    entranceRates: Map<String, BigDecimal>,
    turningWeights: Map<String, Map<Turn, BigDecimal>>,
    randomSeed: Long,
): SimulationConfig {
    val roads = listOf("north", "east", "south", "west").map(::boundaryRoad)
    val junction = Junction("junction", roads.associate { it.id to it.approach() })
    return configuration(roads, roads, listOf(junction), entranceRates, turningWeights, randomSeed)
}

private fun twoIntersections(
    entranceRates: Map<String, BigDecimal>,
    turningWeights: Map<String, Map<Turn, BigDecimal>>,
    randomSeed: Long,
): SimulationConfig {
    val westNorth = boundaryRoad("west-north")
    val westSouth = boundaryRoad("west-south")
    val westWest = boundaryRoad("west-west")
    val connection = Road("connection", Lane("connection-eastbound", 10), Lane("connection-westbound", 10))
    val eastNorth = boundaryRoad("east-north")
    val eastEast = boundaryRoad("east-east")
    val eastSouth = boundaryRoad("east-south")
    val roads = listOf(westNorth, westSouth, westWest, connection, eastNorth, eastEast, eastSouth)
    val junctions = listOf(
        Junction("west-junction", linkedMapOf(
            "north" to westNorth.approach(),
            "east" to Approach(connection.backwardLane.id, connection.forwardLane.id),
            "south" to westSouth.approach(),
            "west" to westWest.approach(),
        )),
        Junction("east-junction", linkedMapOf(
            "north" to eastNorth.approach(),
            "east" to eastEast.approach(),
            "south" to eastSouth.approach(),
            "west" to connection.approach(),
        )),
    )
    return configuration(roads, roads.filter { it != connection }, junctions, entranceRates, turningWeights, randomSeed)
}

private fun configuration(
    roads: List<Road>,
    boundaryRoads: List<Road>,
    junctions: List<Junction>,
    entranceRates: Map<String, BigDecimal>,
    turningWeights: Map<String, Map<Turn, BigDecimal>>,
    randomSeed: Long,
): SimulationConfig {
    val entranceIds = boundaryRoads.map { "${it.id}-entry" }.toSet()
    entranceRates.keys.forEach { require(it in entranceIds) { "Unknown entrance '$it'" } }
    val approachIds = junctions.flatMap { it.approaches.values }.map { it.incomingLaneId }.toSet()
    for ((approachId, weights) in turningWeights) {
        require(approachId in approachIds) { "Unknown incoming approach '$approachId'" }
        require(weights.keys == Turn.entries.toSet()) {
            "Turning weights for approach '$approachId' must contain exactly STRAIGHT, LEFT, and RIGHT"
        }
        require(weights.values.all { it.signum() >= 0 }) {
            "Turning weights for approach '$approachId' must be nonnegative"
        }
    }
    val intersections = junctions.map { fourWayJunction(it, turningWeights) }
    val connectors = intersections.flatMap { it.movements }.map { movement ->
        val length = when (movement.turn) {
            Turn.RIGHT -> 1
            Turn.STRAIGHT -> 2
            Turn.LEFT -> 3
        }
        Lane(movement.connectorLaneId, length)
    }
    val network = RoadNetwork(
        roads = roads,
        connectorLanes = connectors,
        intersections = intersections,
        entrances = boundaryRoads.map {
            val id = "${it.id}-entry"
            Entrance(id, it.forwardLane.id, entranceRates[id] ?: BigDecimal("0.1"))
        },
        exits = boundaryRoads.map { Exit("${it.id}-exit", it.backwardLane.id) },
    )
    val lights = junctions.flatMap { junction ->
        junction.approaches.map { (arm, approach) ->
            TrafficLight("${junction.id}-$arm-light", network.tile(approach.incomingLaneId, 9), 20, 20, LightColor.RED)
        }
    }
    return SimulationConfig(network, lights, randomSeed)
}

private fun fourWayJunction(
    junction: Junction,
    turningWeights: Map<String, Map<Turn, BigDecimal>>,
): Intersection {
    val movements = routes.map { route ->
        val id = "${junction.id}-${route.from}-to-${route.to}"
        Movement(
            id,
            junction.approaches.getValue(route.from).incomingLaneId,
            junction.approaches.getValue(route.to).outgoingLaneId,
            "$id-path",
            route.turn,
            route.resources,
        )
    }
    val weights = movements.groupBy { it.incomingLaneId }.mapValues { (approachId, choices) ->
        val distribution = turningWeights[approachId] ?: defaultWeights
        choices.associate { it.id to distribution.getValue(it.turn) }
    }
    return Intersection(junction.id, movements, weights)
}
