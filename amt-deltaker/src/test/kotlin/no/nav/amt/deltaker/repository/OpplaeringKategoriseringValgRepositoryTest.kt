package no.nav.amt.deltaker.repository

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.repository.dbo.OpplaeringKategoriseringValgDbo
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.internapi.enkeltplass.OpplaringKategoriseringResponse
import no.nav.amt.lib.testing.DatabaseTestExtension
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.UUID

class OpplaeringKategoriseringValgRepositoryTest {
    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Nested
    inner class InsertKategoriseringValg {
        @Test
        fun `tom liste - lagrer ingenting`() {
            // Arrange
            val gjennomforing = lagDeltakerliste()
            TestRepository.insert(gjennomforing)

            // Act
            OpplaeringKategoriseringValgRepository.insertKategoriseringValg(gjennomforing.id, emptyList())

            // Assert
            OpplaeringKategoriseringValgRepository.hentKategoriseringValg(gjennomforing.id).shouldBeEmpty()
        }

        @Test
        fun `enkelt valg - lagrer`() {
            // Arrange
            val gjennomforing = lagDeltakerliste()
            TestRepository.insert(gjennomforing)

            val valg = listOf(
                OpplaeringKategoriseringValgDbo(
                    representerer = OpplaringKategoriseringResponse.Representerer.BRANSJE_ID,
                    kodeverkId = UUID.randomUUID(),
                    tekst = "Bygg og anlegg",
                ),
            )

            // Act
            OpplaeringKategoriseringValgRepository.insertKategoriseringValg(gjennomforing.id, valg)

            // Assert
            OpplaeringKategoriseringValgRepository
                .hentKategoriseringValg(gjennomforing.id)
                .shouldContainExactlyInAnyOrder(valg)
        }

        @Test
        fun `flere valg - lagrer alle`() {
            // Arrange
            val gjennomforing = lagDeltakerliste()
            TestRepository.insert(gjennomforing)

            val valg = listOf(
                OpplaeringKategoriseringValgDbo(
                    representerer = OpplaringKategoriseringResponse.Representerer.BRANSJE_ID,
                    kodeverkId = UUID.randomUUID(),
                    tekst = "Bygg og anlegg",
                ),
                OpplaeringKategoriseringValgDbo(
                    representerer = OpplaringKategoriseringResponse.Representerer.BRANSJE_ID,
                    kodeverkId = UUID.randomUUID(),
                    tekst = "Helse og omsorg",
                ),
                OpplaeringKategoriseringValgDbo(
                    representerer = OpplaringKategoriseringResponse.Representerer.FORERKORT,
                    kodeverkId = UUID.randomUUID(),
                    tekst = "B",
                ),
                OpplaeringKategoriseringValgDbo(
                    representerer = OpplaringKategoriseringResponse.Representerer.SERTIFISERINGER,
                    kodeverkId = UUID.randomUUID(),
                    tekst = "Truckfører T1",
                ),
            )

            // Act
            OpplaeringKategoriseringValgRepository.insertKategoriseringValg(gjennomforing.id, valg)

            // Assert
            OpplaeringKategoriseringValgRepository
                .hentKategoriseringValg(gjennomforing.id)
                .shouldContainExactlyInAnyOrder(valg)
        }

        @Test
        fun `ulike gjennomforinger - lagres separat`() {
            // Arrange
            val gjennomforing1 = lagDeltakerliste()
            TestRepository.insert(gjennomforing1)

            val gjennomforing2 = lagDeltakerliste()
            TestRepository.insert(gjennomforing2)

            val valg1 = listOf(
                OpplaeringKategoriseringValgDbo(
                    representerer = OpplaringKategoriseringResponse.Representerer.BRANSJE_ID,
                    kodeverkId = UUID.randomUUID(),
                    tekst = "Bygg og anlegg",
                ),
            )

            val valg2 = listOf(
                OpplaeringKategoriseringValgDbo(
                    representerer = OpplaringKategoriseringResponse.Representerer.FORERKORT,
                    kodeverkId = UUID.randomUUID(),
                    tekst = "B",
                ),
            )

            // Act
            OpplaeringKategoriseringValgRepository.insertKategoriseringValg(gjennomforing1.id, valg1)
            OpplaeringKategoriseringValgRepository.insertKategoriseringValg(gjennomforing2.id, valg2)

            // Assert
            OpplaeringKategoriseringValgRepository.hentKategoriseringValg(gjennomforing1.id) shouldBe valg1
            OpplaeringKategoriseringValgRepository.hentKategoriseringValg(gjennomforing2.id) shouldBe valg2
        }
    }

    @Nested
    inner class HentKategoriseringValg {
        @Test
        fun `ingen valg lagret - returnerer tom liste`() {
            // Arrange
            val gjennomforing = lagDeltakerliste()
            TestRepository.insert(gjennomforing)

            // Act
            val resultat = OpplaeringKategoriseringValgRepository.hentKategoriseringValg(gjennomforing.id)

            // Assert
            resultat.shouldBeEmpty()
        }

        @Test
        fun `henter kun for angitt gjennomforing`() {
            // Arrange
            val gjennomforing1 = lagDeltakerliste()
            val gjennomforing2 = lagDeltakerliste()
            TestRepository.insert(gjennomforing1)
            TestRepository.insert(gjennomforing2)

            val valg = listOf(
                OpplaeringKategoriseringValgDbo(
                    representerer = OpplaringKategoriseringResponse.Representerer.BRANSJE_ID,
                    kodeverkId = UUID.randomUUID(),
                    tekst = "Bygg og anlegg",
                ),
                OpplaeringKategoriseringValgDbo(
                    representerer = OpplaringKategoriseringResponse.Representerer.FORERKORT,
                    kodeverkId = UUID.randomUUID(),
                    tekst = "B",
                ),
            )

            OpplaeringKategoriseringValgRepository.insertKategoriseringValg(gjennomforing1.id, valg)

            // Act
            val resultat = OpplaeringKategoriseringValgRepository.hentKategoriseringValg(gjennomforing2.id)

            // Assert
            resultat.shouldBeEmpty()
        }

        @Test
        fun `returnerer alle valg for gjennomforing`() {
            // Arrange
            val gjennomforing = lagDeltakerliste()
            TestRepository.insert(gjennomforing)

            val valg = listOf(
                OpplaeringKategoriseringValgDbo(
                    representerer = OpplaringKategoriseringResponse.Representerer.BRANSJE_ID,
                    kodeverkId = UUID.randomUUID(),
                    tekst = "Bygg og anlegg",
                ),
                OpplaeringKategoriseringValgDbo(
                    representerer = OpplaringKategoriseringResponse.Representerer.BRANSJE_ID,
                    kodeverkId = UUID.randomUUID(),
                    tekst = "Helse og omsorg",
                ),
                OpplaeringKategoriseringValgDbo(
                    representerer = OpplaringKategoriseringResponse.Representerer.FORERKORT,
                    kodeverkId = UUID.randomUUID(),
                    tekst = "B",
                ),
            )

            OpplaeringKategoriseringValgRepository.insertKategoriseringValg(gjennomforing.id, valg)

            // Act
            val resultat = OpplaeringKategoriseringValgRepository.hentKategoriseringValg(gjennomforing.id)

            // Assert
            resultat.shouldContainExactlyInAnyOrder(valg)
        }
    }

    @Nested
    inner class DeleteForGjennomforing {
        @Test
        fun `sletter alle valg for gjennomforing`() {
            // Arrange
            val gjennomforing = lagDeltakerliste()
            TestRepository.insert(gjennomforing)

            val valg = listOf(
                OpplaeringKategoriseringValgDbo(
                    representerer = OpplaringKategoriseringResponse.Representerer.BRANSJE_ID,
                    kodeverkId = UUID.randomUUID(),
                    tekst = "Bygg og anlegg",
                ),
                OpplaeringKategoriseringValgDbo(
                    representerer = OpplaringKategoriseringResponse.Representerer.FORERKORT,
                    kodeverkId = UUID.randomUUID(),
                    tekst = "B",
                ),
            )

            OpplaeringKategoriseringValgRepository.insertKategoriseringValg(gjennomforing.id, valg)

            // Act
            OpplaeringKategoriseringValgRepository.deleteForGjennomforing(gjennomforing.id)

            // Assert
            OpplaeringKategoriseringValgRepository.hentKategoriseringValg(gjennomforing.id).shouldBeEmpty()
        }

        @Test
        fun `sletter ikke valg for andre gjennomforinger`() {
            // Arrange
            val gjennomforing1 = lagDeltakerliste()
            val gjennomforing2 = lagDeltakerliste()
            TestRepository.insert(gjennomforing1)
            TestRepository.insert(gjennomforing2)

            val valg = listOf(
                OpplaeringKategoriseringValgDbo(
                    representerer = OpplaringKategoriseringResponse.Representerer.BRANSJE_ID,
                    kodeverkId = UUID.randomUUID(),
                    tekst = "Bygg og anlegg",
                ),
            )

            OpplaeringKategoriseringValgRepository.insertKategoriseringValg(gjennomforing1.id, valg)
            OpplaeringKategoriseringValgRepository.insertKategoriseringValg(gjennomforing2.id, valg)

            // Act
            OpplaeringKategoriseringValgRepository.deleteForGjennomforing(gjennomforing1.id)

            // Assert
            OpplaeringKategoriseringValgRepository.hentKategoriseringValg(gjennomforing1.id).shouldBeEmpty()
            OpplaeringKategoriseringValgRepository.hentKategoriseringValg(gjennomforing2.id) shouldBe valg
        }
    }
}
