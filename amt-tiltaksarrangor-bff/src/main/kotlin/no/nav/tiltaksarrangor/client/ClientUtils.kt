package no.nav.tiltaksarrangor.client

import no.nav.security.token.support.client.core.ClientProperties
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.tiltaksarrangor.model.exceptions.UnauthorizedException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpRequest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.ClientHttpResponse
import org.springframework.web.client.RestClient

object ClientUtils {
    fun buildRestClient(
        baseUrl: String,
        builder: RestClient.Builder,
        clientProperties: ClientProperties,
        oAuth2AccessTokenService: OAuth2AccessTokenService,
    ): RestClient = builder
        .baseUrl(baseUrl)
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .defaultRequest {
            it.header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${oAuth2AccessTokenService.getAccessToken(clientProperties).access_token}",
            )
        }.build()

    fun handleClientError(
        log: org.slf4j.Logger,
        unauthorizedMessage: String,
        defaultErrorMessage: String,
        notFoundMessage: String? = null,
    ): (HttpRequest, ClientHttpResponse) -> Unit = { _, response ->
        when (response.statusCode) {
            HttpStatus.NOT_FOUND -> {
                val message = notFoundMessage ?: "Ressurs ikke funnet"
                log.info(message)
                throw NoSuchElementException(message)
            }

            HttpStatus.UNAUTHORIZED,
            HttpStatus.FORBIDDEN,
            -> throw UnauthorizedException(unauthorizedMessage)

            else -> {
                log.error("$defaultErrorMessage Responsekode: ${response.statusCode.value()}")
                throw RuntimeException(defaultErrorMessage)
            }
        }
    }
}
