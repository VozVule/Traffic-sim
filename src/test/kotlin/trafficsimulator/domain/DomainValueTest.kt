package trafficsimulator.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class DomainValueTest {
    @Test
    fun `lane lookups preserve direction-specific tiles and bounds`() {
        val forward = Lane("f", 3)
        val backward = Lane("b", 1)
        assertEquals(Tile("f", 0), forward.tile(0))
        assertEquals(Tile("f", 2), forward.tile(2))
        assertEquals(Tile("b", 0), backward.tile(0))
        assertNotEquals(forward.tile(0), backward.tile(0))
        rejected("f") { forward.tile(-1) }
        rejected("f") { forward.tile(3) }
        rejected("b") { backward.tile(1) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["0", "0.00", "0.125", "2.75"])
    fun `demand accepts exact nonnegative rates`(rate: String) {
        assertEquals(decimal(rate), Entrance("entry", "lane", decimal(rate)).arrivalRate)
    }

    @Test
    fun `identifiers stay verbatim and opposite lengths can differ`() {
        val road = Road(" road ", Lane(" lane ", 1), Lane("LANE", 4))
        assertEquals(" road ", road.id)
        assertEquals(" lane ", road.forwardLane.tile(0).laneId)
    }

    @TestFactory
    fun `local scalar validation rejects invalid fields`(): List<DynamicTest> {
        val cases: List<Triple<String, String, () -> Unit>> = listOf(
            Triple("blank tile lane", "laneId") { Tile(" ", 0) },
            Triple("negative tile", "index") { Tile("a", -1) },
            Triple("blank lane", "ID") { Lane("", 1) },
            Triple("zero lane length", "length") { Lane("a", 0) },
            Triple("negative lane length", "length") { Lane("a", -1) },
            Triple("blank road", "ID") { Road("", Lane("a", 1), Lane("b", 1)) },
            Triple("repeated road lane", "distinct") { Road("r", Lane("a", 1), Lane("a", 2)) },
            Triple("blank car", "ID") { Car("", Tile("a", 0)) },
            Triple("blank chosen movement", "chosenMovementId") { Car("c", Tile("a", 0), " ") },
            Triple("blank entrance", "ID") { Entrance("", "a", decimal("1")) },
            Triple("blank entrance lane", "laneId") { Entrance("e", " ", decimal("1")) },
            Triple("negative demand", "arrivalRate") { Entrance("e", "a", decimal("-0.001")) },
            Triple("blank exit", "ID") { Exit("", "a") },
            Triple("blank exit lane", "laneId") { Exit("e", " ") },
            Triple("blank light", "ID") { TrafficLight("", Tile("a", 0), 1, 1) },
            Triple("zero red", "redDurationTicks") { TrafficLight("l", Tile("a", 0), 0, 1) },
            Triple("negative red", "redDurationTicks") { TrafficLight("l", Tile("a", 0), -1, 1) },
            Triple("zero green", "greenDurationTicks") { TrafficLight("l", Tile("a", 0), 1, 0) },
            Triple("negative green", "greenDurationTicks") { TrafficLight("l", Tile("a", 0), 1, -1) },
        )
        return cases.map { (name, field, action) -> DynamicTest.dynamicTest(name) { rejected(field, action) } }
    }
}
