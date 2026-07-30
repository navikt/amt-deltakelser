package no.nav.tiltaksarrangor.client.amtarrangor

import no.nav.tiltaksarrangor.client.amtarrangor.dto.OppdaterVeiledereForDeltakerRequest
import no.nav.tiltaksarrangor.consumer.model.AnsattDto
import no.nav.tiltaksarrangor.model.exceptions.UnauthorizedException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientResponseException
import java.util.UUID

@Service
class AmtArrangorClient(
    private val api: AmtArrangorApi,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun getAnsatt(): AnsattDto? = try {
        api.getAnsatt().body
    } catch (e: RestClientResponseException) {
        when (e.statusCode) {
            HttpStatus.NOT_FOUND -> {
                log.info("Ansatt ikke funnet")
                null
            }

            HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN ->
                throw UnauthorizedException("Ikke tilgang til å hente ansatt fra amt-arrangør")

            else -> throw RuntimeException("Kunne ikke hente ansatt fra amt-arrangør. Status=${e.statusCode.value()}", e)
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
        } catch (e: RestClientResponseException) {
            when (e.statusCode) {
                HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN ->
                    throw UnauthorizedException("Ikke tilgang til å legge til deltakerliste i amt-arrangør")

                else -> throw RuntimeException(
                    "Kunne ikke legge til deltakerliste $deltakerlisteId i amt-arrangør. Status=${e.statusCode.value()}",
                    e,
                )
            }
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
        } catch (e: RestClientResponseException) {
            when (e.statusCode) {
                HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN ->
                    throw UnauthorizedException("Ikke tilgang til å fjerne deltakerliste i amt-arrangør")

                else -> throw RuntimeException(
                    "Kunne ikke fjerne deltakerliste $deltakerlisteId i amt-arrangør. Status=${e.statusCode.value()}",
                    e,
                )
            }
        }
    }

    fun oppdaterVeilederForDeltaker(
        deltakerId: UUID,
        oppdaterVeiledereForDeltakerRequest: OppdaterVeiledereForDeltakerRequest,
    ) {
        try {
            api.oppdaterVeilederForDeltaker(deltakerId, oppdaterVeiledereForDeltakerRequest)
            log.info("Oppdatert amt-arrangor med veiledere for $deltakerId")
        } catch (e: RestClientResponseException) {
            when (e.statusCode) {
                HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN ->
                    throw UnauthorizedException("Ikke tilgang til å oppdatere veiledere i amt-arrangør")

                else -> throw RuntimeException(
                    "Kunne ikke oppdatere veiledere for deltaker $deltakerId i amt-arrangør. Status=${e.statusCode.value()}",
                    e,
                )
            }
        }
    }
}
