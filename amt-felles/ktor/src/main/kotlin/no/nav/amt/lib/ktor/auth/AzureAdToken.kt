package no.nav.amt.lib.ktor.auth

import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class AzureAdToken(
    val tokenType: String,
    val accessToken: String,
)
