package no.nav.tiltaksarrangor.config

import no.nav.security.token.support.client.core.ClientProperties
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import no.nav.security.token.support.client.spring.oauth2.EnableOAuth2Client
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import java.util.concurrent.TimeUnit

@EnableOAuth2Client(cacheEnabled = true)
@Configuration(proxyBeanMethods = false)
class HttpClientConfig {
	@Bean
	fun amtArrangorHttpClient(
		clientConfigurationProperties: ClientConfigurationProperties,
		oAuth2AccessTokenService: OAuth2AccessTokenService,
	): OkHttpClient {
		val registrationName = "amt-arrangor-tokenx"
		return buildClient(registrationName, clientConfigurationProperties, oAuth2AccessTokenService)
	}

	@Bean
	fun amtArrangorAADHttpClient(
		clientConfigurationProperties: ClientConfigurationProperties,
		oAuth2AccessTokenService: OAuth2AccessTokenService,
	): OkHttpClient {
		val registrationName = "amt-arrangor-aad"
		return buildClient(registrationName, clientConfigurationProperties, oAuth2AccessTokenService)
	}

	@Bean
	fun amtPersonAADHttpClient(
		clientConfigurationProperties: ClientConfigurationProperties,
		oAuth2AccessTokenService: OAuth2AccessTokenService,
	): OkHttpClient {
		val registrationName = "amt-person-aad"
		return buildClient(registrationName, clientConfigurationProperties, oAuth2AccessTokenService)
	}

	@Bean
	fun amtDeltakerHttpClient(
		clientConfigurationProperties: ClientConfigurationProperties,
		oAuth2AccessTokenService: OAuth2AccessTokenService,
	): OkHttpClient {
		val registrationName = "amt-deltaker-aad"
		return buildClient(registrationName, clientConfigurationProperties, oAuth2AccessTokenService)
	}

	@Bean
	fun simpleHttpClient(): OkHttpClient = OkHttpClient
		.Builder()
		.connectTimeout(5, TimeUnit.SECONDS)
		.readTimeout(5, TimeUnit.SECONDS)
		.followRedirects(false)
		.build()

	private fun buildClient(
		registrationName: String,
		clientConfigurationProperties: ClientConfigurationProperties,
		oAuth2AccessTokenService: OAuth2AccessTokenService,
	): OkHttpClient {
		val clientProperties =
			clientConfigurationProperties.registration[registrationName]
				?: error("Fant ikke config for $registrationName")
		return OkHttpClient
			.Builder()
			.connectTimeout(5, TimeUnit.SECONDS)
			.readTimeout(5, TimeUnit.SECONDS)
			.followRedirects(false)
			.addInterceptor(bearerTokenInterceptor(clientProperties, oAuth2AccessTokenService))
			.build()
	}

	private fun bearerTokenInterceptor(clientProperties: ClientProperties, oAuth2AccessTokenService: OAuth2AccessTokenService): Interceptor =
		Interceptor { chain: Interceptor.Chain ->
			val accessTokenResponse = oAuth2AccessTokenService.getAccessToken(clientProperties)
			val request = chain.request()
			val requestWithToken =
				request
					.newBuilder()
					.addHeader(HttpHeaders.AUTHORIZATION, "Bearer ${accessTokenResponse.access_token}")
					.build()
			chain.proceed(requestWithToken)
		}
}
