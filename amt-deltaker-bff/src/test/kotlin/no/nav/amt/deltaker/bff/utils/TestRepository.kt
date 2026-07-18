package no.nav.amt.deltaker.bff.utils

import kotliquery.Row
import kotliquery.queryOf
import no.nav.amt.deltaker.bff.deltaker.DeltakerRepository
import no.nav.amt.deltaker.bff.deltaker.DeltakerStatusRepository
import no.nav.amt.deltaker.bff.gjennomforing.DeltakerlisteRepository
import no.nav.amt.deltaker.bff.innbygger.NavBrukerRepository
import no.nav.amt.deltaker.bff.model.Deltaker
import no.nav.amt.deltaker.bff.model.Deltakerliste
import no.nav.amt.deltaker.bff.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.bff.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.bff.tiltak.TiltakRepository
import no.nav.amt.deltaker.bff.tiltaksarrangor.ArrangorRepository
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.arrangor.melding.Vurdering
import no.nav.amt.lib.models.arrangor.melding.Vurderingstype
import no.nav.amt.lib.models.deltaker.Arrangor
import no.nav.amt.lib.models.person.NavBruker
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.testing.utils.TestData
import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.objectMapper
import tools.jackson.module.kotlin.readValue
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.UUID

object TestRepository {
    fun insert(
        deltakerliste: Deltakerliste,
        overordnetArrangor: Arrangor? = null,
    ) {
        TiltakRepository().upsert(deltakerliste.tiltak)
        overordnetArrangor?.let { ArrangorRepository().upsert(it) }
        ArrangorRepository().upsert(deltakerliste.arrangor.arrangor)
        DeltakerlisteRepository().upsert(deltakerliste)
    }

    fun insert(deltaker: Deltaker) {
        insert(deltaker.navBruker)
        insert(deltaker.deltakerliste)
        DeltakerRepository().upsert(deltaker)
        DeltakerStatusRepository.insertIfNotExists(deltaker.id, deltaker.status)
    }

    fun insert(
        navEnhet: NavEnhet,
        sistEndret: LocalDateTime,
    ) {
        NavEnhetRepository().upsert(navEnhet)

        Database.query { session ->
            session.update(
                queryOf(
                    "UPDATE nav_enhet SET modified_at = :modified_at WHERE id = :id",
                    mapOf(
                        "id" to navEnhet.id,
                        "modified_at" to sistEndret,
                    ),
                ),
            )
        }
    }

    fun insert(bruker: NavBruker) {
        bruker.navVeilederId?.let { NavAnsattRepository().upsert(TestData.lagNavAnsatt(it)) }
        bruker.navEnhetId?.let { NavEnhetRepository().upsert(TestData.lagNavEnhet(it)) }
        NavBrukerRepository().upsert(bruker)
    }

    fun getDeltakerSistBesokt(deltakerId: UUID): ZonedDateTime? = Database.query { session ->
        session.run(
            queryOf(
                "SELECT sist_besokt FROM deltaker WHERE id = ?",
                deltakerId,
            ).map { row -> row.zonedDateTime("sist_besokt") }.asSingle,
        )
    }

    fun getVurderingerForDeltaker(deltakerId: UUID): List<Vurdering> = Database.query { session ->
        session.run(
            queryOf(
                "SELECT * FROM vurdering WHERE deltaker_id = :deltaker_id",
                mapOf("deltaker_id" to deltakerId),
            ).map { row ->
                Vurdering(
                    id = row.uuid("id"),
                    deltakerId = row.uuid("deltaker_id"),
                    opprettetAvArrangorAnsattId = row.uuid("opprettet_av_arrangor_ansatt_id"),
                    opprettet = row.localDateTime("opprettet"),
                    vurderingstype = Vurderingstype.valueOf(row.string("vurderingstype")),
                    begrunnelse = row.stringOrNull("begrunnelse"),
                )
            }.asList,
        )
    }

    fun getForslagForDeltaker(deltakerId: UUID): List<Forslag> = Database.query { session ->
        session.run(
            queryOf(
                "SELECT * FROM forslag WHERE deltaker_id = :deltaker_id",
                mapOf("deltaker_id" to deltakerId),
            ).map(::forslagRowMapper).asList,
        )
    }

    private fun forslagRowMapper(row: Row) = Forslag(
        id = row.uuid("id"),
        deltakerId = row.uuid("deltaker_id"),
        opprettetAvArrangorAnsattId = row.uuid("arrangoransatt_id"),
        opprettet = row.localDateTime("opprettet"),
        begrunnelse = row.stringOrNull("begrunnelse"),
        endring = objectMapper.readValue(row.string("endring")),
        status = objectMapper.readValue(row.string("status")),
    )
}
