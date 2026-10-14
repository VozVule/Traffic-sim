package trafficsimulator.domain

class SimulationSnapshot(
    val tick: Long,
    cars: List<Car>,
    lightColors: Map<String, LightColor>,
    pendingEntranceCounts: Map<String, Long>,
) {
    val cars: List<Car> = ownedList(cars)
    val lightColors: Map<String, LightColor> = ownedMap(lightColors)
    val pendingEntranceCounts: Map<String, Long> = ownedMap(pendingEntranceCounts)

    init {
        require(tick >= 0) { "Snapshot tick must be nonnegative: $tick" }
        indexById(this.cars, "car") { it.id }
        val positions = HashSet<Tile>()
        for (car in this.cars) {
            require(positions.add(car.position)) { "Multiple cars on tile ${car.position}" }
        }
        this.lightColors.keys.forEach { requireIdentifier(it, "Snapshot light ID") }
        for ((entranceId, count) in this.pendingEntranceCounts) {
            requireIdentifier(entranceId, "Snapshot entrance ID")
            require(count >= 0) { "Pending count for entrance '$entranceId' must be nonnegative: $count" }
        }
    }
}
