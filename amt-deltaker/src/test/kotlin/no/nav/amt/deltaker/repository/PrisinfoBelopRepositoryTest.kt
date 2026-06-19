@file:Suppress("ktlint:standard:no-wildcard-imports")

package no.nav.amt.deltaker.repository

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.repository.dbo.Priskomponent
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.internapi.enkeltplass.PrisinformasjonDto
import no.nav.amt.lib.testing.DatabaseTestExtension
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class PrisinfoBelopRepositoryTest {
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
            PrisinfoBelopRepository.lagrePrisinfoBelop(gjennomforing.id, emptySet())

            // Assert
            PrisinfoBelopRepository.hentPrisinfoBelop(gjennomforing.id).shouldBeEmpty()
        }

        @Test
        fun `flere prisinfo - lagrer alle`() {
            // Arrange
            val gjennomforing = lagDeltakerliste()
            TestRepository.insert(gjennomforing)

            val prisinfos = setOf(
                Priskomponent(PrisinformasjonDto.Tilskudd.Tilskuddstype.SKOLEPENGER, 1),
                Priskomponent(PrisinformasjonDto.Tilskudd.Tilskuddstype.EKSAMENSGEBYR, 2),
                Priskomponent(PrisinformasjonDto.Tilskudd.Tilskuddstype.STUDIEREISE, 3),
                Priskomponent(PrisinformasjonDto.Tilskudd.Tilskuddstype.SEMESTERAVGIFT, 4),
                Priskomponent(PrisinformasjonDto.Tilskudd.Tilskuddstype.INTEGRERT_BOTILBUD, 5),
            )

            // Act
            PrisinfoBelopRepository.lagrePrisinfoBelop(gjennomforing.id, prisinfos)

            // Assert
            PrisinfoBelopRepository
                .hentPrisinfoBelop(gjennomforing.id)
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
            val resultat = PrisinfoBelopRepository.hentPrisinfoBelop(gjennomforing.id)

            // Assert
            resultat.shouldBeEmpty()
        }

        @Test
        fun `henter kun for angitt deltakerliste`() {
            // Arrange
            val gjennomforing1 = lagDeltakerliste()
            TestRepository.insert(gjennomforing1)

            val gjennomforing2 = lagDeltakerliste()
            TestRepository.insert(gjennomforing2)

            val prisinfos = setOf(
                Priskomponent(PrisinformasjonDto.Tilskudd.Tilskuddstype.SKOLEPENGER, 1),
                Priskomponent(PrisinformasjonDto.Tilskudd.Tilskuddstype.EKSAMENSGEBYR, 2),
            )

            PrisinfoBelopRepository.lagrePrisinfoBelop(
                gjennomforingId = gjennomforing1.id,
                belop = prisinfos,
            )

            // Act
            val resultat = PrisinfoBelopRepository.hentPrisinfoBelop(gjennomforing2.id)

            // Assert
            resultat.shouldBeEmpty()
        }
    }

    @Nested
    inner class DeleteForGjennomforingTests {
        @Test
        fun `sletter alle prisinfo for deltakerliste`() {
            // Arrange
            val gjennomforing = lagDeltakerliste()
            TestRepository.insert(gjennomforing)

            PrisinfoBelopRepository.lagrePrisinfoBelop(
                gjennomforingId = gjennomforing.id,
                belop = setOf(
                    Priskomponent(PrisinformasjonDto.Tilskudd.Tilskuddstype.SKOLEPENGER, 1),
                    Priskomponent(PrisinformasjonDto.Tilskudd.Tilskuddstype.EKSAMENSGEBYR, 2),
                ),
            )

            // Act
            PrisinfoBelopRepository.deleteForGjennomforing(gjennomforing.id)

            // Assert
            PrisinfoBelopRepository.hentPrisinfoBelop(gjennomforing.id).shouldBeEmpty()
        }

        @Test
        fun `sletter ikke prisinfo for andre deltakerlister`() {
            // Arrange
            val gjennomforing1 = lagDeltakerliste()
            TestRepository.insert(gjennomforing1)

            val gjennomforing2 = lagDeltakerliste()
            TestRepository.insert(gjennomforing2)

            val prisinfos = setOf(
                Priskomponent(PrisinformasjonDto.Tilskudd.Tilskuddstype.SKOLEPENGER, 1),
                Priskomponent(PrisinformasjonDto.Tilskudd.Tilskuddstype.EKSAMENSGEBYR, 2),
            )

            PrisinfoBelopRepository.lagrePrisinfoBelop(
                gjennomforingId = gjennomforing1.id,
                belop = prisinfos,
            )

            PrisinfoBelopRepository.lagrePrisinfoBelop(
                gjennomforingId = gjennomforing2.id,
                belop = prisinfos,
            )

            // Act
            PrisinfoBelopRepository.deleteForGjennomforing(gjennomforing1.id)

            // Assert
            PrisinfoBelopRepository.hentPrisinfoBelop(gjennomforing1.id).shouldBeEmpty()
            PrisinfoBelopRepository.hentPrisinfoBelop(gjennomforing2.id) shouldBe prisinfos
        }
    }
}
