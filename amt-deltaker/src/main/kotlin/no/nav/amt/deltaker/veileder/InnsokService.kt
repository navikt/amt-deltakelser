package no.nav.amt.deltaker.veileder

import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Innsok
import java.time.LocalDateTime
import java.util.UUID

/*
    Innsøk representerer et øyeblikksbilde av deltakeren på tidspunktet for innsøk, og brukes for sporbarhet og populering av et eget element i historikk på deltakeren
    Innsøk brukes for deltakelser som skal søkes inn og må godkjennes av noen andre enn nav veileder
    Innsøk må godkjennes av:
     - tiltaksansvarlig(i egen fane i mulighetsrommet), eller
     - Beslutter (på enkeltplasser i mulighetsrommet)
 */
class InnsokService(
    private val repository: InnsokRepository,
) {
    fun nyttInnsokUtkastGodkjentAvNav(
        deltaker: Deltaker,
        forrigeStatus: DeltakerStatus,
    ) = innsok(deltaker, forrigeStatus, true)

    fun nyttInnsokUtkastGodkjentAvDeltaker(
        deltaker: Deltaker,
        forrigeStatus: DeltakerStatus,
    ) = innsok(deltaker, forrigeStatus, false)

    private fun innsok(
        deltaker: Deltaker,
        forrigeStatus: DeltakerStatus,
        godkjentAvNav: Boolean,
    ): Innsok {
        if (deltaker.vedtaksinformasjon == null) throw IllegalStateException("Kan ikke søke inn deltaker som ikke har et vedtak")

        val innsok = Innsok(
            id = UUID.randomUUID(),
            deltakerId = deltaker.id,
            innsokt = LocalDateTime.now(),
            innsoktAv = deltaker.vedtaksinformasjon.sistEndretAv,
            innsoktAvEnhet = deltaker.vedtaksinformasjon.sistEndretAvEnhet,
            startdato = deltaker.startdato,
            sluttdato = deltaker.sluttdato,
            deltakelsesinnholdVedInnsok = deltaker.deltakelsesinnhold,
            utkastDelt = if (forrigeStatus.type == DeltakerStatus.Type.UTKAST_TIL_PAMELDING) forrigeStatus.opprettet else null,
            utkastGodkjentAvNav = godkjentAvNav,
            opplaringKategoriseringVedInnsok = deltaker.deltakerliste.opplaringKategorisering,
        )
        repository.insert(innsok)
        return innsok
    }
}
