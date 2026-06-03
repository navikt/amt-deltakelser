package db

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Initializes database schema (creates tables if they don't exist).
 */
object DatabaseSchema {

    fun initialize() {
        try {
            transaction {
                SchemaUtils.create(ValpGjennomforing, ValpTiltakstype)
                println("✓ Database schema initialized")
            }
        } catch (e: Exception) {
            println("✗ Failed to initialize database schema: ${e.message}")
            e.printStackTrace()
        }
    }
}

