package no.nav.tiltaksarrangor.config

import no.nav.tiltaksarrangor.client.AMT_ARRANGOR_AAD_CLIENT_ID
import no.nav.tiltaksarrangor.client.AMT_ARRANGOR_TOKENX_CLIENT_ID
import no.nav.tiltaksarrangor.client.AMT_PERSON_AAD_CLIENT_ID
import no.nav.tiltaksarrangor.client.NAV_CONSUMER_ID_HEADER
import no.nav.tiltaksarrangor.client.NAV_CONSUMER_ID_HEADER_VALUE
import no.nav.tiltaksarrangor.client.amtarrangor.AmtArrangorApi
import no.nav.tiltaksarrangor.client.amtarrangor.HentArrangorApi
import no.nav.tiltaksarrangor.client.amtperson.AmtPersonApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer
import org.springframework.web.service.registry.ImportHttpServices

@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = AMT_ARRANGOR_TOKENX_CLIENT_ID, types = [AmtArrangorApi::class])
@ImportHttpServices(group = AMT_ARRANGOR_AAD_CLIENT_ID, types = [HentArrangorApi::class])
@ImportHttpServices(group = AMT_PERSON_AAD_CLIENT_ID, types = [AmtPersonApi::class])
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
