package trafficsimulator.simulation

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import trafficsimulator.domain.*

class SignalTest {
    @Test
    fun `twenty initial red ticks gate departure through tick twenty`() {
        val simulation = Simulation(boundary(1, lights = listOf(TrafficLight("light", Tile("a+", 0), 20, 3))))
        val snapshots = simulation.steps(21)
        assertTrue(snapshots.take(20).all { it.lightColors.getValue("light") == LightColor.RED })
        assertEquals(LightColor.GREEN, snapshots.last().lightColors.getValue("light"))
        val firstId = snapshots.first().cars.single().id
        assertTrue(snapshots.take(20).all { it.cars.single().id == firstId })
        assertNotEquals(firstId, snapshots.last().cars.single().id)
        assertEquals(19L, snapshots.last().pendingEntranceCounts.getValue("entry-a+"))
    }

    @Test
    fun `unequal phases and initial green retain full durations`() {
        val lights = listOf(
            TrafficLight("red", Tile("a+", 0), 2, 3),
            TrafficLight("green", Tile("a-", 0), 2, 3, LightColor.GREEN),
        )
        val simulation = Simulation(boundary(1, backwardRate = "1", lights = lights))
        val snapshots = simulation.steps(7)
        assertEquals(listOf("RED", "RED", "GREEN", "GREEN", "GREEN", "RED", "RED"), snapshots.map { it.lightColors.getValue("red").name })
        assertEquals(listOf("GREEN", "GREEN", "GREEN", "RED", "RED", "GREEN", "GREEN"), snapshots.map { it.lightColors.getValue("green").name })
        assertNotEquals(snapshots[0].cars.last().id, snapshots[1].cars.last().id)
        assertEquals(snapshots[0].cars.first().id, snapshots[1].cars.first().id)
    }

    @Test
    fun `upstream signals gate only departure from their own tile`() {
        val simulation = Simulation(boundary(4, lights = listOf(TrafficLight("upstream", Tile("a+", 1), 3, 1))))
        val snapshots = simulation.steps(6)
        val first = snapshots.first().cars.first().id
        assertEquals(listOf(0, 1, 1, 2, 3), snapshots.take(5).map { snapshot -> snapshot.cars.first { it.id == first }.position.index })
        assertTrue(snapshots.last().cars.none { it.id == first })
        assertEquals(LightColor.RED, snapshots[4].lightColors.getValue("upstream"))
    }

    @Test
    fun `maximum duration phases require no overflowing duration sum`() {
        val simulation = Simulation(boundary(lights = listOf(TrafficLight("large", Tile("a+", 0), Int.MAX_VALUE, Int.MAX_VALUE))))
        assertTrue(simulation.steps(5).all { it.lightColors.getValue("large") == LightColor.RED })
    }
}
