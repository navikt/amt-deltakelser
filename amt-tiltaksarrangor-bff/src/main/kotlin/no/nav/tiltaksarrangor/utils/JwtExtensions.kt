package no.nav.tiltaksarrangor.utils

import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.server.ResponseStatusException

fun Jwt.personIdent(): String = getClaimAsString("pid")
    ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "PID is missing or is not a string")
