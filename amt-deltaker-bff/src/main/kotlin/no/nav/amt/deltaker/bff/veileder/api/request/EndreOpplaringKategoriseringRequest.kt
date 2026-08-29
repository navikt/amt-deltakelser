package no.nav.amt.deltaker.bff.veileder.api.request

import no.nav.amt.deltaker.bff.model.DeltakerModel
import no.nav.amt.deltaker.bff.veileder.api.utils.validerBegrunnelse
import no.nav.amt.deltaker.bff.veileder.api.utils.validerDeltakerKanEndres
import no.nav.amt.internapi.deltaker.request.OpplaringKategoriseringValgRequest
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltakerliste.SertifiseringValg

data class EndreOpplaringKategoriseringRequest(
    val beskrivelse: String,
    val opplaringKategoriseringValg: Set<OpplaringKategoriseringValgRequest>,
    val sertifiseringValg: Set<SertifiseringValg>,
    val pavirkerPris: Boolean,
) : EndringRequestFromFrontend {
    override fun valider(deltaker: DeltakerModel) {
        // merk at begrunnelse påkrevd i frontend, men følger samme mønster som i øvrige klasser
        require(beskrivelse.isNotBlank()) { "Begrunnelse kan ikke være tom" }
        validerSertifiseringInput(sertifiseringValg)
        require(deltaker.gjennomforing.erEnkeltplass) {
            "Kan ikke endre opplæringskategorisering for deltakere som ikke er på enkeltplass"
        }
        validerBegrunnelse(beskrivelse)

        validerDeltakerKanEndres(
            request = this,
            opprinneligDeltaker = deltaker,
        )
        require(deltaker.status.type !in statusFoerSoktInn) {
            "Kan ikke endre opplæringskategorisering for deltaker med status ${deltaker.status.type}"
        }
    }

    companion object {
        private val statusFoerSoktInn = setOf(
            DeltakerStatus.Type.KLADD,
            DeltakerStatus.Type.UTKAST_TIL_PAMELDING,
            DeltakerStatus.Type.AVBRUTT_UTKAST,
        )
    }
}

private fun validerSertifiseringInput(sertifiseringValg: Set<SertifiseringValg>) {
    val sertifiseringerMedIkkePositivId = sertifiseringValg.filter { it.id <= 0 }
    require(sertifiseringerMedIkkePositivId.isEmpty()) {
        "Ugyldig sertifiseringsvalg. Sertifisering-id må være positiv: $sertifiseringerMedIkkePositivId"
    }

    val sertifiseringerMedTomtNavn = sertifiseringValg.filter { it.navn.isBlank() }
    require(sertifiseringerMedTomtNavn.isEmpty()) {
        "Ugyldig sertifiseringsvalg. Sertifisering-navn kan ikke være tomt"
    }

    val iderMedFlereNavn = sertifiseringValg
        .groupBy { it.id }
        .filterValues { valg -> valg.map { it.navn }.toSet().size > 1 }
        .keys
    require(iderMedFlereNavn.isEmpty()) {
        "Ugyldig sertifiseringsvalg. Samme sertifisering-id kan ikke ha flere navn: $iderMedFlereNavn"
    }
}
