# Release 2 and future work

This is the backlog beyond the initial moving-car prototype. These features come from the user's stated plans; their order and exact release assignment are not decided yet.

The accepted release-one scope and proposed future increment order are captured in [SPEC.md](SPEC.md). Future behavioral details remain to be designed; the roadmap does not make every backlog item implementation-ready.

- [ ] **Roundabouts.** Define circulation, permitted exits, entry rules, and interactions with approaching cars.
- [ ] **Analytics.** Add measurements and reporting for traffic experiments. Candidate measures to discuss include completed journeys, throughput, queue length, and waiting time.
- [ ] **Coordinated traffic lights.** A central goal of release 2: coordinate multiple signals, building on the independent timers of the prototype. Define control groups and how their timing is configured.
- [ ] **Alternative traffic-light behavior.** Extend the initial timer-based signals with additional control approaches. The specific approaches are still to be selected.
- [ ] **One-way roads.** Allow roads with travel in only one direction.
- [ ] **Configurable lane counts.** Allow the number of lanes to be configured separately for each direction. Rules for choosing or changing lanes still need discussion.
- [ ] **Variable car behavior.** Allow differences in how cars behave. The parameters and behavior rules are still to be selected.
- [ ] **Change entrance rates during a run.** Allow configured arrival rates to change while the simulation is running. The mechanism for applying changes is still to be selected.
- [ ] **Rush-hour scenarios.** Build time-varying demand scenarios using entrance rates that can change during a run.
- [ ] **Desktop presentation and controls.** Add a desktop interface to the backend simulation. Toolkit and interaction design are deferred.

## Already in phase one

- Kotlin backend with moderate object-oriented design.
- Predefined road templates.
- Two initial templates: one four-way intersection and two four-way intersections in sequence.
- Every road is two-way, with one lane in each direction.
- Tile positions belonging to individual lanes and discrete-time car movement.
- Multi-position intersection paths, allowing compatible movements to coexist.
- Straight, left, and right turns, with fresh weighted outgoing-road choices at each intersection visit and separate turning weights per incoming direction. U-turns are outside the current scope.
- Continuous deterministic arrivals from each direction, with pending cars buffered at occupied entrances.
- Traffic lights anywhere along roads, affecting their placed direction and using independent timers with separately configurable red and green durations.
- Removal at scene exits; circulation is allowed when the template permits it.
- Waiting before entering an intersection when the outgoing space is unavailable.
- Repeatable runs for the same configuration and random seed.

Additional features will be added here when explicitly deferred during the design interview. This file does not silently assign unresolved first-phase rules to a later release.
