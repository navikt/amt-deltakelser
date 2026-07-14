@file:Suppress("ktlint:standard:no-wildcard-imports")

package no.nav.amt.deltaker.repository

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Anskaffelse
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto.IngenKostnader
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto.IngenKostnader.Aarsak
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd.TilskuddInfo
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd.Tilskuddstype
import no.nav.amt.lib.testing.DatabaseTestExtension
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class PrisinfoRepoAdapterTest {
    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Nested
    inner class HentPrisinfoTests {
        @Test
        fun `henter Anskaffelse prisinfo`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            val anskaffelse = Anskaffelse(pris = 25000)
            PrisinfoRepoAdapter.lagrePrisinfo(deltakerliste.id, anskaffelse)

            // Act
            val result = PrisinfoRepoAdapter.hentPrisinfo(deltakerliste.id)

            // Assert
            result shouldBe anskaffelse
        }

        @Test
        fun `henter Tilskudd prisinfo`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            val tilskudd = Tilskudd(
                tilleggsopplysninger = "Tilskuddsinformasjon",
                tilskudd = listOf(
                    TilskuddInfo(
                        type = Tilskuddstype.SKOLEPENGER,
                        pris = 8000,
                    ),
                    TilskuddInfo(
                        type = Tilskuddstype.EKSAMENSGEBYR,
                        pris = 2000,
                    ),
                ),
            )
            PrisinfoRepoAdapter.lagrePrisinfo(deltakerliste.id, tilskudd)

            // Act
            val result = PrisinfoRepoAdapter.hentPrisinfo(deltakerliste.id)

            // Assert
            result shouldBe tilskudd
        }

        @Test
        fun `henter IngenKostnader prisinfo`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            val ingenKostnader = IngenKostnader(
                aarsak = Aarsak.OPPLAERINGEN_ER_KOSTNADSFRI,
                tilleggsopplysninger = "Gratis opplaering",
            )
            PrisinfoRepoAdapter.lagrePrisinfo(deltakerliste.id, ingenKostnader)

            // Act
            val result = PrisinfoRepoAdapter.hentPrisinfo(deltakerliste.id)

            // Assert
            result shouldBe ingenKostnader
        }

        @Test
        fun `skal returnere null naar prisinfo ikke finnes`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            // Act & Assert
            PrisinfoRepoAdapter.hentPrisinfo(deltakerliste.id) shouldBe null
        }
    }

    @Nested
    inner class LagrePrisinfoTests {
        @Test
        fun `lagrer Anskaffelse prisinfo`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            val anskaffelse = Anskaffelse(pris = 30000)

            // Act
            PrisinfoRepoAdapter.lagrePrisinfo(
                gjennomforingId = deltakerliste.id,
                prisinformasjon = anskaffelse,
            )

            // Assert
            val result = PrisinfoRepoAdapter.hentPrisinfo(deltakerliste.id)
            result shouldBe anskaffelse
        }

        @Test
        fun `lagrer Tilskudd prisinfo med belop`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            val tilskudd = Tilskudd(
                tilleggsopplysninger = "Tilskuddinformasjon",
                tilskudd = listOf(
                    TilskuddInfo(
                        type = Tilskuddstype.SKOLEPENGER,
                        pris = 8000,
                    ),
                    TilskuddInfo(
                        type = Tilskuddstype.EKSAMENSGEBYR,
                        pris = 1500,
                    ),
                    TilskuddInfo(
                        type = Tilskuddstype.STUDIEREISE,
                        pris = 3000,
                    ),
                ),
            )

            // Act
            PrisinfoRepoAdapter.lagrePrisinfo(
                gjennomforingId = deltakerliste.id,
                prisinformasjon = tilskudd,
            )

            // Assert
            val result = PrisinfoRepoAdapter.hentPrisinfo(deltakerliste.id)
            result shouldBe tilskudd
        }

        @Test
        fun `lagrer IngenKostnader prisinfo`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            val ingenKostnader = IngenKostnader(
                aarsak = Aarsak.OPPLAERINGEN_ER_EGENFINANSIERT,
                tilleggsopplysninger = null,
            )

            // Act
            PrisinfoRepoAdapter.lagrePrisinfo(
                gjennomforingId = deltakerliste.id,
                prisinformasjon = ingenKostnader,
            )

            // Assert
            val result = PrisinfoRepoAdapter.hentPrisinfo(deltakerliste.id)
            result shouldBe ingenKostnader
        }

        @Test
        fun `oppdaterer fra Anskaffelse til Tilskudd`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            val anskaffelse = Anskaffelse(pris = 20000)
            PrisinfoRepoAdapter.lagrePrisinfo(deltakerliste.id, anskaffelse)

            val tilskudd = Tilskudd(
                tilleggsopplysninger = "Nye opplysninger",
                tilskudd = listOf(
                    TilskuddInfo(
                        type = Tilskuddstype.SKOLEPENGER,
                        pris = 6000,
                    ),
                ),
            )

            // Act
            PrisinfoRepoAdapter.lagrePrisinfo(
                gjennomforingId = deltakerliste.id,
                prisinformasjon = tilskudd,
            )

            // Assert
            val result = PrisinfoRepoAdapter.hentPrisinfo(deltakerliste.id)
            result shouldBe tilskudd
        }

        @Test
        fun `oppdaterer fra Tilskudd til IngenKostnader sletter gamle belop`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            val tilskudd = Tilskudd(
                tilleggsopplysninger = "Tilskudd",
                tilskudd = listOf(
                    TilskuddInfo(
                        type = Tilskuddstype.SKOLEPENGER,
                        pris = 5000,
                    ),
                    TilskuddInfo(
                        type = Tilskuddstype.EKSAMENSGEBYR,
                        pris = 2000,
                    ),
                ),
            )
            PrisinfoRepoAdapter.lagrePrisinfo(deltakerliste.id, tilskudd)

            val ingenKostnader = IngenKostnader(
                aarsak = Aarsak.OPPLAERINGEN_ER_EGENFINANSIERT,
                tilleggsopplysninger = "Egenfinansiert",
            )

            // Act
            PrisinfoRepoAdapter.lagrePrisinfo(deltakerliste.id, ingenKostnader)

            // Assert
            val result = PrisinfoRepoAdapter.hentPrisinfo(deltakerliste.id)
            result shouldBe ingenKostnader

            // Verifiser at belop er slettet
            val beloep = PrisinfoBelopRepository.hentPrisinfoBelop(deltakerliste.id)
            beloep.shouldBeEmpty()
        }
    }
}
