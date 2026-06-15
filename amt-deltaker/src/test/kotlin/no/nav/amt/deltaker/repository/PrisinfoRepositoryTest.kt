@file:Suppress("ktlint:standard:no-wildcard-imports")

package no.nav.amt.deltaker.repository

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.models.deltakerliste.Priskomponent
import no.nav.amt.lib.testing.DatabaseTestExtension
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class PrisinfoRepositoryTest {
    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Nested
    inner class LagrePrisinfoliste {
        @Test
        fun `tom liste - lagrer ingenting`() {
            // Arrange
            val gjennomforing = lagDeltakerliste()
            TestRepository.insert(gjennomforing)

            // Act
            PrisinfoRepository.lagrePrisinfos(gjennomforing.id, emptySet())

            // Assert
            PrisinfoRepository.hentPrisinfos(gjennomforing.id).shouldBeEmpty()
        }

        @Test
        fun `flere sertifiseringer - lagrer alle`() {
            // Arrange
            val gjennomforing = lagDeltakerliste()
            TestRepository.insert(gjennomforing)

            val prisinfos = setOf(
                Priskomponent(Priskomponent.Pristype.SKOLEPENGER, 1U),
                Priskomponent(Priskomponent.Pristype.EKSAMENSGEBYR, 2U),
                Priskomponent(Priskomponent.Pristype.STUDIEREISE, 3U),
                Priskomponent(Priskomponent.Pristype.SEMESTERAVGIFT, 4U),
                Priskomponent(Priskomponent.Pristype.INTEGRERT_BOTILBUD, 5U),
            )

            // Act
            PrisinfoRepository.lagrePrisinfos(gjennomforing.id, prisinfos)

            // Assert
            PrisinfoRepository
                .hentPrisinfos(gjennomforing.id)
                .shouldContainExactlyInAnyOrder(prisinfos)
        }
    }

    @Nested
    inner class HentPrisinfoliste {
        @Test
        fun `ingen prisinfo lagret - returnerer tomt sett`() {
            // Arrange
            val gjennomforing = lagDeltakerliste()
            TestRepository.insert(gjennomforing)

            // Act
            val resultat = PrisinfoRepository.hentPrisinfos(gjennomforing.id)

            // Assert
            resultat.shouldBeEmpty()
        }

        @Test
        fun `henter kun for angitt deltakerliste`() {
            // Arrange
            val gjennomforing1 = lagDeltakerliste()
            TestRepository.insert(gjennomforing1)

            val gjenomforing2 = lagDeltakerliste()
            TestRepository.insert(gjenomforing2)

            val prisinfos = setOf(
                Priskomponent(Priskomponent.Pristype.SKOLEPENGER, 1U),
                Priskomponent(Priskomponent.Pristype.EKSAMENSGEBYR, 2U),
            )

            PrisinfoRepository.lagrePrisinfos(
                gjennomforingId = gjennomforing1.id,
                prisinfos = prisinfos,
            )

            // Act
            val resultat = PrisinfoRepository.hentPrisinfos(gjenomforing2.id)

            // Assert
            resultat.shouldBeEmpty()
        }
    }

    @Nested
    inner class DeleteForGjennomforingTests {
        @Test
        fun `sletter alle sertifiseringer for deltakerliste`() {
            // Arrange
            val gjennomforing = lagDeltakerliste()
            TestRepository.insert(gjennomforing)

            PrisinfoRepository.lagrePrisinfos(
                gjennomforingId = gjennomforing.id,
                prisinfos = setOf(
                    Priskomponent(Priskomponent.Pristype.SKOLEPENGER, 1U),
                    Priskomponent(Priskomponent.Pristype.EKSAMENSGEBYR, 2U),
                ),
            )

            // Act
            PrisinfoRepository.deleteForGjennomforing(gjennomforing.id)

            // Assert
            PrisinfoRepository.hentPrisinfos(gjennomforing.id).shouldBeEmpty()
        }

        @Test
        fun `sletter ikke sertifiseringer for andre deltakerlister`() {
            // Arrange
            val gjennomforing1 = lagDeltakerliste()
            TestRepository.insert(gjennomforing1)

            val gjennomforing2 = lagDeltakerliste()
            TestRepository.insert(gjennomforing2)

            val prisinfos = setOf(
                Priskomponent(Priskomponent.Pristype.SKOLEPENGER, 1U),
                Priskomponent(Priskomponent.Pristype.EKSAMENSGEBYR, 2U),
            )

            PrisinfoRepository.lagrePrisinfos(
                gjennomforingId = gjennomforing1.id,
                prisinfos = prisinfos,
            )

            PrisinfoRepository.lagrePrisinfos(
                gjennomforingId = gjennomforing2.id,
                prisinfos = prisinfos,
            )

            // Act
            PrisinfoRepository.deleteForGjennomforing(gjennomforing1.id)

            // Assert
            PrisinfoRepository.hentPrisinfos(gjennomforing1.id).shouldBeEmpty()
            PrisinfoRepository.hentPrisinfos(gjennomforing2.id) shouldBe prisinfos
        }
    }
}
