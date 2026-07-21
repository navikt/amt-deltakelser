@file:Suppress("ktlint:standard:no-wildcard-imports")

package no.nav.amt.deltaker.repository

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.repository.PrisinfoRepoAdapter.toPrisinfoUpsertDbo
import no.nav.amt.deltaker.repository.dbo.PrisinfoDbo
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
import java.util.UUID

class PrisinfoRepoAdapterTest {
    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()

        private val gjennomforingInTest = lagDeltakerliste()
    }

    @Nested
    inner class HentPrisinfoTests {
        @Test
        fun `henter Anskaffelse prisinfo`() {
            // Arrange
            TestRepository.insert(gjennomforingInTest)

            val anskaffelse = Anskaffelse(pris = 25000)
            PrisinfoRepoAdapter.lagrePrisinfoForKladdOgUtkast(gjennomforingInTest.id, anskaffelse)

            // Act
            val result = PrisinfoRepoAdapter.hentPrisinfo(gjennomforingInTest.id)

            // Assert
            result shouldBe anskaffelse
        }

        @Test
        fun `henter Tilskudd prisinfo`() {
            // Arrange
            TestRepository.insert(gjennomforingInTest)

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
            PrisinfoRepoAdapter.lagrePrisinfoForKladdOgUtkast(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjon = tilskudd,
            )

            // Act
            val result = PrisinfoRepoAdapter.hentPrisinfo(gjennomforingInTest.id)

            // Assert
            result shouldBe tilskudd
        }

        @Test
        fun `henter IngenKostnader prisinfo`() {
            // Arrange
            TestRepository.insert(gjennomforingInTest)

            val ingenKostnader = IngenKostnader(
                aarsak = Aarsak.OPPLAERINGEN_ER_KOSTNADSFRI,
                tilleggsopplysninger = "Gratis opplaering",
            )

            PrisinfoRepoAdapter.lagrePrisinfoForKladdOgUtkast(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjon = ingenKostnader,
            )

            // Act
            val result = PrisinfoRepoAdapter.hentPrisinfo(gjennomforingInTest.id)

            // Assert
            result shouldBe ingenKostnader
        }

        @Test
        fun `returnerer endring-prisinfo når kun endring finnes`() {
            // Arrange
            TestRepository.insert(gjennomforingInTest)

            val pendingPrisinfo = Anskaffelse(pris = 15000)
            val prisinfoId = PrisinfoRepoAdapter.lagrePrisinfoForKladdOgUtkast(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjon = pendingPrisinfo,
            )

            // Act
            val result = PrisinfoRepoAdapter.hentPrisinfo(gjennomforingInTest.id)

            result shouldBe pendingPrisinfo
        }

        @Test
        fun `prioriterer gjeldende når begge finnes`() {
            // Arrange
            TestRepository.insert(gjennomforingInTest)

            val trueVariant = Tilskudd(
                tilleggsopplysninger = "Pending",
                tilskudd = listOf(
                    TilskuddInfo(type = Tilskuddstype.SKOLEPENGER, pris = 5000),
                ),
            )
            PrisinfoRepoAdapter.lagrePrisinfoForKladdOgUtkast(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjon = trueVariant,
            )

            val falseVariant = Tilskudd(
                tilleggsopplysninger = "Godkjent",
                tilskudd = listOf(
                    TilskuddInfo(type = Tilskuddstype.SKOLEPENGER, pris = 10000),
                ),
            )

            PrisinfoRepository.upsertPrisinfo(
                falseVariant.toPrisinfoUpsertDbo(
                    prisinfoId = UUID.randomUUID(),
                    gjennomforingId = gjennomforingInTest.id,
                ),
            )

            // Act
            val result = PrisinfoRepoAdapter.hentPrisinfo(gjennomforingInTest.id)

            // Assert
            result shouldBe trueVariant
        }

        @Test
        fun `skal returnere null naar prisinfo ikke finnes`() {
            // Arrange
            TestRepository.insert(gjennomforingInTest)

            // Act & Assert
            PrisinfoRepoAdapter.hentPrisinfo(gjennomforingInTest.id) shouldBe null
        }

        @Test
        fun `med brukEndring true - henter kun endring-prisinfo`() {
            // Arrange
            TestRepository.insert(gjennomforingInTest)

            val pendingPrisinfo = Anskaffelse(pris = 15000)
            PrisinfoRepoAdapter.lagrePrisinfoForKladdOgUtkast(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjon = pendingPrisinfo,
            )

            PrisinfoRepoAdapter.godkjennOkonomi(gjennomforingInTest.id)

            // Act
            val result = PrisinfoRepoAdapter.hentPrisinfo(
                gjennomforingId = gjennomforingInTest.id,
                brukEndring = true,
            )

            // Assert
            result shouldBe null
        }

        @Test
        fun `med brukEndring true - returnerer endring når begge finnes`() {
            // Arrange
            TestRepository.insert(gjennomforingInTest)

            val godkjentPrisinfo = Anskaffelse(pris = 5000)
            PrisinfoRepoAdapter.lagrePrisinfoForKladdOgUtkast(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjon = godkjentPrisinfo,
            )

            val pendingPrisinfo = Anskaffelse(pris = 20000)
            PrisinfoRepoAdapter.lagrePrisinfoForKladdOgUtkast(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjon = pendingPrisinfo,
            )

            // Act
            val result = PrisinfoRepoAdapter.hentPrisinfo(
                gjennomforingId = gjennomforingInTest.id,
                brukEndring = true,
            )

            // Assert
            result shouldBe pendingPrisinfo
        }

        @Test
        fun `med brukEndring true - returnerer null når kun gjeldende finnes`() {
            // Arrange
            TestRepository.insert(gjennomforingInTest)

            val godkjentPrisinfo = Anskaffelse(pris = 10000)
            PrisinfoRepoAdapter.lagrePrisinfoForKladdOgUtkast(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjon = godkjentPrisinfo,
            )

            PrisinfoRepoAdapter.godkjennOkonomi(gjennomforingInTest.id)

            // Act
            val result = PrisinfoRepoAdapter.hentPrisinfo(
                gjennomforingId = gjennomforingInTest.id,
                brukEndring = true,
            )

            // Assert
            result shouldBe null
        }

        @Test
        fun `med brukEndring false (default) - prioriterer gjeldende`() {
            // Arrange
            TestRepository.insert(gjennomforingInTest)

            val godkjentPrisinfo = Tilskudd(
                tilleggsopplysninger = "Godkjent",
                tilskudd = listOf(
                    TilskuddInfo(type = Tilskuddstype.SKOLEPENGER, pris = 5000),
                ),
            )
            PrisinfoRepoAdapter.lagrePrisinfoForKladdOgUtkast(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjon = godkjentPrisinfo,
            )

            PrisinfoRepoAdapter.godkjennOkonomi(gjennomforingInTest.id)

            val pendingPrisinfo = Tilskudd(
                tilleggsopplysninger = "Pending",
                tilskudd = listOf(
                    TilskuddInfo(type = Tilskuddstype.SKOLEPENGER, pris = 10000),
                ),
            )

            PrisinfoRepoAdapter.lagrePrisinfoForKladdOgUtkast(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjon = pendingPrisinfo,
            )

            // Act
            val result = PrisinfoRepoAdapter.hentPrisinfo(
                gjennomforingId = gjennomforingInTest.id,
                brukEndring = false,
            )

            // Assert
            result shouldBe godkjentPrisinfo
        }
    }

    @Nested
    inner class GodkjennOkonomiTests {
        @Test
        fun `godkjennOkonomi setter status = GODKJENT`() {
            // Arrange
            TestRepository.insert(gjennomforingInTest)

            val anskaffelse = Anskaffelse(pris = 25000)
            PrisinfoRepoAdapter.lagrePrisinfoForKladdOgUtkast(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjon = anskaffelse,
            )

            val beforeGodkjenning = PrisinfoRepository.hentPrisinfo(
                gjennomforingId = gjennomforingInTest.id,
                rolle = PrisinfoDbo.Rolle.ENDRING,
            )
            beforeGodkjenning?.status shouldBe PrisinfoDbo.PrisinfoStatus.SENDT

            // Act
            PrisinfoRepoAdapter.godkjennOkonomi(gjennomforingInTest.id)

            // Assert
            val afterGodkjenning = PrisinfoRepository.hentPrisinfo(
                gjennomforingId = gjennomforingInTest.id,
                rolle = PrisinfoDbo.Rolle.GJELDENDE,
            )
            afterGodkjenning.shouldNotBeNull().status shouldBe PrisinfoDbo.PrisinfoStatus.GODKJENT

            PrisinfoRepository
                .hentPrisinfo(
                    gjennomforingId = gjennomforingInTest.id,
                    rolle = PrisinfoDbo.Rolle.ENDRING,
                ).shouldBeNull()
        }

        @Test
        fun `godkjennOkonomi kaster exception når ingen prisinfo finnes`() {
            // Arrange
            TestRepository.insert(gjennomforingInTest)

            // Act & Assert - skal ikke kaste exception
            shouldThrow<IllegalStateException> {
                PrisinfoRepoAdapter.godkjennOkonomi(gjennomforingInTest.id)
            }
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
            PrisinfoRepoAdapter.lagrePrisinfoForKladdOgUtkast(
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
            TestRepository.insert(gjennomforingInTest)

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
            PrisinfoRepoAdapter.lagrePrisinfoForKladdOgUtkast(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjon = tilskudd,
            )

            // Assert
            val result = PrisinfoRepoAdapter.hentPrisinfo(gjennomforingInTest.id)
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
            PrisinfoRepoAdapter.lagrePrisinfoForKladdOgUtkast(
                gjennomforingId = deltakerliste.id,
                prisinformasjon = ingenKostnader,
            )

            // Assert
            val result = PrisinfoRepoAdapter.hentPrisinfo(deltakerliste.id)
            result shouldBe ingenKostnader
        }
    }

    @Test
    fun `oppdaterer fra Anskaffelse til Tilskudd`() {
        // Arrange
        val deltakerliste = lagDeltakerliste()
        TestRepository.insert(deltakerliste)

        val anskaffelse = Anskaffelse(pris = 20000)
        PrisinfoRepoAdapter.lagrePrisinfoForKladdOgUtkast(deltakerliste.id, anskaffelse)

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
        PrisinfoRepoAdapter.lagrePrisinfoForKladdOgUtkast(
            gjennomforingId = deltakerliste.id,
            prisinformasjon = tilskudd,
        )

        // Assert
        val result = PrisinfoRepoAdapter.hentPrisinfo(deltakerliste.id)
        result shouldBe tilskudd
    }

    @Test
    fun `oppdaterer fra Tilskudd til IngenKostnader`() {
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
        PrisinfoRepoAdapter.lagrePrisinfoForKladdOgUtkast(deltakerliste.id, tilskudd)

        val ingenKostnader = IngenKostnader(
            aarsak = Aarsak.OPPLAERINGEN_ER_EGENFINANSIERT,
            tilleggsopplysninger = "Egenfinansiert",
        )

        // Act
        val prisinformasjonId = PrisinfoRepoAdapter.lagrePrisinfoForKladdOgUtkast(
            gjennomforingId = deltakerliste.id,
            prisinformasjon = ingenKostnader,
        )

        // Assert
        val result = PrisinfoRepoAdapter.hentPrisinfo(
            gjennomforingId = deltakerliste.id,
            brukEndring = true,
        )

        result shouldBe ingenKostnader
    }
}
