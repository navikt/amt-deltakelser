package no.nav.amt.deltaker.utils.data

import kotliquery.queryOf
import no.nav.amt.deltaker.arrangor.ArrangorRepository
import no.nav.amt.deltaker.deltaker.db.DeltakerEndringRepository
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.db.DeltakerStatusRepository
import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.endring.fra.arrangor.EndringFraArrangorRepository
import no.nav.amt.deltaker.deltaker.forslag.ForslagRepository
import no.nav.amt.deltaker.deltaker.importert.fra.arena.ImportertFraArenaRepository
import no.nav.amt.deltaker.deltaker.innsok.InnsokPaaFellesOppstartRepository
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.deltaker.deltakerliste.DeltakerlisteRepository
import no.nav.amt.deltaker.deltakerliste.tiltakstype.TiltakstypeRepository
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navbruker.NavBrukerRepository
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.tiltakskoordinator.endring.EndringFraTiltakskoordinatorRepository
import no.nav.amt.deltaker.utils.data.TestData.lagNavAnsatt
import no.nav.amt.deltaker.utils.data.TestData.lagNavEnhet
import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltaker.Arrangor
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.ImportertFraArena
import no.nav.amt.lib.models.deltaker.InnsokPaaFellesOppstart
import no.nav.amt.lib.models.deltaker.Vedtak
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavBruker
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import no.nav.amt.lib.utils.database.Database

object TestRepository {
    fun insert(navAnsatt: NavAnsatt) {
        navAnsatt.navEnhetId?.let { navEnhetId ->
            NavEnhetRepository().upsert(lagNavEnhet(navEnhetId))
        }

        NavAnsattRepository().upsert(navAnsatt)
    }

    fun insert(bruker: NavBruker) {
        bruker.navEnhetId?.let { NavEnhetRepository().upsert(lagNavEnhet(it)) }
        bruker.navVeilederId?.let { NavAnsattRepository().upsert(lagNavAnsatt(id = it, navEnhetId = bruker.navEnhetId)) }

        NavBrukerRepository().upsert(bruker)
    }

    fun insert(deltakerliste: Deltakerliste, overordnetArrangor: Arrangor? = null) {
        TiltakstypeRepository().upsert(deltakerliste.tiltakstype)
        overordnetArrangor?.let { ArrangorRepository().upsert(it) }
        ArrangorRepository().upsert(deltakerliste.arrangor)
        DeltakerlisteRepository().upsert(deltakerliste)
    }

    fun insert(vedtak: Vedtak) {
        VedtakRepository().upsert(vedtak)

        Database.query { session ->
            session.update(
                queryOf(
                    "UPDATE vedtak SET modified_at = :sist_endret, created_at = :opprettet WHERE id = :id",
                    mapOf(
                        "id" to vedtak.id,
                        "sist_endret" to vedtak.sistEndret,
                        "opprettet" to vedtak.opprettet,
                    ),
                ),
            )
        }
    }

    fun insert(deltaker: Deltaker, vedtak: Vedtak? = null) {
        insert(deltaker.navBruker)
        insert(deltaker.deltakerliste)
        DeltakerRepository().upsert(deltaker)
        DeltakerStatusRepository.lagreStatus(deltaker.id, deltaker.status)
        vedtak?.let { insert(vedtak) }
    }

    fun <T> insertAll(vararg values: T) {
        values.forEach {
            when (it) {
                is NavAnsatt -> insert(it)
                is NavBruker -> insert(it)
                is NavEnhet -> NavEnhetRepository().upsert(it)
                is Arrangor -> ArrangorRepository().upsert(it)
                is Tiltakstype -> TiltakstypeRepository().upsert(it)
                is Deltakerliste -> insert(it)
                is Deltaker -> insert(it)
                is Vedtak -> insert(it)
                is Forslag -> ForslagRepository().upsert(it)
                is DeltakerEndring -> DeltakerEndringRepository().upsert(it)
                is EndringFraArrangor -> EndringFraArrangorRepository().insert(it)
                is ImportertFraArena -> ImportertFraArenaRepository().upsert(it)
                is InnsokPaaFellesOppstart -> InnsokPaaFellesOppstartRepository().insert(it)
                is EndringFraTiltakskoordinator -> EndringFraTiltakskoordinatorRepository().insert(listOf(it))
                else -> NotImplementedError("insertAll for type ${it!!::class} er ikke implementert")
            }
        }
    }
}
