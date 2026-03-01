package no.nav.amt.deltaker.bff.utils.data

import kotliquery.queryOf
import no.nav.amt.deltaker.bff.arrangor.ArrangorRepository
import no.nav.amt.deltaker.bff.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.bff.deltaker.db.DeltakerStatusRepository
import no.nav.amt.deltaker.bff.deltaker.model.Deltaker
import no.nav.amt.deltaker.bff.deltaker.navbruker.NavBrukerRepository
import no.nav.amt.deltaker.bff.deltakerliste.Deltakerliste
import no.nav.amt.deltaker.bff.deltakerliste.DeltakerlisteRepository
import no.nav.amt.deltaker.bff.deltakerliste.tiltakstype.TiltakstypeRepository
import no.nav.amt.deltaker.bff.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.bff.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.bff.utils.data.TestData.lagNavAnsatt
import no.nav.amt.lib.models.deltaker.Arrangor
import no.nav.amt.lib.models.person.NavBruker
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.utils.database.Database
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.UUID

object TestRepository {
    fun insert(deltakerliste: Deltakerliste, overordnetArrangor: Arrangor? = null) {
        TiltakstypeRepository().upsert(deltakerliste.tiltak)
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

    fun insert(navEnhet: NavEnhet, sistEndret: LocalDateTime) {
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
        bruker.navVeilederId?.let { NavAnsattRepository().upsert(lagNavAnsatt(it)) }
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
}
