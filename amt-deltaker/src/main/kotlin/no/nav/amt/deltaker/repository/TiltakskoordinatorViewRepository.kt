package no.nav.amt.deltaker.repository

import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.internapi.tiltakskoordinator.HandlingFilterValg
import no.nav.amt.internapi.tiltakskoordinator.request.TiltaksKoordinatorDeltakerlisteRequest
import no.nav.amt.internapi.tiltakskoordinator.response.DeltakerlisteFilterCountsResponse
import no.nav.amt.lib.models.arrangor.melding.Vurderingstype
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.person.address.Adressebeskyttelse
import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.objectMapper
import tools.jackson.module.kotlin.readValue
import java.util.UUID

class TiltakskoordinatorViewRepository {
    /**
     * Henter deltakere for en gjennomføring med berikede feltdata.
     *
     * Optimalisert for store deltakerlister (>2000 deltakere) ved å:
     * - Bruke preaggregerte LATERAL JOINs for aktive forslag og siste vurderingstype
     * - Håndtere sokt-inn-dato via COALESCE av 3 kilder (arena, egen oppstart, vedtak)
     * - Berike resultatet med avledede handling-flagg, som `har_aktivt_forslag`
     *
     * Sorteres etter sokt_inn_dato synkende (nyeste først), deretter etter id.
     *
     * @param request inneholder gjennomforingId og statuser (valgfritt); handlingFilterValg brukes ikke som SQL-filter
     * @return Liste av deltakere med berikede feltdata, sortert etter søkt-inn-dato
     */
    fun getDeltakere(request: TiltaksKoordinatorDeltakerlisteRequest): List<TiltakskoordinatorDeltakerRow> = Database.query { session ->
        session.run(
            queryOf(
                deltakereSelectSql(statuser = request.statuser, where = "d.deltakerliste_id = :deltakerliste_id"),
                mapOf("deltakerliste_id" to request.gjennomforingId)
                    .plus(statusFilterParams(request)),
            ).map(::deltakerRowMapper).asList,
        )
    }

    fun getDeltakere(deltakerIder: List<UUID>): List<TiltakskoordinatorDeltakerRow> = Database.query { session ->
        session.run(
            queryOf(
                deltakereSelectSql(where = "d.id = ANY(:deltakerIder)"),
                mapOf("deltakerIder" to deltakerIder.toTypedArray()),
            ).map(::deltakerRowMapper).asList,
        )
    }

    fun getDeltakereCountPerStatus(request: TiltaksKoordinatorDeltakerlisteRequest): DeltakerlisteFilterCountsResponse {
        require(request.statuser.isNotEmpty()) { "Statuser må spesifiseres for å hente deltakerantall per status" }

        val rows = Database.query { session ->
            session.run(
                queryOf(
                    DELTAKERE_COUNT_SQL,
                    mapOf(
                        "deltakerliste_id" to request.gjennomforingId,
                        "statuser" to request.statuser.map { it.name }.toTypedArray(),
                    ),
                ).map {
                    CountPerStatusRow(
                        status = DeltakerStatus.Type.valueOf(it.string("type")),
                        count = it.int("count"),
                        harAktivtForslagCount = it.int("har_aktivt_forslag_count"),
                    )
                }.asList,
            )
        }

        /*
         * TODO(amt-deltaker): Restore exact handling counts for NyeDeltakere and OppdateringFraNav.
         *
         * Before this endpoint move, the BFF calculated those two counters from its own ulest_hendelse table.
         * That table is a BFF-only unread-event projection and does not exist in amt-deltaker, so the copied
         * SQL could not run here. We still move the endpoint now so BFF can stop reading status counts from its
         * own deltakerliste-projection, but the unread-state part of the logic is intentionally incomplete.
         *
         * What still needs to happen to finish the migration:
         * 1. Move the source of truth for tiltakskoordinator unread state into amt-deltaker, or expose another
         *    domain-safe read model from amt-deltaker that answers the same question.
         * 2. Backfill/migrate the state so existing unread markers are preserved.
         * 3. Replace the hardcoded 0 values below with exact counts for the event families that the BFF used:
         *    - InnbyggerGodkjennUtkast / NavGodkjennUtkast -> HandlingFilterValg.NyeDeltakere
         *    - IkkeAktuell / AvsluttDeltakelse / AvbrytDeltakelse / ReaktiverDeltakelse
         *      -> HandlingFilterValg.OppdateringFraNav
         */
        return DeltakerlisteFilterCountsResponse(
            statusCounts = rows.associate { it.status to it.count },
            handlingCounts = mapOf(
                HandlingFilterValg.NyeDeltakere to 0,
                HandlingFilterValg.OppdateringFraNav to 0,
                HandlingFilterValg.AktiveForslag to rows.sumOf { it.harAktivtForslagCount },
            ),
        )
    }

    companion object {
        private data class CountPerStatusRow(
            val status: DeltakerStatus.Type,
            val count: Int,
            val harAktivtForslagCount: Int,
        )

        private val DELTAKERE_COUNT_SQL =
            """
            WITH d AS (
                SELECT id
                FROM deltaker
                WHERE deltakerliste_id = :deltakerliste_id
            )

            SELECT
                ds.type,
                COUNT(*) AS count,
                COUNT(*) FILTER (WHERE af.har_aktivt) AS har_aktivt_forslag_count
            FROM
                d
                JOIN deltaker_status ds
                    ON ds.deltaker_id = d.id
                    AND ds.gyldig_til IS NULL
                    AND ds.gyldig_fra <= CURRENT_TIMESTAMP
                    AND ds.type = ANY(:statuser)
                LEFT JOIN LATERAL (
                    SELECT true AS har_aktivt
                    FROM forslag f
                    WHERE f.deltaker_id = d.id
                    AND f.status->>'type' = 'VenterPaSvar'
                    LIMIT 1
                ) af ON true
            GROUP BY ds.type;
            """.trimIndent()

        // Disse statusene skal aldri vises for tiltakskoordinator
        val SKJULTE_STATUSER = setOf(
            DeltakerStatus.Type.KLADD,
            DeltakerStatus.Type.UTKAST_TIL_PAMELDING,
            DeltakerStatus.Type.AVBRUTT_UTKAST,
            DeltakerStatus.Type.FEILREGISTRERT,
            DeltakerStatus.Type.PABEGYNT_REGISTRERING,
        )

        private fun statusFilterSql(statuser: Set<DeltakerStatus.Type>) = if (statuser.isNotEmpty()) {
            "AND ds.type = ANY(:statuser)"
        } else {
            "AND ds.type NOT IN (${SKJULTE_STATUSER.joinToString(",") { "'${it.name}'" }})"
        }

        private fun statusFilterParams(request: TiltaksKoordinatorDeltakerlisteRequest) = if (request.statuser.isNotEmpty()) {
            mapOf("statuser" to request.statuser.map { it.name }.toTypedArray())
        } else {
            emptyMap<String, Any>()
        }

        private fun deltakereSelectSql(
            where: String,
            statuser: Set<DeltakerStatus.Type> = emptySet(),
        ) = """
            SELECT
                -- deltaker
                d.id                            AS "d.id",
                d.startdato                     AS "d.startdato",
                d.sluttdato                     AS "d.sluttdato",
                d.er_manuelt_delt_med_arrangor  AS "d.er_manuelt_delt_med_arrangor",

                -- nav_bruker
                nb.personident                  AS "nb.personident",
                nb.fornavn                      AS "nb.fornavn",
                nb.mellomnavn                   AS "nb.mellomnavn",
                nb.etternavn                    AS "nb.etternavn",
                nb.er_skjermet                  AS "nb.er_skjermet",
                (nb.adresse IS NOT NULL)        AS "nb.har_adresse",
                nb.adressebeskyttelse           AS "nb.adressebeskyttelse",

                -- deltaker_status
                ds.id                           AS "ds.id",
                ds.type                         AS "ds.type",
                ds.aarsak                       AS "ds.aarsak",
                ds.gyldig_fra                   AS "ds.gyldig_fra",
                ds.gyldig_til                   AS "ds.gyldig_til",
                ds.created_at                   AS "ds.created_at",

                -- nav_enhet — LEFT JOIN, kan være null
                ne.navn                         AS "ne.navn",

                -- sokt-inn-dato (COALESCE av 3 kilder).
                -- vedtak.deltaker_id er UNIQUE (V51) så maks 1 rad — én JOIN dekker begge behov.
                COALESCE(
                    (ifa.deltaker_ved_import->>'innsoktDato')::date,
                    ipfo.innsokt::date,
                    v.created_at::date
                )                               AS sokt_inn_dato,

                -- har aktivt forslag (preaggregert via LATERAL)
                COALESCE(af.har_aktivt, false)  AS har_aktivt_forslag,

                -- siste vurderingstype (preaggregert via LATERAL)
                sv.vurderingstype               AS siste_vurderingstype,

                -- digital bruker cache (null = utdatert eller mangler)
                dbc.er_digital                  AS "dbc.er_digital",

                -- felter for låse-sjekk (fattet kun når vedtaket er gyldig, dvs. gyldig_til IS NULL)
                CASE WHEN v.gyldig_til IS NULL THEN v.fattet ELSE NULL END AS "v.fattet",
                (ifa.deltaker_ved_import->>'innsoktDato')::date AS innsoekt_dato_arena
            FROM
                deltaker d
                JOIN nav_bruker nb ON d.person_id = nb.person_id
                JOIN deltaker_status ds ON
                    d.id = ds.deltaker_id
                    AND ds.gyldig_til IS NULL
                    AND ds.gyldig_fra <= CURRENT_TIMESTAMP
                    ${statusFilterSql(statuser)}
                -- Enkel vedtak-JOIN (UNIQUE deltaker_id garanterer maks 1 rad)
                LEFT JOIN vedtak v ON v.deltaker_id = d.id
                LEFT JOIN nav_enhet ne ON ne.id = nb.nav_enhet_id
                LEFT JOIN importert_fra_arena ifa ON ifa.deltaker_id = d.id
                LEFT JOIN innsok ipfo ON ipfo.deltaker_id = d.id
                LEFT JOIN digital_bruker_cache dbc ON
                    dbc.personident = nb.personident
                    AND dbc.modified_at > NOW() - INTERVAL '24 hours'
                -- Preaggregert: har minst ett aktivt forslag?
                LEFT JOIN LATERAL (
                    SELECT true AS har_aktivt
                    FROM forslag f
                    WHERE 
                        f.deltaker_id = d.id
                        AND f.status->>'type' = 'VenterPaSvar'
                    LIMIT 1
                ) af ON true
                -- Preaggregert: siste vurderingstype (utnytter composite index)
                LEFT JOIN LATERAL (
                    SELECT vr.vurderingstype
                    FROM vurdering vr
                    WHERE vr.deltaker_id = d.id
                    ORDER BY vr.gyldig_fra DESC
                    LIMIT 1
                ) sv ON true
            WHERE $where
            ORDER BY sokt_inn_dato DESC NULLS LAST, d.id ASC
            """.trimIndent()

        private fun deltakerRowMapper(row: Row): TiltakskoordinatorDeltakerRow = TiltakskoordinatorDeltakerRow(
            id = row.uuid("d.id"),
            personident = row.string("nb.personident"),
            startdato = row.localDateOrNull("d.startdato"),
            sluttdato = row.localDateOrNull("d.sluttdato"),
            erManueltDeltMedArrangor = row.boolean("d.er_manuelt_delt_med_arrangor"),
            status = DeltakerStatus(
                id = row.uuid("ds.id"),
                type = DeltakerStatus.Type.valueOf(row.string("ds.type")),
                aarsak = row.stringOrNull("ds.aarsak")?.let { objectMapper.readValue(it) },
                gyldigFra = row.localDateTime("ds.gyldig_fra"),
                gyldigTil = row.localDateTimeOrNull("ds.gyldig_til"),
                opprettet = row.localDateTime("ds.created_at"),
            ),
            fornavn = row.string("nb.fornavn"),
            mellomnavn = row.stringOrNull("nb.mellomnavn"),
            etternavn = row.string("nb.etternavn"),
            erSkjermet = row.boolean("nb.er_skjermet"),
            harAdresse = row.boolean("nb.har_adresse"),
            adressebeskyttelse = row.stringOrNull("nb.adressebeskyttelse")?.let { Adressebeskyttelse.valueOf(it) },
            navEnhetNavn = row.stringOrNull("ne.navn"),
            soktInnDato = row.localDateOrNull("sokt_inn_dato"),
            harAktivtForslag = row.boolean("har_aktivt_forslag"),
            sisteVurderingstype = row.stringOrNull("siste_vurderingstype")?.let { Vurderingstype.valueOf(it) },
            erDigitalCached = row.anyOrNull("dbc.er_digital") as? Boolean,
            vedtakFattet = row.localDateTimeOrNull("v.fattet"),
            innsoektDatoArena = row.localDateOrNull("innsoekt_dato_arena"),
        )
    }
}
