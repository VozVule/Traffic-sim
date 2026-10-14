package trafficsimulator.domain

import java.util.Collections

internal fun requireIdentifier(value: String, field: String) {
    require(value.isNotBlank()) { "$field must not be blank" }
}

internal fun <T> ownedList(values: Collection<T>): List<T> =
    Collections.unmodifiableList(ArrayList(values))

internal fun <T> ownedSet(values: Collection<T>): Set<T> =
    Collections.unmodifiableSet(LinkedHashSet(values))

internal fun <K, V> ownedMap(values: Map<K, V>): Map<K, V> =
    Collections.unmodifiableMap(LinkedHashMap(values))

internal fun <T> indexById(values: Iterable<T>, kind: String, id: (T) -> String): Map<String, T> {
    val index = LinkedHashMap<String, T>()
    for (value in values) {
        val identifier = id(value)
        require(index.putIfAbsent(identifier, value) == null) { "Duplicate $kind ID '$identifier'" }
    }
    return ownedMap(index)
}
