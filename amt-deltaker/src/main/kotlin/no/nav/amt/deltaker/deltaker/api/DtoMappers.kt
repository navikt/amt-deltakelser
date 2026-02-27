package no.nav.amt.deltaker.deltaker.api

import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.response.DeltakerEndringResponse
import no.nav.amt.lib.models.deltaker.internalapis.paamelding.response.OpprettKladdResponse
import no.nav.amt.lib.models.deltaker.internalapis.paamelding.response.UtkastResponse

object DtoMappers {
    fun utkastResponseFromDeltaker(deltaker: Deltaker, historikk: List<DeltakerHistorikk>) = with(deltaker) {
        UtkastResponse(
            id = id,
            startdato = startdato,
            sluttdato = sluttdato,
            dagerPerUke = dagerPerUke,
            deltakelsesprosent = deltakelsesprosent,
            bakgrunnsinformasjon = bakgrunnsinformasjon,
            deltakelsesinnhold = deltakelsesinnhold,
            status = status,
            historikk = historikk,
        )
    }

    fun opprettKladdResponseFromDeltaker(deltaker: Deltaker) = with(deltaker) {
        OpprettKladdResponse(
            id = id,
            navBruker = navBruker,
            deltakerlisteId = deltakerliste.id,
            startdato = startdato,
            sluttdato = sluttdato,
            dagerPerUke = dagerPerUke,
            deltakelsesprosent = deltakelsesprosent,
            bakgrunnsinformasjon = bakgrunnsinformasjon,
            deltakelsesinnhold = deltakelsesinnhold!!,
            status = status,
        )
    }

    fun deltakerEndringResponseFromDeltaker(deltaker: Deltaker, historikk: List<DeltakerHistorikk>) = with(deltaker) {
        DeltakerEndringResponse(
            id = id,
            startdato = startdato,
            sluttdato = sluttdato,
            dagerPerUke = dagerPerUke,
            deltakelsesprosent = deltakelsesprosent,
            bakgrunnsinformasjon = bakgrunnsinformasjon,
            deltakelsesinnhold = deltakelsesinnhold,
            status = status,
            historikk = historikk,
        )
    }
}
