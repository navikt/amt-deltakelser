package no.nav.amt.deltaker.bff.gjennomforing

import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.deltaker.bff.model.Deltakerliste
import no.nav.amt.deltaker.bff.navtiltakskoordinator.api.response.DeltakerlisteFilterCountsResponse
import no.nav.amt.deltaker.bff.tiltak.TiltakRepository
import no.nav.amt.deltaker.bff.utils.prefixColumn
import no.nav.amt.internapi.tiltakskoordinator.HandlingFilterValg
import no.nav.amt.internapi.tiltakskoordinator.request.TiltaksKoordinatorDeltakerlisteRequest
import no.nav.amt.lib.models.deltaker.Arrangor
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.utils.database.Database
import org.slf4j.LoggerFactory
import java.util.UUID

class DeltakerlisteRepository {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Henter antall deltakere per deltakerstatus for en gjennomføring.
     *
     * Aggregerer deltakere etter status, uavhengig av øvrige filter (f.eks. harForslagFraArrangor).
     * Statuser må spesifiseres i requesten — returnerer tom map hvis ingen deltakere finnes.
     *
     * @param request inneholder gjennomforingId og statuser (påkrevd, må være ikke-tomt)
     * @return Map av deltakerstatus til antall deltakere med den statusen
     * @throws IllegalArgumentException hvis request.statuser er tomt
     */
    fun getDeltakereCountPerStatus(request: TiltaksKoordinatorDeltakerlisteRequest): DeltakerlisteFilterCountsResponse {
        require(request.statuser.isNotEmpty()) { "Statuser må spesifiseres for å hente deltakerantall per status" }

        val rows = Database.query { session ->
            session
                .run(
                    queryOf(
                        deltakereCountSql(request),
                        mapOf("deltakerliste_id" to request.gjennomforingId)
                            .plus(statusFilterParams(request)),
                    ).map { row ->
                        CountPerStatusRow(
                            status = DeltakerStatus.Type.valueOf(row.string("type")),
                            count = row.int("count"),
                            erNyDeltakerCount = row.int("er_ny_deltaker_count"),
                            harOppdateringFraNavCount = row.int("har_oppdatering_fra_nav_count"),
                            harAktivtForslagCount = row.int("har_aktivt_forslag_count"),
                        )
                    }.asList,
                )
        }

        return DeltakerlisteFilterCountsResponse(
            statusCounts = rows.associate { it.status to it.count },
            handlingCounts = mapOf(
                HandlingFilterValg.NyeDeltakere to rows.sumOf { it.erNyDeltakerCount },
                HandlingFilterValg.OppdateringFraNav to rows.sumOf { it.harOppdateringFraNavCount },
                HandlingFilterValg.AktiveForslag to rows.sumOf { it.harAktivtForslagCount },
            ),
        )
    }

    fun upsert(deltakerliste: Deltakerliste) {
        val sql =
            """
            INSERT INTO deltakerliste (
                id, 
                navn, 
                status, 
                arrangor_id, 
                tiltakstype_id, 
                start_dato, 
                slutt_dato, 
                oppstart,
                apent_for_pamelding,
                antall_plasser,
                oppmote_sted,
                pameldingstype
            )
            VALUES (
                :id,
                :navn,
                :status,
                :arrangor_id,
                :tiltakstype_id,
                :start_dato,
                :slutt_dato,
                :oppstart,
                :apent_for_pamelding,
                :antall_plasser,
                :oppmote_sted,
                :pameldingstype
            )
            ON CONFLICT (id) DO UPDATE SET
                navn     				= :navn,
                status					= :status,
                arrangor_id 			= :arrangor_id,
                tiltakstype_id			= :tiltakstype_id,
                start_dato				= :start_dato,
                slutt_dato				= :slutt_dato,
                oppstart                = :oppstart,
                modified_at             = CURRENT_TIMESTAMP,
                apent_for_pamelding     = :apent_for_pamelding,
                antall_plasser          = :antall_plasser,
                oppmote_sted            = :oppmote_sted,
                pameldingstype          = :pameldingstype
            """.trimIndent()

        val params = mapOf(
            "id" to deltakerliste.id,
            "navn" to deltakerliste.navn,
            "status" to deltakerliste.status.name,
            "arrangor_id" to deltakerliste.arrangor.arrangor.id,
            "tiltakstype_id" to deltakerliste.tiltak.id,
            "start_dato" to deltakerliste.startDato,
            "slutt_dato" to deltakerliste.sluttDato,
            "oppstart" to deltakerliste.oppstart.name,
            "apent_for_pamelding" to deltakerliste.apentForPamelding,
            "antall_plasser" to deltakerliste.antallPlasser,
            "oppmote_sted" to deltakerliste.oppmoteSted,
            "pameldingstype" to deltakerliste.pameldingstype.name,
        )

        Database.query { session -> session.update(queryOf(sql, params)) }
        log.info("Upsertet deltakerliste med id ${deltakerliste.id}")
    }

    fun delete(id: UUID) = Database.query { session ->
        session.update(
            queryOf(
                statement = "DELETE FROM deltakerliste WHERE id = :id",
                paramMap = mapOf("id" to id),
            ),
        )
        log.info("Slettet deltakerliste med id $id")
    }

    fun get(id: UUID): Result<Deltakerliste> = runCatching {
        val query = queryOf(
            """
            SELECT 
                dl.id as "dl.id",
                dl.navn as "dl.navn",
                dl.status as "dl.status",
                dl.start_dato as "dl.start_dato",
                dl.slutt_dato as "dl.slutt_dato",
                dl.oppstart as "dl.oppstart",
                dl.apent_for_pamelding as "dl.apent_for_pamelding",
                dl.antall_plasser as "dl.antall_plasser",
                dl.oppmote_sted as "dl.oppmote_sted",
                dl.pameldingstype as "dl.pameldingstype",
                a.id as "a.id",
                a.navn as "a.navn",
                a.organisasjonsnummer as "a.organisasjonsnummer",
                a.overordnet_arrangor_id as "a.overordnet_arrangor_id",
                oa.navn as "oa.navn",
                t.id as "t.id",
                t.navn as "t.navn",
                t.tiltakskode as "t.tiltakskode",
                t.innsatsgrupper as "t.innsatsgrupper",
                t.innhold as "t.innhold"
            FROM 
                deltakerliste dl
                JOIN arrangor a ON a.id = dl.arrangor_id
                LEFT JOIN arrangor oa ON oa.id = a.overordnet_arrangor_id
                LEFT JOIN tiltakstype t ON dl.tiltakstype_id = t.id
            WHERE dl.id = :id
            """.trimIndent(),
            mapOf("id" to id),
        ).map(::rowMapper).asSingle

        Database.query { session ->
            session.run(query) ?: throw NoSuchElementException("Fant ikke deltakerliste med id $id")
        }
    }

    companion object {
        private data class CountPerStatusRow(
            val status: DeltakerStatus.Type,
            val count: Int,
            val erNyDeltakerCount: Int,
            val harOppdateringFraNavCount: Int,
            val harAktivtForslagCount: Int,
        )

        private fun statusFilterSql(request: TiltaksKoordinatorDeltakerlisteRequest) = request.statuser
            .takeIf { it.isNotEmpty() }
            ?.let { " AND ds.type = ANY(:statuser)" }
            ?: ""

        private fun statusFilterParams(request: TiltaksKoordinatorDeltakerlisteRequest) = if (request.statuser.isNotEmpty()) {
            mapOf("statuser" to request.statuser.map { it.name }.toTypedArray())
        } else {
            emptyMap<String, Any>()
        }

        private fun deltakereCountSql(request: TiltaksKoordinatorDeltakerlisteRequest) =
            """
            SELECT
                ds.type,
                COUNT(*) AS count,
                COUNT(*) FILTER (WHERE uh_flags.er_ny_deltaker) AS er_ny_deltaker_count,
                COUNT(*) FILTER (WHERE uh_flags.har_oppdatering_fra_nav) AS har_oppdatering_fra_nav_count,
                COUNT(*) FILTER (WHERE af.har_aktivt) AS har_aktivt_forslag_count
            FROM
                deltaker d
                JOIN nav_bruker nb ON d.person_id = nb.person_id
                JOIN deltaker_status ds ON
                    d.id = ds.deltaker_id
                    ${statusFilterSql(request)}
                LEFT JOIN (
                    SELECT
                        deltaker_id,
                        BOOL_OR(hendelse->>'type' IN ('InnbyggerGodkjennUtkast', 'NavGodkjennUtkast')) AS er_ny_deltaker,
                        BOOL_OR(hendelse->>'type' IN ('IkkeAktuell', 'AvsluttDeltakelse', 'AvbrytDeltakelse', 'ReaktiverDeltakelse')) AS har_oppdatering_fra_nav
                    FROM ulest_hendelse
                    GROUP BY deltaker_id
                ) uh_flags ON uh_flags.deltaker_id = d.id
                LEFT JOIN LATERAL (
                    SELECT true AS har_aktivt
                    FROM forslag f
                    WHERE
                        f.deltaker_id = d.id
                        AND f.status->>'type' = 'VenterPaSvar'
                    LIMIT 1
                ) af ON true
            WHERE 
                d.deltakerliste_id = :deltakerliste_id
            GROUP BY ds.type
            """.trimIndent()

        private val col = prefixColumn("dl")

        fun rowMapper(row: Row): Deltakerliste = Deltakerliste(
            id = row.uuid(col("id")),
            tiltak = TiltakRepository.rowMapper(row, "t"),
            navn = row.string(col("navn")),
            status = row.string(col("status")).let { GjennomforingStatusType.valueOf(it) },
            startDato = row.localDateOrNull(col("start_dato")),
            sluttDato = row.localDateOrNull(col("slutt_dato")),
            oppstart = row.string(col("oppstart")).let { Oppstartstype.valueOf(it) },
            arrangor = Deltakerliste.Arrangor(
                arrangor = Arrangor(
                    id = row.uuid("a.id"),
                    navn = row.string("a.navn"),
                    organisasjonsnummer = row.string("a.organisasjonsnummer"),
                    overordnetArrangorId = row.uuidOrNull("a.overordnet_arrangor_id"),
                ),
                overordnetArrangorNavn = row.uuidOrNull("a.overordnet_arrangor_id")?.let {
                    row.string("oa.navn")
                },
            ),
            antallPlasser = row.intOrNull(col("antall_plasser")),
            apentForPamelding = row.boolean(col("apent_for_pamelding")),
            oppmoteSted = row.stringOrNull(col("oppmote_sted")),
            pameldingstype = row.string(col("pameldingstype")).let { GjennomforingPameldingType.valueOf(it) },
        )
    }
}
