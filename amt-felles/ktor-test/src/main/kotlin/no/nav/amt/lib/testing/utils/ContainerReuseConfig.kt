package no.nav.amt.lib.testing.utils

import no.nav.amt.lib.utils.getEnvVar

internal data class ContainerReuseConfig(
    // Default true — sparer ~10-15 sek på containeroppstart for hver test-runde.
    // Krever at testtask-en setter -Dtestcontainers.reuse.enable=true (gjøres i buildSrc).
    // Kan overstyres lokalt med TESTCONTAINERS_REUSE=false hvis man trenger en helt fersk container.
    val reuse: Boolean = getEnvVar("TESTCONTAINERS_REUSE", "true").toBoolean(),
    val reuseLabel: String = "37b4361b-5adc-4de0-823b-f42cc00d7206",
)
