@file:Suppress("ktlint:standard:no-wildcard-imports")

package no.nav.amt.deltaker.repository

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.repository.dbo.PrisinfoDbo
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.models.deltaker.ANSKAFFELSE_SUB_TYPE
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.utils.database.Database
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.UUID

class PrisinfoGjennomforingLinkRepositoryTest {
    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()

        private val gjennomforingInTest = lagDeltakerliste()

        private fun lagPrisinfoInsertDbo(
            prisinfoId: UUID = UUID.randomUUID(),
            gjennomforingId: UUID = gjennomforingInTest.id,
        ) = PrisinfoDbo(
            id = prisinfoId,
            gjennomforingId = gjennomforingId,
            prisinfoJsonSubtype = ANSKAFFELSE_SUB_TYPE,
            anskaffelsePris = 10000,
        )
    }

    @Nested
    inner class UpsertTests {
        @Test
        fun `upsert - GJELDENDE rolle - lagrer kobling`() {
            TestRepository.insert(gjennomforingInTest)

            val prisinfoUpsertDbo = lagPrisinfoInsertDbo()
            PrisinfoRepository.upsertPrisinfo(prisinfoUpsertDbo)

            PrisinfoGjennomforingLinkRepository.upsert(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjonId = prisinfoUpsertDbo.id,
                rolle = PrisinfoDbo.Rolle.GJELDENDE,
            )

            hentPrisinfoId(
                gjennomforingId = gjennomforingInTest.id,
                rolle = PrisinfoDbo.Rolle.GJELDENDE,
            ) shouldBe prisinfoUpsertDbo.id
        }

        @Test
        fun `upsert - ENDRING rolle - lagrer kobling`() {
            TestRepository.insert(gjennomforingInTest)

            val prisinfoUpsertDbo = lagPrisinfoInsertDbo()
            PrisinfoRepository.upsertPrisinfo(prisinfoUpsertDbo)

            PrisinfoGjennomforingLinkRepository.upsert(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjonId = prisinfoUpsertDbo.id,
                rolle = PrisinfoDbo.Rolle.ENDRING,
            )

            hentPrisinfoId(
                gjennomforingId = gjennomforingInTest.id,
                rolle = PrisinfoDbo.Rolle.ENDRING,
            ) shouldBe prisinfoUpsertDbo.id
        }

        @Test
        fun `upsert - duplikat prisinfoId for samme deltakerliste med ulik rolle - kaster feil`() {
            TestRepository.insert(gjennomforingInTest)

            val prisinfoUpsertDbo = lagPrisinfoInsertDbo()
            PrisinfoRepository.upsertPrisinfo(prisinfoUpsertDbo)

            PrisinfoGjennomforingLinkRepository.upsert(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjonId = prisinfoUpsertDbo.id,
                rolle = PrisinfoDbo.Rolle.ENDRING,
            )

            assertThrows<Exception> {
                PrisinfoGjennomforingLinkRepository.upsert(
                    gjennomforingId = gjennomforingInTest.id,
                    prisinformasjonId = prisinfoUpsertDbo.id,
                    rolle = PrisinfoDbo.Rolle.GJELDENDE,
                )
            }
        }

        @Test
        fun `upsert - GJELDENDE og ENDRING for samme deltakerliste - begge lagres`() {
            TestRepository.insert(gjennomforingInTest)

            val prisinfoUpsertDbo1 = lagPrisinfoInsertDbo()
            val prisinfoUpsertDbo2 = lagPrisinfoInsertDbo()

            PrisinfoRepository.upsertPrisinfo(prisinfoUpsertDbo1)
            PrisinfoRepository.upsertPrisinfo(prisinfoUpsertDbo2)

            PrisinfoGjennomforingLinkRepository.upsert(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjonId = prisinfoUpsertDbo1.id,
                rolle = PrisinfoDbo.Rolle.GJELDENDE,
            )

            PrisinfoGjennomforingLinkRepository.upsert(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjonId = prisinfoUpsertDbo2.id,
                rolle = PrisinfoDbo.Rolle.ENDRING,
            )

            hentPrisinfoId(
                gjennomforingId = gjennomforingInTest.id,
                rolle = PrisinfoDbo.Rolle.GJELDENDE,
            ) shouldBe prisinfoUpsertDbo1.id

            hentPrisinfoId(
                gjennomforingId = gjennomforingInTest.id,
                rolle = PrisinfoDbo.Rolle.ENDRING,
            ) shouldBe prisinfoUpsertDbo2.id
        }

        @Test
        fun `upsert - samme rolle - oppdaterer prisinfoId`() {
            TestRepository.insert(gjennomforingInTest)

            val prisinfoUpsertDbo1 = lagPrisinfoInsertDbo()
            val prisinfoUpsertDbo2 = lagPrisinfoInsertDbo()

            PrisinfoRepository.upsertPrisinfo(prisinfoUpsertDbo1)
            PrisinfoRepository.upsertPrisinfo(prisinfoUpsertDbo2)

            PrisinfoGjennomforingLinkRepository.upsert(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjonId = prisinfoUpsertDbo1.id,
                rolle = PrisinfoDbo.Rolle.ENDRING,
            )

            PrisinfoGjennomforingLinkRepository.upsert(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjonId = prisinfoUpsertDbo2.id,
                rolle = PrisinfoDbo.Rolle.ENDRING,
            )

            hentPrisinfoId(
                gjennomforingId = gjennomforingInTest.id,
                rolle = PrisinfoDbo.Rolle.ENDRING,
            ) shouldBe prisinfoUpsertDbo2.id
        }
    }

    @Nested
    inner class HentPrisinformasjonIdForEndringTests {
        @Test
        fun `returnerer prisinformasjonId når ENDRING-kobling finnes`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            val prisinfoUpsertDbo = lagPrisinfoInsertDbo(gjennomforingId = deltakerliste.id)
            PrisinfoRepository.upsertPrisinfo(prisinfoUpsertDbo)

            PrisinfoGjennomforingLinkRepository.upsert(
                gjennomforingId = deltakerliste.id,
                prisinformasjonId = prisinfoUpsertDbo.id,
                rolle = PrisinfoDbo.Rolle.ENDRING,
            )

            // Act
            val result = PrisinfoGjennomforingLinkRepository.hentPrisinformasjonIdForEndring(deltakerliste.id)

            // Assert
            result shouldBe prisinfoUpsertDbo.id
        }

        @Test
        fun `returnerer null når ingen kobling finnes`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            // Act
            val result = PrisinfoGjennomforingLinkRepository.hentPrisinformasjonIdForEndring(deltakerliste.id)

            // Assert
            result shouldBe null
        }

        @Test
        fun `returnerer null når kun GJELDENDE-kobling finnes`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            TestRepository.insert(deltakerliste)

            val prisinfoUpsertDbo = lagPrisinfoInsertDbo(gjennomforingId = deltakerliste.id)
            PrisinfoRepository.upsertPrisinfo(prisinfoUpsertDbo)

            PrisinfoGjennomforingLinkRepository.upsert(
                gjennomforingId = deltakerliste.id,
                prisinformasjonId = prisinfoUpsertDbo.id,
                rolle = PrisinfoDbo.Rolle.GJELDENDE,
            )

            // Act
            val result = PrisinfoGjennomforingLinkRepository.hentPrisinformasjonIdForEndring(deltakerliste.id)

            // Assert
            result shouldBe null
        }
    }

    @Nested
    inner class DeleteTests {
        @Test
        fun `delete - fjerner kobling for gitt rolle`() {
            TestRepository.insert(gjennomforingInTest)

            val prisinfoUpsertDbo = lagPrisinfoInsertDbo()
            PrisinfoRepository.upsertPrisinfo(prisinfoUpsertDbo)

            PrisinfoGjennomforingLinkRepository.upsert(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjonId = prisinfoUpsertDbo.id,
                rolle = PrisinfoDbo.Rolle.ENDRING,
            )

            PrisinfoGjennomforingLinkRepository.delete(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjonId = prisinfoUpsertDbo.id,
                rolle = PrisinfoDbo.Rolle.ENDRING,
            )

            hentPrisinfoId(
                gjennomforingId = gjennomforingInTest.id,
                rolle = PrisinfoDbo.Rolle.ENDRING,
            ) shouldBe null
        }

        @Test
        fun `delete - fjerner ikke andre roller`() {
            TestRepository.insert(gjennomforingInTest)

            val prisinfoUpsertDbo1 = lagPrisinfoInsertDbo()
            val prisinfoUpsertDbo2 = lagPrisinfoInsertDbo()

            PrisinfoRepository.upsertPrisinfo(prisinfoUpsertDbo1)
            PrisinfoRepository.upsertPrisinfo(prisinfoUpsertDbo2)

            PrisinfoGjennomforingLinkRepository.upsert(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjonId = prisinfoUpsertDbo1.id,
                rolle = PrisinfoDbo.Rolle.GJELDENDE,
            )

            PrisinfoGjennomforingLinkRepository.upsert(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjonId = prisinfoUpsertDbo2.id,
                rolle = PrisinfoDbo.Rolle.ENDRING,
            )

            PrisinfoGjennomforingLinkRepository.delete(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjonId = prisinfoUpsertDbo2.id,
                rolle = PrisinfoDbo.Rolle.ENDRING,
            )

            hentPrisinfoId(
                gjennomforingId = gjennomforingInTest.id,
                rolle = PrisinfoDbo.Rolle.GJELDENDE,
            ) shouldBe prisinfoUpsertDbo1.id

            hentPrisinfoId(
                gjennomforingId = gjennomforingInTest.id,
                rolle = PrisinfoDbo.Rolle.ENDRING,
            ) shouldBe null
        }

        @Test
        fun `delete gjennomføring - cascade fjerner alle data`() {
            TestRepository.insert(gjennomforingInTest)

            val prisinfoUpsertDbo = lagPrisinfoInsertDbo()
            PrisinfoRepository.upsertPrisinfo(prisinfoUpsertDbo)

            PrisinfoGjennomforingLinkRepository.upsert(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjonId = prisinfoUpsertDbo.id,
                rolle = PrisinfoDbo.Rolle.ENDRING,
            )

            GjennomforingRepository().delete(gjennomforingInTest.id)

            hentPrisinfoId(
                gjennomforingId = gjennomforingInTest.id,
                rolle = PrisinfoDbo.Rolle.ENDRING,
            ) shouldBe null
        }
    }

    private fun hentPrisinfoId(
        gjennomforingId: UUID,
        rolle: PrisinfoDbo.Rolle,
    ): UUID? = Database.query { session ->
        session.single(
            kotliquery.queryOf(
                "SELECT prisinformasjon_id FROM prisinformasjon_gjennomforing_link WHERE gjennomforing_id = ? AND rolle = ?",
                gjennomforingId,
                rolle.name,
            ),
        ) { it.uuid("prisinformasjon_id") }
    }
}
