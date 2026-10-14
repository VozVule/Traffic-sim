package trafficsimulator.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class IntersectionTest {
    private val straight = movement("straight", "a+", "b+", resources = setOf("straight-path"))
    private val left = movement("left", "a+", "c+", resources = setOf("left-path"))
    private val movements = listOf(straight, left)

    @Test
    fun `exact scaled totals and zero-weight legal movements are accepted`() {
        val scaled = Intersection("i", movements, mapOf("a+" to mapOf("straight" to decimal("0.250"), "left" to decimal("0.75"))))
        assertEquals(decimal("0.250"), scaled.turningWeights.getValue("a+").getValue("straight"))
        val zero = Intersection("i", movements, mapOf("a+" to mapOf("straight" to decimal("0.00"), "left" to decimal("1.000"))))
        assertEquals(setOf("straight", "left"), zero.turningWeights.getValue("a+").keys)
    }

    @TestFactory
    fun `invalid turning distributions are rejected`(): List<DynamicTest> {
        val valid = mapOf("straight" to decimal("0.5"), "left" to decimal("0.5"))
        val cases = linkedMapOf(
            "missing approach" to emptyMap(),
            "foreign approach" to mapOf("wrong" to valid),
            "extra approach" to mapOf("a+" to valid, "wrong" to valid),
            "missing movement" to mapOf("a+" to mapOf("straight" to decimal("1"))),
            "extra movement" to mapOf("a+" to (valid + ("extra" to decimal("0")))),
            "foreign movement" to mapOf("a+" to mapOf("other" to decimal("1"))),
            "negative weight" to mapOf("a+" to mapOf("straight" to decimal("-0.5"), "left" to decimal("1.5"))),
            "rounded total" to mapOf("a+" to mapOf("straight" to decimal("0.4999"), "left" to decimal("0.5"))),
            "percentage total" to mapOf("a+" to mapOf("straight" to decimal("50"), "left" to decimal("50"))),
            "no positive choice" to mapOf("a+" to mapOf("straight" to decimal("0"), "left" to decimal("0"))),
        )
        return cases.map { (name, weights) ->
            DynamicTest.dynamicTest(name) { rejected("i") { Intersection("i", movements, weights) } }
        }
    }

    @Test
    fun `movement keys must belong to their own approach`() {
        val reverse = movement("reverse", "b-", "a-")
        rejected("a+") {
            Intersection("i", listOf(straight, reverse), mapOf("a+" to mapOf("reverse" to decimal("1")), "b-" to mapOf("straight" to decimal("1"))))
        }
    }

    @Test
    fun `merging paths require pairwise shared resources`() {
        val first = movement("first", "a+", "c+", resources = setOf("one", "shared"))
        val second = movement("second", "b+", "c+", resources = setOf("two", "shared"))
        assertEquals(2, intersection("i", listOf(first, second)).movements.size)
        rejected("shared") { intersection("i", listOf(first, movement("second", "b+", "c+", resources = setOf("two")))) }
        val third = movement("third", "d+", "c+", resources = setOf("one", "three"))
        rejected("second") { intersection("i", listOf(first, second, third)) }
    }

    @Test
    fun `disjoint resources declare compatible movements`() {
        val compatible = intersection("i", movements)
        assertTrue(compatible.movements[0].crossingResourceIds.intersect(compatible.movements[1].crossingResourceIds).isEmpty())
    }

    @TestFactory
    fun `movement and intersection local declarations are validated`(): List<DynamicTest> {
        val cases: List<Triple<String, String, () -> Unit>> = listOf(
            Triple("blank movement", "ID") { movement("", "a", "b") },
            Triple("blank incoming", "incomingLaneId") { movement("m", " ", "b") },
            Triple("blank outgoing", "outgoingLaneId") { movement("m", "a", " ") },
            Triple("blank connector", "connectorLaneId") { movement("m", "a", "b", " ") },
            Triple("empty resources", "resources") { movement("m", "a", "b", resources = emptySet()) },
            Triple("blank resource", "resource") { movement("m", "a", "b", resources = setOf(" ")) },
            Triple("blank intersection", "ID") { intersection(" ", listOf(straight)) },
            Triple("empty intersection", "movement") { Intersection("i", emptyList(), emptyMap()) },
            Triple("duplicate movement", "Duplicate") { intersection("i", listOf(straight, straight)) },
        )
        return cases.map { (name, field, action) -> DynamicTest.dynamicTest(name) { rejected(field, action) } }
    }
}
