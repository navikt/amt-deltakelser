package no.nav.tiltaksarrangor.client.amtarrangor

import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import no.nav.tiltaksarrangor.client.ClientUtils.buildRestClient
import no.nav.tiltaksarrangor.client.ClientUtils.handleClientError
import no.nav.tiltaksarrangor.client.amtarrangor.dto.ArrangorMedOverordnetArrangor
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Service
class HentArrangorClient(
    @Value($$"${amt-arrangor.hentarrangor.url}") baseUrl: String,
    builder: RestClient.Builder,
    clientConfigurationProperties: ClientConfigurationProperties,
    oAuth2AccessTokenService: OAuth2AccessTokenService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val client = buildRestClient(
        baseUrl = baseUrl,
        builder = builder,
        oAuth2AccessTokenService = oAuth2AccessTokenService,
        clientProperties = clientConfigurationProperties.registration["amt-arrangor-aad"]
            ?: error("Fant ikke 'amt-arrangor-aad' i OAuth2-config"),
    )

    fun getArrangor(orgnummer: String): ArrangorMedOverordnetArrangor? = client
        .get()
        .uri { uriBuilder ->
            uriBuilder
                .path("/{orgnummer}")
                .build(orgnummer)
        }.retrieve()
        .onStatus(
            HttpStatusCode::isError,
            handleClientError(
                log,
                "Uautorisert tilgang ved henting av arrangør med orgnummer $orgnummer fra amt-arrangør.",
                "Feil ved henting av arrangør med orgnummer $orgnummer fra amt-arrangør.",
                "Arrangør med orgnummer $orgnummer finnes ikke hos amt-arrangør.",
            ),
        ).body<ArrangorMedOverordnetArrangor>()
}
