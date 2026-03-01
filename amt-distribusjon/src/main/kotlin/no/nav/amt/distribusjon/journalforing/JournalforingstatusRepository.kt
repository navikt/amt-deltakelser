package no.nav.amt.distribusjon.journalforing

import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.distribusjon.journalforing.model.Journalforingstatus
import no.nav.amt.lib.utils.database.Database
import java.util.UUID

class JournalforingstatusRepository {
    fun upsert(journalforingstatus: Journalforingstatus) {
        val sql =
            """
            INSERT INTO journalforingstatus (
                hendelse_id, 
                journalpost_id, 
                bestillingsid, 
                kan_ikke_distribueres, 
                kan_ikke_journalfores
            )
            VALUES (
                :hendelse_id, 
                :journalpost_id, 
                :bestillingsid, 
                :kan_ikke_distribueres, 
                :kan_ikke_journalfores
            )
            ON CONFLICT (hendelse_id) DO UPDATE SET
                journalpost_id = :journalpost_id,
                bestillingsid = :bestillingsid,
                kan_ikke_distribueres = :kan_ikke_distribueres,
                kan_ikke_journalfores = :kan_ikke_journalfores,
                modified_at = CURRENT_TIMESTAMP
            """.trimIndent()

        val params = mapOf(
            "hendelse_id" to journalforingstatus.hendelseId,
            "journalpost_id" to journalforingstatus.journalpostId,
            "bestillingsid" to journalforingstatus.bestillingsId,
            "kan_ikke_distribueres" to journalforingstatus.kanIkkeDistribueres,
            "kan_ikke_journalfores" to journalforingstatus.kanIkkeJournalfores,
        )

        Database.query { session -> session.update(queryOf(sql, params)) }
    }

    fun get(hendelseId: UUID): Journalforingstatus? = Database.query { session ->
        session.run(
            queryOf(
                "SELECT * FROM journalforingstatus WHERE hendelse_id = :hendelse_id",
                mapOf("hendelse_id" to hendelseId),
            ).map(::rowMapper).asSingle,
        )
    }

    companion object {
        private fun rowMapper(row: Row) = Journalforingstatus(
            hendelseId = row.uuid("hendelse_id"),
            journalpostId = row.stringOrNull("journalpost_id"),
            bestillingsId = row.uuidOrNull("bestillingsid"),
            kanIkkeDistribueres = row.boolean("kan_ikke_distribueres"),
            kanIkkeJournalfores = row.boolean("kan_ikke_journalfores"),
        )
    }
}
