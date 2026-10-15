package trafficsimulator.simulation

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import trafficsimulator.domain.*

class CrossingTest {
    @Test
    fun `connector positions and boundaries each consume one transition`() {
        val simulation = Simulation(crossing(connectorLength = 3, rates = mapOf("a+" to "1")))
        val snapshots = simulation.steps(6)
        val id = snapshots.first().cars.single().id
        val visits = snapshots.mapNotNull { snapshot -> snapshot.cars.find { it.id == id } }
        assertEquals(listOf(Tile("a+", 0), Tile("ab-path", 0), Tile("ab-path", 1), Tile("ab-path", 2), Tile("b+", 0)), visits.map { it.position })
        assertEquals(listOf(null, "ab", "ab", "ab", null), visits.map { it.chosenMovementId })
        assertTrue(snapshots.last().cars.none { it.id == id })
    }

    @Test
    fun `compatible opposing straights cross concurrently`() {
        val config = fourWay(rates = mapOf("n+" to "1", "s+" to "1"))
        val snapshots = Simulation(config).steps(4)
        assertEquals(setOf("ns-path", "sn-path"), snapshots[2].cars.filter { it.chosenMovementId != null && it.position.laneId.endsWith("path") }.map { it.position.laneId }.toSet())
        assertEquals(setOf("s-", "n-"), snapshots[3].cars.take(2).map { it.position.laneId }.toSet())
    }

    @Test
    fun `conflicts hold through clearance and rotate sustained priority`() {
        val snapshots = Simulation(crossing()).steps(12)
        val entries = snapshots.mapNotNull { snapshot -> snapshot.cars.find { it.position.laneId.endsWith("path") }?.position?.laneId }
        assertEquals(listOf("ab-path", "ba-path", "ab-path", "ba-path", "ab-path", "ba-path"), entries)
        assertTrue(snapshots.all { snapshot -> snapshot.cars.count { it.position.laneId.endsWith("path") } <= 1 })
        assertTrue(snapshots[2].cars.none { it.position.laneId.endsWith("path") })
    }

    @Test
    fun `same connector follower waits through owners final crossing tick`() {
        val snapshots = Simulation(crossing(connectorLength = 2, rates = mapOf("a+" to "1"))).steps(5)
        assertEquals(listOf(0, 1, 1, 0, 1), snapshots.map { s -> s.cars.count { it.position.laneId == "ab-path" } })
        val follower = snapshots[1].cars.last().id
        assertEquals(Tile("a+", 0), snapshots[3].cars.first { it.id == follower }.position)
        assertEquals(Tile("ab-path", 0), snapshots[4].cars.first { it.id == follower }.position)
    }

    @Test
    fun `receiving red permits arrival but blocks later crossing until accepted departure`() {
        val config = crossing(rates = mapOf("a+" to "1"), lights = listOf(TrafficLight("receiver", Tile("b+", 0), 4, 3)))
        val snapshots = Simulation(config).steps(5)
        assertEquals(Tile("b+", 0), snapshots[2].cars.first().position)
        assertEquals(snapshots[2].cars, snapshots[3].cars)
        assertEquals(Tile("ab-path", 0), snapshots[4].cars.first().position)
        assertEquals(LightColor.GREEN, snapshots[4].lightColors.getValue("receiver"))
    }

    @Test
    fun `blocked receiving departure cannot free space through a requested move`() {
        val config = crossing(length = 2, rates = mapOf("a+" to "1"), lights = listOf(TrafficLight("downstream", Tile("b+", 1), 30, 1)))
        val snapshots = Simulation(config).steps(15)
        val final = snapshots.last()
        assertEquals(setOf(Tile("b+", 0), Tile("b+", 1)), final.cars.filter { it.position.laneId == "b+" }.map { it.position }.toSet())
        assertTrue(final.cars.none { it.position.laneId.endsWith("path") })
        assertEquals(snapshots[10].cars, final.cars)
        assertTrue(final.pendingEntranceCounts.getValue("entry-a+") > 0)
    }

    @Test
    fun `blocked approaches do not consume compatible competitors grants`() {
        val config = fourWay(
            weights = mapOf("sn" to "0", "sw" to "1", "se" to "0"),
            rates = mapOf("n+" to "1", "s+" to "1"),
            lights = listOf(TrafficLight("blocked", Tile("s-", 0), 100, 1)),
        )
        val snapshots = Simulation(config).steps(25)
        assertTrue(snapshots.drop(10).all { s -> s.cars.any { it.position == Tile("n+", 1) && it.chosenMovementId == "ns" } })
        assertTrue(snapshots.drop(10).any { s -> s.cars.any { it.position.laneId == "sw-path" } })
    }

    @Test
    fun `merging entries cannot secure the same outgoing lane`() {
        val config = fourWay(
            weights = mapOf("we" to "0", "ws" to "1", "wn" to "0"),
            rates = mapOf("n+" to "1", "w+" to "1"),
        )
        val snapshots = Simulation(config).steps(25)
        assertTrue(snapshots.all { s -> s.cars.count { it.position.laneId == "ns-path" || it.position.laneId == "ws-path" } <= 1 })
        assertTrue(snapshots.any { s -> s.cars.any { it.position.laneId == "ns-path" } })
        assertTrue(snapshots.any { s -> s.cars.any { it.position.laneId == "ws-path" } })
    }

    @Test
    fun `identical resource names are scoped to their own intersections`() {
        val config = connected()
        val network = config.network
        val withBothEntries = SimulationConfig(RoadNetwork(
            network.roads, network.connectorLanes, network.intersections,
            network.entrances.map { it.copy(arrivalRate = java.math.BigDecimal.ONE) }, network.exits,
        ), emptyList(), 42)
        val snapshot = Simulation(withBothEntries).steps(2).last()
        assertEquals(setOf("ab-path", "cb-path"), snapshot.cars.filter { it.position.laneId.endsWith("path") }.map { it.position.laneId }.toSet())
    }

    @Test
    fun `upstream dependency cannot bypass downstream rotated priority`() {
        val config = scene(
            listOf(road("a", 1), road("b", 1), road("c", 1)),
            listOf(
                junction("i", listOf(move("ab", "a+", "b+"), move("ba", "b-", "a-"))),
                junction("j", listOf(move("cb", "c-", "b-"), move("bc", "b+", "c+"))),
            ), mapOf("a+" to "1", "c-" to "0.4"),
        )
        val snapshots = Simulation(config).steps(4)
        val downstream = snapshots[2].cars.first { it.position == Tile("b+", 0) }
        assertEquals(Tile("b+", 0), snapshots[3].cars.first { it.id == downstream.id }.position)
        assertTrue(snapshots[3].cars.any { it.position.laneId == "cb-path" })
        assertTrue(snapshots[3].cars.none { it.position.laneId == "ab-path" || it.position.laneId == "bc-path" })
    }

    @Test
    fun `every approach follows its forced legal straight right and left choices`() {
        for (turn in Turn.entries) {
            val base = fourWay()
            val weights = base.network.intersections.single().movements.associate { it.id to if (it.turn == turn) "1" else "0" }
            val snapshots = Simulation(fourWay(weights)).steps(5)
            val selected = snapshots.flatMap { it.cars }.mapNotNull { it.chosenMovementId }.toSet()
            assertEquals(base.network.intersections.single().movements.filter { it.turn == turn }.map { it.id }.toSet(), selected)
        }
    }

    @Test
    fun `waiting for red and losing contention retain exact weighted choices`() {
        val weights = mapOf("ns" to "0.1", "nw" to "0.200", "ne" to "0.7000")
        val config = fourWay(weights, rates = mapOf("n+" to "1"), lights = listOf(TrafficLight("red", Tile("n+", 1), 12, 2)))
        val snapshots = Simulation(config).steps(12)
        val first = snapshots.first().cars.single().id
        val choices = snapshots.drop(2).map { s -> s.cars.first { it.id == first }.chosenMovementId }
        assertNotNull(choices.first())
        assertEquals(1, choices.toSet().size)
        val competing = Simulation(crossing()).steps(4)
        val loser = competing[1].cars.first { it.position.laneId == "b-" }
        assertEquals("ba", loser.chosenMovementId)
        assertEquals("ba", competing[3].cars.first { it.id == loser.id }.chosenMovementId)
    }
}
