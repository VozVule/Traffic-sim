package trafficsimulator.domain

data class Road(val id: String, val forwardLane: Lane, val backwardLane: Lane) {
    init {
        requireIdentifier(id, "Road ID")
        require(forwardLane.id != backwardLane.id) { "Road '$id' must have distinct lane IDs" }
    }
}
