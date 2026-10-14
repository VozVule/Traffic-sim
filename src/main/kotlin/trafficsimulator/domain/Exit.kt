package trafficsimulator.domain

data class Exit(val id: String, val laneId: String) {
    init {
        requireIdentifier(id, "Exit ID")
        requireIdentifier(laneId, "Exit '$id' laneId")
    }
}
