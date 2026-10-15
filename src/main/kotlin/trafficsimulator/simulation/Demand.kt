package trafficsimulator.simulation

import java.math.BigDecimal
import trafficsimulator.domain.Entrance

/** Accumulates an entrance's exact demand until it can be admitted to the scene. */
internal data class Demand(val remainder: BigDecimal = BigDecimal.ZERO, val pending: Long = 0) {
    /** Returns the next immutable demand state without admitting cars, checking representational limits. */
    fun advance(entrance: Entrance): Demand {
        val total = remainder.add(entrance.arrivalRate)
        val whole = total.toBigInteger()
        try {
            return Demand(total.subtract(BigDecimal(whole)), Math.addExact(pending, whole.longValueExact()))
        } catch (error: ArithmeticException) {
            throw ArithmeticException("Demand overflow for entrance '${entrance.id}': ${error.message}")
        }
    }
}
