package no.nav.amt.aktivitetskort.client

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.web.client.RestClient
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer
import org.springframework.web.service.registry.HttpServiceGroup

@TestConfiguration
class OAuth2ClientTestConfig {
    private val mocks = mutableMapOf<String, MockRestServiceServer>()

    @Bean
    fun mockServerConfigurer(): RestClientHttpServiceGroupConfigurer = RestClientHttpServiceGroupConfigurer { groups ->
        groups.forEachClient { group: HttpServiceGroup, builder: RestClient.Builder ->
            mocks[group.name()] = MockRestServiceServer.bindTo(builder).build()
        }
    }

    fun getMock(group: String): MockRestServiceServer = mocks[group] ?: error("No mock for group '$group'")
}
