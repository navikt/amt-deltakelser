@file:Suppress("ktlint:standard:no-wildcard-imports")

package no.nav.amt.deltaker.repository

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.repository.dbo.PrisinfoDbo
import no.nav.amt.deltaker.repository.dbo.Priskomponent
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.models.deltaker.ANSKAFFELSE_SUB_TYPE
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd.Tilskuddstype
import no.nav.amt.lib.testing.DatabaseTestExtension
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class PrisinfoBelopRepositoryTest {
    private companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()

        val gjennomforingInTest = lagDeltakerliste()
    }

    @Nested
    inner class LagrePrisinfoliste {
        @Test
        fun `tom liste - lagrer ingenting`() {
            // Arrange
            TestRepository.insert(gjennomforingInTest)
            PrisinfoRepository.upsertPrisinfo(prisinfoDboInTest)

            // Act
            PrisinfoBelopRepository.lagrePrisinfoBelop(
                prisinformasjonId = prisinfoDboInTest.id,
                belop = emptySet(),
            )

            // Assert
            PrisinfoBelopRepository.hentPrisinfoBelop(prisinfoDboInTest.id).shouldBeEmpty()
        }

        @Test
        fun `flere prisinfo - lagrer alle`() {
            // Arrange
            TestRepository.insert(gjennomforingInTest)
            PrisinfoRepository.upsertPrisinfo(prisinfoDboInTest)

            val prisinfos = setOf(
                Priskomponent(Tilskuddstype.SKOLEPENGER, 1),
                Priskomponent(Tilskuddstype.EKSAMENSGEBYR, 2),
                Priskomponent(Tilskuddstype.STUDIEREISE, 3),
                Priskomponent(Tilskuddstype.SEMESTERAVGIFT, 4),
                Priskomponent(Tilskuddstype.INTEGRERT_BOTILBUD, 5),
            )

            // Act
            PrisinfoBelopRepository.lagrePrisinfoBelop(
                prisinformasjonId = prisinfoDboInTest.id,
                belop = prisinfos,
            )

            // Assert
            PrisinfoBelopRepository
                .hentPrisinfoBelop(prisinfoDboInTest.id)
                .shouldContainExactlyInAnyOrder(prisinfos)
        }
    }

    @Nested
    inner class HentPrisinfoliste {
        @Test
        fun `ingen prisinfo lagret - returnerer tomt sett`() {
            TestRepository.insert(gjennomforingInTest)
            PrisinfoRepository.upsertPrisinfo(prisinfoDboInTest)

            // Act
            val resultat = PrisinfoBelopRepository.hentPrisinfoBelop(prisinfoDboInTest.id)

            // Assert
            resultat.shouldBeEmpty()
        }
    }

    @Nested
    inner class HentPrisinfoBelopBatchTests {
        @Test
        fun `tom liste - returnerer tomt map uten databasekall`() {
            PrisinfoBelopRepository.hentPrisinfoBelop(emptyList()) shouldBe emptyMap()
        }

        @Test
        fun `flere prisinfoer - grupperer komponenter per prisinfo-id`() {
            // Arrange
            TestRepository.insert(gjennomforingInTest)

            val prisinfoA = PrisinfoDbo(
                gjennomforingId = gjennomforingInTest.id,
                prisinfoJsonSubtype = ANSKAFFELSE_SUB_TYPE,
                anskaffelsePris = 1000,
            )
            val prisinfoB = PrisinfoDbo(
                gjennomforingId = gjennomforingInTest.id,
                prisinfoJsonSubtype = ANSKAFFELSE_SUB_TYPE,
                anskaffelsePris = 2000,
            )
            val prisinfoUtenBelop = PrisinfoDbo(
                gjennomforingId = gjennomforingInTest.id,
                prisinfoJsonSubtype = ANSKAFFELSE_SUB_TYPE,
                anskaffelsePris = 3000,
            )
            PrisinfoRepository.upsertPrisinfo(prisinfoA)
            PrisinfoRepository.upsertPrisinfo(prisinfoB)
            PrisinfoRepository.upsertPrisinfo(prisinfoUtenBelop)

            val belopA = setOf(
                Priskomponent(Tilskuddstype.SKOLEPENGER, 1),
                Priskomponent(Tilskuddstype.EKSAMENSGEBYR, 2),
            )
            val belopB = setOf(Priskomponent(Tilskuddstype.STUDIEREISE, 3))
            PrisinfoBelopRepository.lagrePrisinfoBelop(prisinfoA.id, belopA)
            PrisinfoBelopRepository.lagrePrisinfoBelop(prisinfoB.id, belopB)

            // Act
            val resultat = PrisinfoBelopRepository.hentPrisinfoBelop(
                listOf(prisinfoA.id, prisinfoB.id, prisinfoUtenBelop.id),
            )

            // Assert - kun prisinfoer med komponenter får nøkkel
            resultat.keys shouldContainExactlyInAnyOrder listOf(prisinfoA.id, prisinfoB.id)
            resultat[prisinfoA.id].orEmpty() shouldContainExactlyInAnyOrder belopA.toList()
            resultat[prisinfoB.id].orEmpty() shouldContainExactlyInAnyOrder belopB.toList()
        }
    }

    @Nested
    inner class DeleteForPrisinfoTests {
        @Test
        fun `ingen prisinfo lagret - kaster ikke exception`() {
            // Act & Assert
            shouldNotThrowAny {
                PrisinfoBelopRepository.deleteForPrisinfo(prisinfoDboInTest.id)
            }
        }

        @Test
        fun `prisinfo lagret - sletter alle`() {
            // Arrange
            TestRepository.insert(gjennomforingInTest)
            PrisinfoRepository.upsertPrisinfo(prisinfoDboInTest)

            val prisinfos = setOf(
                Priskomponent(Tilskuddstype.SKOLEPENGER, 1),
                Priskomponent(Tilskuddstype.EKSAMENSGEBYR, 2),
                Priskomponent(Tilskuddstype.STUDIEREISE, 3),
                Priskomponent(Tilskuddstype.SEMESTERAVGIFT, 4),
                Priskomponent(Tilskuddstype.INTEGRERT_BOTILBUD, 5),
            )

            PrisinfoBelopRepository.lagrePrisinfoBelop(
                prisinformasjonId = prisinfoDboInTest.id,
                belop = prisinfos,
            )

            // Act
            val antallRaderSlettet = PrisinfoBelopRepository.deleteForPrisinfo(prisinfoDboInTest.id)

            // Assert
            antallRaderSlettet shouldBe prisinfos.size
        }
    }

    private companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()

        val gjennomforingInTest = lagDeltakerliste()

        val prisinfoDboInTest = PrisinfoDbo(
            gjennomforingId = gjennomforingInTest.id,
            prisinfoJsonSubtype = ANSKAFFELSE_SUB_TYPE,
            anskaffelsePris = 15000,
            tilleggsopplysninger = "Standard opplysning",
            ingenkostnaderAarsak = null,
        )
    }
}
