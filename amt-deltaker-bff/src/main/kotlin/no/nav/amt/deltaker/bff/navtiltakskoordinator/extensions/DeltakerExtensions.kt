package no.nav.amt.deltaker.bff.navtiltakskoordinator.extensions

import no.nav.amt.deltaker.bff.model.Deltaker
import no.nav.amt.deltaker.bff.navtiltakskoordinator.model.TiltakskoordinatorsDeltaker
import no.nav.amt.deltaker.bff.navtiltakskoordinator.ulestdeltakerhendelse.model.UlestHendelse
import no.nav.amt.internapi.deltaker.response.NavVeilederResponse
import no.nav.amt.internapi.tiltakskoordinator.response.DeltakerOppdateringFeilkode
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.arrangor.melding.Vurdering
import no.nav.amt.lib.models.deltaker.extensions.getInnsoktDato
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavEnhet

fun Deltaker.toTiltakskoordinatorsDeltaker(
    sisteVurdering: Vurdering?,
    navEnhet: NavEnhet?,
    navVeileder: NavAnsatt?,
    feilkode: DeltakerOppdateringFeilkode? = null,
    ikkeDigitalOgManglerAdresse: Boolean,
    forslag: List<Forslag>,
    ulesteHendelser: List<UlestHendelse>,
) = TiltakskoordinatorsDeltaker(
    id = id,
    navBruker = navBruker,
    status = status,
    soktInnDato = this.historikk.getInnsoktDato()?.toLocalDate(),
    startdato = startdato,
    sluttdato = sluttdato,
    navEnhet = navEnhet?.navn,
    navVeileder = NavVeilederResponse(
        navn = navVeileder?.navn,
        telefonnummer = navVeileder?.telefon,
        epost = navVeileder?.epost,
    ),
    beskyttelsesmarkering = navBruker.beskyttelsesmarkeringer,
    vurdering = sisteVurdering,
    innsatsgruppe = navBruker.innsatsgruppe,
    deltakerliste = deltakerliste,
    erManueltDeltMedArrangor = erManueltDeltMedArrangor,
    kanEndres = kanEndres,
    feilkode = feilkode,
    ikkeDigitalOgManglerAdresse = ikkeDigitalOgManglerAdresse,
    forslag = forslag,
    ulesteHendelser = ulesteHendelser,
    deltakelsesinnhold = deltakelsesinnhold,
)
