package no.nav.amt.lib.testing.utils

import no.nav.amt.lib.utils.getEnvVar

internal data class ContainerReuseConfig(
    // Default true — sparer ~10-15 sek på containeroppstart for hver test-runde.
    // Krever at test-task-en setter env-varen TESTCONTAINERS_REUSE_ENABLE=true (gjøres i
    // buildSrc-conventions). Alternativt kan dev sette testcontainers.reuse.enable=true
    // i ~/.testcontainers.properties. System property fungerer IKKE.
    // Kan overstyres lokalt med TESTCONTAINERS_REUSE=false hvis man trenger en helt fersk container.
    val reuse: Boolean = getEnvVar("TESTCONTAINERS_REUSE", "true").toBoolean(),
    val reuseLabel: String = buildReuseLabel(),
) {
    companion object {
        private const val BASE_LABEL = "37b4361b-5adc-4de0-823b-f42cc00d7206"

        /**
         * Bygger en reuse-label som er unik per Gradle-modul. Suffixen settes av convention-pluginene
         * (`environment("TESTCONTAINERS_REUSE_LABEL_SUFFIX", project.path)`) slik at moduler som kjører
         * parallelt under `org.gradle.parallel=true` ikke deler én container med en felles shutdown-hook
         * som rydder topics for andre moduler.
         *
         * Hvis env-varen ikke er satt (f.eks. ad-hoc test-kjøring uten convention-plugin), faller vi
         * tilbake til base-labelen og oppfører oss som før (delt container).
         */
        private fun buildReuseLabel(): String {
            val suffix = getEnvVar("TESTCONTAINERS_REUSE_LABEL_SUFFIX", "")
            return if (suffix.isBlank()) {
                BASE_LABEL
            } else {
                "$BASE_LABEL-${suffix.trim().replace(":", "-").trim('-')}"
            }
        }
    }
}
