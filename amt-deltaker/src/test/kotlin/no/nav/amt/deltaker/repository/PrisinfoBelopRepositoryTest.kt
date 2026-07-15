@file:Suppress("ktlint:standard:no-wildcard-imports")

package no.nav.amt.deltaker.repository

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
import java.util.UUID

class PrisinfoBelopRepositoryTest {
    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()

        private val gjennomforingInTest = lagDeltakerliste()

        private val prisinfoInTest = PrisinfoDbo(
            id = UUID.randomUUID(),
            gjennomforingId = gjennomforingInTest.id,
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
            PrisinfoRepository.insertPendingTotrinnskontrollPrisinfo(prisinfoInTest)

            // Act
            PrisinfoBelopRepository.lagrePrisinfoBelop(prisinfoInTest.id, emptySet())

            // Assert
            PrisinfoBelopRepository.hentPrisinfoBelop(prisinfoInTest.id).shouldBeEmpty()
        }

        @Test
        fun `flere prisinfo - lagrer alle`() {
            // Arrange
            TestRepository.insert(gjennomforingInTest)
            PrisinfoRepository.insertPendingTotrinnskontrollPrisinfo(prisinfoInTest)

            val prisinfos = setOf(
                Priskomponent(Tilskuddstype.SKOLEPENGER, 1),
                Priskomponent(Tilskuddstype.EKSAMENSGEBYR, 2),
                Priskomponent(Tilskuddstype.STUDIEREISE, 3),
                Priskomponent(Tilskuddstype.SEMESTERAVGIFT, 4),
                Priskomponent(Tilskuddstype.INTEGRERT_BOTILBUD, 5),
            )

            // Act
            PrisinfoBelopRepository.lagrePrisinfoBelop(prisinfoInTest.id, prisinfos)

            // Assert
            PrisinfoBelopRepository
                .hentPrisinfoBelop(prisinfoInTest.id)
                .shouldContainExactlyInAnyOrder(prisinfos)
        }
    }

    @Nested
    inner class HentPrisinfoliste {
        @Test
        fun `ingen prisinfo lagret - returnerer tomt sett`() {
            TestRepository.insert(gjennomforingInTest)
            PrisinfoRepository.insertPendingTotrinnskontrollPrisinfo(prisinfoInTest)

            // Act
            val resultat = PrisinfoBelopRepository.hentPrisinfoBelop(prisinfoInTest.id)

            // Assert
            resultat.shouldBeEmpty()
        }
    }

    @Nested
    inner class DeleteForGjennomforingTests {
        @Test
        fun `sletter alle prisinfo for deltakerliste hvor økonomi ikke er godkjent`() {
            // Arrange
            TestRepository.insert(gjennomforingInTest)
            PrisinfoRepository.insertPendingTotrinnskontrollPrisinfo(prisinfoInTest)

            PrisinfoBelopRepository.lagrePrisinfoBelop(
                prisinformasjonId = prisinfoInTest.id,
                belop = setOf(
                    Priskomponent(Tilskuddstype.SKOLEPENGER, 1),
                    Priskomponent(Tilskuddstype.EKSAMENSGEBYR, 2),
                ),
            )

            // Act
            PrisinfoRepository.deletePrisinfo(
                gjennomforingId = gjennomforingInTest.id,
                okonomiGodkjent = false,
            )

            // Assert
            PrisinfoBelopRepository.hentPrisinfoBelop(prisinfoInTest.id).shouldBeEmpty()
        }

        @Test
        fun `sletter ikke prisinfo for andre deltakerlister`() {
            // Arrange
            TestRepository.insert(gjennomforingInTest)
            PrisinfoRepository.insertPendingTotrinnskontrollPrisinfo(prisinfoInTest)

            val gjennomforing2 = lagDeltakerliste()
            TestRepository.insert(gjennomforing2)

            val prisinfo2 = prisinfoInTest.copy(
                id = UUID.randomUUID(),
                gjennomforingId = gjennomforing2.id,
            )
            PrisinfoRepository.insertPendingTotrinnskontrollPrisinfo(prisinfo2)

            val prisinfos = setOf(
                Priskomponent(Tilskuddstype.SKOLEPENGER, 1),
                Priskomponent(Tilskuddstype.EKSAMENSGEBYR, 2),
            )

            PrisinfoBelopRepository.lagrePrisinfoBelop(
                prisinformasjonId = prisinfoInTest.id,
                belop = prisinfos,
            )

            PrisinfoBelopRepository.lagrePrisinfoBelop(
                prisinformasjonId = prisinfo2.id,
                belop = prisinfos,
            )

            // Act
            PrisinfoRepository.deletePrisinfo(
                gjennomforingId = gjennomforingInTest.id,
                okonomiGodkjent = false,
            )

            // Assert
            PrisinfoBelopRepository.hentPrisinfoBelop(prisinfoInTest.id).shouldBeEmpty()
            PrisinfoBelopRepository.hentPrisinfoBelop(prisinfo2.id) shouldBe prisinfos
        }
    }
}
