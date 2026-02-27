package no.nav.amt.distribusjon

import no.nav.amt.lib.utils.database.DatabaseConfig

data class Environment(
    val databaseConfig: DatabaseConfig = DatabaseConfig(),
    val azureAdTokenUrl: String = getEnvVar(AZURE_AD_TOKEN_URL_KEY),
    val azureClientId: String = getEnvVar(AZURE_APP_CLIENT_ID_KEY),
    val azureClientSecret: String = getEnvVar(AZURE_APP_CLIENT_SECRET_KEY),
    val azureJwkKeysUrl: String = getEnvVar(AZURE_OPENID_CONFIG_JWKS_URI_KEY),
    val azureJwtIssuer: String = getEnvVar(AZURE_OPENID_CONFIG_ISSUER_KEY),
    val amtPdfgenUrl: String = getEnvVar(AMT_PDFGEN_URL_KEY),
    val amtPersonUrl: String = getEnvVar(AMT_PERSONSERVICE_URL_KEY),
    val amtPersonScope: String = getEnvVar(AMT_PERSONSERVICE_SCOPE_KEY),
    val amtDeltakerUrl: String = getEnvVar(AMT_DELTAKER_URL_KEY),
    val amtDeltakerScope: String = getEnvVar(AMT_DELTAKER_SCOPE_KEY),
    val veilarboppfolgingUrl: String = getEnvVar(VEILARBOPPFOLGING_URL_KEY),
    val veilarboppfolgingScope: String = getEnvVar(VEILARBOPPFOLGING_SCOPE_KEY),
    val dokarkivUrl: String = getEnvVar(DOKARKIV_URL_KEY),
    val dokarkivScope: String = getEnvVar(DOKARKIV_SCOPE_KEY),
    val dokdistkanalUrl: String = getEnvVar(DOKDISTKANAL_URL_KEY),
    val dokdistkanalScope: String = getEnvVar(DOKDISTKANAL_SCOPE_KEY),
    val dokdistfordelingUrl: String = getEnvVar(DOKDISTFORDELING_URL_KEY),
    val dokdistfordelingScope: String = getEnvVar(DOKDISTFORDELING_SCOPE_KEY),
    val leaderElectorUrl: String = getEnvVar(LEADER_ELECTOR_URL),
) {
    companion object {
        const val KAFKA_CONSUMER_GROUP_ID = "amt-distribusjon-consumer"

        const val AZURE_AD_TOKEN_URL_KEY = "AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"
        const val AZURE_APP_CLIENT_SECRET_KEY = "AZURE_APP_CLIENT_SECRET"
        const val AZURE_APP_CLIENT_ID_KEY = "AZURE_APP_CLIENT_ID"
        const val AZURE_OPENID_CONFIG_JWKS_URI_KEY = "AZURE_OPENID_CONFIG_JWKS_URI"
        const val AZURE_OPENID_CONFIG_ISSUER_KEY = "AZURE_OPENID_CONFIG_ISSUER"

        const val AMT_PDFGEN_URL_KEY = "AMT_PDFGEN"
        const val AMT_PERSONSERVICE_SCOPE_KEY = "AMT_PERSONSERVICE_SCOPE"
        const val AMT_PERSONSERVICE_URL_KEY = "AMT_PERSONSERVICE_URL"
        const val AMT_DELTAKER_URL_KEY = "AMT_DELTAKER_URL"
        const val AMT_DELTAKER_SCOPE_KEY = "AMT_DELTAKER_SCOPE"
        const val VEILARBOPPFOLGING_SCOPE_KEY = "VEILARBOPPFOLGING_SCOPE"
        const val VEILARBOPPFOLGING_URL_KEY = "VEILARBOPPFOLGING_URL"
        const val DOKARKIV_URL_KEY = "DOKARKIV_URL"
        const val DOKARKIV_SCOPE_KEY = "DOKARKIV_SCOPE"
        const val DOKDISTKANAL_URL_KEY = "DOKDISTKANAL_URL"
        const val DOKDISTKANAL_SCOPE_KEY = "DOKDISTKANAL_SCOPE"
        const val DOKDISTFORDELING_URL_KEY = "DOKDISTFORDELING_URL"
        const val DOKDISTFORDELING_SCOPE_KEY = "DOKDISTFORDELING_SCOPE"
        const val SAF_TEMA = "OPP"

        const val LEADER_ELECTOR_URL = "ELECTOR_GET_URL"

        const val HTTP_REQUEST_TIMEOUT_MILLIS = 20_000L
        const val HTTP_CONNECT_TIMEOUT_MILLIS = 10_000L
        const val HTTP_SOCKET_TIMEOUT_MILLIS = 15_000L

        // consumer topics
        const val DELTAKER_HENDELSE_TOPIC = "amt.deltaker-hendelse-v1"
        const val MINSIDE_VARSEL_HENDELSE_TOPIC = "min-side.aapen-varsel-hendelse-v1"
        const val ARRANGOR_MELDING_TOPIC = "amt.arrangor-melding-v1"

        // producer topics
        const val MINSIDE_VARSEL_TOPIC = "min-side.aapen-brukervarsel-v1"
        const val TILTAKSHENDELSE_TOPIC = "obo.tiltakshendelse-v1"

        val cluster: String = getEnvVar("NAIS_CLUSTER_NAME", "lokal")
        val appName: String = getEnvVar("NAIS_APP_NAME", "amt-distribusjon")
        val namespace: String = getEnvVar("NAIS_NAMESPACE", "amt")

        fun isDev(): Boolean = cluster == "dev-gcp"

        fun isProd(): Boolean = cluster == "prod-gcp"

        fun isLocal(): Boolean = !isDev() && !isProd()
    }
}

fun getEnvVar(varName: String, defaultValue: String? = null) = System.getenv(varName)
    ?: System.getProperty(varName)
    ?: defaultValue
    ?: if (Environment.isLocal()) "" else error("Missing required variable $varName")
