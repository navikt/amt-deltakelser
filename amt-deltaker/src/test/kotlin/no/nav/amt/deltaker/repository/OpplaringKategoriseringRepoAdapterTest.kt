@file:Suppress("ktlint:standard:no-wildcard-imports")

package no.nav.amt.deltaker.repository

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.enkeltplass.GjennomforingUpserter.Companion.toMulighetsrommetKategorisering
import no.nav.amt.deltaker.enkeltplass.kafka.GjennomforingRequestPayload
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringType
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringValg
import no.nav.amt.lib.models.deltakerliste.SertifiseringValg
import no.nav.amt.lib.testing.DatabaseTestExtension
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.UUID

class OpplaringKategoriseringRepoAdapterTest {
    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Nested
    inner class HentOpplaringKategoriseringValgForMulighetsrommetTests {
        @Test
        fun `skal returnere tom kategorisering naar ingenting er lagret`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            // Act
            val result = OpplaringKategoriseringRepoAdapter
                .hentOpplaringKategoriseringValg(deltakerliste.id)
                .toMulighetsrommetKategorisering()

            // Assert
            result shouldBe GjennomforingRequestPayload.UpsertEnkeltplass.OpplaringKategorisering(
                verdier = emptyMap(),
                sertifiseringer = emptySet(),
            )
        }

        @Test
        fun `skal returnere verdier gruppert per representerer`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            val bransjeId1 = UUID.randomUUID()
            val bransjeId2 = UUID.randomUUID()
            val forerkortId = UUID.randomUUID()

            val valgteVerdier = setOf(
                OpplaringKategoriseringValg.ValgteFelt(
                    representerer = OpplaringKategoriseringType.BRANSJE_ID,
                    valg = mapOf(bransjeId1 to "Bygg og anlegg", bransjeId2 to "Helse og omsorg"),
                ),
                OpplaringKategoriseringValg.ValgteFelt(
                    representerer = OpplaringKategoriseringType.FORERKORT,
                    valg = mapOf(forerkortId to "B"),
                ),
            )

            OpplaringKategoriseringRepoAdapter.lagreOpplaringKategoriseringValg(
                gjennomforingId = deltakerliste.id,
                valgteVerdier = valgteVerdier,
                valgteSertifiseringer = emptySet(),
            )

            // Act
            val result = OpplaringKategoriseringRepoAdapter
                .hentOpplaringKategoriseringValg(deltakerliste.id)
                .toMulighetsrommetKategorisering()
            // Assert
            result.verdier[OpplaringKategoriseringType.BRANSJE_ID] shouldBe setOf(bransjeId1, bransjeId2)
            result.verdier[OpplaringKategoriseringType.FORERKORT] shouldBe setOf(forerkortId)
            result.sertifiseringer.shouldBeEmpty()
        }

        @Test
        fun `skal returnere sertifiseringer`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            val sertifiseringer = setOf(
                SertifiseringValg(id = 1, navn = "Truckfører T1"),
                SertifiseringValg(id = 2, navn = "Truckfører T2"),
            )

            OpplaringKategoriseringRepoAdapter.lagreOpplaringKategoriseringValg(
                gjennomforingId = deltakerliste.id,
                valgteVerdier = emptySet(),
                valgteSertifiseringer = sertifiseringer,
            )

            // Act
            val result = OpplaringKategoriseringRepoAdapter
                .hentOpplaringKategoriseringValg(deltakerliste.id)
                .toMulighetsrommetKategorisering()
            // Assert
            result.verdier shouldBe emptyMap()
            result.sertifiseringer shouldBe sertifiseringer
        }
    }

    @Nested
    inner class HentOpplaringKategoriseringValgForAmtTests {
        @Test
        fun `skal returnere tomt resultat naar ingenting er lagret`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            // Act
            val result = OpplaringKategoriseringRepoAdapter.hentOpplaringKategoriseringValg(deltakerliste.id)

            // Assert
            result shouldBe OpplaringKategoriseringValg(
                valgteKategoriseringer = emptySet(),
                valgteSertifiseringer = emptySet(),
            )
        }

        @Test
        fun `skal returnere ValgteFelt med representerer og valg`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            val bransjeId = UUID.randomUUID()
            val forerkortId = UUID.randomUUID()

            val valgteVerdier = setOf(
                OpplaringKategoriseringValg.ValgteFelt(
                    representerer = OpplaringKategoriseringType.BRANSJE_ID,
                    valg = mapOf(bransjeId to "Bygg og anlegg"),
                ),
                OpplaringKategoriseringValg.ValgteFelt(
                    representerer = OpplaringKategoriseringType.FORERKORT,
                    valg = mapOf(forerkortId to "B"),
                ),
            )

            OpplaringKategoriseringRepoAdapter.lagreOpplaringKategoriseringValg(
                gjennomforingId = deltakerliste.id,
                valgteVerdier = valgteVerdier,
                valgteSertifiseringer = emptySet(),
            )

            // Act
            val result = OpplaringKategoriseringRepoAdapter.hentOpplaringKategoriseringValg(deltakerliste.id)

            // Assert
            result.valgteKategoriseringer shouldBe valgteVerdier
            result.valgteSertifiseringer.shouldBeEmpty()
        }

        @Test
        fun `skal returnere sertifiseringer`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            val sertifiseringer = setOf(
                SertifiseringValg(id = 1, navn = "Truckfører T1"),
            )

            OpplaringKategoriseringRepoAdapter.lagreOpplaringKategoriseringValg(
                gjennomforingId = deltakerliste.id,
                valgteVerdier = emptySet(),
                valgteSertifiseringer = sertifiseringer,
            )

            // Act
            val result = OpplaringKategoriseringRepoAdapter.hentOpplaringKategoriseringValg(deltakerliste.id)

            // Assert
            result.valgteKategoriseringer.shouldBeEmpty()
            result.valgteSertifiseringer shouldBe sertifiseringer
        }
    }

    @Nested
    inner class LagreOpplaringKategoriseringValgTests {
        @Test
        fun `lagrer kategoriseringsvalg`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            val bransjeId = UUID.randomUUID()
            val valgteVerdier = setOf(
                OpplaringKategoriseringValg.ValgteFelt(
                    representerer = OpplaringKategoriseringType.BRANSJE_ID,
                    valg = mapOf(bransjeId to "Bygg og anlegg"),
                ),
            )

            // Act
            OpplaringKategoriseringRepoAdapter.lagreOpplaringKategoriseringValg(
                gjennomforingId = deltakerliste.id,
                valgteVerdier = valgteVerdier,
                valgteSertifiseringer = emptySet(),
            )

            // Assert
            val result = OpplaringKategoriseringRepoAdapter.hentOpplaringKategoriseringValg(deltakerliste.id)
            result.valgteKategoriseringer shouldBe valgteVerdier
        }

        @Test
        fun `lagrer sertifiseringer`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            val sertifiseringer = setOf(
                SertifiseringValg(id = 1, navn = "Truckfører T1"),
                SertifiseringValg(id = 2, navn = "Truckfører T2"),
            )

            // Act
            OpplaringKategoriseringRepoAdapter.lagreOpplaringKategoriseringValg(
                gjennomforingId = deltakerliste.id,
                valgteVerdier = emptySet(),
                valgteSertifiseringer = sertifiseringer,
            )

            // Assert
            val result = OpplaringKategoriseringRepoAdapter.hentOpplaringKategoriseringValg(deltakerliste.id)
            result.valgteSertifiseringer shouldBe sertifiseringer
        }

        @Test
        fun `overskriver eksisterende kategoriseringsvalg`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            val gammelBransjeId = UUID.randomUUID()
            val gamleValg = setOf(
                OpplaringKategoriseringValg.ValgteFelt(
                    representerer = OpplaringKategoriseringType.BRANSJE_ID,
                    valg = mapOf(gammelBransjeId to "Bygg og anlegg"),
                ),
            )

            OpplaringKategoriseringRepoAdapter.lagreOpplaringKategoriseringValg(
                gjennomforingId = deltakerliste.id,
                valgteVerdier = gamleValg,
                valgteSertifiseringer = emptySet(),
            )

            val nyForerkortId = UUID.randomUUID()
            val nyeValg = setOf(
                OpplaringKategoriseringValg.ValgteFelt(
                    representerer = OpplaringKategoriseringType.FORERKORT,
                    valg = mapOf(nyForerkortId to "B"),
                ),
            )

            // Act
            OpplaringKategoriseringRepoAdapter.lagreOpplaringKategoriseringValg(
                gjennomforingId = deltakerliste.id,
                valgteVerdier = nyeValg,
                valgteSertifiseringer = null,
            )

            // Assert
            val result = OpplaringKategoriseringRepoAdapter.hentOpplaringKategoriseringValg(deltakerliste.id)
            result.valgteKategoriseringer shouldBe nyeValg
        }

        @Test
        fun `sletter eksisterende kategoriseringsvalg naar tom liste sendes`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            val bransjeId = UUID.randomUUID()
            val opprinneligeValg = setOf(
                OpplaringKategoriseringValg.ValgteFelt(
                    representerer = OpplaringKategoriseringType.BRANSJE_ID,
                    valg = mapOf(bransjeId to "Bygg og anlegg"),
                ),
            )

            OpplaringKategoriseringRepoAdapter.lagreOpplaringKategoriseringValg(
                gjennomforingId = deltakerliste.id,
                valgteVerdier = opprinneligeValg,
                valgteSertifiseringer = emptySet(),
            )

            // Act
            OpplaringKategoriseringRepoAdapter.lagreOpplaringKategoriseringValg(
                gjennomforingId = deltakerliste.id,
                valgteVerdier = emptySet(),
                valgteSertifiseringer = null,
            )

            // Assert
            val result = OpplaringKategoriseringRepoAdapter.hentOpplaringKategoriseringValg(deltakerliste.id)
            result.valgteKategoriseringer.shouldBeEmpty()
        }

        @Test
        fun `null for valgteVerdier endrer ikke eksisterende kategoriseringsvalg`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            val bransjeId = UUID.randomUUID()
            val opprinneligeValg = setOf(
                OpplaringKategoriseringValg.ValgteFelt(
                    representerer = OpplaringKategoriseringType.BRANSJE_ID,
                    valg = mapOf(bransjeId to "Bygg og anlegg"),
                ),
            )

            OpplaringKategoriseringRepoAdapter.lagreOpplaringKategoriseringValg(
                gjennomforingId = deltakerliste.id,
                valgteVerdier = opprinneligeValg,
                valgteSertifiseringer = emptySet(),
            )

            // Act
            OpplaringKategoriseringRepoAdapter.lagreOpplaringKategoriseringValg(
                gjennomforingId = deltakerliste.id,
                valgteVerdier = null,
                valgteSertifiseringer = null,
            )

            // Assert
            val result = OpplaringKategoriseringRepoAdapter.hentOpplaringKategoriseringValg(deltakerliste.id)
            result.valgteKategoriseringer shouldBe opprinneligeValg
        }

        @Test
        fun `null for sertifiseringer endrer ikke eksisterende sertifiseringer`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            val sertifiseringer = setOf(
                SertifiseringValg(id = 1, navn = "Truckfører T1"),
            )

            OpplaringKategoriseringRepoAdapter.lagreOpplaringKategoriseringValg(
                gjennomforingId = deltakerliste.id,
                valgteVerdier = emptySet(),
                valgteSertifiseringer = sertifiseringer,
            )

            // Act
            OpplaringKategoriseringRepoAdapter.lagreOpplaringKategoriseringValg(
                gjennomforingId = deltakerliste.id,
                valgteVerdier = null,
                valgteSertifiseringer = null,
            )

            // Assert
            val result = OpplaringKategoriseringRepoAdapter.hentOpplaringKategoriseringValg(deltakerliste.id)
            result.valgteSertifiseringer shouldBe sertifiseringer
        }

        @Test
        fun `pavirker ikke andre gjennomforinger`() {
            // Arrange
            val deltakerliste1 = lagDeltakerliste()
            val deltakerliste2 = lagDeltakerliste()
            TestRepository.insert(deltakerliste1)
            TestRepository.insert(deltakerliste2)

            val bransjeId1 = UUID.randomUUID()
            val bransjeId2 = UUID.randomUUID()

            OpplaringKategoriseringRepoAdapter.lagreOpplaringKategoriseringValg(
                gjennomforingId = deltakerliste1.id,
                valgteVerdier = setOf(
                    OpplaringKategoriseringValg.ValgteFelt(
                        representerer = OpplaringKategoriseringType.BRANSJE_ID,
                        valg = mapOf(bransjeId1 to "Bygg og anlegg"),
                    ),
                ),
                valgteSertifiseringer = emptySet(),
            )

            OpplaringKategoriseringRepoAdapter.lagreOpplaringKategoriseringValg(
                gjennomforingId = deltakerliste2.id,
                valgteVerdier = setOf(
                    OpplaringKategoriseringValg.ValgteFelt(
                        representerer = OpplaringKategoriseringType.FORERKORT,
                        valg = mapOf(bransjeId2 to "B"),
                    ),
                ),
                valgteSertifiseringer = emptySet(),
            )

            // Act & Assert
            val result1 = OpplaringKategoriseringRepoAdapter.hentOpplaringKategoriseringValg(deltakerliste1.id)
            result1.valgteKategoriseringer.first().representerer shouldBe OpplaringKategoriseringType.BRANSJE_ID

            val result2 = OpplaringKategoriseringRepoAdapter.hentOpplaringKategoriseringValg(deltakerliste2.id)
            result2.valgteKategoriseringer.first().representerer shouldBe OpplaringKategoriseringType.FORERKORT
        }
    }
}
