@file:Suppress("ktlint:standard:no-wildcard-imports")

package no.nav.amt.deltaker.repository

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.internapi.enkeltplass.PrisinformasjonDto
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

            val anskaffelse = PrisinformasjonDto.Anskaffelse(pris = 25000)
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

            val tilskudd = PrisinformasjonDto.Tilskudd(
                tilleggsopplysninger = "Tilskuddsinformasjon",
                tilskudd = listOf(
                    PrisinformasjonDto.Tilskudd.TilskuddInfo(
                        type = PrisinformasjonDto.Tilskudd.Tilskuddstype.SKOLEPENGER,
                        pris = 8000,
                    ),
                    PrisinformasjonDto.Tilskudd.TilskuddInfo(
                        type = PrisinformasjonDto.Tilskudd.Tilskuddstype.EKSAMENSGEBYR,
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

            val ingenKostnader = PrisinformasjonDto.IngenKostnader(
                aarsak = PrisinformasjonDto.IngenKostnader.Aarsak.OPPLAERINGEN_ER_KOSTNADSFRI,
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

            val anskaffelse = PrisinformasjonDto.Anskaffelse(pris = 30000)

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

            val tilskudd = PrisinformasjonDto.Tilskudd(
                tilleggsopplysninger = "Tilskuddinformasjon",
                tilskudd = listOf(
                    PrisinformasjonDto.Tilskudd.TilskuddInfo(
                        type = PrisinformasjonDto.Tilskudd.Tilskuddstype.SKOLEPENGER,
                        pris = 8000,
                    ),
                    PrisinformasjonDto.Tilskudd.TilskuddInfo(
                        type = PrisinformasjonDto.Tilskudd.Tilskuddstype.EKSAMENSGEBYR,
                        pris = 1500,
                    ),
                    PrisinformasjonDto.Tilskudd.TilskuddInfo(
                        type = PrisinformasjonDto.Tilskudd.Tilskuddstype.STUDIEREISE,
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

            val ingenKostnader = PrisinformasjonDto.IngenKostnader(
                aarsak = PrisinformasjonDto.IngenKostnader.Aarsak.OPPLAERINGEN_ER_EGENFINANSIERT,
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

            val anskaffelse = PrisinformasjonDto.Anskaffelse(pris = 20000)
            PrisinfoRepoAdapter.lagrePrisinfo(deltakerliste.id, anskaffelse)

            val tilskudd = PrisinformasjonDto.Tilskudd(
                tilleggsopplysninger = "Nye opplysninger",
                tilskudd = listOf(
                    PrisinformasjonDto.Tilskudd.TilskuddInfo(
                        type = PrisinformasjonDto.Tilskudd.Tilskuddstype.SKOLEPENGER,
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

            val tilskudd = PrisinformasjonDto.Tilskudd(
                tilleggsopplysninger = "Tilskudd",
                tilskudd = listOf(
                    PrisinformasjonDto.Tilskudd.TilskuddInfo(
                        type = PrisinformasjonDto.Tilskudd.Tilskuddstype.SKOLEPENGER,
                        pris = 5000,
                    ),
                    PrisinformasjonDto.Tilskudd.TilskuddInfo(
                        type = PrisinformasjonDto.Tilskudd.Tilskuddstype.EKSAMENSGEBYR,
                        pris = 2000,
                    ),
                ),
            )
            PrisinfoRepoAdapter.lagrePrisinfo(deltakerliste.id, tilskudd)

            val ingenKostnader = PrisinformasjonDto.IngenKostnader(
                aarsak = PrisinformasjonDto.IngenKostnader.Aarsak.OPPLAERINGEN_ER_EGENFINANSIERT,
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
