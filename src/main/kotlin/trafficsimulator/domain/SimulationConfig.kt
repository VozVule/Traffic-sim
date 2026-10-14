package trafficsimulator.domain

class SimulationConfig(
    val network: RoadNetwork,
    trafficLights: List<TrafficLight>,
    val randomSeed: Long,
) {
    val trafficLights: List<TrafficLight> = ownedList(trafficLights)

    init {
        indexById(this.trafficLights, "traffic light") { it.id }
        val positions = HashSet<Tile>()
        for (light in this.trafficLights) {
            network.tile(light.position.laneId, light.position.index)
            require(network.isRoadLane(light.position.laneId)) {
                "Light '${light.id}' must be placed on a road lane: '${light.position.laneId}'"
            }
            require(positions.add(light.position)) { "Multiple lights on tile ${light.position}" }
        }
    }
}
