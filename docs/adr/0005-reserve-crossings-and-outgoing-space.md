# Reserve a crossing and outgoing space before entering

A car secures its crossing resources and the first outgoing tile before entering an intersection, then holds the crossing reservation until it clears the connector. This trades finer-grained crossing throughput for straightforward collision prevention and nonblocking entry: a following car waits before entering the same reserved path, while opposing straight movements with disjoint resources may cross together.
