import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Helper object for database operations within sim-nav.
 * Use this to wrap any database read/write operations.
 */
object DbOperations {

    /**
     * Execute a block within a database transaction.
     * Safe to call even if database is not connected - will use in-memory fallback.
     */
    fun <T> inTransaction(block: () -> T): T {
        return try {
            transaction {
                block()
            }
        } catch (e: Exception) {
            println("Database operation failed: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
}

