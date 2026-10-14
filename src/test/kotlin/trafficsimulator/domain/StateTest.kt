package trafficsimulator.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class StateTest {
    @Test
    fun `lights can occupy first last and middle road tiles in either direction`() {
        val network = boundaryParts().network()
        val lights = listOf(
            TrafficLight("first", Tile("a+", 0), 1, 2),
            TrafficLight("middle", Tile("a+", 1), 2, 3, LightColor.GREEN),
            TrafficLight("last", Tile("a+", 2), 3, 1),
            TrafficLight("opposite", Tile("a-", 0), 1, 1),
        )
        val config = SimulationConfig(network, lights, Long.MIN_VALUE)
        assertEquals(4, config.trafficLights.size)
        assertEquals(LightColor.RED, config.trafficLights.first().initialColor)
        assertEquals(LightColor.GREEN, config.trafficLights[1].initialColor)
        assertTrue(SimulationConfig(RoadNetwork(), emptyList(), Long.MAX_VALUE).trafficLights.isEmpty())
    }

    @TestFactory
    fun `invalid light placements and duplicate categories are rejected`(): List<DynamicTest> {
        fun light(id: String, lane: String, index: Int) = TrafficLight(id, Tile(lane, index), 1, 1)
        val cases = listOf(
            Triple("unknown lane", "missing", listOf(light("l", "missing", 0))),
            Triple("one past final tile", "a+", listOf(light("l", "a+", 3))),
            Triple("connector placement", "road lane", listOf(light("l", "ab-path", 0))),
            Triple("duplicate IDs", "Duplicate", listOf(light("l", "a+", 0), light("l", "a-", 0))),
            Triple("duplicate tiles", "tile", listOf(light("l", "a+", 0), light("other", "a+", 0))),
        )
        return cases.map { (name, field, lights) -> DynamicTest.dynamicTest(name) {
            rejected(field) { SimulationConfig(junctionParts().network(), lights, 0) }
        } }
    }

    @Test
    fun `snapshots validate local state without resolving network context`() {
        val cars = listOf(Car("a", Tile("unknown+", 900), "unresolved"), Car("b", Tile("unknown-", 900)))
        val snapshot = SimulationSnapshot(Long.MAX_VALUE, cars, mapOf("unresolved-light" to LightColor.RED), mapOf("unresolved-entry" to 0L))
        assertEquals(2, snapshot.cars.size)
        assertEquals("unresolved", snapshot.cars.first().chosenMovementId)
        assertTrue(SimulationSnapshot(0, emptyList(), emptyMap(), emptyMap()).cars.isEmpty())
    }

    @TestFactory
    fun `snapshot local invariant violations are rejected`(): List<DynamicTest> {
        val first = Car("a", Tile("lane", 0))
        val cases: List<Triple<String, String, () -> Unit>> = listOf(
            Triple("negative tick", "tick") { SimulationSnapshot(-1, emptyList(), emptyMap(), emptyMap()) },
            Triple("duplicate identity", "Duplicate car") { SimulationSnapshot(0, listOf(first, Car("a", Tile("lane", 1))), emptyMap(), emptyMap()) },
            Triple("duplicate occupancy", "tile") { SimulationSnapshot(0, listOf(first, Car("b", Tile("lane", 0))), emptyMap(), emptyMap()) },
            Triple("blank light key", "light ID") { SimulationSnapshot(0, emptyList(), mapOf(" " to LightColor.RED), emptyMap()) },
            Triple("blank entrance key", "entrance ID") { SimulationSnapshot(0, emptyList(), emptyMap(), mapOf(" " to 0)) },
            Triple("negative pending count", "entry") { SimulationSnapshot(0, emptyList(), emptyMap(), mapOf("entry" to -1)) },
        )
        return cases.map { (name, field, action) -> DynamicTest.dynamicTest(name) { rejected(field, action) } }
    }
}
