package trafficsimulator.simulation

import trafficsimulator.domain.LightColor
import trafficsimulator.domain.TrafficLight

/** Tracks one light's independent phase; [color] applies to the next movement tick. */
internal class Signal(private val light: TrafficLight) {
    var color: LightColor = light.initialColor
        private set
    private var remaining = duration()

    /** Consumes the completed movement tick and prepares the color for the following tick. */
    fun finishTick() {
        remaining--
        if (remaining == 0) {
            color = if (color == LightColor.RED) LightColor.GREEN else LightColor.RED
            remaining = duration()
        }
    }

    private fun duration(): Int =
        if (color == LightColor.RED) light.redDurationTicks else light.greenDurationTicks
}
