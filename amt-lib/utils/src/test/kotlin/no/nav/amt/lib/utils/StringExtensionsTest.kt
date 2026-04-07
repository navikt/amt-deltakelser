package no.nav.amt.lib.utils

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class StringExtensionsTest {
    @ParameterizedTest(name = "{index} => input=''{0}'' => expected=''{1}''")
    @MethodSource("trimOgFjernAvsluttendePunktumTestCases")
    fun `trimOgFjernAvsluttendePunktum fjerner avsluttende punktum og trimmer whitespace`(
        input: String,
        expected: String,
    ) {
        assertEquals(expected, input.trimOgFjernAvsluttendePunktum())
    }

    @Nested
    inner class ToTitleCaseTests {
        @Test
        fun `toTitleCase - et ord, bare store bokstaver - skal ha stor forbokstav og resten lower case`() {
            val storeBokstaver = "UPPERCASE"

            storeBokstaver.toTitleCase() shouldBe "Uppercase"
        }

        @Test
        fun `toTitleCase - et ord, bare sma bokstaver - skal ha stor forbokstav og resten lower case`() {
            val storeBokstaver = "lowercase"

            storeBokstaver.toTitleCase() shouldBe "Lowercase"
        }

        @Test
        fun `toTitleCase - to ord og AS, bare store bokstaver - skal ha stor forbokstaver og AS i store bokstaver`() {
            val storeBokstaver = "ARRANGØR AS"

            storeBokstaver.toTitleCase() shouldBe "Arrangør AS"
        }

        @Test
        fun `toTitleCase - flere ord med og, bare store bokstaver - skal formatteres riktig`() {
            val storeBokstaver = "ARRANGØR OG SØNN AS"

            storeBokstaver.toTitleCase() shouldBe "Arrangør og Sønn AS"
        }

        @Test
        fun `toTitleCase - flere ord med i, bare store bokstaver - skal formatteres riktig`() {
            val storeBokstaver = "ARRANGØR I BERGEN"

            storeBokstaver.toTitleCase() shouldBe "Arrangør i Bergen"
        }

        @Test
        fun `toTitleCase - med slash, bare store bokstaver - skal formatteres riktig`() {
            val storeBokstaver = "ARRANGØR A/S"

            storeBokstaver.toTitleCase() shouldBe "Arrangør A/S"
        }

        @Test
        fun `toTitleCase - med fnutt, bare store bokstaver - skal formatteres riktig`() {
            val storeBokstaver = "O'ARRANGØR"

            storeBokstaver.toTitleCase() shouldBe "O'Arrangør"
        }
    }

    companion object {
        @JvmStatic
        fun trimOgFjernAvsluttendePunktumTestCases(): Stream<Arguments> = Stream.of(
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
}
