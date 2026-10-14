package trafficsimulator.domain

class RoadNetwork(
    roads: List<Road> = emptyList(),
    connectorLanes: List<Lane> = emptyList(),
    intersections: List<Intersection> = emptyList(),
    entrances: List<Entrance> = emptyList(),
    exits: List<Exit> = emptyList(),
) {
    val roads: List<Road> = ownedList(roads)
    val connectorLanes: List<Lane> = ownedList(connectorLanes)
    val intersections: List<Intersection> = ownedList(intersections)
    val entrances: List<Entrance> = ownedList(entrances)
    val exits: List<Exit> = ownedList(exits)

    private val roadLanes = this.roads.flatMap { listOf(it.forwardLane, it.backwardLane) }
    private val lanesById = indexById(roadLanes + this.connectorLanes, "lane") { it.id }
    private val movementsById = indexById(this.intersections.flatMap { it.movements }, "movement") { it.id }
    private val roadByLaneId = this.roads.flatMap { road ->
        listOf(road.forwardLane.id to road.id, road.backwardLane.id to road.id)
    }.toMap()

    init {
        validateEntityIds()
        validateLaneRoles()
        validateConnectors()
        validateRoutes()
        validateEndpoints()
    }

    fun lane(laneId: String): Lane =
        requireNotNull(lanesById[laneId]) { "Unknown lane '$laneId'" }

    fun tile(laneId: String, index: Int): Tile = lane(laneId).tile(index)

    fun movement(movementId: String): Movement =
        requireNotNull(movementsById[movementId]) { "Unknown movement '$movementId'" }

    internal fun isRoadLane(laneId: String): Boolean = laneId in roadByLaneId

    private fun validateEntityIds() {
        indexById(roads, "road") { it.id }
        indexById(intersections, "intersection") { it.id }
        indexById(entrances, "entrance") { it.id }
        indexById(exits, "exit") { it.id }
    }

    private fun requireRoadLane(laneId: String, reference: String) {
        lane(laneId)
        require(isRoadLane(laneId)) { "$reference must reference a road lane: '$laneId'" }
    }

    private fun validateLaneRoles() {
        entrances.forEach { requireRoadLane(it.laneId, "Entrance '${it.id}'") }
        exits.forEach { requireRoadLane(it.laneId, "Exit '${it.id}'") }
        val connectorIds = connectorLanes.map { it.id }.toSet()
        for (movement in movementsById.values) {
            requireRoadLane(movement.incomingLaneId, "Movement '${movement.id}' incomingLaneId")
            requireRoadLane(movement.outgoingLaneId, "Movement '${movement.id}' outgoingLaneId")
            lane(movement.connectorLaneId)
            require(movement.connectorLaneId in connectorIds) {
                "Movement '${movement.id}' connectorLaneId must reference a connector lane: '${movement.connectorLaneId}'"
            }
        }
    }

    private fun validateConnectors() {
        val owners = movementsById.values.groupBy { it.connectorLaneId }
        for (connector in connectorLanes) {
            require(owners[connector.id]?.size == 1) {
                "Connector lane '${connector.id}' must be owned by exactly one movement"
            }
        }
    }

    private fun validateRoutes() {
        for (intersection in intersections) {
            val routes = HashSet<Pair<String, String>>()
            for (movement in intersection.movements) {
                require(roadByLaneId[movement.incomingLaneId] != roadByLaneId[movement.outgoingLaneId]) {
                    "Movement '${movement.id}' cannot make a U-turn on the same road"
                }
                require(routes.add(movement.incomingLaneId to movement.outgoingLaneId)) {
                    "Intersection '${intersection.id}' has a duplicate route from '${movement.incomingLaneId}' to '${movement.outgoingLaneId}'"
                }
            }
        }
    }

    private fun intersectionEndpoints(incoming: Boolean): Map<String, String> {
        val endpoints = LinkedHashMap<String, String>()
        for (intersection in intersections) {
            for (movement in intersection.movements) {
                val laneId = if (incoming) movement.incomingLaneId else movement.outgoingLaneId
                val previous = endpoints.putIfAbsent(laneId, intersection.id)
                require(previous == null || previous == intersection.id) {
                    "Lane '$laneId' ${if (incoming) "end" else "start"} belongs to multiple intersections '$previous' and '${intersection.id}'"
                }
            }
        }
        return endpoints
    }

    private fun validateEndpoints() {
        val starts = intersectionEndpoints(incoming = false)
        val ends = intersectionEndpoints(incoming = true)
        val entranceCounts = entrances.groupingBy { it.laneId }.eachCount()
        val exitCounts = exits.groupingBy { it.laneId }.eachCount()
        for (lane in roadLanes) {
            requireSingleEndpoint(lane.id, "starting", entranceCounts[lane.id] ?: 0, lane.id in starts)
            requireSingleEndpoint(lane.id, "ending", exitCounts[lane.id] ?: 0, lane.id in ends)
        }
        for (road in roads) {
            require(ends[road.forwardLane.id] == starts[road.backwardLane.id] &&
                starts[road.forwardLane.id] == ends[road.backwardLane.id]) {
                "Road '${road.id}' opposing lane endpoints must be reciprocal at intersections"
            }
        }
    }

    private fun requireSingleEndpoint(laneId: String, endpoint: String, boundaries: Int, intersection: Boolean) {
        val count = boundaries + if (intersection) 1 else 0
        require(count == 1) { "Lane '$laneId' must have exactly one $endpoint endpoint; found $count" }
    }
}
