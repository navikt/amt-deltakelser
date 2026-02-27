package no.nav.amt.distribusjon.utils

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine

class CountingCache<K : Any, V>(
    private val delegate: Cache<K, V> = Caffeine.newBuilder().build(),
) : Cache<K, V> by delegate {
    var putCount = 0

    override fun put(key: K, value: V & Any) {
        putCount++
        delegate.put(key, value)
    }
}
