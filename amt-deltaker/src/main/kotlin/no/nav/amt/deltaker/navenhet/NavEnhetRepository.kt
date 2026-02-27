package no.nav.amt.deltaker.navenhet

import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.utils.database.Database
import java.util.UUID

class NavEnhetRepository {
    fun upsert(navEnhet: NavEnhet): NavEnhet {
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

        val query = queryOf(
            sql,
            mapOf(
                "id" to navEnhet.id,
                "nav_enhet_nummer" to navEnhet.enhetsnummer,
                "navn" to navEnhet.navn,
            ),
        ).map(::rowMapper).asSingle

        return Database.query { session ->
            session.run(query)
                ?: throw RuntimeException("Noe gikk galt ved lagring av nav-enhet")
        }
    }

    fun getOrThrow(enhetsnummer: String): NavEnhet =
        get(enhetsnummer) ?: throw NoSuchElementException("Fant ikke Nav-enhet med enhetsnummer $enhetsnummer")

    fun get(enhetsnummer: String): NavEnhet? = Database.query { session ->
        session.run(
            queryOf(
                "SELECT * FROM nav_enhet WHERE nav_enhet_nummer = :nav_enhet_nummer",
                mapOf("nav_enhet_nummer" to enhetsnummer),
            ).map(::rowMapper).asSingle,
        )
    }

    fun getOrThrow(id: UUID): NavEnhet = get(id) ?: throw NoSuchElementException("Fant ikke Nav-enhet med id $id")

    fun get(id: UUID): NavEnhet? = Database.query { session ->
        session.run(
            queryOf(
                "SELECT * FROM nav_enhet WHERE id = :id",
                mapOf("id" to id),
            ).map(::rowMapper).asSingle,
        )
    }

    fun getMany(ider: Set<UUID>): List<NavEnhet> {
        if (ider.isEmpty()) return emptyList()

        return Database.query { session ->
            session.run(
                queryOf(
                    "SELECT * FROM nav_enhet WHERE id = ANY(:ider)",
                    mapOf("ider" to ider.toTypedArray()),
                ).map(::rowMapper).asList,
            )
        }
    }

    companion object {
        private fun rowMapper(row: Row) = NavEnhet(
            id = row.uuid("id"),
            enhetsnummer = row.string("nav_enhet_nummer"),
            navn = row.string("navn"),
        )
    }
}
