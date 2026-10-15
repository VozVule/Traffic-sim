package trafficsimulator.simulation

import trafficsimulator.domain.LightColor
import trafficsimulator.domain.TrafficLight

internal class Signal(private val light: TrafficLight) {
    var color: LightColor = light.initialColor
        private set
    private var remaining = duration()

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
