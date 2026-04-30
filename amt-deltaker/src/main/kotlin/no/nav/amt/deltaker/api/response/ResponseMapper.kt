package no.nav.amt.deltaker.api.response

import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.internapi.deltaker.response.DeltakerEndringResponse
import no.nav.amt.internapi.paamelding.response.OpprettKladdResponse
import no.nav.amt.internapi.paamelding.response.UtkastResponse
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk

object ResponseMapper {
    fun utkastResponseFromDeltaker(
        deltaker: Deltaker,
        historikk: List<DeltakerHistorikk>,
    ) = with(deltaker) {
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
            erManueltDeltMedArrangor = erManueltDeltMedArrangor,
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

    fun deltakerEndringResponseFromDeltaker(
        deltaker: Deltaker,
        historikk: List<DeltakerHistorikk>,
    ) = with(deltaker) {
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
