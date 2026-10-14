package trafficsimulator.domain

data class Car(val id: String, val position: Tile, val chosenMovementId: String? = null) {
    init {
        requireIdentifier(id, "Car ID")
        chosenMovementId?.let { requireIdentifier(it, "Car '$id' chosenMovementId") }
    }
}
