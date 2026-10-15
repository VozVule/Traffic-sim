package trafficsimulator.simulation

import trafficsimulator.domain.Intersection
import trafficsimulator.domain.Movement
import trafficsimulator.domain.RoadNetwork
import trafficsimulator.domain.Tile

/** Scopes a declared conflict resource to its intersection so names can be reused across the scene. */
internal data class CrossingResource(val intersectionId: String, val resourceId: String)

/** Indexes validated scene connections for movement planning and reservation ownership. */
internal class Topology(val network: RoadNetwork) {
    val connectorMovements = network.intersections.flatMap { it.movements }.associateBy { it.connectorLaneId }
    val intersectionByMovement = network.intersections.flatMap { intersection ->
        intersection.movements.map { it.id to intersection }
    }.toMap()
    val approaches = network.intersections.associate { intersection ->
        intersection.id to intersection.movements.map { it.incomingLaneId }.distinct()
    }

    /** Identifies the intersection-scoped conflict resources that a movement must reserve. */
    fun resources(movement: Movement): List<CrossingResource> = movement.crossingResourceIds.map {
        CrossingResource(intersectionByMovement.getValue(movement.id).id, it)
    }

    /** Returns the first outgoing road tile secured before connector admission. */
    fun receivingTile(movement: Movement): Tile = network.tile(movement.outgoingLaneId, 0)

    /** Returns declared approach order rotated to begin at the arbitration cursor. */
    fun orderedApproaches(intersection: Intersection, cursor: Int): List<String> {
        val lanes = approaches.getValue(intersection.id)
        return lanes.indices.map { lanes[(cursor + it) % lanes.size] }
    }
}
