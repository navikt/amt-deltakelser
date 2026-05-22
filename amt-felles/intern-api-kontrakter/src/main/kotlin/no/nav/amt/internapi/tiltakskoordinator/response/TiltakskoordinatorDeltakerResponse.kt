package no.nav.amt.internapi.tiltakskoordinator.response

import com.fasterxml.jackson.annotation.JsonIgnore
import no.nav.amt.lib.models.arrangor.melding.Vurderingstype
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.person.Beskyttelsesmarkering
import no.nav.amt.lib.models.person.address.Adressebeskyttelse
import no.nav.amt.lib.models.person.extensions.toBeskyttelsesmarkering
import java.time.LocalDate
import java.util.UUID

/**
 * Spisset respons-type for tiltakskoordinator-lista (`POST /tiltakskoordinator/deltakere/{gjennomforingId}`).
 *
 * Kun data BFF faktisk bruker for å rendre liste-visningen er med — ingen forslag-JSONB,
 * ingen full vurdering, ingen vedtaksinformasjon, deltakelsesinnhold eller bakgrunnsinformasjon.
 */

data class TiltakskoordinatorDeltakerResponse(
    val id: UUID,
    val status: DeltakerStatus,
    val navBruker: TiltakskoordinatorNavBrukerResponse,
    val startdato: LocalDate?,
    val sluttdato: LocalDate?,
    val soktInnDato: LocalDate?,
    val erManueltDeltMedArrangor: Boolean,
    val harAktivtForslag: Boolean,
    val sisteVurderingstype: Vurderingstype?,
)

data class TiltakskoordinatorNavBrukerResponse(
    val personident: String,
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
    val erSkjermet: Boolean,
    val adressebeskyttelse: Adressebeskyttelse?,
    val navEnhet: String?,
    val ikkeDigitalOgManglerAdresse: Boolean,
) {
    @get:JsonIgnore
    val beskyttelsesmarkeringer: List<Beskyttelsesmarkering>
        get() = listOfNotNull(
            adressebeskyttelse?.toBeskyttelsesmarkering(),
            if (erSkjermet) Beskyttelsesmarkering.SKJERMET else null,
        )

    fun getVisningsnavn(tilgangTilBruker: Boolean): Triple<String, String?, String> = when {
        adressebeskyttelse != null && !tilgangTilBruker -> Triple("Adressebeskyttet", null, "")
        erSkjermet && !tilgangTilBruker -> Triple("Skjermet person", null, "")
        else -> Triple(fornavn, mellomnavn, etternavn)
    }
}
