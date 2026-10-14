package trafficsimulator.domain

enum class LightColor { RED, GREEN }

data class TrafficLight(
    val id: String,
    val position: Tile,
    val redDurationTicks: Int,
    val greenDurationTicks: Int,
    val initialColor: LightColor = LightColor.RED,
) {
    init {
        requireIdentifier(id, "Traffic light ID")
        require(redDurationTicks > 0) { "Light '$id' redDurationTicks must be positive" }
        require(greenDurationTicks > 0) { "Light '$id' greenDurationTicks must be positive" }
    }
}
