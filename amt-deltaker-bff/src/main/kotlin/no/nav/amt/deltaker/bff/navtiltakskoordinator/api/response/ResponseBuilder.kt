package no.nav.amt.deltaker.bff.navtiltakskoordinator.api.response

import no.nav.amt.deltaker.bff.model.DeltakerModel
import no.nav.amt.deltaker.bff.navtiltakskoordinator.ulestdeltakerhendelse.UlestHendelseService
import no.nav.amt.deltaker.bff.navtiltakskoordinator.ulestdeltakerhendelse.model.UlestHendelseType
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltaker.extensions.getInnsoktDato

class ResponseBuilder(
    private val ulestHendelseService: UlestHendelseService,
) {
    fun toDeltakerResponse(
        deltaker: DeltakerModel,
        kanSeInnbyggersNavn: Boolean,
    ): DeltakerResponse = with(deltaker) {
        val (fornavn, mellomnavn, etternavn) = navBruker.getVisningsnavn(kanSeInnbyggersNavn)
        val ulesteHendelser = ulestHendelseService.getUlesteHendelserForDeltaker(id)

        return DeltakerResponse(
            id = id,
            fornavn = fornavn,
            mellomnavn = mellomnavn,
            etternavn = etternavn,
            status = DeltakerStatusResponse(
                type = status.type,
                aarsak = status.aarsak?.let {
                    DeltakerStatusAarsakResponse(
                        it.type,
                        it.beskrivelse,
                    )
                },
            ),
            vurdering = sisteVurdering?.type,
            beskyttelsesmarkering = navBruker.beskyttelsesmarkeringer,
            navEnhet = navBruker.navEnhet,
            erManueltDeltMedArrangor = erManueltDeltMedArrangor,
            // TODO: Hva skal gjøres her? Denne er alltid null når vi henter data men ved oppdateringer så skal den settes
            // om noe går galt
            feilkode = null,
            ikkeDigitalOgManglerAdresse = navBruker.adresse == null && !navBruker.erDigital,
            harAktiveForslag = endringsforslagFraArrangor.any { f -> f.status == Forslag.Status.VenterPaSvar },
            erNyDeltaker = ulesteHendelser.any {
                it.hendelse is UlestHendelseType.InnbyggerGodkjennUtkast ||
                    it.hendelse is UlestHendelseType.NavGodkjennUtkast
            },
            harOppdateringFraNav = ulesteHendelser.any {
                it.hendelse is UlestHendelseType.IkkeAktuell ||
                    it.hendelse is UlestHendelseType.AvsluttDeltakelse ||
                    it.hendelse is UlestHendelseType.AvbrytDeltakelse ||
                    it.hendelse is UlestHendelseType.ReaktiverDeltakelse
            },
            kanEndres = !erLaastForEndringer,
            soktInnDato = historikk.getInnsoktDato()?.toLocalDate(),
            startdato = startdato,
            sluttdato = sluttdato,
        )
    }
}
