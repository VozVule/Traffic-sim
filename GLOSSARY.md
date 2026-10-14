# Traffic Simulation

The domain of cars moving through roads and junctions under traffic controls.

## Language

**Tick**:
A discrete unit of simulated time.
_Avoid_: Frame

**Car**:
A vehicle whose journey through the road network is simulated.
_Avoid_: Agent

**Tile**:
A discrete position within a particular lane. Tiles in different lanes are distinct, even when they lie beside one another.
_Avoid_: Grid square

**Lane**:
An ordered sequence of positions for travel in one direction.

**Road**:
A stretch of the road network containing lanes for car travel.

**Intersection**:
A meeting of roads with paths connecting incoming and outgoing lanes.

**Approach**:
The incoming lane by which a car reaches an intersection.

**Turning weights**:
The probabilities assigned to permitted outgoing roads for a particular intersection approach.

**Movement**:
A permitted path through an intersection from an incoming lane to an outgoing lane.

**Crossing conflict**:
An incompatibility between movements whose paths use the same physical part of an intersection.

**Traffic light**:
A signal at a road position that grants or withholds permission to proceed in its controlled direction.

**Arrival rate**:
The configured demand for cars entering from a particular direction per unit of simulated time.

**Entrance**:
A location where cars enter the scene along a lane.
_Avoid_: Spawn point

**Entrance buffer**:
The backlog of due cars waiting to enter the scene at an entrance.

**Exit**:
A location where a car leaves the scene.

**Template**:
A predefined road arrangement that a user can select as the starting scene.
_Avoid_: Preset scene

**Scene**:
The arrangement of roads, intersections, entrances, and exits in which cars are simulated.
