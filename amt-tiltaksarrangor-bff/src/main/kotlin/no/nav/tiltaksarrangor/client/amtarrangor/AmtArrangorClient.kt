package no.nav.tiltaksarrangor.client.amtarrangor

import no.nav.amt.lib.spring.boot.client.exception.UpstreamServiceException
import no.nav.tiltaksarrangor.client.AMT_ARRANGOR_TOKENX_CLIENT_ID
import no.nav.tiltaksarrangor.client.amtarrangor.dto.OppdaterVeiledereForDeltakerRequest
import no.nav.tiltaksarrangor.client.executeUpstreamCallWithUnauthorizedMapping
import no.nav.tiltaksarrangor.consumer.model.AnsattDto
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AmtArrangorClient(
    private val api: AmtArrangorApi,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun getAnsatt(): AnsattDto? = try {
        executeUpstreamCallWithUnauthorizedMapping(
            serviceName = AMT_ARRANGOR_TOKENX_CLIENT_ID,
            operation = "hente ansatt",
            unauthorizedMessage = "Ikke tilgang til å hente ansatt fra amt-arrangor",
        ) { api.getAnsatt().body }
    } catch (e: UpstreamServiceException) {
        if (e.statusCode == 404) {
            log.info("Ansatt ikke funnet")
            null
        } else {
            throw e
        }
    }

    fun leggTilDeltakerlisteForKoordinator(
        ansattId: UUID,
        deltakerlisteId: UUID,
        arrangorId: UUID,
    ) {
        executeUpstreamCallWithUnauthorizedMapping(
            serviceName = AMT_ARRANGOR_TOKENX_CLIENT_ID,
            operation = "legge til deltakerliste i amt-arrangor",
            unauthorizedMessage = "Ikke tilgang til å legge til deltakerliste i amt-arrangor",
        ) {
            api.leggTilDeltakerlisteForKoordinator(arrangorId, deltakerlisteId)
        }
        log.info("Oppdatert amt-arrangor med deltakerliste $deltakerlisteId for ansatt $ansattId")
    }

    fun fjernDeltakerlisteForKoordinator(
        ansattId: UUID,
        deltakerlisteId: UUID,
        arrangorId: UUID,
    ) {
        executeUpstreamCallWithUnauthorizedMapping(
            serviceName = AMT_ARRANGOR_TOKENX_CLIENT_ID,
            operation = "fjerne deltakerliste i amt-arrangor",
            unauthorizedMessage = "Ikke tilgang til å fjerne deltakerliste i amt-arrangor",
        ) {
            api.fjernDeltakerlisteForKoordinator(arrangorId, deltakerlisteId)
        }
        log.info("Fjernet amt-arrangor deltakerliste $deltakerlisteId for ansatt $ansattId")
    }

    fun oppdaterVeilederForDeltaker(
        deltakerId: UUID,
        oppdaterVeiledereForDeltakerRequest: OppdaterVeiledereForDeltakerRequest,
    ) {
        executeUpstreamCallWithUnauthorizedMapping(
            serviceName = AMT_ARRANGOR_TOKENX_CLIENT_ID,
            operation = "oppdatere veiledere for deltaker i amt-arrangor",
            unauthorizedMessage = "Ikke tilgang til å oppdatere veiledere i amt-arrangor",
        ) {
            api.oppdaterVeilederForDeltaker(deltakerId, oppdaterVeiledereForDeltakerRequest)
        }
        log.info("Oppdatert amt-arrangor med veiledere for $deltakerId")
    }
}
