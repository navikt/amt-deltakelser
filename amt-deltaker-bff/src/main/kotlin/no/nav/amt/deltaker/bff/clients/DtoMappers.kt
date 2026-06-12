package no.nav.amt.deltaker.bff.clients

import no.nav.amt.deltaker.bff.model.Utkast
import no.nav.amt.internapi.paamelding.request.UtkastRequest

object DtoMappers {
    // benyttes i PaameldingClient
    fun utkastRequestFromUtkast(utkast: Utkast): UtkastRequest = with(utkast.pamelding) {
        UtkastRequest(
            deltakelsesinnhold = deltakelsesinnhold,
            bakgrunnsinformasjon = bakgrunnsinformasjon,
            deltakelsesprosent = deltakelsesprosent,
            dagerPerUke = dagerPerUke,
            endretAv = endretAv,
            endretAvEnhet = endretAvEnhet,
            godkjentAvNav = utkast.godkjentAvNav,
        )
    }
}
