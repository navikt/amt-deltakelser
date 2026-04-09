package no.nav.amt.internapi.enkeltplass

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class EnkeltplassSanitizeExtensionsTest {
    @Test
    fun sanitizeBeskrivelse() {
        val beskrivelse = "a".repeat(1000).sanitizeBeskrivelse()
        beskrivelse.length shouldBe MAX_LENGTH_BESKRIVELSE
    }

    @Test
    fun sanitizePrisinformasjon() {
        val prisinformasjon = "a".repeat(1000).sanitizePrisinformasjon()
        prisinformasjon.length shouldBe MAX_LENGTH_PRISINFORMASJON
    }

    @Test
    fun sanitizeArrangorUnderenhet() {
        val arrangorUnderenhet = "a".repeat(1000).sanitizeArrangorUnderenhet()
        arrangorUnderenhet.length shouldBe MAX_LENGTH_ARRANGOR_UNDERENHET
    }
}
