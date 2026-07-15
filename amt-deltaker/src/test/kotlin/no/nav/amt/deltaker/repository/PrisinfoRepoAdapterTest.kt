@file:Suppress("ktlint:standard:no-wildcard-imports")

package no.nav.amt.deltaker.repository

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.repository.PrisinfoRepoAdapter.toPrisinfoDbo
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
        fun `henter Anskaffelse prisinfo med okonomiGodkjent false`() {
            // Arrange
            TestRepository.insert(gjennomforingInTest)

            val anskaffelse = Anskaffelse(pris = 25000)
            PrisinfoRepoAdapter.lagrePrisinfo(gjennomforingInTest.id, anskaffelse)

            // Act
            val result = PrisinfoRepoAdapter.hentPrisinfo(gjennomforingInTest.id)

            // Assert
            result shouldBe anskaffelse
        }

        @Test
        fun `henter Tilskudd prisinfo med okonomiGodkjent false`() {
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
            PrisinfoRepoAdapter.lagrePrisinfo(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjon = tilskudd,
            )

            // Act
            val result = PrisinfoRepoAdapter.hentPrisinfo(gjennomforingInTest.id)

            // Assert
            result shouldBe tilskudd
        }

        @Test
        fun `henter IngenKostnader prisinfo med okonomiGodkjent false`() {
            // Arrange
            TestRepository.insert(gjennomforingInTest)

            val ingenKostnader = IngenKostnader(
                aarsak = Aarsak.OPPLAERINGEN_ER_KOSTNADSFRI,
                tilleggsopplysninger = "Gratis opplaering",
            )

            PrisinfoRepoAdapter.lagrePrisinfo(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjon = ingenKostnader,
            )

            // Act
            val result = PrisinfoRepoAdapter.hentPrisinfo(gjennomforingInTest.id)

            // Assert
            result shouldBe ingenKostnader
        }

        @Test
        fun `returnerer prisinfo med okonomiGodkjent true når den finnes`() {
            // Arrange
            TestRepository.insert(gjennomforingInTest)

            val pendingPrisinfo = Anskaffelse(pris = 15000)
            PrisinfoRepoAdapter.lagrePrisinfo(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjon = pendingPrisinfo,
            )

            PrisinfoRepository.settGodkjent(gjennomforingInTest.id)

            // Act
            val result = PrisinfoRepoAdapter.hentPrisinfo(gjennomforingInTest.id)

            result shouldBe pendingPrisinfo
        }

        @Test
        fun `prioriterer okonomiGodkjent true når begge finnes`() {
            // Arrange
            TestRepository.insert(gjennomforingInTest)

            val trueVariant = Tilskudd(
                tilleggsopplysninger = "Pending",
                tilskudd = listOf(
                    TilskuddInfo(type = Tilskuddstype.SKOLEPENGER, pris = 5000),
                ),
            )
            PrisinfoRepoAdapter.lagrePrisinfo(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjon = trueVariant,
            )

            PrisinfoRepository.settGodkjent(gjennomforingInTest.id)

            val falseVariant = Tilskudd(
                tilleggsopplysninger = "Godkjent",
                tilskudd = listOf(
                    TilskuddInfo(type = Tilskuddstype.SKOLEPENGER, pris = 10000),
                ),
            )

            PrisinfoRepository.insertPendingTotrinnskontrollPrisinfo(
                falseVariant.toPrisinfoDbo(gjennomforingInTest.id),
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
    }

    @Nested
    inner class HarPrisinfoSomVenterPaaOkonomiGodkjentTests {
        @Test
        fun `returnerer true når prisinfo med okonomiGodkjent false eksisterer og ID matcher`() {
            // Arrange
            TestRepository.insert(gjennomforingInTest)

            val anskaffelse = Anskaffelse(pris = 25000)
            PrisinfoRepoAdapter.lagrePrisinfo(gjennomforingInTest.id, anskaffelse)

            val prisinfo = PrisinfoRepository.hentPrisinfo(
                gjennomforingId = gjennomforingInTest.id,
                okonomiGodkjent = false,
            )!!

            // Act & Assert
            PrisinfoRepoAdapter.harPrisinfoSomVenterPaaOkonomiGodkjent(
                gjennomforingId = gjennomforingInTest.id,
                prisinfoId = prisinfo.id,
            ) shouldBe true
        }

        @Test
        fun `returnerer false når prisinfo med okonomiGodkjent false eksisterer men ID matcher ikke`() {
            // Arrange
            TestRepository.insert(gjennomforingInTest)

            PrisinfoRepoAdapter.lagrePrisinfo(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjon = Anskaffelse(pris = 25000),
            )

            // Act & Assert
            PrisinfoRepoAdapter.harPrisinfoSomVenterPaaOkonomiGodkjent(
                gjennomforingId = gjennomforingInTest.id,
                prisinfoId = UUID.randomUUID(),
            ) shouldBe false
        }

        @Test
        fun `returnerer false når kun prisinfo med okonomiGodkjent true finnes`() {
            // Arrange
            TestRepository.insert(gjennomforingInTest)

            val godkjentPrisinfo = Anskaffelse(pris = 20000)
            val result = PrisinfoRepository.insertPendingTotrinnskontrollPrisinfo(
                godkjentPrisinfo.toPrisinfoDbo(gjennomforingInTest.id),
            )

            PrisinfoRepository.settGodkjent(gjennomforingInTest.id)

            // Act & Assert
            PrisinfoRepoAdapter.harPrisinfoSomVenterPaaOkonomiGodkjent(
                gjennomforingId = gjennomforingInTest.id,
                prisinfoId = result.id,
            ) shouldBe false
        }

        @Test
        fun `returnerer false når prisinfo ikke finnes`() {
            // Arrange
            TestRepository.insert(gjennomforingInTest)

            // Act & Assert
            PrisinfoRepoAdapter.harPrisinfoSomVenterPaaOkonomiGodkjent(
                gjennomforingId = gjennomforingInTest.id,
                prisinfoId = UUID.randomUUID(),
            ) shouldBe false
        }
    }

    @Nested
    inner class GodkjennOkonomiTests {
        @Test
        fun `godkjennOkonomi setter okonomiGodkjent fra false til true`() {
            // Arrange
            TestRepository.insert(gjennomforingInTest)

            val anskaffelse = Anskaffelse(pris = 25000)
            PrisinfoRepoAdapter.lagrePrisinfo(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjon = anskaffelse,
            )

            val beforeGodkjenning = PrisinfoRepository.hentPrisinfo(
                gjennomforingId = gjennomforingInTest.id,
                okonomiGodkjent = false,
            )
            beforeGodkjenning?.okonomiGodkjent shouldBe false

            // Act
            PrisinfoRepoAdapter.godkjennOkonomi(gjennomforingInTest.id)

            // Assert
            val afterGodkjenning = PrisinfoRepository.hentPrisinfo(
                gjennomforingId = gjennomforingInTest.id,
                okonomiGodkjent = true,
            )
            afterGodkjenning.shouldNotBeNull().okonomiGodkjent shouldBe true

            PrisinfoRepository
                .hentPrisinfo(
                    gjennomforingId = gjennomforingInTest.id,
                    okonomiGodkjent = false,
                ).shouldBeNull()
        }

        @Test
        fun `godkjennOkonomi sletter eksisterende godkjent prisinfo når den finnes`() {
            // Arrange
            TestRepository.insert(gjennomforingInTest)

            val gammelGodkjent = Anskaffelse(pris = 10000)
            PrisinfoRepository.insertPendingTotrinnskontrollPrisinfo(
                insertDbo = gammelGodkjent.toPrisinfoDbo(gjennomforingInTest.id),
            )
            PrisinfoRepository.settGodkjent(gjennomforingInTest.id)

            val nyPending = Anskaffelse(pris = 15000)
            PrisinfoRepoAdapter.lagrePrisinfo(gjennomforingInTest.id, nyPending)

            // Act
            PrisinfoRepoAdapter.godkjennOkonomi(gjennomforingInTest.id)

            // Assert
            val nyGodkjent = PrisinfoRepository.hentPrisinfo(
                gjennomforingId = gjennomforingInTest.id,
                okonomiGodkjent = true,
            )
            nyGodkjent.shouldNotBeNull().anskaffelsePris shouldBe 15000

            PrisinfoRepository
                .hentPrisinfo(
                    gjennomforingId = gjennomforingInTest.id,
                    okonomiGodkjent = false,
                ).shouldBeNull()
        }

        @Test
        fun `godkjennOkonomi gjor ingenting når ingen prisinfo finnes`() {
            // Arrange
            TestRepository.insert(gjennomforingInTest)

            // Act & Assert - skal ikke kaste exception
            PrisinfoRepoAdapter.godkjennOkonomi(gjennomforingInTest.id)

            PrisinfoRepository
                .hentPrisinfo(
                    gjennomforingId = gjennomforingInTest.id,
                    okonomiGodkjent = true,
                ).shouldBeNull()

            PrisinfoRepository
                .hentPrisinfo(
                    gjennomforingId = gjennomforingInTest.id,
                    okonomiGodkjent = false,
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
            PrisinfoRepoAdapter.lagrePrisinfo(
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
            PrisinfoRepoAdapter.lagrePrisinfo(
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
