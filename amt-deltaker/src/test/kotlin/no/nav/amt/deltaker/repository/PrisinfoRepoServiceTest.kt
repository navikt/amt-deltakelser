@file:Suppress("ktlint:standard:no-wildcard-imports")

package no.nav.amt.deltaker.repository

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.models.deltakerliste.Prisinformasjon
import no.nav.amt.lib.testing.DatabaseTestExtension
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class PrisinfoRepoServiceTest {
    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    private val service = PrisinfoRepoService()

    @Nested
    inner class HentPrisinfoTests {
        @Test
        fun `henter Anskaffelse prisinfo`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            val anskaffelse = Prisinformasjon.Anskaffelse(pris = 25000)
            service.lagrePrisinfo(deltakerliste.id, anskaffelse)

            // Act
            val result = service.hentPrisinfo(deltakerliste.id)

            // Assert
            result shouldBe anskaffelse
        }

        @Test
        fun `henter Tilskudd prisinfo`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            val tilskudd = Prisinformasjon.Tilskudd(
                tilleggsopplysninger = "Tilskuddsinformasjon",
                tilskudd = mapOf(
                    Prisinformasjon.Tilskudd.Tilskuddstype.SKOLEPENGER to 5000,
                    Prisinformasjon.Tilskudd.Tilskuddstype.EKSAMENSGEBYR to 2000,
                ),
            )
            service.lagrePrisinfo(deltakerliste.id, tilskudd)

            // Act
            val result = service.hentPrisinfo(deltakerliste.id)

            // Assert
            result shouldBe tilskudd
        }

        @Test
        fun `henter IngenKostnader prisinfo`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            val ingenKostnader = Prisinformasjon.IngenKostnader(
                aarsak = Prisinformasjon.IngenKostnader.Aarsak.OPPLAERINGEN_ER_KOSTNADSFRI,
                tilleggsopplysninger = "Gratis opplaering",
            )
            service.lagrePrisinfo(deltakerliste.id, ingenKostnader)

            // Act
            val result = service.hentPrisinfo(deltakerliste.id)

            // Assert
            result shouldBe ingenKostnader
        }

        @Test
        fun `skal returnere null naar prisinfo ikke finnes`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            // Act & Assert
            service.hentPrisinfo(deltakerliste.id) shouldBe null
        }
    }

    @Nested
    inner class LagrePrisinfoTests {
        @Test
        fun `lagrer Anskaffelse prisinfo`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            val anskaffelse = Prisinformasjon.Anskaffelse(pris = 30000)

            // Act
            service.lagrePrisinfo(
                gjennomforingId = deltakerliste.id,
                prisinfo = anskaffelse,
            )

            // Assert
            val result = service.hentPrisinfo(deltakerliste.id)
            result shouldBe anskaffelse
        }

        @Test
        fun `lagrer Tilskudd prisinfo med belop`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            val tilskudd = Prisinformasjon.Tilskudd(
                tilleggsopplysninger = "Tilskuddinformasjon",
                tilskudd = mapOf(
                    Prisinformasjon.Tilskudd.Tilskuddstype.SKOLEPENGER to 8000,
                    Prisinformasjon.Tilskudd.Tilskuddstype.EKSAMENSGEBYR to 1500,
                    Prisinformasjon.Tilskudd.Tilskuddstype.STUDIEREISE to 3000,
                ),
            )

            // Act
            service.lagrePrisinfo(
                gjennomforingId = deltakerliste.id,
                prisinfo = tilskudd,
            )

            // Assert
            val result = service.hentPrisinfo(deltakerliste.id)
            result shouldBe tilskudd
        }

        @Test
        fun `lagrer IngenKostnader prisinfo`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            val ingenKostnader = Prisinformasjon.IngenKostnader(
                aarsak = Prisinformasjon.IngenKostnader.Aarsak.OPPLAERINGEN_ER_EGENFINANSIERT,
                tilleggsopplysninger = null,
            )

            // Act
            service.lagrePrisinfo(
                gjennomforingId = deltakerliste.id,
                prisinfo = ingenKostnader,
            )

            // Assert
            val result = service.hentPrisinfo(deltakerliste.id)
            result shouldBe ingenKostnader
        }

        @Test
        fun `oppdaterer fra Anskaffelse til Tilskudd`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            val anskaffelse = Prisinformasjon.Anskaffelse(pris = 20000)
            service.lagrePrisinfo(deltakerliste.id, anskaffelse)

            val tilskudd = Prisinformasjon.Tilskudd(
                tilleggsopplysninger = "Nye opplysninger",
                tilskudd = mapOf(
                    Prisinformasjon.Tilskudd.Tilskuddstype.SKOLEPENGER to 6000,
                ),
            )

            // Act
            service.lagrePrisinfo(
                gjennomforingId = deltakerliste.id,
                prisinfo = tilskudd,
            )

            // Assert
            val result = service.hentPrisinfo(deltakerliste.id)
            result shouldBe tilskudd
        }

        @Test
        fun `oppdaterer fra Tilskudd til IngenKostnader sletter gamle belop`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            val tilskudd = Prisinformasjon.Tilskudd(
                tilleggsopplysninger = "Tilskudd",
                tilskudd = mapOf(
                    Prisinformasjon.Tilskudd.Tilskuddstype.SKOLEPENGER to 5000,
                    Prisinformasjon.Tilskudd.Tilskuddstype.EKSAMENSGEBYR to 2000,
                ),
            )
            service.lagrePrisinfo(deltakerliste.id, tilskudd)

            val ingenKostnader = Prisinformasjon.IngenKostnader(
                aarsak = Prisinformasjon.IngenKostnader.Aarsak.OPPLAERINGEN_ER_EGENFINANSIERT,
                tilleggsopplysninger = "Egenfinansiert",
            )

            // Act
            service.lagrePrisinfo(deltakerliste.id, ingenKostnader)

            // Assert
            val result = service.hentPrisinfo(deltakerliste.id)
            result shouldBe ingenKostnader

            // Verifiser at belop er slettet
            val beloep = PrisinfoBelopRepository.hentPrisinfoBelop(deltakerliste.id)
            beloep.shouldBeEmpty()
        }
    }
}
