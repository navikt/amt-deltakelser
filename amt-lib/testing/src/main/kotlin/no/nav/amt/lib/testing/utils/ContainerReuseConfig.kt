package no.nav.amt.lib.testing.utils

import no.nav.amt.lib.utils.getEnvVar

internal data class ContainerReuseConfig(
    val reuse: Boolean = getEnvVar("TESTCONTAINERS_REUSE", "false").toBoolean(),
    val reuseLabel: String = "37b4361b-5adc-4de0-823b-f42cc00d7206",
)
