package no.nav.amt.aktivitetskort.config

import no.nav.amt.aktivitetskort.client.AktivitetArenaAclApi
import no.nav.amt.aktivitetskort.client.AmtArenaAclApi
import no.nav.amt.aktivitetskort.client.AmtArrangorApi
import no.nav.amt.aktivitetskort.client.NAV_CONSUMER_ID_HEADER
import no.nav.amt.aktivitetskort.client.NAV_CONSUMER_ID_HEADER_VALUE
import no.nav.amt.aktivitetskort.client.VeilarboppfolgingApi
import no.nav.amt.person.service.clients.AKTIVITET_ARENA_ACL_CLIENT_ID
import no.nav.amt.person.service.clients.AMT_ARENA_ACL_CLIENT_ID
import no.nav.amt.person.service.clients.AMT_ARRANGOR_CLIENT_ID
import no.nav.amt.person.service.clients.VEILARBOPPFOLGING_CLIENT_ID
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer
import org.springframework.web.service.registry.ImportHttpServices

@Configuration(proxyBeanMethods = false)
// Det er én HTTP-klient per gruppe, så vi bruker klient-ID som gruppenavn
@ImportHttpServices(group = AMT_ARENA_ACL_CLIENT_ID, types = [AmtArenaAclApi::class])
@ImportHttpServices(group = AKTIVITET_ARENA_ACL_CLIENT_ID, types = [AktivitetArenaAclApi::class])
@ImportHttpServices(group = AMT_ARRANGOR_CLIENT_ID, types = [AmtArrangorApi::class])
@ImportHttpServices(group = VEILARBOPPFOLGING_CLIENT_ID, types = [VeilarboppfolgingApi::class])
class ClientConfig {
    @Bean
    fun httpServiceGroupConfigurer() = RestClientHttpServiceGroupConfigurer { groups ->
        groups.forEachClient { _, builder ->
            builder
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(NAV_CONSUMER_ID_HEADER, NAV_CONSUMER_ID_HEADER_VALUE)
        }
    }
}
