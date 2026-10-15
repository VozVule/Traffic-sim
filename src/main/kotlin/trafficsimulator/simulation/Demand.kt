package trafficsimulator.simulation

import java.math.BigDecimal
import trafficsimulator.domain.Entrance

internal data class Demand(val remainder: BigDecimal = BigDecimal.ZERO, val pending: Long = 0) {
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
