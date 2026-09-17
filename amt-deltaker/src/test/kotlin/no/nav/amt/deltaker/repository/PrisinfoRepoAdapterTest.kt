@file:Suppress("ktlint:standard:no-wildcard-imports")

package no.nav.amt.deltaker.repository

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotliquery.queryOf
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.repository.dbo.PrisinfoDbo
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestData.lagVedtak
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.internapi.deltaker.request.EndretPrisinfoRequest
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Anskaffelse
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto.IngenKostnader
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto.IngenKostnader.Aarsak
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd.TilskuddInfo
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd.Tilskuddstype
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.utils.TestData.lagNavAnsatt
import no.nav.amt.lib.testing.utils.TestData.lagNavEnhet
import no.nav.amt.lib.utils.database.Database
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDateTime
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
        fun `skal returnere null naar prisinfo ikke finnes`() {
            // Arrange
            TestRepository.insert(gjennomforingInTest)

            // Act & Assert
            PrisinfoRepoAdapter.hentPrisinfo(gjennomforingInTest.id) shouldBe null
        }

        @Test
        fun `med rolle = ENDRING - henter kun endring-prisinfo`() {
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
                rolle = PrisinfoDbo.Rolle.ENDRING,
            )

            // Assert
            result shouldBe null
        }

        @Test
        fun `med rolle = ENDRING - returnerer endring når begge finnes`() {
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
                rolle = PrisinfoDbo.Rolle.ENDRING,
            )

            // Assert
            result shouldBe pendingPrisinfo
        }

        @Test
        fun `med rolle = ENDRING - returnerer null når kun gjeldende finnes`() {
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
            val endring = PrisinfoRepoAdapter.hentPrisinfo(
                gjennomforingId = gjennomforingInTest.id,
                rolle = PrisinfoDbo.Rolle.ENDRING,
            )

            // Assert
            endring shouldBe null
        }

        @Test
        fun `med rolle = GJELDENDE - returnerer gjeldende`() {
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

            val gjeldende = PrisinfoRepoAdapter.hentPrisinfo(
                gjennomforingId = gjennomforingInTest.id,
                rolle = PrisinfoDbo.Rolle.GJELDENDE,
            )

            // Assert
            gjeldende shouldBe godkjentPrisinfo
        }

        @Test
        fun `med rolle = null (default) - prioriterer gjeldende`() {
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
                rolle = null,
            )

            // Assert
            result shouldBe godkjentPrisinfo
        }
    }

    @Nested
    inner class ErUendretPrisinfoTests {
        @Test
        fun `returnerer true når pending prisinfo er identisk`() {
            // Arrange
            TestRepository.insert(gjennomforingInTest)

            val prisinfo = Anskaffelse(pris = 15000)
            PrisinfoRepoAdapter.lagrePrisinfoForKladdOgUtkast(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjon = prisinfo,
            )

            val request = EndretPrisinfoRequest(
                endretAv = "Z123456",
                endretAvEnhet = "1234",
                prisinfo = prisinfo,
                begrunnelse = "begrunnelse",
            )

            // Act + Assert
            PrisinfoRepoAdapter.erUendretPrisinfo(
                gjennomforingId = gjennomforingInTest.id,
                endringRequest = request,
            ) shouldBe true
        }

        @Test
        fun `returnerer true når kun gjeldende prisinfo er identisk`() {
            // Arrange
            TestRepository.insert(gjennomforingInTest)

            val prisinfo = Tilskudd(
                tilleggsopplysninger = "Godkjent",
                tilskudd = listOf(
                    TilskuddInfo(type = Tilskuddstype.SKOLEPENGER, pris = 5000),
                ),
            )
            val prisinformasjonId = PrisinfoRepoAdapter.lagrePrisinfoForKladdOgUtkast(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjon = prisinfo,
            )
            PrisinfoRepoAdapter.godkjennOkonomi(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjonId = prisinformasjonId,
            )

            val request = EndretPrisinfoRequest(
                endretAv = "Z123456",
                endretAvEnhet = "1234",
                prisinfo = prisinfo,
                begrunnelse = "begrunnelse",
            )

            // Act + Assert
            PrisinfoRepoAdapter.erUendretPrisinfo(
                gjennomforingId = gjennomforingInTest.id,
                endringRequest = request,
            ) shouldBe true
        }

        @Test
        fun `returnerer false når pending prisinfo er forskjellig selv om gjeldende er lik`() {
            // Arrange
            TestRepository.insert(gjennomforingInTest)

            val gjeldendePrisinfo = Anskaffelse(pris = 10000)
            val prisinformasjonId = PrisinfoRepoAdapter.lagrePrisinfoForKladdOgUtkast(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjon = gjeldendePrisinfo,
            )
            PrisinfoRepoAdapter.godkjennOkonomi(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjonId = prisinformasjonId,
            )

            val annenPrisinfo = Anskaffelse(pris = 20000)
            PrisinfoRepoAdapter.lagrePrisinfoForKladdOgUtkast(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjon = annenPrisinfo,
            )

            val request = EndretPrisinfoRequest(
                endretAv = "Z123456",
                endretAvEnhet = "1234",
                prisinfo = gjeldendePrisinfo,
                begrunnelse = "begrunnelse",
            )

            // Act + Assert
            PrisinfoRepoAdapter.erUendretPrisinfo(
                gjennomforingId = gjennomforingInTest.id,
                endringRequest = request,
            ) shouldBe false
        }

        @Test
        fun `ignorerer pending med status RETURNERT og sammenligner mot gjeldende`() {
            // Arrange
            TestRepository.insert(gjennomforingInTest)

            val gjeldendePrisinfo = Anskaffelse(pris = 10000)
            val gjeldendePrisinfoId = PrisinfoRepoAdapter.lagrePrisinfoForKladdOgUtkast(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjon = gjeldendePrisinfo,
            )
            PrisinfoRepoAdapter.godkjennOkonomi(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjonId = gjeldendePrisinfoId,
            )

            val returnertPrisinfo = Anskaffelse(pris = 20000)
            val returnertPrisinfoId = PrisinfoRepoAdapter.lagrePrisinfoForKladdOgUtkast(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjon = returnertPrisinfo,
            )
            PrisinfoRepository.oppdaterStatus(
                prisinformasjonId = returnertPrisinfoId,
                status = PrisinfoDbo.PrisinfoStatus.RETURNERT,
            )

            val request = EndretPrisinfoRequest(
                endretAv = "Z123456",
                endretAvEnhet = "1234",
                prisinfo = gjeldendePrisinfo,
                begrunnelse = "begrunnelse",
            )

            // Act + Assert
            PrisinfoRepoAdapter.erUendretPrisinfo(
                gjennomforingId = gjennomforingInTest.id,
                endringRequest = request,
            ) shouldBe true
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

        @Test
        fun `godkjennOkonomi returnerer false for historisk prisinformasjonId og lar mappinger vaere uendret`() {
            // Arrange
            TestRepository.insert(gjennomforingInTest)

            val gjeldendePrisinfo = Anskaffelse(pris = 10000)
            val gjeldendePrisinfoId = PrisinfoRepoAdapter.lagrePrisinfoForKladdOgUtkast(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjon = gjeldendePrisinfo,
            )
            PrisinfoRepoAdapter.godkjennOkonomi(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjonId = gjeldendePrisinfoId,
            )

            val endringPrisinfo = Anskaffelse(pris = 20000)
            PrisinfoRepoAdapter.lagrePrisinfoEndring(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjon = endringPrisinfo,
            )
            val aktivEndringId = Deltakerliste2PrisinfoRepository.hentPrisinformasjonIdForEndring(gjennomforingInTest.id)
            val stalePrisinfoId = UUID.randomUUID()

            val endringFoer = PrisinfoRepository
                .hentPrisinfo(
                    gjennomforingId = gjennomforingInTest.id,
                    rolle = PrisinfoDbo.Rolle.ENDRING,
                ).shouldNotBeNull()
            val gjeldendeFoer = PrisinfoRepository
                .hentPrisinfo(
                    gjennomforingId = gjennomforingInTest.id,
                    rolle = PrisinfoDbo.Rolle.GJELDENDE,
                ).shouldNotBeNull()

            // Act
            val result = PrisinfoRepoAdapter.godkjennOkonomi(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjonId = stalePrisinfoId,
            )

            // Assert
            result shouldBe false
            Deltakerliste2PrisinfoRepository.hentPrisinformasjonIdForEndring(gjennomforingInTest.id) shouldBe aktivEndringId

            val endringEtter = PrisinfoRepository
                .hentPrisinfo(
                    gjennomforingId = gjennomforingInTest.id,
                    rolle = PrisinfoDbo.Rolle.ENDRING,
                ).shouldNotBeNull()
            val gjeldendeEtter = PrisinfoRepository
                .hentPrisinfo(
                    gjennomforingId = gjennomforingInTest.id,
                    rolle = PrisinfoDbo.Rolle.GJELDENDE,
                ).shouldNotBeNull()

            endringEtter.id shouldBe endringFoer.id
            endringEtter.status shouldBe endringFoer.status
            gjeldendeEtter.id shouldBe gjeldendeFoer.id
            gjeldendeEtter.status shouldBe gjeldendeFoer.status
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
            PrisinfoRepoAdapter.hentPrisinfo(
                gjennomforingId = deltakerliste.id,
                rolle = PrisinfoDbo.Rolle.ENDRING,
            ) shouldBe null
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
                rolle = PrisinfoDbo.Rolle.ENDRING,
            )

            result shouldBe ingenKostnader
        }
    }

    @Nested
    inner class HentGodkjentPrisinfoForHistorikkTests {
        private val navEnhet = lagNavEnhet()
        private val navAnsatt = lagNavAnsatt()
        private val deltakerliste = lagDeltakerliste()
        private val deltaker = lagDeltaker(
            deltakerliste = deltakerliste,
            status = lagDeltakerStatus(statusType = DeltakerStatus.Type.DELTAR),
        )
        private val vedtakFattet = LocalDateTime.now()

        @BeforeEach
        fun beforeEach() {
            NavEnhetRepository().upsert(navEnhet)
            TestRepository.insert(navAnsatt)
            TestRepository.insert(
                deltaker,
                lagVedtak(
                    deltakerId = deltaker.id,
                    deltakerVedVedtak = deltaker,
                    fattet = vedtakFattet,
                    opprettetAv = navAnsatt,
                    opprettetAvEnhet = navEnhet,
                    sistEndretAv = navAnsatt,
                    sistEndretAvEnhet = navEnhet,
                ),
            )
        }

        @Test
        fun `mapper godkjent tilskudd med belop til prisinformasjon`() {
            // Arrange
            val tilskudd = Tilskudd(
                tilleggsopplysninger = "Tilskuddsinformasjon",
                tilskudd = listOf(
                    TilskuddInfo(type = Tilskuddstype.SKOLEPENGER, pris = 8000),
                    TilskuddInfo(type = Tilskuddstype.EKSAMENSGEBYR, pris = 2000),
                ),
            )
            val prisinformasjonId = PrisinfoRepoAdapter.lagrePrisinfoForKladdOgUtkast(
                gjennomforingId = deltakerliste.id,
                prisinformasjon = tilskudd,
            )
            PrisinfoRepoAdapter.godkjennOkonomi(
                gjennomforingId = deltakerliste.id,
                prisinformasjonId = prisinformasjonId,
            )
            settGodkjenningstidspunkt(prisinformasjonId, vedtakFattet.minusMinutes(1))

            // Act
            val result = PrisinfoRepoAdapter.hentGodkjentPrisinfoForHistorikkEldsteForst(deltaker.id)

            // Assert
            result.size shouldBe 1
            result.first().prisinformasjon shouldBe tilskudd
        }

        @Test
        fun `skiller forste godkjenning fra senere prisendring`() {
            // Arrange - forste godkjenning skjedde da vedtaket ble fattet
            val forsteId = PrisinfoRepoAdapter.lagrePrisinfoForKladdOgUtkast(
                gjennomforingId = deltakerliste.id,
                prisinformasjon = Anskaffelse(pris = 10000),
            )
            PrisinfoRepoAdapter.godkjennOkonomi(deltakerliste.id, forsteId)
            settGodkjenningstidspunkt(forsteId, vedtakFattet.minusMinutes(1))

            // Arrange - prisendringen ble godkjent etter at vedtaket var fattet
            val endringId = PrisinfoRepoAdapter.lagrePrisinfoEndring(
                gjennomforingId = deltakerliste.id,
                prisinformasjon = Anskaffelse(pris = 20000),
            )
            PrisinfoRepoAdapter.godkjennOkonomi(deltakerliste.id, endringId)
            settGodkjenningstidspunkt(endringId, vedtakFattet.plusMinutes(1))

            // Act
            val result = PrisinfoRepoAdapter.hentGodkjentPrisinfoForHistorikkEldsteForst(deltaker.id)

            // Assert
            result.size shouldBe 2
            result[0].erForsteGodkjenning shouldBe true
            result[0].prisinformasjon shouldBe Anskaffelse(pris = 10000)
            result[1].erForsteGodkjenning shouldBe false
            result[1].prisinformasjon shouldBe Anskaffelse(pris = 20000)
        }

        private fun settGodkjenningstidspunkt(prisinformasjonId: UUID, tidspunkt: LocalDateTime) = Database.query { session ->
            session.update(
                queryOf(
                    "UPDATE enkeltplass_prisinformasjon SET modified_at = ? WHERE id = ?",
                    tidspunkt,
                    prisinformasjonId,
                ),
            )
        }
    }
}
