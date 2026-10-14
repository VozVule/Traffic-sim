package trafficsimulator.domain

import java.math.BigDecimal

class Intersection(
    val id: String,
    movements: List<Movement>,
    turningWeights: Map<String, Map<String, BigDecimal>>,
) {
    val movements: List<Movement> = ownedList(movements)
    val turningWeights: Map<String, Map<String, BigDecimal>> =
        ownedMap(turningWeights.mapValues { (_, weights) -> ownedMap(weights) })

    init {
        requireIdentifier(id, "Intersection ID")
        require(this.movements.isNotEmpty()) { "Intersection '$id' requires at least one movement" }
        indexById(this.movements, "movement") { it.id }
        validateTurningWeights()
        validateMergingResources()
    }

    private fun validateTurningWeights() {
        val approaches = movements.groupBy { it.incomingLaneId }
        require(turningWeights.keys == approaches.keys) {
            "Intersection '$id' turningWeights approaches must match incoming lanes ${approaches.keys}"
        }
        for ((laneId, choices) in approaches) {
            val weights = turningWeights.getValue(laneId)
            require(weights.keys == choices.map { it.id }.toSet()) {
                "Intersection '$id' turningWeights for lane '$laneId' must match its movement IDs"
            }
            for ((movementId, weight) in weights) {
                require(weight.signum() >= 0) {
                    "Intersection '$id' weight for movement '$movementId' must be nonnegative"
                }
            }
            require(weights.values.any { it.signum() > 0 }) {
                "Intersection '$id' lane '$laneId' requires a positive turning weight"
            }
            val total = weights.values.fold(BigDecimal.ZERO, BigDecimal::add)
            require(total.compareTo(BigDecimal.ONE) == 0) {
                "Intersection '$id' weights for lane '$laneId' must total exactly one: $total"
            }
        }
    }

    private fun validateMergingResources() {
        for (merging in movements.groupBy { it.outgoingLaneId }.values) {
            for (firstIndex in merging.indices) {
                for (secondIndex in firstIndex + 1 until merging.size) {
                    val first = merging[firstIndex]
                    val second = merging[secondIndex]
                    require(first.crossingResourceIds.any { it in second.crossingResourceIds }) {
                        "Intersection '$id' merging movements '${first.id}' and '${second.id}' require a shared crossing resource"
                    }
                }
            }
        }
    }
}
