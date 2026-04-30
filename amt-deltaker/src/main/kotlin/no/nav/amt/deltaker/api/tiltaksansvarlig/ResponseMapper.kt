package no.nav.amt.deltaker.api.tiltaksansvarlig

import io.ktor.network.sockets.SocketTimeoutException
import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.internapi.tiltakskoordinator.response.DeltakerOppdateringFeilkode
import no.nav.amt.internapi.tiltakskoordinator.response.DeltakerOppdateringResponse
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import java.net.SocketException
import java.sql.SQLException

object ResponseMapper {
    fun Deltaker.toDeltakerOppdatering(historikk: List<DeltakerHistorikk>): DeltakeroppdateringResponse = DeltakeroppdateringResponse(
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
        forcedUpdate = false,
    )

    fun fromDeltakerOppdateringResult(
        oppdateringResult: DeltakerOppdateringResult,
        historikk: List<DeltakerHistorikk>,
    ): DeltakerOppdateringResponse = with(oppdateringResult.deltaker) {
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
            sistEndret = sistEndret,
            erManueltDeltMedArrangor = erManueltDeltMedArrangor,
            feilkode = oppdateringResult.exception?.toOppdateringFeilkode(),
        )
    }

    private fun Throwable.toOppdateringFeilkode() = when (this) {
        is IllegalStateException -> DeltakerOppdateringFeilkode.UGYLDIG_STATE
        is IllegalArgumentException -> DeltakerOppdateringFeilkode.UGYLDIG_STATE
        is SQLException -> DeltakerOppdateringFeilkode.UGYLDIG_STATE
        is SocketTimeoutException -> DeltakerOppdateringFeilkode.MIDLERTIDIG_FEIL
        is SocketException -> DeltakerOppdateringFeilkode.MIDLERTIDIG_FEIL
        is Exception -> null
        else -> null
    }
}
