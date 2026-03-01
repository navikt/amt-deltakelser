package no.nav.amt.deltaker.bff.navenhet

import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.utils.database.Database
import java.util.UUID

class NavEnhetRepository {
    fun upsert(navEnhet: NavEnhet): NavEnhetDbo {
        val sql =
            """
            INSERT INTO nav_enhet (
                id, 
                nav_enhet_nummer, 
                navn 
            )
            VALUES (
                :id, 
                :nav_enhet_nummer, 
                :navn 
            ) 
            ON CONFLICT (id) DO UPDATE SET
                nav_enhet_nummer = :nav_enhet_nummer,
                navn = :navn,
                modified_at = CURRENT_TIMESTAMP
            RETURNING *
            """.trimIndent()

        return Database.query { session ->
            session.run(
                queryOf(
                    sql,
                    mapOf(
                        "id" to navEnhet.id,
                        "nav_enhet_nummer" to navEnhet.enhetsnummer,
                        "navn" to navEnhet.navn,
                    ),
                ).map(::rowMapper).asSingle,
            ) ?: throw RuntimeException("Noe gikk galt ved lagring av nav-enhet")
        }
    }

    fun get(enhetsnummer: String): NavEnhetDbo? = Database.query { session ->
        session.run(
            queryOf(
                "SELECT * FROM nav_enhet WHERE nav_enhet_nummer = :nav_enhet_nummer",
                mapOf("nav_enhet_nummer" to enhetsnummer),
            ).map(::rowMapper).asSingle,
        )
    }

    fun get(id: UUID): NavEnhetDbo? = Database.query { session ->
        session.run(
            queryOf(
                "SELECT * FROM nav_enhet WHERE id = :id",
                mapOf("id" to id),
            ).map(::rowMapper).asSingle,
        )
    }

    fun getMany(enhetIder: List<UUID>): List<NavEnhetDbo> {
        if (enhetIder.isEmpty()) return emptyList()

        return Database.query { session ->
            session.run(
                queryOf(
                    "SELECT * FROM nav_enhet WHERE id in (${enhetIder.joinToString { "?" }})",
                    *enhetIder.toTypedArray(),
                ).map(::rowMapper).asList,
            )
        }
    }

    companion object {
        private fun rowMapper(row: Row) = NavEnhetDbo(
            id = row.uuid("id"),
            enhetsnummer = row.string("nav_enhet_nummer"),
            navn = row.string("navn"),
            sistEndret = row.localDateTime("modified_at"),
        )
    }
}
