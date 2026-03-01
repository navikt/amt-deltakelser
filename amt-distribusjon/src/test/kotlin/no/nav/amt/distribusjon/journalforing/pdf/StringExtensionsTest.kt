package no.nav.amt.distribusjon.journalforing.pdf

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class StringExtensionsTest {
    companion object {
        @JvmStatic
        fun testCases(): Stream<Arguments> = Stream.of(
            // Ingen punktum
            Arguments.of("Tekst", "Tekst"),
            Arguments.of(" Tekst ", "Tekst"),
            Arguments.of("Tekst ", "Tekst"),
            // Ett avsluttende punktum
            Arguments.of("Tekst.", "Tekst"),
            Arguments.of("Tekst. ", "Tekst"),
            Arguments.of(" Tekst. ", "Tekst"),
            // Flere avsluttende punktum
            Arguments.of("Tekst..", "Tekst"),
            Arguments.of(" Tekst... ", "Tekst"),
            // Punktum inne i teksten skal ikke berøres
            Arguments.of("En setning. Med mer.", "En setning. Med mer"),
            Arguments.of(" En setning. Med mer. ", "En setning. Med mer"),
            // Bare punktum
            Arguments.of(".", ""),
            Arguments.of("..", ""),
            Arguments.of(" ... ", ""),
            // Tomme strenger
            Arguments.of("", ""),
            Arguments.of("   ", ""),
            // Linjeskift på slutten
            Arguments.of("Tekst.\n", "Tekst"),
            Arguments.of("Tekst.\r", "Tekst"),
            Arguments.of("Tekst.\r\n", "Tekst"),
            Arguments.of(" Tekst.\n ", "Tekst"),
        )
    }

    @ParameterizedTest(name = "{index} => input=''{0}'' => expected=''{1}''")
    @MethodSource("testCases")
    fun `trimOgFjernAvsluttendePunktum fjerner avsluttende punktum og trimmer whitespace`(input: String, expected: String) {
        assertEquals(expected, input.trimOgFjernAvsluttendePunktum())
    }
}
