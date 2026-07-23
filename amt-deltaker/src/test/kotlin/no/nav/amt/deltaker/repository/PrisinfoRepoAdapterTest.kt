@file:Suppress("ktlint:standard:no-wildcard-imports")

package no.nav.amt.deltaker.repository

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
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
            PrisinfoRepoAdapter.lagrePrisinfoForKladdOgUtkast(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjon = anskaffelse,
            )

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
            PrisinfoRepoAdapter.lagrePrisinfoForKladdOgUtkast(
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

            val gjeldende = Tilskudd(
                tilleggsopplysninger = "Godkjent",
                tilskudd = listOf(
                    TilskuddInfo(type = Tilskuddstype.SKOLEPENGER, pris = 10000),
                ),
            )
            val prisinformasjonId = PrisinfoRepoAdapter.lagrePrisinfoForKladdOgUtkast(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjon = gjeldende,
            )
            PrisinfoRepoAdapter.godkjennOkonomi(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjonId = prisinformasjonId,
            )

            val endring = Tilskudd(
                tilleggsopplysninger = "Pending",
                tilskudd = listOf(
                    TilskuddInfo(type = Tilskuddstype.SKOLEPENGER, pris = 5000),
                ),
            )
            PrisinfoRepoAdapter.lagrePrisinfoForKladdOgUtkast(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjon = endring,
            )

            // Act
            val result = PrisinfoRepoAdapter.hentPrisinfo(gjennomforingInTest.id)

            // Assert
            result shouldBe gjeldende
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
            val prisinformasjonId = PrisinfoRepoAdapter.lagrePrisinfoForKladdOgUtkast(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjon = pendingPrisinfo,
            )

            PrisinfoRepoAdapter.godkjennOkonomi(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjonId = prisinformasjonId,
            )

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
            val prisinformasjonId = PrisinfoRepoAdapter.lagrePrisinfoForKladdOgUtkast(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjon = godkjentPrisinfo,
            )

            PrisinfoRepoAdapter.godkjennOkonomi(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjonId = prisinformasjonId,
            )

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
            val prisinformasjonId = PrisinfoRepoAdapter.lagrePrisinfoForKladdOgUtkast(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjon = godkjentPrisinfo,
            )

            PrisinfoRepoAdapter.godkjennOkonomi(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjonId = prisinformasjonId,
            )

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
            val prisinformasjonId = PrisinfoRepoAdapter.lagrePrisinfoForKladdOgUtkast(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjon = anskaffelse,
            )

            val beforeGodkjenning = PrisinfoRepository
                .hentPrisinfo(
                    gjennomforingId = gjennomforingInTest.id,
                    rolle = PrisinfoDbo.Rolle.ENDRING,
                ).shouldNotBeNull()

            beforeGodkjenning.status shouldBe PrisinfoDbo.PrisinfoStatus.KLADD_UTKAST

            // Act
            PrisinfoRepoAdapter.godkjennOkonomi(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjonId = prisinformasjonId,
            )

            // Assert
            val afterGodkjenning = PrisinfoRepository
                .hentPrisinfo(
                    gjennomforingId = gjennomforingInTest.id,
                    rolle = PrisinfoDbo.Rolle.GJELDENDE,
                ).shouldNotBeNull()

            afterGodkjenning.status shouldBe PrisinfoDbo.PrisinfoStatus.GODKJENT

            PrisinfoRepository
                .hentPrisinfo(
                    gjennomforingId = gjennomforingInTest.id,
                    rolle = PrisinfoDbo.Rolle.ENDRING,
                ).shouldBeNull()
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

    @Nested
    inner class LagrePrisinfoEndringTests {
        @Test
        fun `lagrer Anskaffelse prisinfo`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            val anskaffelse = Anskaffelse(pris = 30000)

            // Act
            PrisinfoRepoAdapter.lagrePrisinfoEndring(
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
                    TilskuddInfo(type = Tilskuddstype.SKOLEPENGER, pris = 8000),
                    TilskuddInfo(type = Tilskuddstype.EKSAMENSGEBYR, pris = 1500),
                ),
            )

            // Act
            PrisinfoRepoAdapter.lagrePrisinfoEndring(
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
            PrisinfoRepoAdapter.lagrePrisinfoEndring(
                gjennomforingId = deltakerliste.id,
                prisinformasjon = ingenKostnader,
            )

            // Assert
            val result = PrisinfoRepoAdapter.hentPrisinfo(deltakerliste.id)
            result shouldBe ingenKostnader
        }

        @Test
        fun `erstatter eksisterende ENDRING med ny`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            val forste = Anskaffelse(pris = 10000)
            val forsteId = PrisinfoRepoAdapter.lagrePrisinfoEndring(
                gjennomforingId = deltakerliste.id,
                prisinformasjon = forste,
            )

            val andre = Anskaffelse(pris = 20000)

            // Act
            val andreId = PrisinfoRepoAdapter.lagrePrisinfoEndring(
                gjennomforingId = deltakerliste.id,
                prisinformasjon = andre,
            )

            // Assert — ny ID, og ny data er aktiv
            andreId shouldNotBe forsteId
            val result = PrisinfoRepoAdapter.hentPrisinfo(deltakerliste.id)
            result shouldBe andre
        }

        @Test
        fun `beholder GJELDENDE ved lagring av ny ENDRING`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            val gjeldende = Anskaffelse(pris = 5000)
            val prisinformasjonId = PrisinfoRepoAdapter.lagrePrisinfoForKladdOgUtkast(
                gjennomforingId = deltakerliste.id,
                prisinformasjon = gjeldende,
            )
            PrisinfoRepoAdapter.godkjennOkonomi(
                gjennomforingId = deltakerliste.id,
                prisinformasjonId = prisinformasjonId,
            )

            val endring = Anskaffelse(pris = 15000)

            // Act
            PrisinfoRepoAdapter.lagrePrisinfoEndring(
                gjennomforingId = deltakerliste.id,
                prisinformasjon = endring,
            )

            // Assert — hentPrisinfo (brukEndring=false) prioriterer GJELDENDE
            val result = PrisinfoRepoAdapter.hentPrisinfo(deltakerliste.id)
            result shouldBe gjeldende
        }
    }

    @Nested
    inner class TilbakekallPrisinfoEndringTests {
        @Test
        fun `tilbakekaller ENDRING og returnerer prisinformasjonId`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            val endring = Anskaffelse(pris = 20000)
            val prisinformasjonId = PrisinfoRepoAdapter.lagrePrisinfoEndring(
                gjennomforingId = deltakerliste.id,
                prisinformasjon = endring,
            )

            // Act
            val returnertId = PrisinfoRepoAdapter.tilbakekallPrisinfoEndring(deltakerliste.id)

            // Assert
            returnertId shouldBe prisinformasjonId
            PrisinfoRepoAdapter.hentPrisinfo(gjennomforingId = deltakerliste.id, brukEndring = true) shouldBe null
        }

        @Test
        fun `kaster exception naar ingen ENDRING finnes`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            // Act & Assert
            shouldThrow<IllegalArgumentException> {
                PrisinfoRepoAdapter.tilbakekallPrisinfoEndring(deltakerliste.id)
            }
        }

        @Test
        fun `paavirker ikke GJELDENDE naar ENDRING tilbakekalles`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            val gjeldende = Anskaffelse(pris = 5000)
            val gjeldendId = PrisinfoRepoAdapter.lagrePrisinfoForKladdOgUtkast(
                gjennomforingId = deltakerliste.id,
                prisinformasjon = gjeldende,
            )
            PrisinfoRepoAdapter.godkjennOkonomi(
                gjennomforingId = deltakerliste.id,
                prisinformasjonId = gjeldendId,
            )

            PrisinfoRepoAdapter.lagrePrisinfoEndring(
                gjennomforingId = deltakerliste.id,
                prisinformasjon = Anskaffelse(pris = 15000),
            )

            // Act
            PrisinfoRepoAdapter.tilbakekallPrisinfoEndring(deltakerliste.id)

            // Assert — GJELDENDE er urørt
            val result = PrisinfoRepoAdapter.hentPrisinfo(deltakerliste.id)
            result shouldBe gjeldende
        }
    }

    @Nested
    inner class LagrePrisinfoForKladdOgUtkastTests {
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
            PrisinfoRepoAdapter.lagrePrisinfoForKladdOgUtkast(
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
}
