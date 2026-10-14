package trafficsimulator.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class RoadNetworkTest {
    @Test
    fun `boundary road and checked lookups are usable`() {
        val network = boundaryParts().network()
        assertEquals(2, network.entrances.size)
        assertEquals(2, network.exits.size)
        assertEquals(Tile("a+", 0), network.tile("a+", 0))
        assertEquals(Tile("a+", 2), network.tile("a+", 2))
        assertEquals(3, network.lane("a-").length)
        rejected("unknown") { network.lane("unknown") }
        rejected("unknown") { network.tile("unknown", 0) }
        rejected("a+") { network.tile("a+", -1) }
        rejected("a+") { network.tile("a+", 3) }
        rejected("unknown") { network.movement("unknown") }
    }

    @Test
    fun `intersection connectors support one-position paths`() {
        val network = junctionParts().network()
        assertEquals("b+", network.movement("ab").outgoingLaneId)
        assertEquals(Tile("ab-path", 0), network.tile(network.movement("ab").connectorLaneId, 0))
        rejected("ab-path") { network.tile("ab-path", 1) }
    }

    @Test
    fun `two intersections can share a reciprocal connecting road`() {
        val network = connectedParts().network()
        assertEquals("b+", network.movement("ab").outgoingLaneId)
        assertEquals("b+", network.movement("bc").incomingLaneId)
        assertEquals("b-", network.movement("cb").outgoingLaneId)
        assertEquals("b-", network.movement("ba").incomingLaneId)
        assertEquals(2, network.entrances.size)
    }

    @Test
    fun `closed loops disconnected components and empty scenes are accepted`() {
        val loop = loopParts()
        assertTrue(loop.network().entrances.isEmpty())
        assertTrue(loop.network().exits.isEmpty())
        val boundary = sceneParts(listOf(road("c")), emptyList())
        val disconnected = RoadNetwork(
            loop.roads + boundary.roads, loop.connectors, loop.intersections,
            boundary.entrances, boundary.exits,
        )
        assertEquals(3, disconnected.roads.size)
        assertEquals("a+", disconnected.movement("ba2").outgoingLaneId)
        assertTrue(RoadNetwork().roads.isEmpty())
    }

    @Test
    fun `multiple legal movements may share an intersection endpoint`() {
        val movements = listOf(
            movement("ab", "a+", "b+"), movement("ac", "a+", "c+"),
            movement("ba", "b-", "a-"), movement("ca", "c-", "a-"),
        )
        val network = sceneParts(listOf(road("a"), road("b"), road("c")), listOf(intersection("i", movements))).network()
        assertEquals(4, network.intersections.single().movements.size)
        assertEquals(3, network.entrances.size)
    }

    @TestFactory
    fun `duplicate identities are rejected by entity kind`(): List<DynamicTest> {
        val cases: List<Triple<String, String, (SceneParts) -> Unit>> = listOf(
            Triple("roads", "road") { p -> p.roads.add(Road("a", Lane("extra+", 1), Lane("extra-", 1))) },
            Triple("road lanes", "lane") { p -> p.roads.add(Road("extra", Lane("a+", 1), Lane("extra-", 1))) },
            Triple("road connector lanes", "lane") { p -> p.connectors.add(Lane("a+", 1)) },
            Triple("connector lanes", "lane") { p -> p.connectors.add(p.connectors.first()) },
            Triple("intersections", "intersection") { p -> p.intersections.add(intersection("i", listOf(movement("new", "a+", "b+")))) },
            Triple("movements across intersections", "movement") { p -> p.intersections.add(intersection("j", listOf(p.intersections.first().movements.first()))) },
            Triple("entrances", "entrance") { p -> p.entrances.add(p.entrances.first()) },
            Triple("exits", "exit") { p -> p.exits.add(p.exits.first()) },
        )
        return cases.map { (name, field, mutate) -> DynamicTest.dynamicTest(name) {
            val parts = junctionParts()
            mutate(parts)
            rejected("Duplicate $field") { parts.network() }
        } }
    }

    @TestFactory
    fun `references and lane roles are validated`(): List<DynamicTest> {
        val cases: List<Triple<String, String, (SceneParts) -> Unit>> = listOf(
            Triple("unknown entrance lane", "missing") { p -> p.entrances[0] = Entrance("e", "missing", decimal("0")) },
            Triple("unknown exit lane", "missing") { p -> p.exits[0] = Exit("e", "missing") },
            Triple("entrance on connector", "road lane") { p -> p.entrances[0] = Entrance("e", "ab-path", decimal("0")) },
            Triple("exit on connector", "road lane") { p -> p.exits[0] = Exit("e", "ab-path") },
            Triple("unknown incoming", "missing") { p -> replaceFirst(p, movement("ab", "missing", "b+")) },
            Triple("unknown outgoing", "missing") { p -> replaceFirst(p, movement("ab", "a+", "missing")) },
            Triple("unknown connector", "missing") { p -> replaceFirst(p, movement("ab", "a+", "b+", "missing")) },
            Triple("incoming connector", "road lane") { p -> replaceFirst(p, movement("ab", "ab-path", "b+")) },
            Triple("outgoing connector", "road lane") { p -> replaceFirst(p, movement("ab", "a+", "ab-path")) },
            Triple("road as connector", "connectorLaneId") { p -> replaceFirst(p, movement("ab", "a+", "b+", "a+")) },
        )
        return cases.map { (name, field, mutate) -> DynamicTest.dynamicTest(name) {
            val parts = junctionParts()
            mutate(parts)
            rejected(field) { parts.network() }
        } }
    }

    @Test
    fun `unused and shared connector paths are rejected`() {
        val unused = junctionParts()
        unused.connectors.add(Lane("unused", 1))
        rejected("unused") { unused.network() }
        val shared = junctionParts()
        shared.intersections[0] = intersection("i", listOf(movement("ab", "a+", "b+"), movement("ba", "b-", "a-", "ab-path")))
        shared.connectors.removeAt(1)
        rejected("ab-path") { shared.network() }
    }

    @Test
    fun `U-turns and ambiguous duplicate routes are rejected`() {
        val uturn = junctionParts()
        replaceFirst(uturn, movement("ab", "a+", "a-"))
        rejected("U-turn") { uturn.network() }
        val duplicate = junctionParts()
        duplicate.intersections[0] = intersection("i", duplicate.intersections[0].movements + movement("ab2", "a+", "b+"))
        duplicate.connectors.add(Lane("ab2-path", 1))
        rejected("duplicate route") { duplicate.network() }
    }

    @TestFactory
    fun `every road lane needs exactly one endpoint of each kind`(): List<DynamicTest> {
        val cases: List<Triple<String, String, (SceneParts) -> Unit>> = listOf(
            Triple("missing start", "starting") { p -> p.entrances.removeAt(0) },
            Triple("missing end", "ending") { p -> p.exits.removeAt(0) },
            Triple("multiple entrances", "starting") { p -> p.entrances.add(Entrance("another", p.entrances[0].laneId, decimal("1"))) },
            Triple("multiple exits", "ending") { p -> p.exits.add(Exit("another", p.exits[0].laneId)) },
            Triple("entrance competes with intersection", "starting") { p -> p.entrances.add(Entrance("extra", "b+", decimal("1"))) },
            Triple("exit competes with intersection", "ending") { p -> p.exits.add(Exit("extra", "a+")) },
        )
        return cases.map { (name, field, mutate) -> DynamicTest.dynamicTest(name) {
            val parts = junctionParts()
            mutate(parts)
            rejected(field) { parts.network() }
        } }
    }

    @Test
    fun `starting and ending endpoints cannot belong to multiple intersections`() {
        val roads = listOf(road("a"), road("b"), road("c"))
        val first = intersection("i", listOf(movement("ab", "a+", "b+")))
        val sharedStart = intersection("j", listOf(movement("cb", "c+", "b+")))
        rejected("start belongs to multiple") { sceneParts(roads, listOf(first, sharedStart)).network() }
        val sharedEnd = intersection("j", listOf(movement("ac", "a+", "c+")))
        rejected("end belongs to multiple") { sceneParts(roads, listOf(first, sharedEnd)).network() }
    }

    @Test
    fun `opposing road directions must have reciprocal intersection endpoints`() {
        val wrong = intersection("i", listOf(movement("ab", "a+", "b+"), movement("wrong", "a-", "b-")))
        rejected("reciprocal") { sceneParts(listOf(road("a"), road("b")), listOf(wrong)).network() }
    }

    private fun replaceFirst(parts: SceneParts, replacement: Movement) {
        parts.intersections[0] = intersection("i", listOf(replacement, parts.intersections[0].movements[1]))
    }
}
