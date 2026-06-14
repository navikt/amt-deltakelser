package no.nav.tiltaksarrangor.client.amtperson

import no.nav.amt.lib.models.deltaker.Kontaktinformasjon
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import no.nav.tiltaksarrangor.client.ClientUtils.buildRestClient
import no.nav.tiltaksarrangor.client.ClientUtils.handleClientError
import no.nav.tiltaksarrangor.consumer.model.NavEnhet
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.requiredBody
import java.util.UUID

@Service
class AmtPersonClient(
    @Value($$"${amt-person.url}") baseUrl: String,
    clientConfigurationProperties: ClientConfigurationProperties,
    oAuth2AccessTokenService: OAuth2AccessTokenService,
    builder: RestClient.Builder,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val client = buildRestClient(
        baseUrl = baseUrl,
        builder = builder,
        oAuth2AccessTokenService = oAuth2AccessTokenService,
        clientProperties = clientConfigurationProperties.registration["amt-person-aad"]
            ?: error("Fant ikke 'amt-person-aad' i OAuth2-config"),
    )

    fun hentEnhet(id: UUID): NavEnhet = client
        .get()
        .uri { it.path("/api/nav-enhet/{id}").build(id) }
        .retrieve()
        .onStatus(
            HttpStatusCode::isError,
            handleClientError(
                log = log,
                unauthorizedMessage = "Ikke tilgang til å hente NAV-enhet fra amt-person-service",
                defaultErrorMessage = "Kunne ikke hente NAV-enhet fra amt-person-service",
            ),
        ).requiredBody<NavEnhetDto>()
        .toNavEnhet()

    fun hentNavAnsatt(id: UUID): NavAnsattResponse = client
        .get()
        .uri { it.path("/api/nav-ansatt/{id}").build(id) }
        .retrieve()
        .onStatus(
            HttpStatusCode::isError,
            handleClientError(
                log = log,
                unauthorizedMessage = "Ikke tilgang til å hente NAV-ansatt fra amt-person-service",
                defaultErrorMessage = "Kunne ikke hente NAV-ansatt fra amt-person-service",
            ),
        ).requiredBody<NavAnsattResponse>()

    fun hentOppdatertKontaktinfo(personident: String): Result<Kontaktinformasjon> =
        hentOppdatertKontaktinfo(setOf(personident)).mapCatching {
            it[personident] ?: throw NoSuchElementException("Klarte ikke hente kontaktinformasjon for person med ident")
        }

    fun hentOppdatertKontaktinfo(personidenter: Set<String>): Result<Map<String, Kontaktinformasjon>> = runCatching {
        client
            .post()
            .uri { it.path("/api/nav-bruker/kontaktinformasjon").build() }
            .contentType(MediaType.APPLICATION_JSON)
            .body(personidenter)
            .retrieve()
            .onStatus(
                HttpStatusCode::isError,
                handleClientError(
                    log = log,
                    unauthorizedMessage = KONTAKTINFO_ERROR_MSG,
                    defaultErrorMessage = KONTAKTINFO_ERROR_MSG,
                ),
            ).requiredBody<Map<String, Kontaktinformasjon>>()
    }

    companion object {
        private const val KONTAKTINFO_ERROR_MSG = "Kunne ikke hente kontaktinformasjon fra amt-person-service."
    }
}
