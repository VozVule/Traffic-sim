package trafficsimulator.domain

data class Tile(val laneId: String, val index: Int) {
    init {
        requireIdentifier(laneId, "Tile laneId")
        require(index >= 0) { "Tile index must be nonnegative: $index" }
    }
}
