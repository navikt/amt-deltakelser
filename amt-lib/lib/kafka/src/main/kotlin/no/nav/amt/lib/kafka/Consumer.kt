package no.nav.amt.lib.kafka

interface Consumer<K, V> {
    fun start()

    suspend fun close()
}
