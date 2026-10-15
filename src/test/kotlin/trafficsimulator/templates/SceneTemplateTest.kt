package trafficsimulator.templates

import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import trafficsimulator.domain.*

class SceneTemplateTest {
    @Test
    fun `catalog has the fixed order and display names`() {
        assertEquals(listOf(SceneTemplate.SINGLE_INTERSECTION, SceneTemplate.TWO_INTERSECTIONS), SceneTemplate.entries)
        assertEquals(
            listOf("Single four-way intersection", "Two four-way intersections"),
            SceneTemplate.entries.map { it.displayName },
        )
    }

    @Test
    fun `defaults have fixed topology boundaries ordering and settings`() {
        for (template in SceneTemplate.entries) {
            val config = template.create()
            val network = config.network
            val roadIds = if (template == SceneTemplate.SINGLE_INTERSECTION) {
                listOf("north", "east", "south", "west")
            } else {
                listOf("west-north", "west-south", "west-west", "connection", "east-north", "east-east", "east-south")
            }
            val boundaryIds = roadIds.filter { it != "connection" }
            val junctions = expectedJunctions(template)
            assertEquals(roadIds, network.roads.map { it.id })
            assertEquals(junctions.keys.toList(), network.intersections.map { it.id })
            assertEquals(roadIds.size * 2, network.roads.flatMap { listOf(it.forwardLane, it.backwardLane) }.size)
            for (road in network.roads) {
                assertEquals(10, road.forwardLane.length)
                assertEquals(10, road.backwardLane.length)
                val lanes = if (road.id == "connection") listOf("connection-eastbound", "connection-westbound")
                    else listOf("${road.id}-in", "${road.id}-out")
                assertEquals(lanes, listOf(road.forwardLane.id, road.backwardLane.id))
            }
            assertEquals(boundaryIds.map { Entrance("$it-entry", "$it-in", BigDecimal("0.1")) }, network.entrances)
            assertEquals(boundaryIds.map { Exit("$it-exit", "$it-out") }, network.exits)
            assertEquals(junctions.size * 12, network.connectorLanes.size)
            assertEquals(junctions.size * 12, network.intersections.sumOf { it.movements.size })
            assertEquals(42L, config.randomSeed)
            assertEquals(
                junctions.flatMap { (junctionId, arms) ->
                    arms.map { (arm, lanes) -> TrafficLight("$junctionId-$arm-light", Tile(lanes.first, 9), 20, 20, LightColor.RED) }
                },
                config.trafficLights,
            )
            for (intersection in network.intersections) {
                val incoming = junctions.getValue(intersection.id).values.map { it.first }
                assertEquals(incoming, intersection.turningWeights.keys.toList())
                for (approach in incoming) {
                    val choices = intersection.movements.filter { it.incomingLaneId == approach }
                    assertEquals(choices.map { it.id }, intersection.turningWeights.getValue(approach).keys.toList())
                    assertEquals(
                        listOf(BigDecimal("0.50"), BigDecimal("0.25"), BigDecimal("0.25")),
                        intersection.turningWeights.getValue(approach).values.toList(),
                    )
                }
            }
        }
    }

    @Test
    fun `each junction has exact legal routes lengths and crossing resources`() {
        for (template in SceneTemplate.entries) {
            val network = template.create().network
            for (intersection in network.intersections) {
                val arms = expectedJunctions(template).getValue(intersection.id)
                assertEquals(expectedRoutes.map { "${intersection.id}-${it.from}-to-${it.to}" }, intersection.movements.map { it.id })
                for ((expected, movement) in expectedRoutes.zip(intersection.movements)) {
                    assertEquals(arms.getValue(expected.from).first, movement.incomingLaneId)
                    assertEquals(arms.getValue(expected.to).second, movement.outgoingLaneId)
                    assertEquals(expected.turn, movement.turn)
                    assertNotEquals(expected.from, expected.to)
                    assertEquals(expected.resources, movement.crossingResourceIds)
                    assertEquals("${movement.id}-path", movement.connectorLaneId)
                    val expectedLength = when (expected.turn) {
                        Turn.STRAIGHT -> 2
                        Turn.LEFT -> 3
                        Turn.RIGHT -> 1
                    }
                    assertEquals(expectedLength, network.lane(movement.connectorLaneId).length)
                }
                val straights = intersection.movements.filter { it.turn == Turn.STRAIGHT }
                for (first in straights.indices) {
                    for (second in first + 1 until straights.size) {
                        val shared = straights[first].crossingResourceIds.intersect(straights[second].crossingResourceIds)
                        assertEquals(second - first == 2, shared.isEmpty(), "Opposing straights must be compatible")
                    }
                }
                for (merging in intersection.movements.groupBy { it.outgoingLaneId }.values) {
                    for (first in merging.indices) {
                        for (second in first + 1 until merging.size) {
                            assertTrue(merging[first].crossingResourceIds.intersect(merging[second].crossingResourceIds).isNotEmpty())
                        }
                    }
                }
            }
            assertEquals(network.intersections.flatMap { it.movements }.map { it.connectorLaneId }, network.connectorLanes.map { it.id })
        }
    }

    @Test
    fun `connection has reciprocal junction endpoints and no boundary endpoints`() {
        val network = SceneTemplate.TWO_INTERSECTIONS.create().network
        val west = network.intersections[0]
        val east = network.intersections[1]
        assertEquals("connection-eastbound", network.movement("west-junction-west-to-east").outgoingLaneId)
        assertEquals("connection-eastbound", network.movement("east-junction-west-to-east").incomingLaneId)
        assertEquals(setOf("connection-eastbound"), west.movements.filter { it.outgoingLaneId.startsWith("connection") }.map { it.outgoingLaneId }.toSet())
        assertEquals(setOf("connection-westbound"), west.movements.filter { it.incomingLaneId.startsWith("connection") }.map { it.incomingLaneId }.toSet())
        assertEquals(setOf("connection-westbound"), east.movements.filter { it.outgoingLaneId.startsWith("connection") }.map { it.outgoingLaneId }.toSet())
        assertEquals(setOf("connection-eastbound"), east.movements.filter { it.incomingLaneId.startsWith("connection") }.map { it.incomingLaneId }.toSet())
        assertTrue(network.entrances.none { it.laneId.startsWith("connection") })
        assertTrue(network.exits.none { it.laneId.startsWith("connection") })
    }

    @Test
    fun `sparse overrides preserve exact entrance rates and unrelated defaults`() {
        for (template in SceneTemplate.entries) {
            val defaults = template.create()
            val entrances = defaults.network.entrances
            val overrides = mapOf(entrances[0].id to BigDecimal.ZERO, entrances[1].id to BigDecimal("2.7500"))
            val config = template.create(entranceRates = overrides)
            assertEquals(entrances.map { it.copy(arrivalRate = overrides[it.id] ?: it.arrivalRate) }, config.network.entrances)
            assertEquals(
                configurationFields(defaults).drop(1),
                configurationFields(config).drop(1),
            )
        }
    }

    @Test
    fun `approach overrides replace complete distributions including connection approaches`() {
        for (template in SceneTemplate.entries) {
            val defaults = template.create()
            val approach = if (template == SceneTemplate.SINGLE_INTERSECTION) "north-in" else "connection-eastbound"
            val overrides = mapOf(Turn.RIGHT to BigDecimal.ZERO, Turn.LEFT to BigDecimal("0.2500"), Turn.STRAIGHT to BigDecimal("0.7500"))
            val config = template.create(turningWeights = mapOf(approach to overrides))
            for ((before, after) in defaults.network.intersections.zip(config.network.intersections)) {
                for ((lane, weights) in after.turningWeights) {
                    if (lane == approach) {
                        assertEquals(after.movements.filter { it.incomingLaneId == lane }.associate { it.id to overrides.getValue(it.turn) }, weights)
                    } else assertEquals(before.turningWeights.getValue(lane), weights)
                }
            }
            assertEquals(defaults.network.roads, config.network.roads)
            assertEquals(defaults.network.entrances, config.network.entrances)
            assertEquals(defaults.trafficLights, config.trafficLights)
            assertEquals(defaults.randomSeed, config.randomSeed)
        }
    }

    @Test
    fun `unknown and other-template override keys are rejected clearly`() {
        for (template in SceneTemplate.entries) {
            val otherEntrance = if (template == SceneTemplate.SINGLE_INTERSECTION) "west-north-entry" else "north-entry"
            val otherApproach = if (template == SceneTemplate.SINGLE_INTERSECTION) "connection-westbound" else "north-in"
            for (id in listOf("missing-entry", otherEntrance)) {
                rejected(id) { template.create(entranceRates = mapOf(id to BigDecimal.ZERO)) }
            }
            for (id in listOf("missing-approach", otherApproach)) {
                rejected(id) { template.create(turningWeights = mapOf(id to forced(Turn.STRAIGHT))) }
            }
        }
    }

    @Test
    fun `invalid rates and turn distributions fail during construction`() {
        for (template in SceneTemplate.entries) {
            val defaults = template.create()
            val entrance = defaults.network.entrances.first().id
            val approach = defaults.network.intersections.first().movements.first().incomingLaneId
            rejected(entrance) { template.create(entranceRates = mapOf(entrance to BigDecimal("-0.1"))) }
            val invalid = listOf(
                emptyMap(),
                mapOf(Turn.STRAIGHT to BigDecimal.ONE, Turn.LEFT to BigDecimal.ZERO),
                mapOf(Turn.STRAIGHT to BigDecimal("1.1"), Turn.LEFT to BigDecimal("-0.1"), Turn.RIGHT to BigDecimal.ZERO),
                Turn.entries.associateWith { BigDecimal.ZERO },
                Turn.entries.associateWith { BigDecimal("0.3") },
                Turn.entries.associateWith { BigDecimal("0.4") },
            )
            for (weights in invalid) {
                rejected(approach) { template.create(turningWeights = mapOf(approach to weights)) }
            }
        }
    }

    @Test
    fun `every seed is passed through unchanged`() {
        for (template in SceneTemplate.entries) {
            for (seed in listOf(0L, -7L, 7L, Long.MIN_VALUE, Long.MAX_VALUE)) {
                assertEquals(seed, template.create(randomSeed = seed).randomSeed)
            }
        }
    }

    @Test
    fun `caller map order does not affect ordered configuration`() {
        for (template in SceneTemplate.entries) {
            val defaults = template.create()
            val rates = defaults.network.entrances.associate { it.id to BigDecimal("0.2") }
            val weights = defaults.network.intersections.flatMap { it.turningWeights.keys }.associateWith {
                mapOf(Turn.STRAIGHT to BigDecimal("0.6"), Turn.LEFT to BigDecimal("0.3"), Turn.RIGHT to BigDecimal("0.1"))
            }
            val reversedWeights = weights.entries.reversed().associate { (approach, distribution) ->
                approach to distribution.entries.reversed().associate { it.toPair() }
            }
            assertEquals(
                configurationFields(template.create(rates, weights, 7L)),
                configurationFields(template.create(rates.entries.reversed().associate { it.toPair() }, reversedWeights, 7L)),
            )
        }
    }

    @Test
    fun `factories own supplied maps and independent configurations`() {
        for (template in SceneTemplate.entries) {
            val defaults = template.create()
            val before = configurationFields(defaults)
            val entrance = defaults.network.entrances.first().id
            val approach = defaults.network.intersections.first().movements.first().incomingLaneId
            val rates = mutableMapOf(entrance to BigDecimal("0.7"))
            val distribution = forced(Turn.LEFT).toMutableMap()
            val weights = mutableMapOf<String, Map<Turn, BigDecimal>>(approach to distribution)
            val config = template.create(rates, weights, -10L)
            val captured = configurationFields(config)
            rates.clear()
            distribution.clear()
            weights.clear()
            assertEquals(captured, configurationFields(config))
            assertEquals(before, configurationFields(defaults))
            assertEquals(before, configurationFields(template.create()))
            val network = config.network
            for (list in listOf(network.roads, network.connectorLanes, network.intersections, network.entrances, network.exits, config.trafficLights)) {
                assertThrows(UnsupportedOperationException::class.java) { (list as MutableList).clear() }
            }
            for (intersection in network.intersections) {
                assertThrows(UnsupportedOperationException::class.java) { (intersection.movements as MutableList).clear() }
                assertThrows(UnsupportedOperationException::class.java) { (intersection.turningWeights as MutableMap).clear() }
                for (distribution in intersection.turningWeights.values) {
                    assertThrows(UnsupportedOperationException::class.java) { (distribution as MutableMap).clear() }
                }
                for (movement in intersection.movements) {
                    assertThrows(UnsupportedOperationException::class.java) { (movement.crossingResourceIds as MutableSet).clear() }
                }
            }
        }
    }

    private fun rejected(key: String, action: () -> Unit) {
        val failure = assertThrows(IllegalArgumentException::class.java, action)
        assertTrue(failure.message.orEmpty().contains(key), "Expected offending key '$key' in '${failure.message}'")
    }
}

internal fun forced(turn: Turn): Map<Turn, BigDecimal> =
    Turn.entries.associateWith { if (it == turn) BigDecimal.ONE else BigDecimal.ZERO }

internal fun configurationFields(config: SimulationConfig): List<Any> = listOf(
    config.network.entrances,
    config.network.roads,
    config.network.connectorLanes,
    config.network.intersections.map { intersection ->
        listOf(
            intersection.id,
            intersection.movements.map { listOf(it.id, it.incomingLaneId, it.outgoingLaneId, it.connectorLaneId, it.turn, it.crossingResourceIds.toList()) },
            intersection.turningWeights.map { (approach, weights) -> approach to weights.toList() },
        )
    },
    config.network.exits,
    config.trafficLights,
    config.randomSeed,
)

private fun expectedJunctions(template: SceneTemplate): Map<String, Map<String, Pair<String, String>>> =
    if (template == SceneTemplate.SINGLE_INTERSECTION) {
        mapOf("junction" to listOf("north", "east", "south", "west").associateWith { "$it-in" to "$it-out" })
    } else {
        linkedMapOf(
            "west-junction" to linkedMapOf(
                "north" to ("west-north-in" to "west-north-out"),
                "east" to ("connection-westbound" to "connection-eastbound"),
                "south" to ("west-south-in" to "west-south-out"),
                "west" to ("west-west-in" to "west-west-out"),
            ),
            "east-junction" to linkedMapOf(
                "north" to ("east-north-in" to "east-north-out"),
                "east" to ("east-east-in" to "east-east-out"),
                "south" to ("east-south-in" to "east-south-out"),
                "west" to ("connection-eastbound" to "connection-westbound"),
            ),
        )
    }

private data class ExpectedRoute(val from: String, val turn: Turn, val to: String, val resources: Set<String>)

private val expectedRoutes = listOf(
    ExpectedRoute("north", Turn.STRAIGHT, "south", setOf("north-west", "south-west")),
    ExpectedRoute("north", Turn.LEFT, "east", setOf("north-west", "south-west", "south-east")),
    ExpectedRoute("north", Turn.RIGHT, "west", setOf("north-west")),
    ExpectedRoute("east", Turn.STRAIGHT, "west", setOf("north-east", "north-west")),
    ExpectedRoute("east", Turn.LEFT, "south", setOf("north-east", "north-west", "south-west")),
    ExpectedRoute("east", Turn.RIGHT, "north", setOf("north-east")),
    ExpectedRoute("south", Turn.STRAIGHT, "north", setOf("south-east", "north-east")),
    ExpectedRoute("south", Turn.LEFT, "west", setOf("south-east", "north-east", "north-west")),
    ExpectedRoute("south", Turn.RIGHT, "east", setOf("south-east")),
    ExpectedRoute("west", Turn.STRAIGHT, "east", setOf("south-west", "south-east")),
    ExpectedRoute("west", Turn.LEFT, "north", setOf("south-west", "south-east", "north-east")),
    ExpectedRoute("west", Turn.RIGHT, "south", setOf("south-west")),
)
