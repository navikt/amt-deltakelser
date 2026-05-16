package no.nav.amt.deltaker.repository

import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.lib.models.arrangor.melding.Vurderingstype
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.models.person.address.Adressebeskyttelse
import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.objectMapper
import tools.jackson.module.kotlin.readValue
import java.util.UUID

class TiltakskoordinatorViewRepository {
    /**
     * Henter alle deltakere for en gjennomføring med berikede felt (soktInnDato,
     * harAktivtForslag, sisteVurderingstype, digital-bruker-cache, låse-felt).
     *
     * Deltakerliste-/tiltakstype-/arrangør-kolonner er **ikke** med — de hentes via
     * [DeltakerlisteRepository.get] for å unngå å gjenta identiske data
     * for alle deltakere (kan være 2000+).
     */
    fun getDeltakere(gjennomforingId: UUID): List<TiltakskoordinatorDeltakerRow> = Database.query { session ->
        session.run(
            queryOf(DELTAKERE_SQL, mapOf("deltakerliste_id" to gjennomforingId))
                .map(::deltakerRowMapper)
                .asList,
        )
    }

    companion object {
        private val DELTAKERE_SQL =
            """
            SELECT
                -- deltaker
                d.id                            AS "d.id",
                d.startdato                     AS "d.startdato",
                d.sluttdato                     AS "d.sluttdato",
                d.modified_at                   AS "d.modified_at",
                d.kilde                         AS "d.kilde",
                d.er_manuelt_delt_med_arrangor  AS "d.er_manuelt_delt_med_arrangor",
                d.created_at                    AS "d.created_at",

                -- nav_bruker
                nb.personident                  AS "nb.personident",
                nb.fornavn                      AS "nb.fornavn",
                nb.mellomnavn                   AS "nb.mellomnavn",
                nb.etternavn                    AS "nb.etternavn",
                nb.nav_veileder_id              AS "nb.nav_veileder_id",
                nb.nav_enhet_id                 AS "nb.nav_enhet_id",
                nb.er_skjermet                  AS "nb.er_skjermet",
                nb.adresse                      AS "nb.adresse",
                nb.adressebeskyttelse           AS "nb.adressebeskyttelse",

                -- deltaker_status
                ds.id                           AS "ds.id",
                ds.type                         AS "ds.type",
                ds.aarsak                       AS "ds.aarsak",
                ds.gyldig_fra                   AS "ds.gyldig_fra",
                ds.gyldig_til                   AS "ds.gyldig_til",
                ds.created_at                   AS "ds.created_at",

                -- nav_ansatt (veileder) — LEFT JOIN, kan være null
                na.navn                         AS "na.navn",
                na.epost                        AS "na.epost",
                na.telefonnummer                AS "na.telefon",

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
                    AND ds.type NOT IN ('KLADD', 'UTKAST_TIL_PAMELDING', 'AVBRUTT_UTKAST', 'FEILREGISTRERT', 'PABEGYNT_REGISTRERING')
                -- Enkel vedtak-JOIN (UNIQUE deltaker_id garanterer maks 1 rad)
                LEFT JOIN vedtak v ON v.deltaker_id = d.id
                LEFT JOIN nav_ansatt na ON na.id = nb.nav_veileder_id
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
                    WHERE f.deltaker_id = d.id
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
            WHERE d.deltakerliste_id = :deltakerliste_id
            """.trimIndent()

        private fun deltakerRowMapper(row: Row): TiltakskoordinatorDeltakerRow {
            val statusType = DeltakerStatus.Type.valueOf(row.string("ds.type"))

            return TiltakskoordinatorDeltakerRow(
                id = row.uuid("d.id"),
                personident = row.string("nb.personident"),
                startdato = row.localDateOrNull("d.startdato"),
                sluttdato = row.localDateOrNull("d.sluttdato"),
                sistEndret = row.localDateTime("d.modified_at"),
                kilde = Kilde.valueOf(row.string("d.kilde")),
                erManueltDeltMedArrangor = row.boolean("d.er_manuelt_delt_med_arrangor"),
                opprettet = row.localDateTime("d.created_at"),
                status = DeltakerStatus(
                    id = row.uuid("ds.id"),
                    type = statusType,
                    aarsak = row.stringOrNull("ds.aarsak")?.let { objectMapper.readValue(it) },
                    gyldigFra = row.localDateTime("ds.gyldig_fra"),
                    gyldigTil = row.localDateTimeOrNull("ds.gyldig_til"),
                    opprettet = row.localDateTime("ds.created_at"),
                ),
                fornavn = row.string("nb.fornavn"),
                mellomnavn = row.stringOrNull("nb.mellomnavn"),
                etternavn = row.string("nb.etternavn"),
                erSkjermet = row.boolean("nb.er_skjermet"),
                adresse = row.stringOrNull("nb.adresse")?.let { objectMapper.readValue(it) },
                adressebeskyttelse = row.stringOrNull("nb.adressebeskyttelse")?.let { Adressebeskyttelse.valueOf(it) },
                navVeilederId = row.uuidOrNull("nb.nav_veileder_id"),
                navVeilederNavn = row.stringOrNull("na.navn"),
                navVeilederEpost = row.stringOrNull("na.epost"),
                navVeilederTelefon = row.stringOrNull("na.telefon"),
                navEnhetId = row.uuidOrNull("nb.nav_enhet_id"),
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
}
