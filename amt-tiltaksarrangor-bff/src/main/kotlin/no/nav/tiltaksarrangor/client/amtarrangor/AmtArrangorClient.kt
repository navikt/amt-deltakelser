package no.nav.tiltaksarrangor.client.amtarrangor

import no.nav.tiltaksarrangor.client.AMT_ARRANGOR_TOKENX_CLIENT_ID
import no.nav.tiltaksarrangor.client.amtarrangor.dto.OppdaterVeiledereForDeltakerRequest
import no.nav.tiltaksarrangor.client.toExternalServiceException
import no.nav.tiltaksarrangor.consumer.model.AnsattDto
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import java.util.UUID

@Service
class AmtArrangorClient(
    private val api: AmtArrangorApi,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun getAnsatt(): AnsattDto? = try {
        api.getAnsatt().body
    } catch (e: RestClientException) {
        if (e is RestClientResponseException && e.statusCode == HttpStatus.NOT_FOUND) {
            log.info("Ansatt ikke funnet")
            null
        } else {
            throw e.toExternalServiceException(
                serviceName = AMT_ARRANGOR_TOKENX_CLIENT_ID,
                action = "hente ansatt",
                unauthorizedMessage = "Ikke tilgang til å hente ansatt fra amt-arrangor",
            )
        }
    }

    fun leggTilDeltakerlisteForKoordinator(
        ansattId: UUID,
        deltakerlisteId: UUID,
        arrangorId: UUID,
    ) {
        try {
            api.leggTilDeltakerlisteForKoordinator(arrangorId, deltakerlisteId)
            log.info("Oppdatert amt-arrangor med deltakerliste $deltakerlisteId for ansatt $ansattId")
        } catch (e: RestClientException) {
            throw e.toExternalServiceException(
                serviceName = AMT_ARRANGOR_TOKENX_CLIENT_ID,
                action = "legge til deltakerliste $deltakerlisteId i amt-arrangor",
                unauthorizedMessage = "Ikke tilgang til å legge til deltakerliste i amt-arrangor",
            )
        }
    }

    fun fjernDeltakerlisteForKoordinator(
        ansattId: UUID,
        deltakerlisteId: UUID,
        arrangorId: UUID,
    ) {
        try {
            api.fjernDeltakerlisteForKoordinator(arrangorId, deltakerlisteId)
            log.info("Fjernet amt-arrangor deltakerliste $deltakerlisteId for ansatt $ansattId")
        } catch (e: RestClientException) {
            throw e.toExternalServiceException(
                serviceName = AMT_ARRANGOR_TOKENX_CLIENT_ID,
                action = "fjerne deltakerliste $deltakerlisteId i amt-arrangor",
                unauthorizedMessage = "Ikke tilgang til å fjerne deltakerliste i amt-arrangor",
            )
        }
    }

    fun oppdaterVeilederForDeltaker(
        deltakerId: UUID,
        oppdaterVeiledereForDeltakerRequest: OppdaterVeiledereForDeltakerRequest,
    ) {
        try {
            api.oppdaterVeilederForDeltaker(deltakerId, oppdaterVeiledereForDeltakerRequest)
            log.info("Oppdatert amt-arrangor med veiledere for $deltakerId")
        } catch (e: RestClientException) {
            throw e.toExternalServiceException(
                serviceName = AMT_ARRANGOR_TOKENX_CLIENT_ID,
                action = "oppdatere veiledere for deltaker $deltakerId i amt-arrangor",
                unauthorizedMessage = "Ikke tilgang til å oppdatere veiledere i amt-arrangor",
            )
        }
    }
}
