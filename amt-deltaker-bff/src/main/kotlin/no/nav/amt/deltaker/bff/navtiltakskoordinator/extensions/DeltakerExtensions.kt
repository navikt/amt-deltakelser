package no.nav.amt.deltaker.bff.navtiltakskoordinator.extensions

import no.nav.amt.deltaker.bff.deltaker.model.Deltaker
import no.nav.amt.deltaker.bff.navtiltakskoordinator.model.TiltakskoordinatorsDeltaker
import no.nav.amt.deltaker.bff.navtiltakskoordinator.ulesthendelse.model.UlestHendelse
import no.nav.amt.internapi.deltaker.response.NavVeilederResponse
import no.nav.amt.internapi.tiltakskoordinator.response.DeltakerOppdateringFeilkode
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.arrangor.melding.Vurdering
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavEnhet
import java.time.LocalDate

fun Deltaker.soktInnDato(): LocalDate? = this.historikk
    .filter {
        it is DeltakerHistorikk.InnsokPaaFellesOppstart ||
            it is DeltakerHistorikk.Vedtak ||
            it is DeltakerHistorikk.ImportertFraArena
    }.maxByOrNull { it.sistEndret }
    ?.let {
        when (it) {
            is DeltakerHistorikk.InnsokPaaFellesOppstart -> it.data.innsokt.toLocalDate()
            is DeltakerHistorikk.Vedtak -> it.vedtak.fattet?.toLocalDate()
            is DeltakerHistorikk.ImportertFraArena -> it.importertFraArena.deltakerVedImport.innsoktDato
            else -> null
        }
    }

fun Deltaker.toTiltakskoordinatorsDeltaker(
    sisteVurdering: Vurdering?,
    navEnhet: NavEnhet?,
    navVeileder: NavAnsatt?,
    feilkode: DeltakerOppdateringFeilkode? = null,
    ikkeDigitalOgManglerAdresse: Boolean,
    forslag: List<Forslag>,
    ulesteHendelser: List<UlestHendelse>,
): TiltakskoordinatorsDeltaker = TiltakskoordinatorsDeltaker(
    id = id,
    navBruker = navBruker,
    status = status,
    soktInnDato = this.soktInnDato(),
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
