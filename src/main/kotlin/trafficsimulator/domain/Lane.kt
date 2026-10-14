package trafficsimulator.domain

data class Lane(val id: String, val length: Int) {
    init {
        requireIdentifier(id, "Lane ID")
        require(length > 0) { "Lane '$id' length must be positive: $length" }
    }

    fun tile(index: Int): Tile {
        require(index in 0 until length) { "Tile index $index is outside lane '$id' of length $length" }
        return Tile(id, index)
    }
}
