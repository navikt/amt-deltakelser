package db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import java.time.Duration

object DatabaseConfig {

    private lateinit var dataSource: HikariDataSource

    fun initialize() {
        try {
            val host = System.getenv("DB_HOST") ?: "localhost"
            val port = System.getenv("DB_PORT")?.toIntOrNull() ?: 5432
            val dbName = System.getenv("DB_NAME") ?: "simnav_db"
            val dbUser = System.getenv("DB_USER") ?: "myuser"
            val dbPassword = System.getenv("DB_PASSWORD") ?: "mypassword"

            val jdbcUrl = "jdbc:postgresql://$host:$port/$dbName"

            val config = HikariConfig().apply {
                this.jdbcUrl = jdbcUrl
                username = dbUser
                password = dbPassword
                maximumPoolSize = 5
                minimumIdle = 1
                connectionTimeout = Duration.ofSeconds(10).toMillis()
                idleTimeout = Duration.ofMinutes(5).toMillis()
                maxLifetime = Duration.ofMinutes(30).toMillis()
                leakDetectionThreshold = Duration.ofMinutes(5).toMillis()
            }

            dataSource = HikariDataSource(config)

            // Initialize Exposed with the datasource
            Database.connect(dataSource)

            println("✓ Database connected: $jdbcUrl")
        } catch (e: Exception) {
            println("✗ Failed to initialize database: ${e.message}")
            e.printStackTrace()
        }
    }

    fun shutdown() {
        dataSource.close()
    }

}