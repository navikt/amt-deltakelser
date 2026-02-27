package no.nav.amt.deltaker.bff.deltaker.api.model

import no.nav.amt.deltaker.bff.deltaker.api.utils.harEndretSluttaarsak
import no.nav.amt.deltaker.bff.deltaker.api.utils.validerAarsaksBeskrivelse
import no.nav.amt.deltaker.bff.deltaker.api.utils.validerBegrunnelse
import no.nav.amt.deltaker.bff.deltaker.api.utils.validerDeltakerKanEndres
import no.nav.amt.deltaker.bff.deltaker.model.Deltaker
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import java.time.LocalDate
import java.util.UUID

data class EndreAvslutningRequest(
    val aarsak: DeltakerEndring.Aarsak?,
    val harDeltatt: Boolean? = true,
    val harFullfort: Boolean? = null,
    val begrunnelse: String?,
    val sluttdato: LocalDate? = null,
    override val forslagId: UUID?,
) : EndringsforslagRequest {
    private val kanEndreAvslutning =
        listOf(DeltakerStatus.Type.AVBRUTT, DeltakerStatus.Type.FULLFORT, DeltakerStatus.Type.HAR_SLUTTET, DeltakerStatus.Type.DELTAR)

    override fun valider(deltaker: Deltaker) {
        validerAarsaksBeskrivelse(aarsak?.beskrivelse)
        validerBegrunnelse(begrunnelse)
        validerDeltakerKanEndres(deltaker)
        require(deltaker.status.type in kanEndreAvslutning) {
            "Kan ikke endre avslutning for deltaker som ikke har status AVBRUTT, FULLFORT, HAR_SLUTTET eller DELTAR"
        }

        require(deltakerErEndret(deltaker)) {
            "Kan ikke avslutte deltakelse med uendret avslutning, årsak eller sluttdato"
        }

        val endreTilAvbrutt = harDeltatt() && !harFullfort()
        if (endreTilAvbrutt) {
            require(aarsak != null) { "Årsak er påkrevd for å avbryte deltakelse" }
        }
    }

    fun harDeltatt(): Boolean = harDeltatt == null || harDeltatt

    fun harFullfort(): Boolean = harFullfort == null || harFullfort

    private fun deltakerErEndret(deltaker: Deltaker): Boolean = (deltaker.status.type === DeltakerStatus.Type.AVBRUTT && harFullfort()) ||
        (deltaker.status.type === DeltakerStatus.Type.FULLFORT && !harFullfort()) ||
        harEndretSluttaarsak(deltaker.status.aarsak, aarsak) ||
        deltaker.sluttdato != sluttdato
}
