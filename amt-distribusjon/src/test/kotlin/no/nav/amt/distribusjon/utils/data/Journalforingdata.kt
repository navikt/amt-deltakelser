package no.nav.amt.distribusjon.utils.data

import no.nav.amt.distribusjon.veilarboppfolging.Sak
import java.util.UUID

object Journalforingdata {
    fun lagSak(
        oppfolgingsperiodeId: UUID = UUID.randomUUID(),
        sakId: Long = (1000L..99999L).random(),
        fagsaksystem: String = "ARBEIDSOPPFOLGING",
    ) = Sak(oppfolgingsperiodeId, sakId, fagsaksystem)
}
