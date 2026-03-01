package no.nav.amt.deltaker.bff.deltakerliste

import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.deltaker.bff.deltakerliste.tiltakstype.TiltakstypeRepository
import no.nav.amt.deltaker.bff.utils.prefixColumn
import no.nav.amt.lib.models.deltaker.Arrangor
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.utils.database.Database
import org.slf4j.LoggerFactory
import java.util.UUID

class DeltakerlisteRepository {
    private val log = LoggerFactory.getLogger(javaClass)

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
            "status" to deltakerliste.status?.name,
            "arrangor_id" to deltakerliste.arrangor.arrangor.id,
            "tiltakstype_id" to deltakerliste.tiltak.id,
            "start_dato" to deltakerliste.startDato,
            "slutt_dato" to deltakerliste.sluttDato,
            "oppstart" to deltakerliste.oppstart?.name,
            "apent_for_pamelding" to deltakerliste.apentForPamelding,
            "antall_plasser" to deltakerliste.antallPlasser,
            "oppmote_sted" to deltakerliste.oppmoteSted,
            "pameldingstype" to deltakerliste.pameldingstype?.name,
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
        private val col = prefixColumn("dl")

        fun rowMapper(row: Row): Deltakerliste = Deltakerliste(
            id = row.uuid(col("id")),
            tiltak = TiltakstypeRepository.rowMapper(row, "t"),
            navn = row.string(col("navn")),
            status = row.stringOrNull(col("status"))?.let { GjennomforingStatusType.valueOf(it) },
            startDato = row.localDateOrNull(col("start_dato")),
            sluttDato = row.localDateOrNull(col("slutt_dato")),
            oppstart = row.stringOrNull(col("oppstart"))?.let { Oppstartstype.valueOf(it) },
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
            pameldingstype = row.stringOrNull(col("pameldingstype"))?.let { GjennomforingPameldingType.valueOf(it) },
        )
    }
}
