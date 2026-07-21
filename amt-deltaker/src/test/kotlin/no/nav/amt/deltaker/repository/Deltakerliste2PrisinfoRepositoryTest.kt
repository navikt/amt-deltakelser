@file:Suppress("ktlint:standard:no-wildcard-imports")

package no.nav.amt.deltaker.repository

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.repository.dbo.PrisinfoDbo
import no.nav.amt.deltaker.repository.dbo.PrisinfoUpsertDbo
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

class Deltakerliste2PrisinfoRepositoryTest {
    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()

        private val gjennomforingInTest = lagDeltakerliste()

        private fun lagPrisinfoInsertDbo(prisinfoId: UUID = UUID.randomUUID()) = PrisinfoUpsertDbo(
            id = prisinfoId,
            gjennomforingId = gjennomforingInTest.id,
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

            Deltakerliste2PrisinfoRepository.upsert(
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

            Deltakerliste2PrisinfoRepository.upsert(
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

            Deltakerliste2PrisinfoRepository.upsert(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjonId = prisinfoUpsertDbo.id,
                rolle = PrisinfoDbo.Rolle.ENDRING,
            )

            assertThrows<Exception> {
                Deltakerliste2PrisinfoRepository.upsert(
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

            Deltakerliste2PrisinfoRepository.upsert(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjonId = prisinfoUpsertDbo1.id,
                rolle = PrisinfoDbo.Rolle.GJELDENDE,
            )

            Deltakerliste2PrisinfoRepository.upsert(
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

            Deltakerliste2PrisinfoRepository.upsert(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjonId = prisinfoUpsertDbo1.id,
                rolle = PrisinfoDbo.Rolle.ENDRING,
            )

            Deltakerliste2PrisinfoRepository.upsert(
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
    inner class DeleteTests {
        @Test
        fun `delete - fjerner kobling for gitt rolle`() {
            TestRepository.insert(gjennomforingInTest)

            val prisinfoUpsertDbo = lagPrisinfoInsertDbo()
            PrisinfoRepository.upsertPrisinfo(prisinfoUpsertDbo)

            Deltakerliste2PrisinfoRepository.upsert(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjonId = prisinfoUpsertDbo.id,
                rolle = PrisinfoDbo.Rolle.ENDRING,
            )

            Deltakerliste2PrisinfoRepository.delete(
                gjennomforingId = gjennomforingInTest.id,
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

            Deltakerliste2PrisinfoRepository.upsert(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjonId = prisinfoUpsertDbo1.id,
                rolle = PrisinfoDbo.Rolle.GJELDENDE,
            )

            Deltakerliste2PrisinfoRepository.upsert(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjonId = prisinfoUpsertDbo2.id,
                rolle = PrisinfoDbo.Rolle.ENDRING,
            )

            Deltakerliste2PrisinfoRepository.delete(
                gjennomforingId = gjennomforingInTest.id,
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

            Deltakerliste2PrisinfoRepository.upsert(
                gjennomforingId = gjennomforingInTest.id,
                prisinformasjonId = prisinfoUpsertDbo.id,
                rolle = PrisinfoDbo.Rolle.ENDRING,
            )

            DeltakerlisteRepository().delete(gjennomforingInTest.id)

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
                "SELECT prisinformasjon_id FROM deltakerliste_2_prisinformasjon WHERE deltakerliste_id = ? AND rolle = ?",
                gjennomforingId,
                rolle.name,
            ),
        ) { it.uuid("prisinformasjon_id") }
    }
}
