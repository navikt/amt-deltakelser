package no.nav.amt.lib.utils

fun getEnvVar(varName: String, defaultValue: String = "") = System.getenv(varName)
    ?: System.getProperty(varName)
    ?: defaultValue
