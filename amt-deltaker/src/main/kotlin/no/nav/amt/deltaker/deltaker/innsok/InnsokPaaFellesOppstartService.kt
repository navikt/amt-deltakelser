package no.nav.amt.deltaker.deltaker.innsok

import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.InnsokPaaFellesOppstart
import java.time.LocalDateTime
import java.util.UUID

class InnsokPaaFellesOppstartService(
    private val repository: InnsokPaaFellesOppstartRepository,
) {
    fun nyttInnsokUtkastGodkjentAvNav(deltaker: Deltaker, forrigeStatus: DeltakerStatus) = innsok(deltaker, forrigeStatus, true)

    fun nyttInnsokUtkastGodkjentAvDeltaker(deltaker: Deltaker, forrigeStatus: DeltakerStatus) = innsok(deltaker, forrigeStatus, false)

    private fun innsok(
        deltaker: Deltaker,
        forrigeStatus: DeltakerStatus,
        godkjentAvNav: Boolean,
    ): InnsokPaaFellesOppstart {
        if (deltaker.vedtaksinformasjon == null) throw IllegalStateException("Kan ikke søke inn deltaker som ikke har et vedtak")

        val innsok = InnsokPaaFellesOppstart(
            id = UUID.randomUUID(),
            deltakerId = deltaker.id,
            innsokt = LocalDateTime.now(),
            innsoktAv = deltaker.vedtaksinformasjon.sistEndretAv,
            innsoktAvEnhet = deltaker.vedtaksinformasjon.sistEndretAvEnhet,
            deltakelsesinnholdVedInnsok = deltaker.deltakelsesinnhold,
            utkastDelt = if (forrigeStatus.type == DeltakerStatus.Type.UTKAST_TIL_PAMELDING) forrigeStatus.opprettet else null,
            utkastGodkjentAvNav = godkjentAvNav,
        )
        repository.insert(innsok)
        return innsok
    }
}
