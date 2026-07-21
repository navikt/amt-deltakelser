@file:Suppress("ktlint:standard:no-wildcard-imports")

package no.nav.amt.deltaker.repository

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.repository.dbo.PrisinfoUpsertDbo
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
    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()

        private val gjennomforingInTest = lagDeltakerliste()

        private val prisinfoUpsertDboInTest = PrisinfoUpsertDbo(
            prisinfoJsonSubtype = ANSKAFFELSE_SUB_TYPE,
            anskaffelsePris = 15000,
            tilleggsopplysninger = "Standard opplysning",
            ingenkostnaderAarsak = null,
        )
    }

    @Nested
    inner class LagrePrisinfoliste {
        @Test
        fun `tom liste - lagrer ingenting`() {
            // Arrange
            TestRepository.insert(gjennomforingInTest)
            PrisinfoRepository.upsertPrisinfo(prisinfoUpsertDboInTest)

            // Act
            PrisinfoBelopRepository.lagrePrisinfoBelop(
                prisinformasjonId = prisinfoUpsertDboInTest.id,
                belop = emptySet(),
            )

            // Assert
            PrisinfoBelopRepository.hentPrisinfoBelop(prisinfoUpsertDboInTest.id).shouldBeEmpty()
        }

        @Test
        fun `flere prisinfo - lagrer alle`() {
            // Arrange
            TestRepository.insert(gjennomforingInTest)
            PrisinfoRepository.upsertPrisinfo(prisinfoUpsertDboInTest)

            val prisinfos = setOf(
                Priskomponent(Tilskuddstype.SKOLEPENGER, 1),
                Priskomponent(Tilskuddstype.EKSAMENSGEBYR, 2),
                Priskomponent(Tilskuddstype.STUDIEREISE, 3),
                Priskomponent(Tilskuddstype.SEMESTERAVGIFT, 4),
                Priskomponent(Tilskuddstype.INTEGRERT_BOTILBUD, 5),
            )

            // Act
            PrisinfoBelopRepository.lagrePrisinfoBelop(
                prisinformasjonId = prisinfoUpsertDboInTest.id,
                belop = prisinfos,
            )

            // Assert
            PrisinfoBelopRepository
                .hentPrisinfoBelop(prisinfoUpsertDboInTest.id)
                .shouldContainExactlyInAnyOrder(prisinfos)
        }
    }

    @Nested
    inner class HentPrisinfoliste {
        @Test
        fun `ingen prisinfo lagret - returnerer tomt sett`() {
            TestRepository.insert(gjennomforingInTest)
            PrisinfoRepository.upsertPrisinfo(prisinfoUpsertDboInTest)

            // Act
            val resultat = PrisinfoBelopRepository.hentPrisinfoBelop(prisinfoUpsertDboInTest.id)

            // Assert
            resultat.shouldBeEmpty()
        }
    }

    @Nested
    inner class DeleteForPrisinfoTests {
        @Test
        fun `ingen prisinfo lagret - kaster ikke exception`() {
            // Act & Assert
            shouldNotThrowAny {
                PrisinfoBelopRepository.deleteForPrisinfo(prisinfoUpsertDboInTest.id)
            }
        }

        @Test
        fun `prisinfo lagret - sletter alle`() {
            // Arrange
            TestRepository.insert(gjennomforingInTest)
            PrisinfoRepository.upsertPrisinfo(prisinfoUpsertDboInTest)

            val prisinfos = setOf(
                Priskomponent(Tilskuddstype.SKOLEPENGER, 1),
                Priskomponent(Tilskuddstype.EKSAMENSGEBYR, 2),
                Priskomponent(Tilskuddstype.STUDIEREISE, 3),
                Priskomponent(Tilskuddstype.SEMESTERAVGIFT, 4),
                Priskomponent(Tilskuddstype.INTEGRERT_BOTILBUD, 5),
            )

            PrisinfoBelopRepository.lagrePrisinfoBelop(
                prisinformasjonId = prisinfoUpsertDboInTest.id,
                belop = prisinfos,
            )

            // Act
            val antallRaderSlettet = PrisinfoBelopRepository.deleteForPrisinfo(prisinfoUpsertDboInTest.id)

            // Assert
            antallRaderSlettet shouldBe prisinfos.size
        }
    }
}
