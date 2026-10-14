package trafficsimulator.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class OwnershipTest {
    @Test
    fun `movement owns resource set and preserves declared order`() {
        val resources = linkedSetOf("second", "first")
        val movement = movement("m", "a+", "b+", resources = resources)
        resources.clear()
        assertEquals(listOf("second", "first"), movement.crossingResourceIds.toList())
        immutable { (movement.crossingResourceIds as MutableSet<String>).add("third") }
        immutable { movement.crossingResourceIds.iterator().let { it.next(); (it as MutableIterator<String>).remove() } }
    }

    @Test
    fun `intersection owns movements and both weight map levels in input order`() {
        val first = movement("first", "a+", "b+")
        val second = movement("second", "a+", "c+")
        val reverse = movement("reverse", "b-", "a-")
        val movements = mutableListOf(second, first, reverse)
        val choices = linkedMapOf("second" to decimal("0.75"), "first" to decimal("0.25"))
        val weights = linkedMapOf("b-" to linkedMapOf("reverse" to decimal("1.00")), "a+" to choices)
        val intersection = Intersection("i", movements, weights)
        movements.clear()
        choices.clear()
        weights.getValue("b-").clear()
        weights.clear()
        assertEquals(listOf("second", "first", "reverse"), intersection.movements.map { it.id })
        assertEquals(listOf("b-", "a+"), intersection.turningWeights.keys.toList())
        assertEquals(listOf("second", "first"), intersection.turningWeights.getValue("a+").keys.toList())
        assertEquals(decimal("1.00"), intersection.turningWeights.getValue("b-").getValue("reverse"))
        immutable { (intersection.movements as MutableList<Movement>).add(first) }
        immutable { (intersection.turningWeights as MutableMap).clear() }
        immutable { (intersection.turningWeights.getValue("a+") as MutableMap).clear() }
        immutable { (intersection.turningWeights.getValue("a+").entries.first() as MutableMap.MutableEntry).setValue(decimal("1")) }
    }

    @Test
    fun `network owns every list and retains construction order and lookups`() {
        val parts = connectedParts()
        parts.roads.reverse()
        parts.connectors.reverse()
        parts.intersections.reverse()
        parts.entrances.reverse()
        parts.exits.reverse()
        val network = parts.network()
        val expectedRoads = parts.roads.toList()
        val expectedConnectors = parts.connectors.toList()
        val expectedIntersections = parts.intersections.toList()
        val expectedEntrances = parts.entrances.toList()
        val expectedExits = parts.exits.toList()
        parts.roads.clear()
        parts.connectors.clear()
        parts.intersections.clear()
        parts.entrances.clear()
        parts.exits.clear()
        assertEquals(expectedRoads, network.roads)
        assertEquals(expectedConnectors, network.connectorLanes)
        assertEquals(expectedIntersections, network.intersections)
        assertEquals(expectedEntrances, network.entrances)
        assertEquals(expectedExits, network.exits)
        assertEquals("c+", network.movement("bc").outgoingLaneId)
        assertEquals(Tile("b+", 2), network.tile("b+", 2))
        immutable { (network.roads as MutableList).clear() }
        immutable { (network.connectorLanes as MutableList).clear() }
        immutable { (network.intersections as MutableList).clear() }
        immutable { (network.entrances as MutableList).clear() }
        immutable { (network.exits as MutableList).clear() }
    }

    @Test
    fun `configuration owns light list in supplied order`() {
        val first = TrafficLight("first", Tile("a+", 0), 1, 1)
        val second = TrafficLight("second", Tile("a-", 0), 1, 1)
        val lights = mutableListOf(second, first)
        val config = SimulationConfig(boundaryParts().network(), lights, 0)
        lights.clear()
        assertEquals(listOf("second", "first"), config.trafficLights.map { it.id })
        immutable { (config.trafficLights as MutableList).add(first) }
    }

    @Test
    fun `snapshot owns cars and both status maps in supplied order`() {
        val first = Car("first", Tile("a", 0))
        val second = Car("second", Tile("b", 0))
        val cars = mutableListOf(second, first)
        val colors = linkedMapOf("second" to LightColor.GREEN, "first" to LightColor.RED)
        val counts = linkedMapOf("second" to Long.MAX_VALUE, "first" to 0L)
        val snapshot = SimulationSnapshot(0, cars, colors, counts)
        cars.clear()
        colors.clear()
        counts.clear()
        assertEquals(listOf("second", "first"), snapshot.cars.map { it.id })
        assertEquals(listOf("second", "first"), snapshot.lightColors.keys.toList())
        assertEquals(LightColor.GREEN, snapshot.lightColors.getValue("second"))
        assertEquals(listOf("second", "first"), snapshot.pendingEntranceCounts.keys.toList())
        assertEquals(Long.MAX_VALUE, snapshot.pendingEntranceCounts.getValue("second"))
        immutable { (snapshot.cars as MutableList).add(first) }
        immutable { (snapshot.lightColors as MutableMap).clear() }
        immutable { (snapshot.pendingEntranceCounts as MutableMap).clear() }
    }

    @Test
    fun `empty scene and state collections reject JVM mutation too`() {
        val network = RoadNetwork()
        val config = SimulationConfig(network, emptyList(), 0)
        val snapshot = SimulationSnapshot(0, emptyList(), emptyMap(), emptyMap())
        immutable { (network.roads as MutableList).add(road("new")) }
        immutable { (config.trafficLights as MutableList).add(TrafficLight("l", Tile("a", 0), 1, 1)) }
        immutable { (snapshot.cars as MutableList).add(Car("c", Tile("a", 0))) }
        immutable { (snapshot.lightColors as MutableMap)["l"] = LightColor.RED }
        immutable { (snapshot.pendingEntranceCounts as MutableMap)["e"] = 0 }
    }

    private fun immutable(action: () -> Unit) {
        assertThrows(UnsupportedOperationException::class.java, action)
    }
}
