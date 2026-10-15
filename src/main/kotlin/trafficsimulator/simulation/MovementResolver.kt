package trafficsimulator.simulation

import trafficsimulator.domain.Car
import trafficsimulator.domain.Movement
import trafficsimulator.domain.Tile

internal data class Transition(val car: Car, val destination: Tile?, val entry: Movement? = null)

internal class MovementResolver(
    private val topology: Topology,
    private val cars: List<Car>,
    private val requests: Map<String, Transition>,
    private val reservations: Map<String, Movement>,
    private val cursors: Map<String, Int>,
) {
    private val occupancy = cars.associateBy { it.position }
    private val accepted = linkedSetOf<String>()
    private val resources = mutableMapOf<CrossingResource, String>()
    private val receiving = mutableMapOf<Tile, String>()
    private val candidates = topology.network.intersections.flatMap { intersection ->
        topology.orderedApproaches(intersection, cursors.getValue(intersection.id)).mapNotNull { laneId ->
            val lane = topology.network.lane(laneId)
            occupancy[lane.tile(lane.length - 1)]?.id?.takeIf { requests[it]?.entry != null }
        }.let { list -> list.mapIndexed { index, id -> id to list.take(index) } }
    }.toMap()

    init {
        for ((owner, movement) in reservations) claim(owner, movement)
    }

    fun resolve(): Set<String> {
        for (candidate in candidates.keys) attempt(candidate)
        for (car in cars) if (requests[car.id]?.entry == null) attempt(car.id)
        return accepted
    }

    private data class Frame(
        val id: String,
        val optional: Boolean,
        val acceptedCheckpoint: Int,
        val claimedCheckpoint: Int,
        var priorityIndex: Int = 0,
        var dependencies: List<String>? = null,
        var dependencyIndex: Int = 0,
    )

    private fun attempt(root: String) {
        if (root in accepted) return
        val added = mutableListOf<String>()
        val claimed = mutableListOf<Pair<String, Movement>>()
        val visiting = hashSetOf<String>()
        val stack = ArrayDeque<Frame>()

        fun enter(id: String, optional: Boolean): Boolean {
            if (id !in requests) return false
            if (!visiting.add(id)) return false
            stack.addLast(Frame(id, optional, added.size, claimed.size))
            return true
        }

        fun reject() {
            do {
                val frame = stack.removeLast()
                visiting.remove(frame.id)
                while (added.size > frame.acceptedCheckpoint) accepted.remove(added.removeLast())
                while (claimed.size > frame.claimedCheckpoint) {
                    val (owner, movement) = claimed.removeLast()
                    topology.resources(movement).forEach { resources.remove(it, owner) }
                    receiving.remove(topology.receivingTile(movement), owner)
                }
            } while (!frame.optional && stack.isNotEmpty())
        }

        if (!enter(root, optional = false)) return
        while (stack.isNotEmpty()) {
            val frame = stack.last()
            val priorities = candidates[frame.id].orEmpty()
            // A dependency entrant must honor its own intersection's priority before freeing upstream space.
            if (frame.priorityIndex < priorities.size) {
                val prior = priorities[frame.priorityIndex++]
                if (prior !in accepted) enter(prior, optional = true)
                continue
            }
            val request = requests.getValue(frame.id)
            if (frame.dependencies == null) {
                val movement = request.entry
                val destinationReserved = request.destination?.let { receiving[it]?.let { owner -> owner != frame.id } } == true
                val crossingReserved = movement != null &&
                    (topology.resources(movement).any { it in resources } || topology.receivingTile(movement) in receiving)
                if (destinationReserved || crossingReserved) {
                    reject()
                    continue
                }
                if (movement != null) {
                    claim(frame.id, movement)
                    claimed.add(frame.id to movement)
                }
                val requiredTiles = listOfNotNull(request.destination, movement?.let(topology::receivingTile))
                frame.dependencies = requiredTiles.mapNotNull { occupancy[it]?.id }.distinct()
            }
            val dependencies = frame.dependencies!!
            if (frame.dependencyIndex == dependencies.size) {
                stack.removeLast()
                visiting.remove(frame.id)
                accepted.add(frame.id)
                added.add(frame.id)
            } else {
                val dependency = dependencies[frame.dependencyIndex++]
                if (dependency !in accepted && !enter(dependency, optional = false)) reject()
            }
        }
    }

    private fun claim(owner: String, movement: Movement) {
        topology.resources(movement).forEach { resources[it] = owner }
        receiving[topology.receivingTile(movement)] = owner
    }
}
