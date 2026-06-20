package no.nav.amt.deltaker.repository

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.models.deltakerliste.SertifiseringValg
import no.nav.amt.lib.testing.DatabaseTestExtension
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class SertifiseringValgRepositoryTest {
    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Nested
    inner class LagreSertifiseringValg {
        @Test
        fun `tom liste - lagrer ingenting`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            // Act
            SertifiseringValgRepository.lagreSertifiseringValg(
                gjennomforingId = deltakerliste.id,
                sertifiseringValg = emptySet(),
            )

            // Assert
            SertifiseringValgRepository.hentSertifiseringValg(deltakerliste.id).shouldBeEmpty()
        }

        @Test
        fun `flere sertifiseringer - lagrer alle`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)
            val sertifiseringer = setOf(
                SertifiseringValg(id = 1, navn = "Truckfører T1"),
                SertifiseringValg(id = 2, navn = "Truckfører T2"),
                SertifiseringValg(id = 3, navn = "Masseforflytningsmaskinfører"),
            )

            // Act
            SertifiseringValgRepository.lagreSertifiseringValg(
                gjennomforingId = deltakerliste.id,
                sertifiseringValg = sertifiseringer,
            )

            // Assert
            SertifiseringValgRepository
                .hentSertifiseringValg(deltakerliste.id)
                .shouldContainExactlyInAnyOrder(sertifiseringer)
        }

        @Test
        fun `ulike deltakerlister - lagres separat`() {
            // Arrange
            val deltakerliste1 = lagDeltakerliste()
            val deltakerliste2 = lagDeltakerliste()
            TestRepository.insert(deltakerliste1)
            TestRepository.insert(deltakerliste2)

            val sertifiseringer1 = setOf(SertifiseringValg(id = 1, navn = "Truckfører T1"))
            val sertifiseringer2 = setOf(SertifiseringValg(id = 2, navn = "Truckfører T2"))

            // Act
            SertifiseringValgRepository.lagreSertifiseringValg(
                gjennomforingId = deltakerliste1.id,
                sertifiseringValg = sertifiseringer1,
            )
            SertifiseringValgRepository.lagreSertifiseringValg(
                gjennomforingId = deltakerliste2.id,
                sertifiseringValg = sertifiseringer2,
            )

            // Assert
            SertifiseringValgRepository.hentSertifiseringValg(deltakerliste1.id) shouldBe sertifiseringer1
            SertifiseringValgRepository.hentSertifiseringValg(deltakerliste2.id) shouldBe sertifiseringer2
        }
    }

    @Nested
    inner class HentSertifiseringValg {
        @Test
        fun `ingen sertifiseringer lagret - returnerer tomt sett`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            // Act
            val resultat = SertifiseringValgRepository.hentSertifiseringValg(deltakerliste.id)

            // Assert
            resultat.shouldBeEmpty()
        }

        @Test
        fun `henter kun for angitt deltakerliste`() {
            // Arrange
            val deltakerliste1 = lagDeltakerliste()
            val deltakerliste2 = lagDeltakerliste()
            TestRepository.insert(deltakerliste1)
            TestRepository.insert(deltakerliste2)

            val sertifiseringer = setOf(
                SertifiseringValg(id = 1, navn = "Truckfører T1"),
                SertifiseringValg(id = 2, navn = "Truckfører T2"),
            )
            SertifiseringValgRepository.lagreSertifiseringValg(
                gjennomforingId = deltakerliste1.id,
                sertifiseringValg = sertifiseringer,
            )

            // Act
            val resultat = SertifiseringValgRepository.hentSertifiseringValg(deltakerliste2.id)

            // Assert
            resultat.shouldBeEmpty()
        }
    }

    @Nested
    inner class DeleteForGjennomforing {
        @Test
        fun `sletter alle sertifiseringer for deltakerliste`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)
            SertifiseringValgRepository.lagreSertifiseringValg(
                gjennomforingId = deltakerliste.id,
                sertifiseringValg = setOf(
                    SertifiseringValg(id = 1, navn = "Truckfører T1"),
                    SertifiseringValg(id = 2, navn = "Truckfører T2"),
                ),
            )

            // Act
            SertifiseringValgRepository.deleteForGjennomforing(deltakerliste.id)

            // Assert
            SertifiseringValgRepository.hentSertifiseringValg(deltakerliste.id).shouldBeEmpty()
        }

        @Test
        fun `sletter ikke sertifiseringer for andre deltakerlister`() {
            // Arrange
            val deltakerliste1 = lagDeltakerliste()
            val deltakerliste2 = lagDeltakerliste()
            TestRepository.insert(deltakerliste1)
            TestRepository.insert(deltakerliste2)

            val sertifiseringer = setOf(SertifiseringValg(id = 1, navn = "Truckfører T1"))
            SertifiseringValgRepository.lagreSertifiseringValg(
                gjennomforingId = deltakerliste1.id,
                sertifiseringValg = sertifiseringer,
            )
            SertifiseringValgRepository.lagreSertifiseringValg(
                gjennomforingId = deltakerliste2.id,
                sertifiseringValg = sertifiseringer,
            )

            // Act
            SertifiseringValgRepository.deleteForGjennomforing(deltakerliste1.id)

            // Assert
            SertifiseringValgRepository.hentSertifiseringValg(deltakerliste1.id).shouldBeEmpty()
            SertifiseringValgRepository.hentSertifiseringValg(deltakerliste2.id) shouldBe sertifiseringer
        }
    }
}
