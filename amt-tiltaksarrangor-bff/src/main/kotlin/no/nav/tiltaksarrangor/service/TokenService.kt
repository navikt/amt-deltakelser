package no.nav.tiltaksarrangor.service

import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class TokenService {
    fun getPersonligIdentTilInnloggetAnsatt(): String {
        val authentication = SecurityContextHolder.getContext().authentication
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not authorized, valid token is missing")

        val jwt = (authentication as? JwtAuthenticationToken)?.token
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not authorized, valid token is missing")

        return jwt.getClaimAsString("pid")
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "PID is missing or is not a string")
    }
}
