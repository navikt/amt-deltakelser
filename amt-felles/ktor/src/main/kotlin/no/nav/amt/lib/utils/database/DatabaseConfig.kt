package no.nav.amt.lib.utils.database

import no.nav.amt.lib.utils.getEnvVar

data class DatabaseConfig(
    val dbUsername: String = getEnvVar(DB_USERNAME_KEY),
    val dbPassword: String = getEnvVar(DB_PASSWORD_KEY),
    val dbDatabase: String = getEnvVar(DB_DATABASE_KEY),
    val dbHost: String = getEnvVar(DB_HOST_KEY),
    val dbPort: String = getEnvVar(DB_PORT_KEY),
    val jdbcURL: String = getEnvVar(JDBC_URL_KEY),
) {
    companion object {
        const val DB_USERNAME_KEY = "DB_USERNAME"
        const val DB_PASSWORD_KEY = "DB_PASSWORD"
        const val DB_DATABASE_KEY = "DB_DATABASE"
        const val DB_HOST_KEY = "DB_HOST"
        const val DB_PORT_KEY = "DB_PORT"
        const val JDBC_URL_KEY = "DB_JDBC_URL"
    }
}
