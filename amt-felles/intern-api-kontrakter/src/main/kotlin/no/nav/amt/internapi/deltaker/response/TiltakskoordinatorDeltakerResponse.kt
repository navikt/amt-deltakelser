package no.nav.amt.internapi.deltaker.response

import no.nav.amt.lib.models.arrangor.melding.Vurderingstype
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.models.person.Beskyttelsesmarkering
import no.nav.amt.lib.models.person.address.Adresse
import no.nav.amt.lib.models.person.address.Adressebeskyttelse
import no.nav.amt.lib.models.person.extensions.toBeskyttelsesmarkering
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/**
 * Spisset respons-type for tiltakskoordinator-lista (`GET /tiltakskoordinator/deltakere/{gjennomforingId}`).
 *
 * Kun data BFF faktisk bruker for å rendre liste-visningen er med — ingen forslag-JSONB,
 * ingen full vurdering, ingen vedtaksinformasjon, deltakelsesinnhold eller bakgrunnsinformasjon.
 */
data class TiltakskoordinatorDeltakereResponse(
    val gjennomforing: GjennomforingResponse?,
    val deltakere: List<TiltakskoordinatorDeltakerResponse>,
)

data class TiltakskoordinatorDeltakerResponse(
    val id: UUID,
    val status: DeltakerStatus,
    val navBruker: TiltakskoordinatorNavBrukerResponse,
    val startdato: LocalDate?,
    val sluttdato: LocalDate?,
    val soktInnDato: LocalDate?,
    val erManueltDeltMedArrangor: Boolean,
    val erLaastForEndringer: Boolean,
    val harAktivtForslag: Boolean,
    val sisteVurderingstype: Vurderingstype?,
    val sistEndret: LocalDateTime,
    val kilde: Kilde,
    val opprettet: LocalDateTime,
    val prisinformasjon: String?,
)

data class TiltakskoordinatorNavBrukerResponse(
    val personident: String,
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
    val erSkjermet: Boolean,
    val adresse: Adresse?,
    val adressebeskyttelse: Adressebeskyttelse?,
    val navVeileder: NavVeilederResponse?,
    val navEnhet: String?,
    val erDigital: Boolean,
) {
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
