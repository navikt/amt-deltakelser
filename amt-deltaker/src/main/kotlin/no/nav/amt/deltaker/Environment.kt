package no.nav.amt.deltaker

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.amt.lib.ktor.auth.PreAuthorizedApp
import no.nav.amt.lib.utils.database.DatabaseConfig
import no.nav.amt.lib.utils.objectMapper

data class Environment(
    val databaseConfig: DatabaseConfig = DatabaseConfig(),
    val azureAdTokenUrl: String = getEnvVar(AZURE_AD_TOKEN_URL_KEY),
    val azureClientId: String = getEnvVar(AZURE_APP_CLIENT_ID_KEY),
    val azureClientSecret: String = getEnvVar(AZURE_APP_CLIENT_SECRET_KEY),
    val jwkKeysUrl: String = getEnvVar(AZURE_OPENID_CONFIG_JWKS_URI_KEY),
    val jwtIssuer: String = getEnvVar(AZURE_OPENID_CONFIG_ISSUER_KEY),
    val amtPersonServiceUrl: String = getEnvVar(AMT_PERSONSERVICE_URL_KEY),
    val amtPersonServiceScope: String = getEnvVar(AMT_PERSONSERVICE_SCOPE_KEY),
    val amtArrangorUrl: String = getEnvVar(AMT_ARRANGOR_URL_KEY),
    val amtArrangorScope: String = getEnvVar(AMT_ARRANGOR_SCOPE_KEY),
    val preAuthorizedApps: List<PreAuthorizedApp> = getEnvVar(
        AZURE_APP_PRE_AUTHORIZED_APPS,
        objectMapper.writeValueAsString(
            emptyList<PreAuthorizedApp>(),
        ),
    ).let { objectMapper.readValue(it) },
    val electorPath: String = getEnvVar(ELECTOR_PATH),
    val poaoTilgangUrl: String = getEnvVar(POAO_TILGANG_URL_KEY),
    val poaoTilgangScope: String = getEnvVar(POAO_TILGANG_SCOPE_KEY),
    val appName: String = "amt-deltaker",
    val unleashUrl: String = getEnvVar(UNLEASH_SERVER_API_URL),
    val unleashApiToken: String = getEnvVar(UNLEASH_SERVER_API_TOKEN),
    val isOppfolgingstilfelleUrl: String = getEnvVar(ISOPPFOLGINGSTILFELLE_URL_KEY),
    val isOppfolgingstilfelleScope: String = getEnvVar(ISOPPFOLGINGSTILFELLE_SCOPE_KEY),
    val amtTiltakUrl: String = getEnvVar(AMT_TILTAK_URL_KEY),
    val amtTiltakScope: String = getEnvVar(AMT_TILTAK_SCOPE_KEY),
    val mulighetsrommetApiUrl: String = getEnvVar(MULIGHETSROMMET_API_URL_KEY),
    val mulighetsrommetApiScope: String = getEnvVar(MULIGHETSROMMET_API_SCOPE_KEY),
) {
    companion object {
        const val KAFKA_CONSUMER_GROUP_ID = "amt-deltaker-consumer"
        const val DELTAKERLISTE_V2_TOPIC = "team-mulighetsrommet.siste-tiltaksgjennomforinger-v2"
        const val AMT_ARRANGOR_TOPIC = "amt.arrangor-v1"
        const val AMT_NAV_ANSATT_TOPIC = "amt.nav-ansatt-personalia-v1"
        const val AMT_NAV_BRUKER_TOPIC = "amt.nav-bruker-personalia-v1"
        const val AMT_NAV_ENHET_TOPIC = "amt.nav-enhet-v1"
        const val TILTAKSTYPE_TOPIC = "team-mulighetsrommet.siste-tiltakstyper-v3"
        const val DELTAKER_V2_TOPIC = "amt.deltaker-v2"
        const val DELTAKER_V1_TOPIC = "amt.deltaker-v1"
        const val DELTAKER_EKSTERN_V1_TOPIC = "amt.deltaker-ekstern-v1"
        const val ENKELTPLASS_DELTAKER_TOPIC = "amt.arena-enkeltplass-deltaker-v1"
        const val DELTAKER_HENDELSE_TOPIC = "amt.deltaker-hendelse-v1"
        const val ARRANGOR_MELDING_TOPIC = "amt.arrangor-melding-v1"

        const val AMT_PERSONSERVICE_URL_KEY = "AMT_PERSONSERVICE_URL"
        const val AMT_PERSONSERVICE_SCOPE_KEY = "AMT_PERSONSERVICE_SCOPE"
        const val AMT_ARRANGOR_URL_KEY = "AMT_ARRANGOR_URL"
        const val AMT_ARRANGOR_SCOPE_KEY = "AMT_ARRANGOR_SCOPE"
        const val AMT_TILTAK_URL_KEY = "AMT_TILTAK_URL"
        const val AMT_TILTAK_SCOPE_KEY = "AMT_TILTAK_SCOPE"

        const val AZURE_AD_TOKEN_URL_KEY = "AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"
        const val AZURE_APP_CLIENT_SECRET_KEY = "AZURE_APP_CLIENT_SECRET"
        const val AZURE_APP_CLIENT_ID_KEY = "AZURE_APP_CLIENT_ID"
        const val AZURE_OPENID_CONFIG_JWKS_URI_KEY = "AZURE_OPENID_CONFIG_JWKS_URI"
        const val AZURE_OPENID_CONFIG_ISSUER_KEY = "AZURE_OPENID_CONFIG_ISSUER"
        const val AZURE_APP_PRE_AUTHORIZED_APPS = "AZURE_APP_PRE_AUTHORIZED_APPS"
        const val UNLEASH_SERVER_API_URL = "UNLEASH_SERVER_API_URL"
        const val UNLEASH_SERVER_API_TOKEN = "UNLEASH_SERVER_API_TOKEN"

        const val POAO_TILGANG_URL_KEY = "POAO_TILGANG_URL"
        const val POAO_TILGANG_SCOPE_KEY = "POAO_TILGANG_SCOPE"
        const val ISOPPFOLGINGSTILFELLE_URL_KEY = "ISOPPFOLGINGSTILFELLE_URL"
        const val ISOPPFOLGINGSTILFELLE_SCOPE_KEY = "ISOPPFOLGINGSTILFELLE_SCOPE"
        const val MULIGHETSROMMET_API_URL_KEY = "MULIGHETSROMMET_API_URL"
        const val MULIGHETSROMMET_API_SCOPE_KEY = "MULIGHETSROMMET_API_SCOPE"

        const val ELECTOR_PATH = "ELECTOR_PATH"

        const val HTTP_REQUEST_TIMEOUT_MILLIS = 10_000L
        const val HTTP_CONNECT_TIMEOUT_MILLIS = 5_000L
        const val HTTP_SOCKET_TIMEOUT_MILLIS = 15_000L

        fun isDev(): Boolean {
            val cluster = System.getenv("NAIS_CLUSTER_NAME") ?: "Ikke dev"
            return cluster == "dev-gcp"
        }

        fun isProd(): Boolean {
            val cluster = System.getenv("NAIS_CLUSTER_NAME") ?: "Ikke prod"
            return cluster == "prod-gcp"
        }

        fun isLocal(): Boolean = !isDev() && !isProd()
    }
}

fun getEnvVar(varName: String, defaultValue: String? = null) = System.getenv(varName)
    ?: System.getProperty(varName)
    ?: defaultValue
    ?: if (Environment.isLocal()) "" else error("Missing required variable $varName")
