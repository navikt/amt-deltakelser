package no.nav.tiltaksarrangor.client.amtarrangor

import no.nav.tiltaksarrangor.client.AMT_ARRANGOR_TOKENX_CLIENT_ID
import no.nav.tiltaksarrangor.client.ClientUtils.handleClientError
import no.nav.tiltaksarrangor.client.NAV_CONSUMER_ID_HEADER
import no.nav.tiltaksarrangor.client.NAV_CONSUMER_ID_HEADER_VALUE
import no.nav.tiltaksarrangor.client.amtarrangor.dto.OppdaterVeiledereForDeltakerRequest
import no.nav.tiltaksarrangor.consumer.model.AnsattDto
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import java.util.UUID

@Service
class AmtArrangorClient(
    @Value($$"${amt-arrangor.default.url}") baseUrl: String,
    builder: RestClient.Builder,
    @Qualifier("tokenXAuthorizedClientManager") manager: OAuth2AuthorizedClientManager,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val client = builder
        .baseUrl(baseUrl)
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .defaultHeader(NAV_CONSUMER_ID_HEADER, NAV_CONSUMER_ID_HEADER_VALUE)
        .requestInterceptor(
            OAuth2ClientHttpRequestInterceptor(manager).apply {
                setClientRegistrationIdResolver { AMT_ARRANGOR_TOKENX_CLIENT_ID }
            },
        ).build()

    fun getAnsatt(): AnsattDto? = try {
        client
            .get()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/api/ansatt")
                    .build()
            }.retrieve()
            .onStatus(
                HttpStatusCode::isError,
                handleClientError(
                    log = log,
                    unauthorizedMessage = "Ikke tilgang til å hente ansatt fra amt-arrangør",
                    defaultErrorMessage = "Kunne ikke hente ansatt fra amt-arrangør.",
                ),
            ).body<AnsattDto>()
    } catch (_: NoSuchElementException) {
        log.info("Ansatt ikke funnet")
        null
    }

    fun leggTilDeltakerlisteForKoordinator(
        ansattId: UUID,
        deltakerlisteId: UUID,
        arrangorId: UUID,
    ) {
        client
            .post()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/api/ansatt/koordinator/{arrangorId}/{deltakerlisteId}")
                    .build(arrangorId, deltakerlisteId)
            }.retrieve()
            .onStatus(
                HttpStatusCode::isError,
                handleClientError(
                    log = log,
                    unauthorizedMessage = "Ikke tilgang til å legge til deltakerliste i amt-arrangør",
                    defaultErrorMessage = "Kunne ikke legge til deltakerliste $deltakerlisteId i amt-arrangør.",
                ),
            ).toBodilessEntity()

        log.info("Oppdatert amt-arrangor med deltakerliste $deltakerlisteId for ansatt $ansattId")
    }

    fun fjernDeltakerlisteForKoordinator(
        ansattId: UUID,
        deltakerlisteId: UUID,
        arrangorId: UUID,
    ) {
        client
            .delete()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/api/ansatt/koordinator/{arrangorId}/{deltakerlisteId}")
                    .build(arrangorId, deltakerlisteId)
            }.retrieve()
            .onStatus(
                HttpStatusCode::isError,
                handleClientError(
                    log = log,
                    unauthorizedMessage = "Ikke tilgang til å fjerne deltakerliste i amt-arrangør",
                    defaultErrorMessage = "Kunne ikke fjerne deltakerliste $deltakerlisteId i amt-arrangør.",
                ),
            ).toBodilessEntity()

        log.info("Fjernet amt-arrangor deltakerliste $deltakerlisteId for ansatt $ansattId")
    }

    fun oppdaterVeilederForDeltaker(
        deltakerId: UUID,
        oppdaterVeiledereForDeltakerRequest: OppdaterVeiledereForDeltakerRequest,
    ) {
        client
            .post()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/api/ansatt/veiledere/{deltakerId}")
                    .build(deltakerId)
            }.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .body(oppdaterVeiledereForDeltakerRequest)
            .retrieve()
            .onStatus(
                HttpStatusCode::isError,
                handleClientError(
                    log = log,
                    unauthorizedMessage = "Ikke tilgang til å oppdatere veiledere i amt-arrangør",
                    defaultErrorMessage = "Kunne ikke oppdatere veiledere for deltaker $deltakerId i amt-arrangør.",
                ),
            ).toBodilessEntity()

        log.info("Oppdatert amt-arrangor med veiledere for $deltakerId")
    }
}
