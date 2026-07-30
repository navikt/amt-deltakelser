package no.nav.tiltaksarrangor.config

import no.nav.tiltaksarrangor.client.amtarrangor.AmtArrangorApi
import no.nav.tiltaksarrangor.client.amtarrangor.HentArrangorApi
import no.nav.tiltaksarrangor.client.amtperson.AmtPersonApi
import org.springframework.boot.restclient.RestClientCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.service.registry.ImportHttpServices

@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = "amt-arrangor-tokenx", types = [AmtArrangorApi::class])
@ImportHttpServices(group = "amt-arrangor-aad", types = [HentArrangorApi::class])
@ImportHttpServices(group = "amt-person-aad", types = [AmtPersonApi::class])
class ClientConfig {
    @Bean
    fun defaultHeadersCustomizer() = RestClientCustomizer { builder ->
        builder
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader("Nav-Consumer-Id", "amt-tiltaksarrangor-bff")
    }
}
