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
    fun sanitizeArrangorUnderenhet() {
        val arrangorUnderenhet = "a".repeat(1000).sanitizeArrangorUnderenhet()
        arrangorUnderenhet.length shouldBe MAX_LENGTH_ARRANGOR_UNDERENHET
    }
}
