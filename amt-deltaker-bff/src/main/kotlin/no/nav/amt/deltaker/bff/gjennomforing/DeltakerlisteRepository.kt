package no.nav.amt.deltaker.bff.gjennomforing

import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.deltaker.bff.model.Deltakerliste
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

    fun upsert(
        deltakerliste: Deltakerliste,
        tiltakstypeId: UUID,
    ) {
        val sql =
            """
            INSERT INTO deltakerliste (
                id, 
                status, 
                arrangor_id, 
                tiltakstype_id, 
                slutt_dato, 
                oppstart,
                pameldingstype
            )
            VALUES (
                :id,
                :status,
                :arrangor_id,
                :tiltakstype_id,
                :slutt_dato,
                :oppstart,
                :pameldingstype
            )
            ON CONFLICT (id) DO UPDATE SET
                status					= :status,
                arrangor_id 			= :arrangor_id,
                tiltakstype_id			= :tiltakstype_id,
                slutt_dato				= :slutt_dato,
                oppstart                = :oppstart,
                modified_at             = CURRENT_TIMESTAMP,
                pameldingstype          = :pameldingstype
            """.trimIndent()

        val params = mapOf(
            "id" to deltakerliste.id,
            "status" to deltakerliste.status.name,
            "arrangor_id" to deltakerliste.arrangor.id,
            "tiltakstype_id" to tiltakstypeId,
            "slutt_dato" to deltakerliste.sluttDato,
            "oppstart" to deltakerliste.oppstart.name,
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

    // verifiserTilgjengeligDeltakerliste
    fun get(id: UUID): Result<Deltakerliste> = runCatching {
        val query = queryOf(
            """
            SELECT 
                dl.id as "dl.id",
                dl.status as "dl.status",
                dl.slutt_dato as "dl.slutt_dato",
                dl.oppstart as "dl.oppstart",
                dl.pameldingstype as "dl.pameldingstype",
                a.id as "a.id",
                a.navn as "a.navn",
                a.organisasjonsnummer as "a.organisasjonsnummer",
                a.overordnet_arrangor_id as "a.overordnet_arrangor_id"
            FROM 
                deltakerliste dl
                JOIN arrangor a ON a.id = dl.arrangor_id
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
            status = row.string(col("status")).let { GjennomforingStatusType.valueOf(it) },
            sluttDato = row.localDateOrNull(col("slutt_dato")),
            oppstart = row.string(col("oppstart")).let { Oppstartstype.valueOf(it) },
            arrangor = Arrangor(
                id = row.uuid("a.id"),
                navn = row.string("a.navn"),
                organisasjonsnummer = row.string("a.organisasjonsnummer"),
                overordnetArrangorId = row.uuidOrNull("a.overordnet_arrangor_id"),
            ),
            pameldingstype = row.string(col("pameldingstype")).let { GjennomforingPameldingType.valueOf(it) },
        )
    }
}
