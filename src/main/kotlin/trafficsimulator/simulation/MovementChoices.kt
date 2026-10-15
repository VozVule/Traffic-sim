package trafficsimulator.simulation

import java.math.BigInteger
import java.util.Random
import trafficsimulator.domain.Intersection
import trafficsimulator.domain.Movement

internal class MovementChoices(intersections: List<Intersection>, seed: Long) {
    private data class Choice(val movement: Movement, val weight: BigInteger)
    private val random = Random(seed)
    private val choices = intersections.flatMap { intersection ->
        intersection.movements.groupBy { it.incomingLaneId }.map { (lane, movements) ->
            val weights = intersection.turningWeights.getValue(lane)
            val positive = movements.filter { weights.getValue(it.id).signum() > 0 }
            val scale = positive.maxOf { weights.getValue(it.id).scale().coerceAtLeast(0) }
            lane to positive.map { Choice(it, weights.getValue(it.id).setScale(scale).unscaledValue()) }
        }
    }.toMap()

    fun choose(laneId: String): Movement? {
        val options = choices[laneId] ?: return null
        if (options.size == 1) return options.single().movement
        val total = options.fold(BigInteger.ZERO) { sum, choice -> sum.add(choice.weight) }
        var sample: BigInteger
        do {
            sample = BigInteger(total.bitLength(), random)
        } while (sample >= total)
        var cumulative = BigInteger.ZERO
        for (option in options) {
            cumulative = cumulative.add(option.weight)
            if (sample < cumulative) return option.movement
        }
        error("Exact movement sample outside its total")
    }
}
