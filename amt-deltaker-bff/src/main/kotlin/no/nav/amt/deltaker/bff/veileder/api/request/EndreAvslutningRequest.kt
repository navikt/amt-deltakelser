package no.nav.amt.deltaker.bff.veileder.api.request

import no.nav.amt.deltaker.bff.model.DeltakerModel
import no.nav.amt.deltaker.bff.veileder.api.utils.harEndretSluttaarsak
import no.nav.amt.deltaker.bff.veileder.api.utils.validerAarsaksBeskrivelse
import no.nav.amt.deltaker.bff.veileder.api.utils.validerBegrunnelse
import no.nav.amt.deltaker.bff.veileder.api.utils.validerDeltakerKanEndres
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
) : EndringMedForslagRequest {
    private val kanEndreAvslutning =
        listOf(DeltakerStatus.Type.AVBRUTT, DeltakerStatus.Type.FULLFORT, DeltakerStatus.Type.HAR_SLUTTET, DeltakerStatus.Type.DELTAR)

    override fun valider(deltaker: DeltakerModel) {
        validerAarsaksBeskrivelse(aarsak?.beskrivelse)
        validerBegrunnelse(begrunnelse)
        validerDeltakerKanEndres(this, deltaker)
        require(deltaker.status.type in kanEndreAvslutning) {
            "Kan ikke endre avslutning for deltaker som ikke har status AVBRUTT, FULLFORT, HAR_SLUTTET eller DELTAR"
        }

        require(deltakerErEndret(deltaker)) {
            "Kan ikke avslutte deltakelse med uendret avslutning, årsak eller sluttdato"
        }

        if (deltaker.erLaastForEndringer && sluttdato != null) {
            require(sluttdato.isBefore(LocalDate.now())) {
                "Sluttdato må være tilbake i tid når deltakelsen er låst for endringer"
            }
        }
        val endreTilAvbrutt = harDeltatt() && !harFullfort()
        if (endreTilAvbrutt) {
            require(aarsak != null) { "Årsak er påkrevd for å avbryte deltakelse" }
        }
    }

    fun harDeltatt(): Boolean = harDeltatt == null || harDeltatt

    fun harFullfort(): Boolean = harFullfort == null || harFullfort

    private fun deltakerErEndret(deltaker: DeltakerModel): Boolean =
        (deltaker.status.type === DeltakerStatus.Type.AVBRUTT && harFullfort()) ||
            (deltaker.status.type === DeltakerStatus.Type.FULLFORT && !harFullfort()) ||
            harEndretSluttaarsak(deltaker.status.aarsak, aarsak) ||
            deltaker.sluttdato != sluttdato || (deltaker.status.type === DeltakerStatus.Type.HAR_SLUTTET && harDeltatt == false)
}
