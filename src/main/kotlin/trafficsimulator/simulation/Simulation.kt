package trafficsimulator.simulation

import trafficsimulator.domain.Car
import trafficsimulator.domain.LightColor
import trafficsimulator.domain.Movement
import trafficsimulator.domain.SimulationConfig
import trafficsimulator.domain.SimulationSnapshot

/** Owns an initially empty, deterministic run of a configured scene. Callers must serialize access to each instance. */
class Simulation(config: SimulationConfig) {
    private val network = config.network
    private val topology = Topology(network)
    private val choices = MovementChoices(network.intersections, config.randomSeed)
    private val lights = config.trafficLights
    private val signals = lights.associate { it.id to Signal(it) }
    private val lightByTile = lights.associateBy { it.position }
    private var tick = 0L
    private var demand = network.entrances.associate { it.id to Demand() }
    private var cars = emptyList<Car>()
    private val reservations = linkedMapOf<String, Movement>()
    private val cursors = network.intersections.associate { it.id to 0 }.toMutableMap()
    private var published = observe(lights.associate { it.id to it.initialColor })

    /** Observes the immutable completed state without advancing time or randomness; tick zero reports initial colors. */
    fun snapshot(): SimulationSnapshot = published

    /**
     * Advances one tick and returns its immutable snapshot, including the colors used for movement.
     * Entrance admission follows movement, so newly admitted cars cannot move during this tick.
     *
     * @throws ArithmeticException if tick or demand counts exceed representational limits, leaving the run unchanged.
     */
    fun step(): SimulationSnapshot {
        val nextTick = try {
            Math.addExact(tick, 1)
        } catch (_: ArithmeticException) {
            throw ArithmeticException("Simulation tick overflow")
        }
        val nextDemand = network.entrances.associate { it.id to demand.getValue(it.id).advance(it) }
        val colors = lights.associate { it.id to signals.getValue(it.id).color }
        demand = nextDemand
        cars = cars.map { car ->
            val lane = network.lane(car.position.laneId)
            if (car.chosenMovementId == null && car.position.index == lane.length - 1) {
                car.copy(chosenMovementId = choices.choose(lane.id)?.id)
            } else car
        }
        val requests = cars.mapNotNull { car -> request(car, colors)?.let { car.id to it } }.toMap()
        val accepted = MovementResolver(topology, cars, requests, reservations, cursors).resolve()
        updateCursors(requests, accepted)
        cars = cars.mapNotNull { car ->
            if (car.id !in accepted) return@mapNotNull car
            val transition = requests.getValue(car.id)
            val connectorDeparture = car.position.laneId in topology.connectorMovements &&
                transition.destination?.laneId != car.position.laneId
            if (connectorDeparture) reservations.remove(car.id)
            transition.entry?.let { reservations[car.id] = it }
            transition.destination?.let { destination ->
                car.copy(position = destination, chosenMovementId = if (connectorDeparture) null else car.chosenMovementId)
            }
        }
        tick = nextTick
        admit()
        signals.values.forEach { it.finishTick() }
        published = observe(colors)
        return published
    }

    private fun request(car: Car, colors: Map<String, LightColor>): Transition? {
        val position = car.position
        if (lightByTile[position]?.let { colors.getValue(it.id) == LightColor.RED } == true) return null
        val lane = network.lane(position.laneId)
        if (position.index < lane.length - 1) return Transition(car, lane.tile(position.index + 1))
        val crossing = topology.connectorMovements[lane.id]
        if (crossing != null) return Transition(car, topology.receivingTile(crossing))
        val movement = car.chosenMovementId?.let(network::movement)
        return if (movement == null) Transition(car, null)
        else Transition(car, network.tile(movement.connectorLaneId, 0), movement)
    }

    private fun updateCursors(requests: Map<String, Transition>, accepted: Set<String>) {
        for (intersection in network.intersections) {
            val order = topology.orderedApproaches(intersection, cursors.getValue(intersection.id))
            val admitted = order.firstOrNull { lane ->
                requests.values.any { it.car.id in accepted && it.entry?.incomingLaneId == lane }
            } ?: continue
            val approaches = topology.approaches.getValue(intersection.id)
            cursors[intersection.id] = (approaches.indexOf(admitted) + 1) % approaches.size
        }
    }

    private fun admit() {
        val occupied = cars.mapTo(hashSetOf()) { it.position }
        val admitted = mutableListOf<Car>()
        val remaining = demand.toMutableMap()
        for (entrance in network.entrances) {
            val state = remaining.getValue(entrance.id)
            val tile = network.tile(entrance.laneId, 0)
            if (state.pending > 0 && occupied.add(tile)) {
                admitted.add(Car("${entrance.id.length}:${entrance.id}:$tick", tile))
                remaining[entrance.id] = state.copy(pending = state.pending - 1)
            }
        }
        cars = cars + admitted
        demand = remaining
    }

    private fun observe(colors: Map<String, LightColor>): SimulationSnapshot = SimulationSnapshot(
        tick, cars, colors, network.entrances.associate { it.id to demand.getValue(it.id).pending },
    )
}
