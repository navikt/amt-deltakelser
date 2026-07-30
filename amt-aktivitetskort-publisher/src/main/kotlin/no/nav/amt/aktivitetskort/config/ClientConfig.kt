package no.nav.amt.aktivitetskort.config

import no.nav.amt.aktivitetskort.client.AktivitetArenaAclApi
import no.nav.amt.aktivitetskort.client.AmtArenaAclApi
import no.nav.amt.aktivitetskort.client.AmtArrangorApi
import no.nav.amt.aktivitetskort.client.VeilarboppfolgingApi
import org.springframework.boot.restclient.RestClientCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.service.registry.ImportHttpServices

@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = "amt-arena-acl", types = [AmtArenaAclApi::class])
@ImportHttpServices(group = "aktivitet-arena-acl", types = [AktivitetArenaAclApi::class])
@ImportHttpServices(group = "amt-arrangor", types = [AmtArrangorApi::class])
@ImportHttpServices(group = "veilarboppfolging", types = [VeilarboppfolgingApi::class])
class ClientConfig {
    @Bean
    fun defaultHeadersCustomizer() = RestClientCustomizer { builder ->
        builder
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader("Nav-Consumer-Id", "amt-aktivitetskort-publisher")
    }
}
