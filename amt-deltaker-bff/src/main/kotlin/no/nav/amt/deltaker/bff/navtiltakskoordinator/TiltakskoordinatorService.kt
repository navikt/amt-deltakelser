package no.nav.amt.deltaker.bff.navtiltakskoordinator

import no.nav.amt.deltaker.bff.deltaker.DeltakerRepository
import no.nav.amt.deltaker.bff.deltaker.DeltakerService
import no.nav.amt.deltaker.bff.model.Deltaker
import no.nav.amt.deltaker.bff.navansatt.NavAnsattService
import no.nav.amt.deltaker.bff.navenhet.NavEnhetService
import no.nav.amt.deltaker.bff.navtiltakskoordinator.api.AvslagRequest
import no.nav.amt.deltaker.bff.navtiltakskoordinator.api.response.DeltakerResponseUtils.SKJULTE_STATUSER
import no.nav.amt.deltaker.bff.navtiltakskoordinator.extensions.toTiltakskoordinatorsDeltaker
import no.nav.amt.deltaker.bff.navtiltakskoordinator.model.TiltakskoordinatorsDeltaker
import no.nav.amt.deltaker.bff.navtiltakskoordinator.ulestdeltakerhendelse.UlestHendelseRepository
import no.nav.amt.deltaker.bff.tiltaksarrangor.forslag.ForslagRepository
import no.nav.amt.deltaker.bff.tiltaksarrangor.vurdering.VurderingService
import no.nav.amt.internapi.tiltakskoordinator.response.TiltakskoordinatorDeltakerResponse
import no.nav.amt.lib.ktor.clients.distribusjon.AmtDistribusjonClient
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import java.util.UUID

class TiltakskoordinatorService(
    private val tiltaksKoordinatorClient: TiltakskoordinatorClient,
    private val deltakerRepository: DeltakerRepository,
    private val deltakerService: DeltakerService,
    private val vurderingService: VurderingService,
    private val navEnhetService: NavEnhetService,
    private val navAnsattService: NavAnsattService,
    private val amtDistribusjonClient: AmtDistribusjonClient,
    private val forslagRepository: ForslagRepository,
    private val ulestHendelseRepository: UlestHendelseRepository,
) {
    suspend fun endreDeltakere(
        deltakerIder: List<UUID>,
        endring: EndringFraTiltakskoordinator.Endring,
        endretAv: String,
    ): List<TiltakskoordinatorDeltakerResponse> = when (endring) {
        EndringFraTiltakskoordinator.SettPaaVenteliste -> tiltaksKoordinatorClient.settPaaVenteliste(deltakerIder, endretAv)
        EndringFraTiltakskoordinator.DelMedArrangor -> tiltaksKoordinatorClient.delMedArrangor(deltakerIder, endretAv)
        EndringFraTiltakskoordinator.TildelPlass -> tiltaksKoordinatorClient.tildelPlass(deltakerIder, endretAv)
        is EndringFraTiltakskoordinator.Avslag -> throw NotImplementedError("Batch håndtering for avslag er ikke støttet")
    }

    suspend fun giAvslag(
        request: AvslagRequest,
        endretAv: String,
    ): TiltakskoordinatorsDeltaker {
        val deltakeroppdatering = tiltaksKoordinatorClient.giAvslag(request, endretAv)

        deltakerService.oppdaterDeltaker(deltakeroppdatering)

        return deltakerRepository.get(deltakeroppdatering.id).getOrThrow().toTiltakskoordinatorsDeltaker()
    }

    fun TiltakskoordinatorsDeltaker.skalSkjules() = status.type in SKJULTE_STATUSER

    private suspend fun Deltaker.toTiltakskoordinatorsDeltaker() = listOf(this).toTiltakskoordinatorsDeltaker().first()

    private suspend fun List<Deltaker>.toTiltakskoordinatorsDeltaker(): List<TiltakskoordinatorsDeltaker> {
        val navEnheter = navEnhetService.hentEnheter(this.mapNotNull { it.navBruker.navEnhetId })
        val navVeiledere = navAnsattService.hentAnsatte(this.mapNotNull { it.navBruker.navVeilederId })
        val forslag = forslagRepository.getForDeltakere(this.map { it.id })

        return this
            .map {
                val sisteVurdering = vurderingService.getSisteVurderingForDeltaker(it.id)

                var ikkeDigitalOgManglerAdresse = false
                if (it.navBruker.adresse == null) {
                    ikkeDigitalOgManglerAdresse = !amtDistribusjonClient.digitalBruker(it.navBruker.personident)
                }

                val ulesteHendelser = ulestHendelseRepository.getForDeltaker(it.id)

                it.toTiltakskoordinatorsDeltaker(
                    sisteVurdering,
                    navEnheter[it.navBruker.navEnhetId],
                    navVeiledere[it.navBruker.navVeilederId],
                    null,
                    ikkeDigitalOgManglerAdresse,
                    forslag.filter { forslag -> forslag.deltakerId == it.id },
                    ulesteHendelser,
                )
            }.filterNot { it.skalSkjules() }
    }
}
