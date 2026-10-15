package trafficsimulator.simulation

import trafficsimulator.domain.Intersection
import trafficsimulator.domain.Movement
import trafficsimulator.domain.RoadNetwork
import trafficsimulator.domain.Tile

internal data class CrossingResource(val intersectionId: String, val resourceId: String)

internal class Topology(val network: RoadNetwork) {
    val connectorMovements = network.intersections.flatMap { it.movements }.associateBy { it.connectorLaneId }
    val intersectionByMovement = network.intersections.flatMap { intersection ->
        intersection.movements.map { it.id to intersection }
    }.toMap()
    val approaches = network.intersections.associate { intersection ->
        intersection.id to intersection.movements.map { it.incomingLaneId }.distinct()
    }

    fun resources(movement: Movement): List<CrossingResource> = movement.crossingResourceIds.map {
        CrossingResource(intersectionByMovement.getValue(movement.id).id, it)
    }

    fun receivingTile(movement: Movement): Tile = network.tile(movement.outgoingLaneId, 0)

    fun orderedApproaches(intersection: Intersection, cursor: Int): List<String> {
        val lanes = approaches.getValue(intersection.id)
        return lanes.indices.map { lanes[(cursor + it) % lanes.size] }
    }
}
