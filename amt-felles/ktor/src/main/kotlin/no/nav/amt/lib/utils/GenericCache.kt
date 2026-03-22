package no.nav.amt.lib.utils

import java.util.UUID

data class GenericCache<T>(
    private val cacheName: String,
    private val itemMap: Map<UUID, T>,
) {
    constructor(
        cacheName: String,
        items: List<T>,
        idSelector: (T) -> UUID,
    ) : this(
        cacheName = cacheName,
        itemMap = items.associateBy(idSelector),
    )

    fun getOrThrow(id: UUID): T = itemMap[id]
        ?: throw NoSuchElementException("Fant ikke entry med id $id i cache $cacheName")
}
