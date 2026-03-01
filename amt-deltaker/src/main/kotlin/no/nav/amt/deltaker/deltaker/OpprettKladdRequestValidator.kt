package no.nav.amt.deltaker.deltaker

import io.ktor.server.plugins.requestvalidation.ValidationResult
import no.nav.amt.deltaker.apiclients.oppfolgingstilfelle.IsOppfolgingstilfelleClient
import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.deltaker.deltakerliste.DeltakerlisteRepository
import no.nav.amt.deltaker.navbruker.NavBrukerService
import no.nav.amt.lib.ktor.clients.AmtPersonServiceClient
import no.nav.amt.lib.models.deltaker.Innsatsgruppe
import no.nav.amt.lib.models.deltaker.internalapis.paamelding.request.OpprettKladdRequest
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import java.time.Year

class OpprettKladdRequestValidator(
    private val deltakerlisteRepository: DeltakerlisteRepository,
    private val brukerService: NavBrukerService,
    private val personServiceClient: AmtPersonServiceClient,
    private val isOppfolgingsTilfelleClient: IsOppfolgingstilfelleClient,
) {
    suspend fun validateRequest(request: OpprettKladdRequest): ValidationResult {
        val deltakerListe = deltakerlisteRepository.get(request.deltakerlisteId).getOrThrow()

        if (deltakerListe.erAvsluttet()) {
            return ValidationResult.Invalid("Deltakerliste er avsluttet")
        }

        if (!deltakerListe.apentForPamelding) {
            return ValidationResult.Invalid("Deltakerliste er ikke åpen for påmelding")
        }

        if (!harRiktigInnsatsGruppe(request.personident, deltakerListe)) {
            return ValidationResult.Invalid("Bruker har ikke riktig innsatsgruppe")
        }

        // pre-sjekk for deltakerForUng
        if (deltakerListe.tiltakstype.tiltakskode == Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING && deltakerListe.startDato == null) {
            return ValidationResult.Invalid(
                "Deltakerliste med tiltakskode ${Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING} mangler startdato",
            )
        }

        if (deltakerForUng(request.personident, deltakerListe)) {
            return ValidationResult.Invalid("DELTAKER_FOR_UNG ${deltakerListe.tiltakstype.tiltakskode}")
        }

        return ValidationResult.Valid
    }

    private suspend fun harRiktigInnsatsGruppe(personIdent: String, deltakerListe: Deltakerliste): Boolean {
        val navBruker = brukerService.get(personIdent).getOrThrow()

        return if (navBruker.innsatsgruppe in deltakerListe.tiltakstype.innsatsgrupper) {
            true
        } else if (deltakerListe.tiltakstype.tiltakskode == Tiltakskode.ARBEIDSRETTET_REHABILITERING &&
            navBruker.innsatsgruppe == Innsatsgruppe.SITUASJONSBESTEMT_INNSATS
        ) {
            isOppfolgingsTilfelleClient.erSykmeldtMedArbeidsgiver(navBruker.personident)
        } else {
            false
        }
    }

    private suspend fun deltakerForUng(personIdent: String, deltakerListe: Deltakerliste): Boolean {
        fun alderVedKursStart(foedselAar: Int): Int {
            val startDato = deltakerListe.startDato
                ?: throw IllegalStateException("Startdato kan ikke være null for ${Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING}")
            return Year.now().value.coerceAtLeast(startDato.year) - foedselAar
        }

        return if (deltakerListe.tiltakstype.tiltakskode == Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING) {
            // For kurstiltak med løpende oppstart, kan oppstartsdato for kurset være i fortiden.
            // Personen må ha fylt 19 år på tidspunktet som man melder på
            alderVedKursStart(personServiceClient.hentNavBrukerFodselsar(personIdent)) < GRUPPE_AMO_ALDERSGRENSE
        } else {
            false
        }
    }

    companion object {
        const val GRUPPE_AMO_ALDERSGRENSE = 19
    }
}
