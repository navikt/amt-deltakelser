package no.nav.amt.deltaker.bff.apiclients

import no.nav.amt.deltaker.bff.deltaker.model.Deltaker
import no.nav.amt.deltaker.bff.deltaker.model.Deltakeroppdatering
import no.nav.amt.deltaker.bff.deltaker.model.Utkast
import no.nav.amt.kladd.request.UtkastRequest
import no.nav.amt.kladd.response.OpprettKladdResponse
import no.nav.amt.kladd.response.UtkastResponse
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.response.DeltakerEndringResponse
import no.nav.amt.lib.models.deltaker.internalapis.tiltakskoordinator.response.DeltakerOppdateringFeilkode
import no.nav.amt.lib.models.deltaker.internalapis.tiltakskoordinator.response.DeltakerOppdateringResponse

object DtoMappers {
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

    // benyttes i PaameldingService og TiltakskoordinatorService
    fun UtkastResponse.toDeltakerOppdatering() = Deltakeroppdatering(
        id = id,
        startdato = startdato,
        sluttdato = sluttdato,
        dagerPerUke = dagerPerUke,
        deltakelsesprosent = deltakelsesprosent,
        bakgrunnsinformasjon = bakgrunnsinformasjon,
        deltakelsesinnhold = deltakelsesinnhold,
        status = status,
        historikk = historikk,
        erManueltDeltMedArrangor = false,
    )

    // benyttes i DeltakerService
    fun DeltakerEndringResponse.toDeltakeroppdatering() = Deltakeroppdatering(
        id = id,
        startdato = startdato,
        sluttdato = sluttdato,
        dagerPerUke = dagerPerUke,
        deltakelsesprosent = deltakelsesprosent,
        bakgrunnsinformasjon = bakgrunnsinformasjon,
        deltakelsesinnhold = deltakelsesinnhold,
        status = status,
        historikk = historikk,
        erManueltDeltMedArrangor = false,
    )

    // benyttes i TiltakskoordinatorService
    fun DeltakerOppdateringResponse.toDeltakerOppdatering() = Deltakeroppdatering(
        id = id,
        startdato = startdato,
        sluttdato = sluttdato,
        dagerPerUke = dagerPerUke,
        deltakelsesprosent = deltakelsesprosent,
        bakgrunnsinformasjon = bakgrunnsinformasjon,
        deltakelsesinnhold = deltakelsesinnhold,
        status = status,
        historikk = historikk,
        sistEndret = sistEndret,
        erManueltDeltMedArrangor = erManueltDeltMedArrangor,
    )

    // benyttes kun i tester (tiltakskoordinator)
    internal fun deltakerOppdateringResponseFromDeltaker(
        deltaker: Deltaker,
        feilkode: DeltakerOppdateringFeilkode? = null,
    ) = with(deltaker) {
        DeltakerOppdateringResponse(
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
            sistEndret = sistEndret,
            feilkode = feilkode,
        )
    }
}
