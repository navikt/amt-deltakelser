package no.nav.amt.deltaker.veileder

import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.deltaker.utils.toPGObject
import no.nav.amt.lib.models.deltaker.Innsok
import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.objectMapper
import tools.jackson.module.kotlin.readValue
import java.util.UUID

class InnsokRepository {
    fun insert(innsok: Innsok) {
        val sql =
            """
            INSERT INTO innsok (
                id, 
                deltaker_id, 
                innsokt, 
                innsokt_av, 
                innsokt_av_enhet, 
                utkast_godkjent_av_nav, 
                utkast_delt, 
                startdato,
                sluttdato,
                dager_per_uke_ved_innsok,
                deltakelsesinnhold_ved_innsok,
                prisinformasjon_ved_innsok,
                kategorisering_ved_innsok
            ) 
            VALUES (
                :id, 
                :deltaker_id, 
                :innsokt, 
                :innsokt_av, 
                :innsokt_av_enhet, 
                :utkast_godkjent_av_nav, 
                :utkast_delt, 
                :startdato,
                :sluttdato,
                :dager_per_uke_ved_innsok,
                :deltakelsesinnhold_ved_innsok,
                :prisinformasjon_ved_innsok,
                :kategorisering_ved_innsok
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
            "startdato" to innsok.startdato,
            "sluttdato" to innsok.sluttdato,
            "dager_per_uke_ved_innsok" to innsok.dagerPerUkeVedInnsok,
            "deltakelsesinnhold_ved_innsok" to toPGObject(innsok.deltakelsesinnholdVedInnsok),
            "prisinformasjon_ved_innsok" to toPGObject(innsok.prisinformasjonVedInnsok),
            "kategorisering_ved_innsok" to toPGObject(innsok.opplaringKategoriseringVedInnsok),
        )

        Database.query { session -> session.update(queryOf(sql, params)) }
    }

    fun get(id: UUID): Result<Innsok> = runCatching {
        Database.query { session ->
            session.run(
                queryOf(
                    """
                    SELECT 
                        id, 
                        deltaker_id, 
                        innsokt, 
                        innsokt_av, 
                        innsokt_av_enhet, 
                        utkast_godkjent_av_nav, 
                        utkast_delt, 
                        startdato,
                        sluttdato,
                        dager_per_uke_ved_innsok,
                        deltakelsesinnhold_ved_innsok,
                        prisinformasjon_ved_innsok,
                        kategorisering_ved_innsok
                    FROM innsok 
                    WHERE id = :id
                    """.trimIndent(),
                    mapOf("id" to id),
                ).map(::rowMapper).asSingle,
            ) ?: throw NoSuchElementException("Fant ikke innsok med id $id")
        }
    }

    fun getForDeltaker(deltakerId: UUID): Result<Innsok> = runCatching {
        Database.query { session ->
            session.run(
                queryOf(
                    """
                    SELECT 
                        id, 
                        deltaker_id, 
                        innsokt, 
                        innsokt_av, 
                        innsokt_av_enhet, 
                        utkast_godkjent_av_nav, 
                        utkast_delt, 
                        startdato,
                        sluttdato,
                        dager_per_uke_ved_innsok,
                        deltakelsesinnhold_ved_innsok,
                        prisinformasjon_ved_innsok,
                        kategorisering_ved_innsok
                    FROM innsok 
                    WHERE deltaker_id = :deltaker_id
                    """.trimIndent(),
                    mapOf("deltaker_id" to deltakerId),
                ).map(::rowMapper).asSingle,
            ) ?: throw NoSuchElementException("Fant ikke innsok for deltaker med id $deltakerId")
        }
    }

    fun deleteForDeltaker(deltakerId: UUID) = Database.query { session ->
        session.update(
            queryOf(
                "DELETE FROM innsok WHERE deltaker_id = :deltaker_id",
                mapOf("deltaker_id" to deltakerId),
            ),
        )
    }

    companion object {
        private fun rowMapper(row: Row) = Innsok(
            id = row.uuid("id"),
            deltakerId = row.uuid("deltaker_id"),
            innsokt = row.localDateTime("innsokt"),
            innsoktAv = row.uuid("innsokt_av"),
            innsoktAvEnhet = row.uuid("innsokt_av_enhet"),
            utkastGodkjentAvNav = row.boolean("utkast_godkjent_av_nav"),
            utkastDelt = row.localDateTimeOrNull("utkast_delt"),
            startdato = row.localDateOrNull("startdato"),
            sluttdato = row.localDateOrNull("sluttdato"),
            dagerPerUkeVedInnsok = row.intOrNull("dager_per_uke_ved_innsok"),
            deltakelsesinnholdVedInnsok = row.stringOrNull("deltakelsesinnhold_ved_innsok")?.let { objectMapper.readValue(it) },
            prisinformasjonVedInnsok = row.stringOrNull("prisinformasjon_ved_innsok")?.let { objectMapper.readValue(it) },
            opplaringKategoriseringVedInnsok = row.stringOrNull("kategorisering_ved_innsok")?.let {
                objectMapper.readValue(it)
            },
        )
    }
}
