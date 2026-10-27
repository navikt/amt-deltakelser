package no.nav.amt.distribusjon.journalforing

import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.distribusjon.journalforing.model.Journalforingstatus
import no.nav.amt.lib.utils.database.Database
import java.util.UUID

class JournalforingstatusRepository {
    /**
     * Lagrer journalføringsstatusen. En annullering som allerede er lagret, blir stående.
     *
     * @param journalforingstatus Statusen som skal lagres.
     */
    fun upsert(journalforingstatus: Journalforingstatus) {
        val sql =
            """
            INSERT INTO journalforingstatus (
                hendelse_id, 
                journalpost_id, 
                bestillingsid, 
                kan_ikke_distribueres, 
                kan_ikke_journalfores,
                annullert
            )
            VALUES (
                :hendelse_id, 
                :journalpost_id, 
                :bestillingsid, 
                :kan_ikke_distribueres, 
                :kan_ikke_journalfores,
                :annullert
            )
            ON CONFLICT (hendelse_id) DO UPDATE SET
                journalpost_id = EXCLUDED.journalpost_id,
                bestillingsid = EXCLUDED.bestillingsid,
                kan_ikke_distribueres = EXCLUDED.kan_ikke_distribueres,
                kan_ikke_journalfores = EXCLUDED.kan_ikke_journalfores,
                annullert = journalforingstatus.annullert OR EXCLUDED.annullert,
                modified_at = CURRENT_TIMESTAMP
            """.trimIndent()

        val params = mapOf(
            "hendelse_id" to journalforingstatus.hendelseId,
            "journalpost_id" to journalforingstatus.journalpostId,
            "bestillingsid" to journalforingstatus.bestillingsId,
            "kan_ikke_distribueres" to journalforingstatus.kanIkkeDistribueres,
            "kan_ikke_journalfores" to journalforingstatus.kanIkkeJournalfores,
            "annullert" to journalforingstatus.annullert,
        )

        Database.query { session -> session.update(queryOf(sql, params)) }
    }

    /**
     * Annullerer hendelser som ikke er distribuert eller merket som ikke-behandlingsbare.
     *
     * @param hendelseIder ID-ene til hendelsene som skal annulleres.
     * @return Antall journalføringsstatuser som ble endret.
     */
    fun annuller(hendelseIder: Collection<UUID>): Int {
        if (hendelseIder.isEmpty()) return 0

        val sql =
            """
            UPDATE journalforingstatus
            SET
                annullert = TRUE,
                modified_at = CURRENT_TIMESTAMP
            WHERE
                hendelse_id = ANY(:hendelseIder)
                AND annullert IS NOT TRUE
                AND bestillingsid IS NULL
                AND kan_ikke_distribueres IS NOT TRUE
                AND kan_ikke_journalfores IS NOT TRUE
            """.trimIndent()

        return Database.query { session ->
            session.update(
                queryOf(
                    sql,
                    mapOf("hendelseIder" to hendelseIder.toTypedArray()),
                ),
            )
        }
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
        /**
         * Bygger journalføringsstatus fra en rad i databasen.
         *
         * @param row Raden som inneholder journalføringsstatusen.
         * @return Journalføringsstatusen fra raden.
         */
        private fun rowMapper(row: Row) = Journalforingstatus(
            hendelseId = row.uuid("hendelse_id"),
            journalpostId = row.stringOrNull("journalpost_id"),
            bestillingsId = row.uuidOrNull("bestillingsid"),
            kanIkkeDistribueres = row.boolean("kan_ikke_distribueres"),
            kanIkkeJournalfores = row.boolean("kan_ikke_journalfores"),
            annullert = row.boolean("annullert"),
        )
    }
}
