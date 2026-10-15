package trafficsimulator.simulation

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import trafficsimulator.domain.*

class NetworkTest {
    @Test
    fun `connected intersections queue under red then choose anew and exit`() {
        val config = connected(listOf(TrafficLight("downstream", Tile("b+", 1), 20, 20)))
        val snapshots = Simulation(config).steps(35)
        val id = snapshots.first().cars.single().id
        val path = snapshots.mapNotNull { s -> s.cars.find { it.id == id } }
        assertTrue(path.any { it.chosenMovementId == "ab" })
        assertTrue(path.any { it.position.laneId == "b+" && it.chosenMovementId == null })
        assertTrue(path.any { it.position == Tile("b+", 1) && it.chosenMovementId == "bc" })
        assertTrue(path.any { it.position.laneId == "bc-path" })
        assertTrue(path.any { it.position.laneId == "c+" && it.chosenMovementId == null })
        assertTrue(snapshots.last().cars.none { it.id == id })
        assertTrue(snapshots[19].pendingEntranceCounts.getValue("entry-a+") > 0)
        val admitted = mutableSetOf<String>()
        val departed = mutableSetOf<String>()
        var previous = emptySet<String>()
        for (snapshot in snapshots) {
            val current = snapshot.cars.map { it.id }.toSet()
            admitted.addAll(current)
            departed.addAll(previous - current)
            assertEquals(snapshot.tick, snapshot.pendingEntranceCounts.getValue("entry-a+") + current.size + departed.size)
            previous = current
            assertContext(config, snapshot)
        }
    }

    @Test
    fun `boundary fed loop revisits and selects a fresh route on a later visit`() {
        val config = loop()
        val snapshots = Simulation(config).steps(300)
        val choicesByCar = snapshots.flatMap { it.cars }.groupBy { it.id }
        assertTrue(choicesByCar.values.any { observations ->
            val choices = observations.mapNotNull { it.chosenMovementId }
            "ba" in choices && "bx" in choices
        }, "A car must continue around the loop, then choose the boundary exit on a later visit")
        snapshots.forEach { assertContext(config, it) }
    }

    @Test
    fun `congested loops terminate safely and closed components remain empty`() {
        val config = loop("2", allowExit = false)
        val snapshots = Simulation(config).steps(500)
        snapshots.forEach { assertContext(config, it) }
        assertTrue(snapshots.last().pendingEntranceCounts.getValue("entry-x+") > 0)
        val stopped = snapshots.last().cars
        assertTrue(snapshots.takeLast(20).all { it.cars == stopped })
        assertTrue(stopped.none { it.position.laneId.endsWith("path") })
        assertEquals(6, stopped.count { it.position.laneId == "a+" || it.position.laneId == "b+" })
        val closed = Simulation(loop(closed = true))
        assertTrue(closed.steps(50).all { it.cars.isEmpty() })
    }

    @Test
    fun `representative traffic preserves one transition speed and declared crossing safety`() {
        for (config in listOf(fourWay(mapOf("ns" to "0.2", "nw" to "0.3", "ne" to "0.5")), connected(), loop())) {
            var previous = emptyMap<String, Car>()
            for (snapshot in Simulation(config).steps(150)) {
                assertContext(config, snapshot)
                for (car in snapshot.cars) {
                    val prior = previous[car.id] ?: continue
                    if (prior.position == car.position) continue
                    val lane = config.network.lane(prior.position.laneId)
                    if (prior.position.index < lane.length - 1) {
                        assertEquals(lane.tile(prior.position.index + 1), car.position)
                    } else {
                        val movement = (prior.chosenMovementId ?: car.chosenMovementId)?.let(config.network::movement)
                        assertNotNull(movement)
                        assertEquals(0, car.position.index)
                        assertEquals(
                            if (prior.position.laneId == movement!!.connectorLaneId) movement.outgoingLaneId else movement.connectorLaneId,
                            car.position.laneId,
                        )
                    }
                }
                for (intersection in config.network.intersections) {
                    val active = snapshot.cars.mapNotNull { car ->
                        intersection.movements.find { it.connectorLaneId == car.position.laneId }
                    }
                    val resources = active.flatMap { it.crossingResourceIds }
                    assertEquals(resources.size, resources.toSet().size)
                    assertEquals(active.size, active.map { it.outgoingLaneId }.toSet().size)
                }
                previous = snapshot.cars.associateBy { it.id }
            }
        }
    }

    @Test
    fun `unequal opposing lengths and disconnected roads remain valid`() {
        val config = scene(listOf(road("a", 1, 5), road("b", 3, 2)), rates = mapOf("a+" to "1", "a-" to "1", "b+" to "0.5"))
        Simulation(config).steps(20).forEach { assertContext(config, it) }
    }

    private fun assertContext(config: SimulationConfig, snapshot: SimulationSnapshot) {
        assertEquals(snapshot.cars.size, snapshot.cars.map { it.position }.toSet().size)
        assertEquals(snapshot.cars.size, snapshot.cars.map { it.id }.toSet().size)
        val connectorIds = config.network.connectorLanes.map { it.id }.toSet()
        for (car in snapshot.cars) {
            assertEquals(car.position, config.network.tile(car.position.laneId, car.position.index))
            val movement = car.chosenMovementId?.let(config.network::movement)
            if (car.position.laneId in connectorIds) {
                assertNotNull(movement)
                assertEquals(car.position.laneId, movement!!.connectorLaneId)
            } else if (movement != null) {
                assertEquals(movement.incomingLaneId, car.position.laneId)
                assertEquals(config.network.lane(car.position.laneId).length - 1, car.position.index)
            }
        }
    }
}
