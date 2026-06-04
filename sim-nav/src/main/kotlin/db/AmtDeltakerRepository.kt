package db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.time.Duration
import java.util.*

data class DeltakerOption(
    val id: UUID,
    val deltakerlisteNavn: String,
    val status: String?,
)

class AmtDeltakerRepository {
    private val dataSource: HikariDataSource

    init {
        val host = System.getenv("DB_HOST") ?: "localhost"
        val port = System.getenv("DB_PORT")?.toIntOrNull() ?: 5432
        val dbUser = System.getenv("DB_USER") ?: "myuser"
        val dbPassword = System.getenv("DB_PASSWORD") ?: "mypassword"
        val jdbcUrl = "jdbc:postgresql://$host:$port/amt_deltaker_db"

        val config = HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            username = dbUser
            password = dbPassword
            maximumPoolSize = 3
            minimumIdle = 1
            connectionTimeout = Duration.ofSeconds(10).toMillis()
            idleTimeout = Duration.ofMinutes(5).toMillis()
            maxLifetime = Duration.ofMinutes(30).toMillis()
            leakDetectionThreshold = Duration.ofMinutes(5).toMillis()
        }

        dataSource = HikariDataSource(config)
        println("✓ amt-deltaker database connected: $jdbcUrl")
    }

    fun getDeltakereForPersonident(personident: String): List<DeltakerOption> {
        val sql = """
            SELECT d.id, dl.navn AS deltakerliste_navn, ds.type AS status
            FROM deltaker d
            JOIN nav_bruker nb ON d.person_id = nb.person_id
            JOIN deltakerliste dl ON d.deltakerliste_id = dl.id
            LEFT JOIN deltaker_status ds ON ds.deltaker_id = d.id AND ds.gyldig_til IS NULL
            WHERE nb.personident = ?
            ORDER BY d.modified_at DESC
        """.trimIndent()

        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, personident)
                stmt.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                DeltakerOption(
                                    id = UUID.fromString(rs.getString("id")),
                                    deltakerlisteNavn = rs.getString("deltakerliste_navn"),
                                    status = rs.getString("status"),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    fun shutdown() {
        dataSource.close()
    }
}

