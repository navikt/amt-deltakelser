package no.nav.amt.aktivitetskort.client.configuration

import no.nav.amt.aktivitetskort.client.AktivitetArenaAclClient
import no.nav.amt.aktivitetskort.client.AmtArenaAclClient
import no.nav.amt.aktivitetskort.client.AmtArrangorClient
import no.nav.amt.aktivitetskort.client.VeilarboppfolgingClient
import no.nav.common.token_client.builder.AzureAdTokenClientBuilder
import no.nav.common.token_client.client.MachineToMachineTokenClient
import no.nav.security.token.support.spring.api.EnableJwtTokenValidation
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper

@Configuration(proxyBeanMethods = false)
@EnableJwtTokenValidation
class RestClientConfiguration(
    private val objectMapper: ObjectMapper,
) {
    @Bean
    fun machineToMachineTokenClient(
        @Value($$"${nais.env.azureAppClientId}") azureAdClientId: String,
        @Value($$"${nais.env.azureOpenIdConfigTokenEndpoint}") azureTokenEndpoint: String,
        @Value($$"${nais.env.azureAppJWK}") azureAdJWK: String,
    ): MachineToMachineTokenClient = AzureAdTokenClientBuilder
        .builder()
        .withClientId(azureAdClientId)
        .withTokenEndpointUrl(azureTokenEndpoint)
        .withPrivateJwk(azureAdJWK)
        .buildMachineToMachineTokenClient()

    @Bean
    fun amtArenaAclClient(
        machineToMachineTokenClient: MachineToMachineTokenClient,
        @Value($$"${amt.arena-acl.url}") amtArenaAclUrl: String,
        @Value($$"${amt.arena-acl.scope}") amtArenaAclScope: String,
    ) = AmtArenaAclClient(
        baseUrl = amtArenaAclUrl,
        tokenProvider = { machineToMachineTokenClient.createMachineToMachineToken(amtArenaAclScope) },
        objectMapper = objectMapper,
    )

    @Bean
    fun amtArrangorClient(
        machineToMachineTokenClient: MachineToMachineTokenClient,
        @Value($$"${amt.arrangor.url}") amtArrangorUrl: String,
        @Value($$"${amt.arrangor.scope}") amtArrangorScope: String,
    ) = AmtArrangorClient(
        baseUrl = amtArrangorUrl,
        tokenProvider = { machineToMachineTokenClient.createMachineToMachineToken(amtArrangorScope) },
        objectMapper = objectMapper,
    )

    @Bean
    fun aktivitetArenaAclClient(
        machineToMachineTokenClient: MachineToMachineTokenClient,
        @Value($$"${aktivitet.arena-acl.url}") aktivitetArenaAclUrl: String,
        @Value($$"${aktivitet.arena-acl.scope}") aktivitetArenaAclScope: String,
    ) = AktivitetArenaAclClient(
        baseUrl = aktivitetArenaAclUrl,
        tokenProvider = { machineToMachineTokenClient.createMachineToMachineToken(aktivitetArenaAclScope) },
        objectMapper = objectMapper,
    )

    @Bean
    fun veilarboppfolgingClient(
        machineToMachineTokenClient: MachineToMachineTokenClient,
        @Value($$"${veilarboppfolging.url}") veilarboppfolgingUrl: String,
        @Value($$"${veilarboppfolging.scope}") veilarboppfolgingScope: String,
    ) = VeilarboppfolgingClient(
        baseUrl = "$veilarboppfolgingUrl/veilarboppfolging",
        veilarboppfolgingTokenProvider = { machineToMachineTokenClient.createMachineToMachineToken(veilarboppfolgingScope) },
        objectMapper = objectMapper,
    )
}
