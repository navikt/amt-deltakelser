package no.nav.amt.distribusjon.digitalbruker

import no.nav.amt.distribusjon.distribusjonskanal.Distribusjonskanal
import no.nav.amt.distribusjon.distribusjonskanal.DokdistkanalClient
import no.nav.amt.distribusjon.distribusjonskanal.skalDistribueresDigitalt
import no.nav.amt.distribusjon.veilarboppfolging.VeilarboppfolgingClient

class DigitalBrukerService(
    private val dokdistkanalClient: DokdistkanalClient,
    private val veilarboppfolgingClient: VeilarboppfolgingClient,
) {
    companion object {
        fun skalDistribueresDigitalt(distribusjonskanal: Distribusjonskanal, erUnderManuellOppfolging: Boolean): Boolean {
            if (erUnderManuellOppfolging) {
                return false
            }
            return distribusjonskanal.skalDistribueresDigitalt()
        }
    }

    suspend fun erDigital(personident: String): Boolean {
        val erUnderManuellOppfolging = veilarboppfolgingClient.erUnderManuellOppfolging(personident)
        if (erUnderManuellOppfolging) {
            return false
        }
        val distribusjonskanal = dokdistkanalClient.bestemDistribusjonskanal(personident)
        return distribusjonskanal.skalDistribueresDigitalt()
    }
}
