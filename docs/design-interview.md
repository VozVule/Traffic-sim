# Traffic simulator design interview

Status: concluded; shared understanding accepted on 2026-10-11. The final design, including its conservative crossing restriction, was confirmed by the user. The subsequent task 1 request authorizes implementation of R1.1 using Maven.

## Stated constraints

- Use Kotlin.
- Focus on the backend first; the intended eventual application is desktop.
- Use object-oriented design with classes and abstractions, without excessive abstraction.
- Users select predefined scene templates.
- Start with red/green traffic lights and configurable timers. Twenty ticks is an example duration, not an agreed fixed value.
- Cars occupy one tile and advance at most one tile per tick, waiting when movement is blocked.

## Agreed in round one

- At each intersection visit, a car receives a fresh random value that selects its outgoing road according to configured weights. The weights sum to one, or equivalently to one hundred percent.
- Random choice happens again at each subsequent intersection visit. This implies one choice per visit, rather than repeated choices on every tick while waiting.
- A tile is a position along a lane, rather than a square in a required global grid.
- The first phase is a prototype of moving cars. Analytics come later.
- Cars arrive continuously at a configured rate from each direction while the simulation runs. The finite-total proposal was not adopted.
- Traffic lights may be placed anywhere there is a road, including away from intersections. The designated-stop-position restriction was not adopted.
- Roundabouts and analytics are future features tracked in `../release-2.md`, together with the future features from the initial brief.

## Agreed in round two

- Every first-phase road is two-way, with one lane in each direction. One-way roads and configurable lane counts per direction belong in release 2.
- Straight, left, and right turns are available where road connections permit them. U-turns are outside the current scope and are not a committed future feature.
- A car occupies one tile. The prototype prevents overlapping car occupancy and blocked cars wait.
- A light affects only the direction/side where it is placed. Traffic in the other direction remains unaffected.
- The user specifies that cars cannot get past the tile where a light is placed. Round three resolves this as stopping on that tile rather than before entering it.
- Each light has an independent timer. Coordinated lights are a central purpose of release 2.
- Arrivals are deterministic. An arrival blocked by an occupied entrance remains valid and pending in an entrance buffer until it can enter.
- A car is removed when it leaves the scene. Cars may revisit intersections or circulate when the template permits it.
- The same configuration and random seed must reproduce the same run.
- Configured entrance rates changing during a running simulation are explicitly in release 2.

## Agreed in round three

- Turning weights are separate per incoming direction, summing to one across the permitted outgoing roads.
- A tile belongs to a particular lane. Position ten in one lane and position ten in the opposite lane are different tiles; one car may occupy each. A red light on position ten prevents proceeding past that position in its controlled lane.
- Intersections use multiple internal positions along their movement paths. Compatible paths, including opposing straight movements, can operate together.
- A car may move into a tile vacated by another successful move in the same tick. Successful movements take effect together.
- A red phase lasting twenty ticks blocks movement for movement ticks one through twenty. Movement past the light becomes possible on movement tick twenty-one.
- Red and green durations are independently configurable per light. This overrides the proposed common switching interval in Q20; the Q19 answer resolves both duration and transition behavior.
- Deterministic rates may be fractional cars per tick. The recommendation accepted in Q21 starts with an empty scene and no immediate arrival; for rate 0.5, arrivals become due on ticks two, four, six, and so on. Actual admission may be delayed by an occupied entrance.

At the end of round three, initial template contents and intersection admission/conflict policy remained open. Rate semantics were settled.

## Agreed in round four

- Avoid blocking an intersection: a car waits until there is free space to proceed. Green and a free first crossing position are insufficient when the receiving outgoing lane is blocked. The conservative reservation mechanism was subsequently accepted in the final design review.
- Exactly two starting templates: one four-way intersection, and two four-way intersections in sequence. The proposed three-intersection corridor was not selected.

## Design tree outcome

The user confirmed shared understanding of the concrete Kotlin model and nonblocking entry rule in `phase-1-design.md`, including the reservation rule that makes a following car wait before entering the same occupied crossing. The interview is complete.

### Resolved implementation decisions

- The final accepted design covers declared path conflicts, safe entry reservations, fair deterministic arbitration, the tick sequence, template details, initial signal color, entrance admission, and configuration validation.
- At the close of this interview, the user requested an accepted design and incremental specification. The subsequent task 1 request authorizes implementation of R1.1 using Maven.
- Buffering never discards pending arrivals. Loops are permitted without a promise that every car eventually departs. No global liveness guarantee is assumed.

The approved simulation interface is the primary test seam. It was reviewed with the user before acceptance and does not require another interview.

## Records

- Domain terms are recorded in `../GLOSSARY.md`.
- Future scope is recorded in `../release-2.md`.
- The accepted concrete design is recorded in `phase-1-design.md`.
- Significant accepted trade-offs are recorded in `adr/`. No ADR is used for an unresolved recommendation.
