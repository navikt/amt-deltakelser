package no.nav.tiltaksarrangor.service

import no.nav.security.token.support.core.context.TokenValidationContextHolder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException

@Component
class TokenService(
	private val contextHolder: TokenValidationContextHolder,
) {
	fun getPersonligIdentTilInnloggetAnsatt(): String {
		val context = contextHolder.getTokenValidationContext()

		val token =
			context.firstValidToken
				?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not authorized, valid token is missing")

		return token.jwtTokenClaims.getStringClaim("pid") ?: throw ResponseStatusException(
			HttpStatus.UNAUTHORIZED,
			"PID is missing or is not a string",
		)
	}
}
