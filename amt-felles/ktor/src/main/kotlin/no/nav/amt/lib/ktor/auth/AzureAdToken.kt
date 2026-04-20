package no.nav.amt.lib.ktor.auth

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class AzureAdToken(
    val tokenType: String,
    val accessToken: String,
)
