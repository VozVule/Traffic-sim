package trafficsimulator.simulation

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import trafficsimulator.domain.*

class SimulationTest {
    @Test
    fun `initial snapshots are complete and observing is inert`() {
        val light = TrafficLight("light", Tile("a+", 0), 2, 3)
        val simulation = Simulation(boundary(lights = listOf(light)))
        val initial = simulation.snapshot()
        assertEquals(0L, initial.tick)
        assertTrue(initial.cars.isEmpty())
        assertEquals(mapOf("light" to LightColor.RED), initial.lightColors)
        assertEquals(linkedMapOf("entry-a+" to 0L, "entry-a-" to 0L), initial.pendingEntranceCounts)
        repeat(5) { assertEquals(fields(initial), fields(simulation.snapshot())) }
        val empty = Simulation(SimulationConfig(RoadNetwork(), emptyList(), 1))
        assertEquals(3L, empty.steps(3).last().tick)
        assertTrue(empty.snapshot().cars.isEmpty())
    }

    @Test
    fun `independent runs preserve complete reproducible ordering`() {
        for (config in listOf(fourWay(mapOf("ns" to "0.2", "nw" to "0.30", "ne" to "0.500")), connected(), loop())) {
            val first = Simulation(config)
            val second = Simulation(config)
            val sequence = first.steps(120).map(::fields)
            assertEquals(0L, second.snapshot().tick)
            assertEquals(sequence, second.steps(120).map(::fields))
        }
    }

    @Test
    fun `one tile roads admit once and exit on the next tick`() {
        val simulation = Simulation(boundary(1, backwardRate = "1"))
        val first = simulation.step()
        assertEquals(listOf(Tile("a+", 0), Tile("a-", 0)), first.cars.map { it.position })
        assertEquals(2, first.cars.map { it.id }.toSet().size)
        val second = simulation.step()
        assertTrue(second.cars.none { it.id in first.cars.map { car -> car.id } })
        assertEquals(2, second.cars.size)
        assertTrue(second.pendingEntranceCounts.values.all { it == 0L })
    }

    @Test
    fun `following chains reuse every vacated tile including an exit`() {
        val simulation = Simulation(boundary(3))
        val filled = simulation.steps(3).last()
        assertEquals(listOf(2, 1, 0), filled.cars.map { it.position.index })
        val next = simulation.step()
        assertEquals(filled.cars.drop(1).map { it.id }, next.cars.take(2).map { it.id })
        assertEquals(listOf(2, 1, 0), next.cars.map { it.position.index })
    }

    @Test
    fun `blocked leader stops its followers and retains all demand`() {
        val simulation = Simulation(boundary(3, "2.5", lights = listOf(TrafficLight("red", Tile("a+", 2), 100, 1))))
        val snapshots = simulation.steps(8)
        val full = snapshots[2].cars
        assertEquals(full, snapshots.last().cars)
        snapshots.forEach { snapshot ->
            assertEquals((snapshot.tick * 25 / 10), snapshot.cars.size.toLong() + snapshot.pendingEntranceCounts.getValue("entry-a+"))
        }
        assertEquals(17L, snapshots.last().pendingEntranceCounts.getValue("entry-a+"))
    }

    @Test
    fun `decimal demand follows exact due schedules`() {
        for ((rate, ticks) in listOf("0" to emptyList(), "0.5" to listOf(2L, 4L, 6L, 8L, 10L), "0.1" to listOf(10L))) {
            val simulation = Simulation(boundary(1, rate))
            assertEquals(ticks, simulation.steps(10).filter { it.cars.isNotEmpty() }.map { it.tick })
        }
        val simulation = Simulation(boundary(1, "1.25"))
        assertEquals(listOf(0L, 0L, 0L, 1L, 1L, 1L, 1L, 2L), simulation.steps(8).map { it.pendingEntranceCounts.getValue("entry-a+") })
    }

    @Test
    fun `due overflow on a later entrance is atomic and repeatable`() {
        val simulation = Simulation(boundary(1, "0.5", "9223372036854775808"))
        val before = fields(simulation.snapshot())
        repeat(2) {
            val error = assertThrows(ArithmeticException::class.java) { simulation.step() }
            assertTrue(error.message!!.contains("entry-a-"))
            assertEquals(before, fields(simulation.snapshot()))
        }
    }

    @Test
    fun `pending overflow preflights before admission or any other state change`() {
        val light = TrafficLight("phase", Tile("a+", 0), 1, 1)
        val simulation = Simulation(boundary(1, "0.5", "9223372036854775807", listOf(light)))
        val before = fields(simulation.step())
        repeat(2) {
            val error = assertThrows(ArithmeticException::class.java) { simulation.step() }
            assertTrue(error.message!!.contains("entry-a-"))
            assertEquals(before, fields(simulation.snapshot()))
        }
    }

    @Test
    fun `snapshots own their collections and preserve earlier observations`() {
        val simulation = Simulation(boundary())
        val retained = simulation.step()
        val before = fields(retained)
        assertThrows(UnsupportedOperationException::class.java) { (retained.cars as MutableList).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (retained.pendingEntranceCounts as MutableMap).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (retained.lightColors as MutableMap)["fake"] = LightColor.RED }
        simulation.steps(20)
        assertEquals(before, fields(retained))
    }
}
