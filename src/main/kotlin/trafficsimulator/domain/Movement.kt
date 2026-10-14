package trafficsimulator.domain

enum class Turn { STRAIGHT, LEFT, RIGHT }

class Movement(
    val id: String,
    val incomingLaneId: String,
    val outgoingLaneId: String,
    val connectorLaneId: String,
    val turn: Turn,
    crossingResourceIds: Set<String>,
) {
    val crossingResourceIds: Set<String> = ownedSet(crossingResourceIds)

    init {
        requireIdentifier(id, "Movement ID")
        requireIdentifier(incomingLaneId, "Movement '$id' incomingLaneId")
        requireIdentifier(outgoingLaneId, "Movement '$id' outgoingLaneId")
        requireIdentifier(connectorLaneId, "Movement '$id' connectorLaneId")
        require(this.crossingResourceIds.isNotEmpty()) { "Movement '$id' requires crossing resources" }
        this.crossingResourceIds.forEach { requireIdentifier(it, "Movement '$id' crossing resource") }
    }
}
