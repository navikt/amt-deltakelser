package no.nav.amt.deltaker.bff.arrangor

import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.lib.models.deltaker.Arrangor
import no.nav.amt.lib.utils.database.Database
import java.util.UUID

class ArrangorRepository {
    fun upsert(arrangor: Arrangor) {
        val sql =
            """
            INSERT INTO arrangor (
                id, 
                navn, 
                organisasjonsnummer, 
                overordnet_arrangor_id
            )
            VALUES (
                :id, 
                :navn, 
                :organisasjonsnummer, 
                :overordnet_arrangor_id
            ) 
            ON CONFLICT (id) DO UPDATE SET
                navn = :navn,
                organisasjonsnummer = :organisasjonsnummer,
                overordnet_arrangor_id = :overordnet_arrangor_id,
                modified_at = CURRENT_TIMESTAMP
            """.trimIndent()

        Database.query { session ->
            session.update(
                queryOf(
                    sql,
                    mapOf(
                        "id" to arrangor.id,
                        "navn" to arrangor.navn,
                        "organisasjonsnummer" to arrangor.organisasjonsnummer,
                        "overordnet_arrangor_id" to arrangor.overordnetArrangorId,
                    ),
                ),
            )
        }
    }

    fun get(id: UUID): Arrangor? = Database.query { session ->
        session.run(
            queryOf(
                "SELECT * FROM arrangor WHERE id = :id",
                mapOf("id" to id),
            ).map(::rowMapper).asSingle,
        )
    }

    fun get(orgnr: String): Arrangor? = Database.query { session ->
        session.run(
            queryOf(
                "SELECT * FROM arrangor WHERE organisasjonsnummer = :orgnr",
                mapOf("orgnr" to orgnr),
            ).map(::rowMapper).asSingle,
        )
    }

    fun delete(id: UUID) {
        Database.query { session ->
            session.update(
                queryOf(
                    "DELETE FROM arrangor WHERE id = :id",
                    mapOf("id" to id),
                ),
            )
        }
    }

    companion object {
        private fun rowMapper(row: Row) = Arrangor(
            id = row.uuid("id"),
            navn = row.string("navn"),
            organisasjonsnummer = row.string("organisasjonsnummer"),
            overordnetArrangorId = row.uuidOrNull("overordnet_arrangor_id"),
        )
    }
}
