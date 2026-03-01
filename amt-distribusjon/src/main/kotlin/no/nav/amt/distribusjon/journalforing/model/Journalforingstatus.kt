package no.nav.amt.distribusjon.journalforing.model

import no.nav.amt.distribusjon.digitalbruker.DigitalBrukerService
import no.nav.amt.distribusjon.distribusjonskanal.Distribusjonskanal
import java.util.UUID

data class Journalforingstatus(
    val hendelseId: UUID,
    val journalpostId: String?,
    val bestillingsId: UUID?,
    val kanIkkeDistribueres: Boolean?,
    val kanIkkeJournalfores: Boolean?,
) {
    fun erJournalfort(): Boolean = journalpostId != null || kanIkkeJournalfores == true

    fun erDistribuert(distribusjonskanal: Distribusjonskanal, erUnderManuellOppfolging: Boolean): Boolean =
        if (DigitalBrukerService.skalDistribueresDigitalt(distribusjonskanal, erUnderManuellOppfolging)) {
            true
        } else {
            bestillingsId != null || kanIkkeDistribueres == true
        }
}
