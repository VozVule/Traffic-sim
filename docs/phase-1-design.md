# Phase 1: Kotlin traffic simulation

Status: accepted on 2026-10-11. The user confirmed shared understanding of the domain rules, concrete model, operational defaults, and conservative crossing restriction. Maven project setup is complete. The current request implements domain entities and configuration only; see ../SPEC.md and tasks/domain-model.md.

## Accepted scope

The first release is a Kotlin backend prototype that simulates cars moving, stopping, and turning. It uses moderate object-oriented design. The eventual application is desktop, with presentation and analytics deferred.

- Two templates: one four-way intersection, and two four-way intersections in sequence.
- Every road is two-way, with one lane per direction.
- Each tile belongs to one lane. Matching position numbers in different lanes identify different tiles. A car occupies one tile and advances at most one tile per tick.
- Cars can follow into tiles vacated by successful moves during the same tick.
- Straight, left, and right movements are available where the template connects them. No U-turns.
- Each incoming intersection direction has weights over its permitted outgoing roads, summing to one or one hundred percent. A car draws once per visit and retains its choice while waiting; a later visit draws again.
- Traffic lights may be placed anywhere on road lanes. A red light prevents leaving its own tile in the controlled direction; the opposite lane is unaffected.
- Lights are independent and have separately configurable red and green durations. A red phase lasting twenty ticks blocks movement ticks one through twenty; green first allows movement on tick twenty-one.
- Entrance rates are fixed during a run and deterministic, expressed in cars per tick with fractional values supported. A rate of 0.5 makes arrivals due on ticks two, four, six, and so on, starting from an empty scene.
- Due arrivals remain pending in an entrance buffer until they can enter.
- Cars disappear at scene exits. Templates may allow circulation; there is no guarantee that each car eventually departs.
- Identical configuration and random seed reproduce the same run.
- A car should wait rather than enter and block an intersection.

## Nonblocking intersection entry

Before entering, a car requires a safe crossing and space in the outgoing lane, rather than checking only the first internal tile. With a blocked outgoing lane, the car waits on its incoming road even if the first crossing position is empty and its light is green. The user confirmed this conservative interpretation in the final design review.

## Object model

Topology and scenario configuration stay immutable during a run. `Simulation` owns the mutable run state and exposes immutable snapshots.

| Type | Responsibility |
| --- | --- |
| `Road` | Groups the two opposing road lanes in phase one. |
| `Lane` | An ordered, directed series of tile positions; also usable as a short connector through an intersection. |
| `Tile(laneId, index)` | Identifies a position within one lane; it is a value object rather than a global grid square. |
| `Intersection` | Contains allowed movements and the turning weights for each incoming lane. |
| `Movement` | Connects an incoming lane to an outgoing lane through an ordered connector path, with declared physical conflicts. |
| `Car` | Holds identity, position, and its chosen movement for the current intersection visit. |
| `TrafficLight` | Holds its road-lane tile, red/green durations, and initial color. Its controlled direction follows from its lane. |
| `Entrance` | Identifies the entering lane and configured arrival rate. |
| `RoadNetwork` | Holds roads, connector lanes, intersections, entrances, and explicit scene exits; validates their connections. |
| `SimulationConfig` | Combines a selected network, placed lights, entrance rates, and random seed. |
| `SimulationSnapshot` | Contains tick number, car positions, light colors, and pending entrance counts. |
| `Simulation` | Advances one tick, resolves movement and crossing admission, and returns a snapshot. |

Templates are ordinary factory functions. No car hierarchy, light hierarchy, controller framework, event bus, or persistence layer is needed for this prototype. Additional behavior can be introduced when release 2 supplies an actual alternative.

### Small simulation interface

```kotlin
class Simulation(config: SimulationConfig) {
    fun step(): SimulationSnapshot
    fun snapshot(): SimulationSnapshot
}
```

One call to `step()` advances one simulated tick. Real-time pacing belongs to a caller, so tests and a console runner can advance rapidly while a later desktop interface can pace the same simulation. Constructing a new simulation restarts a run. The console runner can select a template and run a chosen number of ticks; callers configure the domain objects without needing a frontend.

## Crossing rule

Different connector lanes may represent paths through the same physical crossing space. Distinct tile identities therefore do not, by themselves, prevent intersection collisions.

Before entering, a car must acquire its movement's crossing resources and the first outgoing tile together. The template declares which movements conflict, including movements that merge into the same outgoing lane. Opposing straight movements must use compatible resources and may proceed concurrently. Crossing resources remain held until the car clears its connector path; the car still moves only one tile per tick.

If an outgoing tile is occupied, it can be secured only if its occupant has an accepted move that vacates it this tick. A reservation prevents another movement from taking that outgoing space during the crossing. Completed crossing reservations are released after moves commit.

Eligible competing approaches use deterministic rotating priority. A car whose requested transition is blocked is skipped; losing a competition does not cause a new random turn choice. A red light applies only when that transition departs its own road-lane tile: a car already beyond an upstream light remains unaffected when the light turns red. This is internal intersection logic, not a public policy hierarchy.

This rule is conservative: conflicting paths remain unavailable for the whole crossing. It also makes a following car wait before entering the same connector while the preceding car holds that crossing reservation, even if the first connector tile is being vacated. This junction-admission restriction was explicitly accepted in the final design review; ordinary road following still uses tiles vacated this tick. The rule prevents cars entering a crossing without somewhere to leave, but does not promise that a fully congested network will always keep moving.

## Tick sequence

1. Determine the signal colors for this movement tick. The first twenty movement ticks of a twenty-tick red phase use red; the next uses green.
2. Accumulate scheduled demand and add due arrivals to entrance buffers.
3. Select movements once for cars that have reached an intersection approach, in stable car order, then construct at most one movement request per existing car.
4. Resolve red-light gates, crossing reservations, competing requests, and dependencies on tiles being vacated. A follower advances only if the car ahead actually advances.
5. Commit successful movements together, remove cars that leave through exits, and release crossings that have cleared.
6. Admit pending arrivals into free entrance tiles. A newly admitted car occupies that first tile and receives no additional movement in the same tick.
7. Return the completed tick's snapshot, including the signal colors used for that tick.

An entrance vacated during movement can admit a pending car at the end of the same tick. Each entrance admits at most one car per tick. Buffers can store a count of pending identical arrivals rather than allocate off-scene car objects. Arrival accounting should preserve configured decimal rates without accumulating binary floating-point drift.

## Configuration and operational defaults

- Lights have configurable initial colors, defaulting to red; each starts at the beginning of that color's full duration. Mid-phase offsets are deferred unless needed.
- Templates define topology, road lengths, connector lengths, and crossing conflicts. The two-intersection template uses one connecting two-way road, with the remaining approaches leading to scene boundaries.
- Each movement progresses through its connector positions and then into the first tile of its outgoing road lane. Crossing the intersection is never an instantaneous hop.
- Initially, traffic lights attach to road-lane positions; crossing connector positions belong to the intersection paths.
- Configuration validation rejects invalid references, nonpositive lane lengths or signal durations, negative rates, invalid/negative weights, weights that do not sum correctly, and illegal or missing movement connections. An approach must have at least one positive legal outgoing choice.
- Duplicate lights at the same lane position are rejected. A valid light in the opposite lane is independent.
- No overtaking, spontaneous rerouting, or global deadlock recovery is included.
- Operational snapshots support inspecting the prototype; experiment analytics remain in release 2.

## Verification targets

Verify the behaviors that define this prototype: lane-specific occupancy; directional red-light stopping and the twenty/ twenty-one boundary; deterministic buffered arrivals; simultaneous following; one retained turn choice per intersection visit; repeatable seeded runs; compatible versus conflicting crossings; waiting at blocked exits; and removal at scene exits.

These are planned checks, not a claim that code has been implemented or tested.

## Future work

See [release-2.md](../release-2.md) for roundabouts, analytics, coordinated lights, one-way roads, configurable lane counts, variable car behavior, changing entrance rates during a run, rush-hour scenarios, and desktop presentation.
