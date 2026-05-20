package no.nav.amt.deltaker.repository

import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.internapi.deltaker.request.PageRequest
import no.nav.amt.internapi.deltaker.request.TiltaksKoordinatorDeltakerlisteRequest
import no.nav.amt.lib.models.arrangor.melding.Vurderingstype
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.person.address.Adressebeskyttelse
import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.objectMapper
import tools.jackson.module.kotlin.readValue

class TiltakskoordinatorViewRepository {
    fun getDeltakereTotalCount(request: TiltaksKoordinatorDeltakerlisteRequest): Int = Database.query { session ->
        session.run(
            queryOf(
                deltakereCountSql(request),
                mapOf("deltakerliste_id" to request.gjennomforingId)
                    .plus(
                        statusFilterParams(
                            paginationEnabled = true,
                            request = request,
                        ),
                    ),
            ).map { row -> row.int("total_count") }.asSingle,
        ) ?: 0
    }

    fun getDeltakere(
        request: TiltaksKoordinatorDeltakerlisteRequest,
        paginationEnabled: Boolean,
    ): List<TiltakskoordinatorDeltakerRow> = Database.query { session ->
        session.run(
            queryOf(
                deltakereSelectSql(
                    paginationEnabled = paginationEnabled,
                    request = request,
                ),
                mapOf(
                    "deltakerliste_id" to request.gjennomforingId,
                ).plus(
                    statusFilterParams(
                        paginationEnabled = paginationEnabled,
                        request = request,
                    ),
                ).plus(
                    if (paginationEnabled) {
                        mapOf(
                            "page_size" to request.pageRequest.pageSize,
                            "offset" to request.pageRequest.offset,
                        )
                    } else {
                        emptyMap()
                    },
                ),
            ).map(::deltakerRowMapper).asList,
        )
    }

    companion object {
        private val sortColumnMap = mapOf(
            TiltaksKoordinatorDeltakerlisteRequest.SortColumn.NAVN to "nb.etternavn",
            TiltaksKoordinatorDeltakerlisteRequest.SortColumn.NAV_ENHET to "ne.navn",
            TiltaksKoordinatorDeltakerlisteRequest.SortColumn.SOKT_INN_DATO to "sokt_inn_dato",
            TiltaksKoordinatorDeltakerlisteRequest.SortColumn.STARTDATO to "d.startdato",
            TiltaksKoordinatorDeltakerlisteRequest.SortColumn.SLUTTDATO to "d.sluttdato",
            TiltaksKoordinatorDeltakerlisteRequest.SortColumn.STATUS to "ds.type",
        )

        private const val DEFAULT_SORT_COLUMN = "sokt_inn_dato"
        private val DEFAULT_SORT_DIRECTION = PageRequest.SortDirection.DESC

        private fun statusFilterSql(request: TiltaksKoordinatorDeltakerlisteRequest) = request.statuser
            .takeIf { it.isNotEmpty() }
            ?.let { " AND ds.type = ANY(:statuser)" }
            ?: ""

        private fun statusFilterParams(
            paginationEnabled: Boolean,
            request: TiltaksKoordinatorDeltakerlisteRequest,
        ) = if (paginationEnabled && request.statuser.isNotEmpty()) {
            mapOf("statuser" to request.statuser.map { it.name }.toTypedArray())
        } else {
            emptyMap<String, Any>()
        }

        private fun harForslagFraArrangorJoinClause(request: TiltaksKoordinatorDeltakerlisteRequest) = if (request.harForslagFraArrangor) {
            """
            LEFT JOIN LATERAL (
                SELECT true AS har_aktivt
                FROM forslag f
                WHERE 
                    f.deltaker_id = d.id
                    AND f.status->>'type' = 'VenterPaSvar'
                LIMIT 1
            ) af ON true                    
            """.trimIndent()
        } else {
            ""
        }

        private fun harForslagFraArrangorWhereClause(request: TiltaksKoordinatorDeltakerlisteRequest) = if (request.harForslagFraArrangor) {
            " AND har_aktivt = true"
        } else {
            ""
        }

        private fun PageRequest<TiltaksKoordinatorDeltakerlisteRequest.SortColumn>.orderByClause(): String {
            val sortColumn = sortColumnMap[sort] ?: DEFAULT_SORT_COLUMN
            val sortDirection = sort?.let { order } ?: DEFAULT_SORT_DIRECTION

            return "ORDER BY $sortColumn $sortDirection NULLS LAST, d.id ASC"
        }

        private fun deltakereCountSql(request: TiltaksKoordinatorDeltakerlisteRequest) =
            """
            SELECT COUNT(d.id) AS total_count
            FROM
                deltaker d
                JOIN deltaker_status ds ON
                    d.id = ds.deltaker_id
                    AND ds.gyldig_til IS NULL
                    AND ds.gyldig_fra <= CURRENT_TIMESTAMP
                    ${statusFilterSql(request)}
                    AND ds.type NOT IN ('KLADD', 'UTKAST_TIL_PAMELDING', 'AVBRUTT_UTKAST', 'FEILREGISTRERT', 'PABEGYNT_REGISTRERING')
               ${harForslagFraArrangorJoinClause(request)}     
            WHERE 
                d.deltakerliste_id = :deltakerliste_id
                ${harForslagFraArrangorWhereClause(request)}
            """.trimIndent()

        private fun deltakereSelectSql(
            paginationEnabled: Boolean,
            request: TiltaksKoordinatorDeltakerlisteRequest,
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
                    ${statusFilterSql(request)}
                    AND ds.type NOT IN ('KLADD', 'UTKAST_TIL_PAMELDING', 'AVBRUTT_UTKAST', 'FEILREGISTRERT', 'PABEGYNT_REGISTRERING')
                -- Enkel vedtak-JOIN (UNIQUE deltaker_id garanterer maks 1 rad)
                LEFT JOIN vedtak v ON v.deltaker_id = d.id
                LEFT JOIN nav_enhet ne ON ne.id = nb.nav_enhet_id
                LEFT JOIN importert_fra_arena ifa ON ifa.deltaker_id = d.id
                LEFT JOIN innsok_paa_felles_oppstart ipfo ON ipfo.deltaker_id = d.id
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
            WHERE 
                d.deltakerliste_id = :deltakerliste_id
                ${harForslagFraArrangorWhereClause(request)}
            ${if (paginationEnabled) request.pageRequest.orderByClause() else ""}
            ${if (paginationEnabled) "LIMIT :limit OFFSET :offset" else ""}
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
