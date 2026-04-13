package no.nav.amt.deltaker.deltaker.innsok

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.deltaker.utils.toPGObject
import no.nav.amt.lib.models.deltaker.InnsokPaaFellesOppstart
import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.objectMapper
import java.util.UUID

class InnsokPaaFellesOppstartRepository {
    fun insert(innsok: InnsokPaaFellesOppstart) {
        val sql =
            """
            INSERT INTO innsok_paa_felles_oppstart (
                id, 
                deltaker_id, 
                innsokt, 
                innsokt_av, 
                innsokt_av_enhet, 
                utkast_godkjent_av_nav, 
                utkast_delt, 
                deltakelsesinnhold_ved_innsok
            ) 
            VALUES (
                :id, 
                :deltaker_id, 
                :innsokt, 
                :innsokt_av, 
                :innsokt_av_enhet, 
                :utkast_godkjent_av_nav, 
                :utkast_delt, 
                :deltakelsesinnhold_ved_innsok
            )
            """.trimIndent()

        val params = mapOf(
            "id" to innsok.id,
            "deltaker_id" to innsok.deltakerId,
            "innsokt" to innsok.innsokt,
            "innsokt_av" to innsok.innsoktAv,
            "innsokt_av_enhet" to innsok.innsoktAvEnhet,
            "utkast_godkjent_av_nav" to innsok.utkastGodkjentAvNav,
            "utkast_delt" to innsok.utkastDelt,
            "deltakelsesinnhold_ved_innsok" to toPGObject(innsok.deltakelsesinnholdVedInnsok),
        )

        Database.query { session -> session.update(queryOf(sql, params)) }
    }

    fun get(id: UUID): Result<InnsokPaaFellesOppstart> = runCatching {
        Database.query { session ->
            session.run(
                queryOf(
                    "SELECT * FROM innsok_paa_felles_oppstart WHERE id = :id",
                    mapOf("id" to id),
                ).map(::rowMapper).asSingle,
            ) ?: throw NoSuchElementException("Fant ikke innsok med id $id")
        }
    }

    fun getForDeltaker(deltakerId: UUID): Result<InnsokPaaFellesOppstart> = runCatching {
        Database.query { session ->
            session.run(
                queryOf(
                    "SELECT * FROM innsok_paa_felles_oppstart WHERE deltaker_id = :deltaker_id",
                    mapOf("deltaker_id" to deltakerId),
                ).map(::rowMapper).asSingle,
            ) ?: throw NoSuchElementException("Fant ikke innsok for deltaker med id $deltakerId")
        }
    }

    fun deleteForDeltaker(deltakerId: UUID) = Database.query { session ->
        session.update(
            queryOf(
                "DELETE FROM innsok_paa_felles_oppstart WHERE deltaker_id = :deltaker_id",
                mapOf("deltaker_id" to deltakerId),
            ),
        )
    }

    companion object {
        private fun rowMapper(row: Row) = InnsokPaaFellesOppstart(
            id = row.uuid("id"),
            deltakerId = row.uuid("deltaker_id"),
            innsokt = row.localDateTime("innsokt"),
            innsoktAv = row.uuid("innsokt_av"),
            innsoktAvEnhet = row.uuid("innsokt_av_enhet"),
            utkastGodkjentAvNav = row.boolean("utkast_godkjent_av_nav"),
            utkastDelt = row.localDateTimeOrNull("utkast_delt"),
            deltakelsesinnholdVedInnsok = objectMapper.readValue(row.string("deltakelsesinnhold_ved_innsok")),
        )
    }
}
