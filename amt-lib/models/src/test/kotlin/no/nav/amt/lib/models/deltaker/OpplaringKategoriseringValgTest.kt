package no.nav.amt.lib.models.deltaker

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.amt.lib.models.deltakerliste.SertifiseringValg
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

class OpplaringKategoriseringValgTest {
    @Nested
    inner class HentVerdierTests {
        @Test
        fun `hentVerdier skal returnere verdier for gitt kategoriseringstype`() {
            val valg = OpplaringKategoriseringValg(
                valgteKategoriseringer = setOf(
                    OpplaringKategoriseringValg.ValgteFelt(
                        representerer = OpplaringKategoriseringType.BRANSJE_ID,
                        valg = mapOf(
                            UUID.randomUUID() to "Verdi 1",
                            UUID.randomUUID() to "Verdi 2",
                        ),
                    ),
                ),
                valgteSertifiseringer = emptySet(),
            )

            val resultat = valg.hentVerdier(OpplaringKategoriseringType.BRANSJE_ID)

            resultat shouldBe listOf("Verdi 1", "Verdi 2")
        }

        @Test
        fun `hentVerdier skal returnere sertifiseringer`() {
            val valg = OpplaringKategoriseringValg(
                valgteKategoriseringer = emptySet(),
                valgteSertifiseringer = setOf(
                    SertifiseringValg(1, "~sertifisering~"),
                ),
            )

            val resultat = valg.hentVerdier(OpplaringKategoriseringType.SERTIFISERINGER)

            resultat shouldBe listOf("~sertifisering~")
        }

        @Test
        fun `hentVerdier skal returnere verdier selv når flere kategoriseringer finnes`() {
            val valg = OpplaringKategoriseringValg(
                valgteKategoriseringer = setOf(
                    OpplaringKategoriseringValg.ValgteFelt(
                        representerer = OpplaringKategoriseringType.BRANSJE_ID,
                        valg = mapOf(UUID.randomUUID() to "Bransje A"),
                    ),
                    OpplaringKategoriseringValg.ValgteFelt(
                        representerer = OpplaringKategoriseringType.FORERKORT,
                        valg = mapOf(UUID.randomUUID() to "B", UUID.randomUUID() to "C"),
                    ),
                ),
                valgteSertifiseringer = emptySet(),
            )

            val resultat = valg.hentVerdier(OpplaringKategoriseringType.FORERKORT)

            resultat shouldBe listOf("B", "C")
        }

        @Test
        fun `hentVerdier skal returnere tom liste når throwIfEmpty er false og kategoriseringstype ikke finnes`() {
            val valg = OpplaringKategoriseringValg(
                valgteKategoriseringer = setOf(
                    OpplaringKategoriseringValg.ValgteFelt(
                        representerer = OpplaringKategoriseringType.BRANSJE_ID,
                        valg = mapOf(UUID.randomUUID() to "Verdi"),
                    ),
                ),
                valgteSertifiseringer = emptySet(),
            )

            val resultat = valg.hentVerdier(OpplaringKategoriseringType.FORERKORT, throwIfEmpty = false)

            resultat shouldBe emptyList()
        }

        @Test
        fun `hentVerdier skal returnere tom liste når throwIfEmpty er false og kategoriseringstype har tomme verdier`() {
            val valg = OpplaringKategoriseringValg(
                valgteKategoriseringer = emptySet(),
                valgteSertifiseringer = emptySet(),
            )

            val resultat = valg.hentVerdier(OpplaringKategoriseringType.BRANSJE_ID, throwIfEmpty = false)

            resultat shouldBe emptyList()
        }

        @Test
        fun `hentVerdier skal kaste IllegalArgumentException når kategorisering ikke finnes`() {
            val valg = OpplaringKategoriseringValg(
                valgteKategoriseringer = emptySet(),
                valgteSertifiseringer = emptySet(),
            )

            shouldThrow<IllegalArgumentException> {
                valg.hentVerdier(OpplaringKategoriseringType.BRANSJE_ID)
            }
        }
    }

    @Nested
    inner class HentRepresenterTests {
        @Test
        fun `hentRepresenterer skal returnere tomt sett når ingen kategoriseringer finnes`() {
            val valg = OpplaringKategoriseringValg(
                valgteKategoriseringer = emptySet(),
                valgteSertifiseringer = emptySet(),
            )

            val resultat = valg.hentRepresenterer()

            resultat shouldBe emptySet()
        }

        @Test
        fun `hentRepresenterer skal returnere sett med en kategoriseringstype`() {
            val valg = OpplaringKategoriseringValg(
                valgteKategoriseringer = setOf(
                    OpplaringKategoriseringValg.ValgteFelt(
                        representerer = OpplaringKategoriseringType.BRANSJE_ID,
                        valg = mapOf(UUID.randomUUID() to "Verdi"),
                    ),
                ),
                valgteSertifiseringer = emptySet(),
            )

            val resultat = valg.hentRepresenterer()

            resultat shouldBe setOf(OpplaringKategoriseringType.BRANSJE_ID)
        }

        @Test
        fun `hentRepresenterer skal returnere sett med flere kategoriseringstyper`() {
            val valg = OpplaringKategoriseringValg(
                valgteKategoriseringer = setOf(
                    OpplaringKategoriseringValg.ValgteFelt(
                        representerer = OpplaringKategoriseringType.BRANSJE_ID,
                        valg = mapOf(UUID.randomUUID() to "Verdi 1"),
                    ),
                    OpplaringKategoriseringValg.ValgteFelt(
                        representerer = OpplaringKategoriseringType.FORERKORT,
                        valg = mapOf(UUID.randomUUID() to "Verdi 2"),
                    ),
                ),
                valgteSertifiseringer = setOf(SertifiseringValg(1, "~sertifisering~")),
            )

            val resultat = valg.hentRepresenterer()

            resultat shouldBe setOf(
                OpplaringKategoriseringType.BRANSJE_ID,
                OpplaringKategoriseringType.FORERKORT,
            )
        }
    }
}
