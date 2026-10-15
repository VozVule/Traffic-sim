package trafficsimulator.templates

import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import trafficsimulator.domain.*
import trafficsimulator.simulation.Simulation

class SceneTemplateSimulationTest {
    @TestFactory
    fun `all single junction turns reach the expected boundary`(): List<DynamicTest> {
        val destinations = linkedMapOf(
            "north" to listOf("south", "east", "west"),
            "east" to listOf("west", "south", "north"),
            "south" to listOf("north", "west", "east"),
            "west" to listOf("east", "north", "south"),
        )
        return destinations.flatMap { (from, targets) ->
            Turn.entries.zip(targets).map { (turn, to) ->
                DynamicTest.dynamicTest("$from $turn to $to") {
                    val config = SceneTemplate.SINGLE_INTERSECTION.create(
                        entranceRates = isolatedRates(SceneTemplate.SINGLE_INTERSECTION, "$from-entry"),
                        turningWeights = mapOf("$from-in" to forced(turn)),
                    )
                    val movement = "junction-$from-to-$to"
                    assertJourney(config, listOf("$from-in", "$movement-path", "$to-out"), listOf(movement))
                }
            }
        }
    }

    @TestFactory
    fun `cars choose at both junctions and traverse the eastbound connection`(): List<DynamicTest> =
        Turn.entries.zip(listOf("east", "north", "south")).map { (secondTurn, destination) ->
            DynamicTest.dynamicTest("west straight then $secondTurn to east-$destination") {
                val config = SceneTemplate.TWO_INTERSECTIONS.create(
                    entranceRates = isolatedRates(SceneTemplate.TWO_INTERSECTIONS, "west-west-entry"),
                    turningWeights = mapOf(
                        "west-west-in" to forced(Turn.STRAIGHT),
                        "connection-eastbound" to forced(secondTurn),
                    ),
                )
                val first = "west-junction-west-to-east"
                val second = "east-junction-west-to-$destination"
                assertJourney(
                    config,
                    listOf("west-west-in", "$first-path", "connection-eastbound", "$second-path", "east-$destination-out"),
                    listOf(first, second),
                )
            }
        }

    @Test
    fun `both default templates advance with valid nonoverlapping positions`() {
        for (template in SceneTemplate.entries) {
            val config = template.create()
            val simulation = Simulation(config)
            var observedCars = false
            repeat(300) {
                val snapshot = simulation.step()
                assertEquals(it + 1L, snapshot.tick)
                assertValid(config, snapshot)
                observedCars = observedCars || snapshot.cars.isNotEmpty()
            }
            assertTrue(observedCars)
        }
    }

    @Test
    fun `ordered seeded runs repeat despite caller map order and preserve earlier observations`() {
        for (template in SceneTemplate.entries) {
            val defaults = template.create()
            val rates = defaults.network.entrances.mapIndexed { index, entrance ->
                entrance.id to BigDecimal(listOf("0.1", "0.17", "0.025")[index % 3])
            }.toMap()
            val weights = defaults.network.intersections.flatMap { it.turningWeights.keys }.associateWith {
                linkedMapOf(Turn.STRAIGHT to BigDecimal("0.65"), Turn.LEFT to BigDecimal("0.20"), Turn.RIGHT to BigDecimal("0.15"))
            }
            val reversedRates = rates.entries.reversed().associate { it.toPair() }
            val reversedWeights = weights.entries.reversed().associate { (approach, distribution) ->
                approach to distribution.entries.reversed().associate { it.toPair() }
            }
            val config = template.create(rates, weights, -7L)
            val configBefore = configurationFields(config)
            val first = Simulation(config)
            val second = Simulation(template.create(reversedRates, reversedWeights, -7L))
            val retained = mutableListOf<Pair<SimulationSnapshot, List<Any>>>()
            retained.add(first.snapshot() to snapshotFields(first.snapshot()))
            assertEquals(snapshotFields(first.snapshot()), snapshotFields(second.snapshot()))
            repeat(300) {
                val snapshot = first.step()
                val repeated = second.step()
                assertEquals(snapshotFields(snapshot), snapshotFields(repeated), "$template tick ${snapshot.tick}")
                assertValid(config, snapshot)
                if (it == 49) retained.add(snapshot to snapshotFields(snapshot))
            }
            assertEquals(configBefore, configurationFields(config))
            for ((snapshot, before) in retained) assertEquals(before, snapshotFields(snapshot))
            assertEquals(snapshotFields(retained.first().first), snapshotFields(Simulation(config).snapshot()))
        }
    }

    @Test
    fun `all zero entrance rates produce empty advancing runs`() {
        for (template in SceneTemplate.entries) {
            val rates = template.create().network.entrances.associate { it.id to BigDecimal.ZERO }
            val simulation = Simulation(template.create(entranceRates = rates))
            repeat(100) {
                val snapshot = simulation.step()
                assertEquals(it + 1L, snapshot.tick)
                assertTrue(snapshot.cars.isEmpty())
                assertTrue(snapshot.pendingEntranceCounts.values.all { it == 0L })
            }
        }
    }

    private fun isolatedRates(template: SceneTemplate, activeEntrance: String): Map<String, BigDecimal> =
        template.create().network.entrances.associate {
            it.id to if (it.id == activeEntrance) BigDecimal("0.05") else BigDecimal.ZERO
        }

    private fun assertJourney(config: SimulationConfig, expectedLanes: List<String>, expectedMovements: List<String>) {
        val simulation = Simulation(config)
        val snapshots = (1..140).map { simulation.step().also { assertValid(config, it) } }
        val firstCar = snapshots.first { it.cars.isNotEmpty() }.cars.first().id
        val observations = snapshots.mapNotNull { snapshot -> snapshot.cars.find { it.id == firstCar } }
        assertEquals(expectedLanes, observations.map { it.position.laneId }.distinct())
        assertEquals(expectedMovements, observations.mapNotNull { it.chosenMovementId }.distinct())
        assertEquals(Tile(expectedLanes.last(), 9), observations.last().position)
        assertTrue(config.network.exits.any { it.laneId == expectedLanes.last() })
        assertTrue(snapshots.last().cars.none { it.id == firstCar }, "First car must leave the scene")
    }

    private fun assertValid(config: SimulationConfig, snapshot: SimulationSnapshot) {
        assertEquals(snapshot.cars.size, snapshot.cars.map { it.position }.toSet().size)
        for (car in snapshot.cars) {
            assertEquals(car.position, config.network.tile(car.position.laneId, car.position.index))
            car.chosenMovementId?.let { config.network.movement(it) }
        }
    }

    private fun snapshotFields(snapshot: SimulationSnapshot): List<Any> =
        listOf(snapshot.tick, snapshot.cars.toList(), snapshot.lightColors.toList(), snapshot.pendingEntranceCounts.toList())
}
