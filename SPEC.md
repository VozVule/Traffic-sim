# Kotlin traffic simulator: incremental specification

Design status: accepted on 2026-10-11. The Kotlin/JVM Maven project is set up. The current task implements domain entities and configuration only; simulation behavior remains planned in the increments below.

Release 1 is specified for implementation below. Release 2 and later are captured as a proposed roadmap of agreed goals; their detailed behavior is not yet specified. The increment boundaries are a delivery recommendation based on the accepted design.

## Problem Statement

The user wants to experiment with how cars move through roads and intersections under configurable traffic demand and traffic lights. There is currently no implemented simulator. Building a frontend first would delay the most important part: a correct, inspectable simulation.

The initial need is a small Kotlin backend prototype with object-oriented domain modeling, predefined scenes, continuous arrivals, turning cars, and independent timed lights. The longer-term motivation is experimenting with coordinated lights, congestion, rush-hour demand, and different car behavior. Those extensions need a dependable simulation to build on.

## Solution

Build a headless Kotlin simulation that advances one logical tick at a time and returns an immutable snapshot. Use concrete domain classes and a small simulation interface that can be called by tests, a console runner, and an eventual desktop application.

Deliver the engine in runnable increments: first moving traffic on roads, then timed lights, then a complete intersection, then two connected intersections, and finally the configurable prototype. Each increment includes observable behavior and its verification. Modeling and tests accompany behavior rather than becoming separate preliminary projects.

Release 1 provides exactly two user-selectable templates: one four-way intersection and two four-way intersections in sequence. Users configure entrance rates, turning weights, road-lane lights, signal durations, initial colors, and a random seed before running. Release 1 demonstrates moving cars; analytics and presentation are future work.

## User Stories

### Release 1: simulation and roads

1. As a simulation user, I want a Kotlin backend that runs without a frontend, so that I can develop and inspect traffic behavior first.
2. As a simulation user, I want to choose a single-intersection or two-intersection template, so that I can start from a predefined scene.
3. As a simulation user, I want to configure a scene before starting a run, so that I can try different traffic conditions without changing the simulation rules.
4. As a simulation user, I want to advance exactly one simulated tick per request, so that I can inspect movement without depending on real-time pacing.
5. As a simulation user, I want to read the current state without advancing time, so that inspection does not alter a run.
6. As a simulation user, I want every first-release road to carry one lane in each direction, so that the initial road model stays simple and two-way.
7. As a simulation user, I want each tile to belong to its own lane, so that matching position numbers in opposite lanes remain distinct.
8. As a simulation user, I want at most one car on any tile, so that cars do not overlap in a lane.
9. As a simulation user, I want each existing car to advance at most one tile per tick, so that movement has a clear discrete speed.
10. As a simulation user, I want a following car to use a tile its leader successfully vacates that tick, so that ordinary traffic can move together without artificial gaps.

### Release 1: arrivals and exits

11. As a simulation user, I want a configured arrival rate for each entrance direction, so that I can create different levels of incoming traffic.
12. As a simulation user, I want cars to continue becoming due while I advance the simulation, so that traffic is continuous rather than a finite initial batch.
13. As a simulation user, I want deterministic fractional rates expressed in cars per tick, so that low arrival rates have predictable schedules.
14. As a simulation user, I want a due car to remain pending when its entrance is occupied, so that congestion does not silently discard demand.
15. As a simulation user, I want each entrance to admit at most one car per tick into its first tile when free, so that admission respects lane capacity.
16. As a simulation user, I want newly admitted cars to wait until the next tick for further movement, so that entry does not give them an extra move.
17. As a simulation user, I want cars removed when they leave through a scene exit, so that departed cars no longer occupy the network.
18. As a simulation user, I want cars to revisit intersections when a scene permits circulation, so that the engine supports looping networks without fixed destinations.

### Release 1: traffic lights

19. As a simulation user, I want to place a traffic light at any valid road-lane tile, so that lights can control mid-road positions as well as intersection approaches.
20. As a simulation user, I want a light to affect only its placed lane and direction, so that the opposite direction can continue independently.
21. As a simulation user, I want a car to reach the light's tile but stay there on red, so that the stopping position matches the configured location.
22. As a simulation user, I want separately configurable red and green durations, so that I can vary each part of a signal cycle.
23. As a simulation user, I want to configure a light's initial color, so that a run can begin with different signal states.
24. As a simulation user, I want twenty red movement ticks followed by permitted movement on tick twenty-one, so that phase boundaries are unambiguous.
25. As a simulation user, I want every light to run on its own timer initially, so that independent signals form the baseline for later coordination.
26. As a simulation user, I want a car that has passed a light to remain unaffected when it turns red, so that the signal only gates its actual stopping position.

### Release 1: intersections and turning

27. As a simulation user, I want straight, left, and right movements where connections allow them, so that cars can take different onward roads without U-turns.
28. As a simulation user, I want turning weights configured separately for each incoming approach, so that each direction can have its own outgoing-road probabilities.
29. As a simulation user, I want a fresh seeded random choice at each intersection visit, so that cars follow the configured turning distribution.
30. As a simulation user, I want a car to keep its chosen movement while waiting, so that blocked traffic does not redraw its way into another route.
31. As a simulation user, I want another fresh choice at a later intersection or revisit, so that a car's entire journey is not predetermined at entry.
32. As a simulation user, I want cars to traverse ordered positions through intersections, so that crossing still consumes one-tile movements over time.
33. As a simulation user, I want opposing straight movements to cross together, so that compatible traffic can proceed concurrently.
34. As a simulation user, I want physically conflicting paths prevented from crossing together even when their lane positions differ, so that tile identity alone cannot conceal a collision.
35. As a simulation user, I want cars to wait before entering when outgoing space is unavailable, so that they do not enter and become stranded inside an intersection.
36. As a simulation user, I want an admitted car's outgoing space secured during its crossing, so that another movement cannot take the space it needs to leave.
37. As a simulation user, I want a following car to wait before entering the same reserved crossing path, so that the accepted conservative crossing rule remains safe and simple.
38. As a simulation user, I want deterministic rotating priority among eligible approaches, so that a fixed iteration order does not permanently favor one competing direction.

### Release 1: inspection and configuration

39. As a simulation user, I want the same configuration and seed to produce the same run, so that I can reproduce traffic behavior and investigate bugs.
40. As a simulation user, I want immutable snapshots containing tick, car positions, light colors, and pending entrance counts, so that I can inspect current and earlier states safely.
41. As a simulation user, I want invalid scene and light configuration rejected before advancement, so that mistakes fail clearly rather than corrupting the run.
42. As a simulation user, I want a console runner that selects a template and advances a chosen number of ticks, so that I can exercise the backend without building a desktop interface.

### Release 2 and later: agreed goals, not detailed behavior

43. As a simulation user, I want coordinated traffic lights, so that I can experiment with the central motivation for building the simulator.
44. As a simulation user, I want alternative traffic-light control approaches, so that I can explore behavior beyond independent fixed timers.
45. As a simulation user, I want traffic analytics, so that I can measure and compare experiments after the movement prototype works.
46. As a simulation user, I want configured entrance rates to change during a run, so that demand can vary without restarting.
47. As a simulation user, I want rush-hour demand scenarios, so that I can explore traffic pressure changing over time.
48. As a simulation user, I want one-way roads, so that scene layouts can represent more road arrangements.
49. As a simulation user, I want lane counts configured independently per direction, so that roads can have different directional capacities.
50. As a simulation user, I want roundabouts, so that I can explore another kind of junction.
51. As a simulation user, I want variable car behavior, so that traffic need not consist entirely of identical drivers.
52. As a simulation user, I want desktop presentation and controls, so that I can see and interact with the backend simulation.

## Implementation Decisions

### Domain and module shape

- Use Kotlin and moderate object-oriented design. Prefer concrete classes and value objects; avoid a hierarchy or configurable policy interface for every concept.
- The core simulation module owns advancement, car state, entrance buffers, seeded choices, crossing reservations, and fair admission. Keep its public interface small: construct from configuration, advance one tick, and read the current snapshot.
- This simulation interface is the primary seam for callers and tests. The user reviewed and accepted it with the design; no additional seam interview is needed.
- Road, Lane, and Tile model directional road positions. Tile identity includes its lane and position. A matching position in another lane is a different tile.
- Intersection and Movement model permitted connections and ordered connector paths. A connector is a short directed lane through an intersection. Road-lane occupancy and shared physical crossing conflicts are distinct concepts.
- Car records identity, current tile, and the movement selected for its current intersection visit.
- TrafficLight records its road-lane tile, separate red and green durations, and initial color. The lane determines the controlled direction.
- Entrance identifies an entering lane and its rate. RoadNetwork holds the scene topology, permitted connections, entrances, and explicit exits. SimulationConfig combines those definitions, placed lights, and a seed.
- Topology and configuration remain immutable during a release-one run. Simulation owns mutable state; snapshots expose immutable copies of tick number, car positions, movement-tick light colors, and pending entrance counts.
- Templates are ordinary factories. A console runner is a caller of the same simulation module. Real-time pacing belongs to callers; constructing another simulation restarts an empty run.

### Movement and arrivals

- Every release-one road is two-way, with exactly one lane per direction. Each car occupies one tile and makes at most one tile advance in a tick. Moving from the last incoming road tile to the first connector tile consumes one tick; moving from the last connector tile to the first outgoing road tile also consumes one tick. Neither boundary transition grants an additional movement.
- Existing cars move simultaneously. A follower may enter a tile vacated by an accepted move that tick; if the leading move fails, dependent following moves also fail.
- New runs begin empty, with no immediate initial arrival. Deterministic demand accumulates from the first advance. At rate 0.5 cars per tick, arrivals become due on ticks two, four, six, and so on.
- Rates remain fixed during release one and may be zero or fractional. Preserve the configured decimal schedule without cumulative binary floating-point drift. Rates above physical entrance capacity create pending demand rather than overlapping cars or discarded arrivals.
- Due demand goes into an entrance buffer. Buffered arrivals remain valid indefinitely; no silent loss or arbitrary capacity cutoff is part of this release. Pending identical arrivals may be represented as a count rather than allocated off-scene Car objects.
- After existing moves commit, an entrance may admit one pending car into its free first tile, including a tile vacated during the same tick. Admission consumes the car's placement for that tick; it receives no additional advance until the next tick.
- Cars leave the simulation when their movement crosses an explicit scene exit. Template-supported loops are permitted without fixed destinations or a guarantee of eventual departure.

### Turning and lights

- Support straight, left, and right movements where connected by a template. Do not include U-turns.
- Each incoming approach has its own weights across legal outgoing movements, summing to one or equivalently one hundred percent. Invalid weights or missing legal choices fail validation.
- Choose a movement once when a car reaches an intersection approach, using the run's seeded random generator. Retain it through waiting and crossing. A subsequent intersection visit, including a revisit, receives a fresh choice.
- Keep draw order and all contention ordering stable so configuration and seed determine the snapshot sequence.
- Lights can occupy any valid road-lane tile. Red prevents departure from that tile, including entry into an intersection or departure through an exit when applicable. It does not prevent reaching the light tile or affect a car already beyond it.
- Opposite lanes and all distinct lights remain independent. Crossing admission still enforces safety if conflicting approaches both have green.
- Each light has positive red and green durations and a configurable initial color, default red. It begins with the full duration of that initial phase.
- A twenty-tick red phase gates movement ticks one through twenty. The twenty-first movement tick uses green. Snapshots report the colors used for their completed movement tick, avoiding ambiguity in traces.
- Release-one lights attach to road lanes, not connector positions inside intersections.

### Intersection admission

- Templates declare shared crossing resources for physically overlapping movements. Distinct connector tile identities do not imply that paths are physically compatible.
- Opposing straight movements must be compatible and able to proceed together. Movements that use shared crossing space, or need the same receiving tile, cannot obtain conflicting reservations.
- Before a car enters, acquire the needed crossing resources and the first outgoing tile together. If either cannot be secured, the car waits on the incoming road, even when green and the first connector position is empty.
- An occupied outgoing tile can become available through an accepted departure in the same tick. A request to depart that is itself blocked does not make that tile available.
- Hold the crossing reservation until the car clears its connector. Reserve outgoing space so another admitted movement cannot take the receiving tile during the crossing. Committing the move into the first outgoing tile releases both reservations; ordinary car occupancy then protects that tile until the car actually departs it.
- A following car waits before entering the same reserved connector, even if the preceding car vacates the first connector tile. This accepted admission restriction is the explicit exception to ordinary same-tick following.
- Resolve eligible competing approaches with deterministic rotating priority, skipping requests whose actual transition is blocked. Losing contention does not redraw a chosen movement. An upstream light turning red does not block a car that already passed its tile.
- This conservative approach favors a simple, safe prototype over maximum crossing throughput. It prevents admission without outgoing space; it does not promise freedom from congestion or gridlock across an entire network.

### Tick contract

Each advance follows this observable sequence:

1. Determine the light colors for this movement tick.
2. Accumulate deterministic demand and place newly due arrivals into entrance buffers.
3. Make any new per-visit movement choices in stable order and construct at most one move request per existing car.
4. Resolve directional light gates, crossing admission, conflicting requests, and dependencies on vacated tiles.
5. Commit accepted car movements together, remove cars at exits, and release completed crossings.
6. Admit buffered cars into free entrance tiles without moving them again.
7. Return the completed tick's immutable snapshot.

### Scene and configuration contract

- Provide exactly two public templates: one four-way intersection, and two four-way intersections in sequence joined by one two-way road. The remaining approaches lead to scene boundaries.
- Templates define road lengths, connector lengths, permitted paths, and crossing conflicts. Initial fixture or loop networks used by tests are not extra user-selectable templates.
- Users can configure entrance rates, approach weights, light positions, durations, initial colors, seed, and the console runner's number of ticks. Release one does not include live editing of topology or rates.
- Reject invalid references or illegal connections, nonpositive lane lengths and signal durations, negative or nonfinite rates, invalid or negative weights, incorrect weight totals, and approaches without a positive legal outgoing choice.
- Reject duplicate lights on the same road-lane tile. Lights at matching positions in opposite lanes are valid and independent.
- Configuration syntax, console text format, precise template dimensions, the random generator implementation, and internal ordering data structures are implementation choices. Document them when selected; do not reopen settled domain behavior.

### Release-one increments

Every increment must run through the same simulation interface, demonstrate its behavior headlessly, and pass the acceptance scenarios relevant to its scope. Integrate into the previous increment rather than creating a parallel engine.

| Increment | Deliverable | Depends on |
| --- | --- | --- |
| R1.1 | Deterministic road simulation | None |
| R1.2 | Independent timed lights | R1.1 |
| R1.3 | One complete four-way intersection | R1.1–R1.2 |
| R1.4 | Two intersections in sequence | R1.3 |
| R1.5 | Configurable, verified prototype | R1.1–R1.4 |

#### R1.1 — Deterministic road simulation

- Deliver a working clock, snapshots, two-way road/lane/tile model, car movement, simultaneous following, deterministic arrivals, entrance buffers, and removal at exits.
- Use a small road fixture for the first runnable example. It is a development fixture, not a third public template.
- Include validation for the configuration supported by this increment and a minimal headless trace demonstrating cars moving from entrance to exit.
- Done when successive snapshots show distinct opposing lanes, single-tile movement, safe occupancy, exact fractional demand, preservation of pending arrivals under over-capacity demand, and admission without an extra move.
- The simulation must be runnable at this checkpoint. A set of classes with no advancing engine is insufficient.

#### R1.2 — Independent timed lights

- Add lights on arbitrary valid road-lane positions with independent timers, separate red/green durations, and configurable initial colors.
- Exercise the existing engine with stopped traffic, queues, and releases. Preserve the movement/admission sequence from R1.1.
- Done when a car reaches the light tile and cannot leave on red; other lanes remain independent; a previously passed light does not stop the car; and twenty red movement ticks permit movement on tick twenty-one.
- The light-controlled road example must run and be inspectable through snapshots.

#### R1.3 — One complete four-way intersection

- Deliver the first public template, permitted straight/left/right connector paths, per-approach turning weights, seeded per-visit choices, and physical conflict declarations.
- Add conservative crossing and outgoing-space reservations, rotating eligible-approach priority, and the accepted same-connector admission restriction.
- Done when opposing straight traffic can coexist, conflicting greens cannot create overlapping crossings, merges cannot take the same receiving tile, and a blocked outgoing lane keeps a car outside the intersection.
- Show that a chosen turn survives waiting, traversal advances one connector position per tick, and an admitted crossing retains its secured outgoing space.
- The single-intersection scene must run end to end, including lights and continuous arrivals.

#### R1.4 — Two intersections in sequence

- Deliver the second public template using the same engine and model, with one connecting two-way road and independent signal configurations.
- Verify fresh choices at subsequent intersection visits, queues on the connecting road, downstream blockage, and exits at the scene boundary.
- Use an internal loop fixture to verify supported revisits; do not introduce another public template.
- Done when cars can traverse both intersections, congestion is handled safely across the connection, turns are chosen per visit, pending demand is retained, and cars disappear after leaving.

#### R1.5 — Configurable, verified prototype

- Complete the user configuration entry point and console runner: choose either template, configure demand and turning weights, place lights, set durations and initial colors, choose a seed, and advance a selected number of ticks.
- Consolidate configuration errors, document the selected input and output conventions, and provide runnable representative scenarios.
- Run the complete acceptance suite through the same simulation seam. New construction restarts a run; snapshot inspection never advances it or exposes mutable internal state.
- Done when the entire agreed prototype can be exercised without frontend code, invalid configurations fail before advancement, and repeated runs with the same configuration and seed produce matching snapshots.

## Testing Decisions

- Test external behavior through the approved simulation seam: construct a configured scene, advance ticks, and inspect snapshots. This is the same seam used by callers. Do not introduce test-only mutation hooks or mock every domain class.
- Test good outcomes, boundary timing, and invariants. Do not mirror internal loop order, class decomposition, private reservation containers, or random-generator bookkeeping.
- The primary tested module is Simulation, including integrated arrivals, movement, directional light gates, turning, and crossing safety. Exercise template validity and configuration failure through construction and real runs. A small console smoke check verifies that the runner can use both templates.
- There is no existing code or test suite, so there is no repository prior art to reuse. Establish scenario-based behavior tests with minimal fixtures, then retain them as later increments add features.
- Add the tests belonging to each increment alongside its behavior. Keep earlier tests passing; do not postpone verification until R1.5.
- Use forced turning weights for exact route assertions and fixed seeds for reproducibility. Avoid flaky statistical thresholds as a substitute for deterministic behavioral checks.

### Required acceptance scenarios

| Scenario | Observable requirement | First checkpoint |
| --- | --- | --- |
| Initial state and observation | Tick zero is empty; repeated snapshot reads do not advance or change state. | R1.1 |
| Opposite-lane positions | Equal position numbers in opposite lanes are distinct and can each hold one car. | R1.1 |
| Movement distance and occupancy | An existing car advances no more than one position per tick; no tile contains two cars. | R1.1 |
| Following chain | Cars follow into tiles vacated by successful moves. A blocked leader prevents dependent following moves. | R1.1 movement; R1.2 blocking |
| Fractional demand | Rate 0.5 makes one car due on each of ticks two, four, six, with no immediate arrival. Other configured decimal rates keep their exact schedules. | R1.1 |
| Over-capacity demand | Demand above admission capacity accumulates pending arrivals without loss or overlapping placement. | R1.1 |
| Admission boundary | An entrance vacated this tick can admit one pending car after movement; that car does not advance again. | R1.1 |
| Scene exit | Departed cars no longer appear in snapshots or block following cars. | R1.1 |
| Light stopping tile | Red allows reaching the light tile but prevents departing from it in that lane. | R1.2 |
| Timer boundary | A light initially red for twenty movement ticks stays red through tick twenty; tick twenty-one is green and can permit departure. Distinct red/green durations cycle correctly; an initially green light uses its full configured green duration. | R1.2 |
| Light independence | Different lanes and lights run independently, and a car beyond an upstream light keeps moving when it turns red. | R1.2 |
| Legal weighted turn | Different approaches with different forced weights each choose their own specified legal road; invalid distributions and U-turn connections are rejected. | R1.3 |
| Retained choice | A waiting car retains its chosen movement; losing contention does not trigger a new choice. | R1.3 |
| Multi-position traversal | The car visibly visits successive connector positions, with one full tick for connector entry and one for connector departure; crossing is not an instantaneous hop. | R1.3 |
| Opposing straight paths | Both can be inside their compatible crossings at the same time. | R1.3 |
| Physical crossing conflict | Simultaneous green does not let incompatible paths use shared physical crossing space together, even with distinct tile identities. | R1.3 |
| Merge and receiving space | Two movements cannot secure the same outgoing tile. A blocked exit keeps entrants outside. | R1.3 |
| Successful versus blocked departure | Outgoing space qualifies when its occupant actually leaves this tick; a blocked departure request does not qualify. | R1.3 |
| Same-connector restriction | A follower waits before entering while the preceding crossing reservation is held, despite a free first connector tile; it can proceed after clearance. | R1.3 |
| Rotating eligible priority | Sustained feasible competition allows each eligible approach to progress, without claiming progress for infeasible requests. | R1.3 |
| Subsequent visits | A car arriving at another intersection or revisiting through a loop gets a fresh per-visit choice; waiting within one visit does not. | R1.4 |
| Connected scenes | The two-intersection template handles traffic and queues across its connecting road using the same rules. | R1.4 |
| Repeatable runs | Identical complete configurations and seeds produce matching snapshot sequences for both public templates. | R1.3; both templates in R1.4 |
| Snapshot ownership | Previously returned snapshots remain unchanged after later steps and cannot mutate the simulation. | R1.1; full regression in R1.5 |
| Invalid configuration | Illegal references, bounds, lengths, durations, rates, weights, missing connections, and duplicate lights are rejected before the first advance. | As features appear; complete in R1.5 |
| Headless use | Both public templates run with configured parameters for a chosen number of ticks using the documented console entry point. | R1.5 |

Future increments must preserve the existing simulation checks and extend them only for their actual new behavior. Their additional acceptance rules are not invented in this specification.

## Out of Scope

- Release-one implementation excludes desktop or web presentation, analytics, coordinated lights, alternative controllers, changing rates during a run, rush-hour profiles, roundabouts, one-way roads, multiple lanes per direction, and variable car behavior. Their agreed goals remain in the future roadmap below.
- U-turns are excluded and are not automatically scheduled for release two.
- Do not add overtaking, spontaneous rerouting, global deadlock recovery, continuous physics, a general geometry engine, persistence, distributed execution, an event bus, or a hierarchy for hypothetical future behaviors.
- Do not promise real-world traffic fidelity, optimal signal timing, maximum intersection throughput, or that every car in a loop eventually exits.
- The original specification task ended with accepted design records. Maven project setup is complete. The current request authorizes domain entities and configuration only; the simulation engine and runnable increments remain future work.

## Further Notes

### Release-two and future roadmap

The following order is recommended so the existing simulation remains the foundation. These are planning increments for already requested goals, not implementation-ready behavioral specs. Refine each before it is picked up; do not silently infer missing algorithms or rules.

| Future increment | Agreed goals | Dependency and refinement needed |
| --- | --- | --- |
| R2.1 — Coordinated lights | Central release-two goal: coordinate signals; subsequently support selected alternative control approaches. | Build on R1.5. Choose grouping, phase rules, configuration, and the specific control approaches before implementation. Retain independent timers as the baseline behavior. |
| R2.2 — Changing demand | Change configured entrance rates during a run, then build rush-hour demand scenarios. | Build on the completed arrivals engine. Define when changes take effect, how pending demand is preserved, and whether changes are manual or scheduled. |
| R2.3 — Analytics | Measure and compare traffic experiments. | Build on the stable simulation. Select metrics and define them precisely; completed journeys, throughput, queue length, and waiting time are candidates, not an agreed mandatory set. |
| R2.4 — More road configurations | Add one-way roads and configurable lane counts per direction. | Define distribution across lanes, permitted connections, and any needed lane-selection or lane-changing rules. Lane changing is not silently required by lane-count configuration. |
| R2.5 — Roundabouts | Add roundabout scenes and traffic movement. | Define circulation, entry priority, permitted exits, and conflict handling. Use the directional-lane model where suitable; additional road configurations may be useful but are not a settled prerequisite. |
| R2.6 — Variable car behavior | Introduce selected differences in how cars behave. | Choose the behavior parameters and movement rules, then add variation where it is actually used. No particular speed, acceleration, or driver model has been chosen. |
| R2.7 — Desktop application | Present the simulation and provide desktop controls. | Build on the same simulation interface. Choose toolkit, rendering, layout, and interactions later; a frontend is not a prerequisite for any release-one increment. |

These future increments can be reordered as priorities develop. Runtime demand changes, analytics, and road generalization do not depend on coordinated-light implementation merely because they appear later in the table.

### Handoff and publication

- The accepted design, domain glossary, and ADRs supply the terminology and rationale for this spec. The release-two backlog remains the place to retain and update future goals.
- Keep the increment dependencies and their done conditions when creating implementation tasks. Each R1 increment should be independently reviewable, runnable, and covered through the same simulation seam.
- The project repository is https://github.com/VozVule/Traffic-sim. No issue tracker workflow or project-specific triage vocabulary is configured. The requested issue-tracker label is ready-for-agent; no label or issue was created. Current work is recorded in docs/tasks/.
- Run /setup-matt-pocock-skills to provide the issue-tracker setup required by the invoked to-spec workflow. Publication is the outstanding workflow step, not a reason to leave the local specification incomplete.
