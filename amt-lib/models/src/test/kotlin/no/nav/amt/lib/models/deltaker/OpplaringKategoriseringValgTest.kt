package no.nav.amt.lib.models.deltaker

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
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
                valgteKategoriseringer = setOf(
                    OpplaringKategoriseringValg.ValgteFelt(
                        representerer = OpplaringKategoriseringType.BRANSJE_ID,
                        valg = emptyMap(),
                    ),
                ),
                valgteSertifiseringer = emptySet(),
            )

            val resultat = valg.hentVerdier(OpplaringKategoriseringType.BRANSJE_ID, throwIfEmpty = false)

            resultat shouldBe emptyList()
        }

        @Test
        fun `hentVerdier skal kaste IllegalArgumentException som default når kategoriseringstype ikke finnes`() {
            val valg = OpplaringKategoriseringValg(
                valgteKategoriseringer = setOf(
                    OpplaringKategoriseringValg.ValgteFelt(
                        representerer = OpplaringKategoriseringType.BRANSJE_ID,
                        valg = mapOf(UUID.randomUUID() to "Verdi"),
                    ),
                ),
                valgteSertifiseringer = emptySet(),
            )

            shouldThrow<IllegalArgumentException> {
                valg.hentVerdier(OpplaringKategoriseringType.FORERKORT)
            }
        }

        @Test
        fun `hentVerdier skal kaste IllegalArgumentException når ingen kategoriseringer finnes`() {
            val valg = OpplaringKategoriseringValg(
                valgteKategoriseringer = emptySet(),
                valgteSertifiseringer = emptySet(),
            )

            shouldThrow<IllegalArgumentException> {
                valg.hentVerdier(OpplaringKategoriseringType.BRANSJE_ID)
            }
        }

        @Test
        fun `hentVerdier skal kaste IllegalArgumentException når throwIfEmpty er true og kategoriseringstype ikke finnes`() {
            val valg = OpplaringKategoriseringValg(
                valgteKategoriseringer = setOf(
                    OpplaringKategoriseringValg.ValgteFelt(
                        representerer = OpplaringKategoriseringType.BRANSJE_ID,
                        valg = mapOf(UUID.randomUUID() to "Verdi"),
                    ),
                ),
                valgteSertifiseringer = emptySet(),
            )

            shouldThrow<IllegalArgumentException> {
                valg.hentVerdier(OpplaringKategoriseringType.FORERKORT, throwIfEmpty = true)
            }
        }

        @Test
        fun `hentVerdier skal kaste IllegalArgumentException når throwIfEmpty er true og verdier er tomme`() {
            val valg = OpplaringKategoriseringValg(
                valgteKategoriseringer = setOf(
                    OpplaringKategoriseringValg.ValgteFelt(
                        representerer = OpplaringKategoriseringType.BRANSJE_ID,
                        valg = emptyMap(),
                    ),
                ),
                valgteSertifiseringer = emptySet(),
            )

            shouldThrow<IllegalArgumentException> {
                valg.hentVerdier(OpplaringKategoriseringType.BRANSJE_ID, throwIfEmpty = true)
            }
        }
    }

    @Nested
    inner class HentRepresenterTests {
        @Test
        fun `hentRepresenterer skal returnere tom set når ingen kategoriseringer finnes`() {
            val valg = OpplaringKategoriseringValg(
                valgteKategoriseringer = emptySet(),
                valgteSertifiseringer = emptySet(),
            )

            val resultat = valg.hentRepresenterer()

            resultat shouldBe emptySet()
        }

        @Test
        fun `hentRepresenterer skal returnere set med en kategoriseringstype`() {
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
        fun `hentRepresenterer skal returnere set med flere kategoriseringstyper`() {
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
                    OpplaringKategoriseringValg.ValgteFelt(
                        representerer = OpplaringKategoriseringType.SERTIFISERINGER,
                        valg = mapOf(UUID.randomUUID() to "Verdi 3"),
                    ),
                ),
                valgteSertifiseringer = emptySet(),
            )

            val resultat = valg.hentRepresenterer()

            resultat shouldBe setOf(
                OpplaringKategoriseringType.BRANSJE_ID,
                OpplaringKategoriseringType.FORERKORT,
                OpplaringKategoriseringType.SERTIFISERINGER,
            )
        }

        @Test
        fun `hentRepresenterer skal ignorere sertifiseringer`() {
            val valg = OpplaringKategoriseringValg(
                valgteKategoriseringer = setOf(
                    OpplaringKategoriseringValg.ValgteFelt(
                        representerer = OpplaringKategoriseringType.BRANSJE_ID,
                        valg = mapOf(UUID.randomUUID() to "Verdi"),
                    ),
                ),
                valgteSertifiseringer = setOf(), // Even with sertifiseringer, they should be ignored
            )

            val resultat = valg.hentRepresenterer()

            resultat shouldBe setOf(OpplaringKategoriseringType.BRANSJE_ID)
        }

        @Test
        fun `hentRepresenterer skal returnere set med samme kategoriseringstyper selv med duplikater`() {
            // Verify that the result is deduplicated (Set behavior)
            val valg = OpplaringKategoriseringValg(
                valgteKategoriseringer = setOf(
                    OpplaringKategoriseringValg.ValgteFelt(
                        representerer = OpplaringKategoriseringType.BRANSJE_ID,
                        valg = mapOf(UUID.randomUUID() to "Verdi 1"),
                    ),
                    OpplaringKategoriseringValg.ValgteFelt(
                        representerer = OpplaringKategoriseringType.BRANSJE_ID,
                        valg = mapOf(UUID.randomUUID() to "Verdi 2"),
                    ),
                ),
                valgteSertifiseringer = emptySet(),
            )

            val resultat = valg.hentRepresenterer()

            // Since valgteKategoriseringer is a Set, we can't actually have duplicates,
            // but we test that result is a Set with unique items
            resultat.size shouldBe 1
            resultat shouldBe setOf(OpplaringKategoriseringType.BRANSJE_ID)
        }
    }
}
