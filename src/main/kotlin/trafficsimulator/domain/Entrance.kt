package trafficsimulator.domain

import java.math.BigDecimal

data class Entrance(val id: String, val laneId: String, val arrivalRate: BigDecimal) {
    init {
        requireIdentifier(id, "Entrance ID")
        requireIdentifier(laneId, "Entrance '$id' laneId")
        require(arrivalRate.signum() >= 0) { "Entrance '$id' arrivalRate must be nonnegative" }
    }
}
