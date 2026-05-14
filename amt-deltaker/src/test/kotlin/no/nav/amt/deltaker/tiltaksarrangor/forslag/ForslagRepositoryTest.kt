package no.nav.amt.deltaker.tiltaksarrangor.forslag

import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.testing.DatabaseTestExtension
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDateTime
import java.util.UUID

class ForslagRepositoryTest {
    private val forslagRepository = ForslagRepository()

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Nested
    inner class Upsert {
        @Test
        fun `upsert - nytt forslag - inserter`() {
            // Arrange
            val deltaker = TestData.lagDeltaker()
            TestRepository.insert(deltaker)
            val forslag = TestData.lagForslag(deltakerId = deltaker.id)

            // Act
            forslagRepository.upsert(forslag)

            // Assert
            val resultat = forslagRepository.getForDeltaker(deltaker.id)
            resultat.size shouldBe 1
            resultat.single().id shouldBe forslag.id
        }

        @Test
        fun `upsert - eksisterende forslag - oppdaterer`() {
            // Arrange
            val deltaker = TestData.lagDeltaker()
            TestRepository.insert(deltaker)
            val forslag = TestData.lagForslag(deltakerId = deltaker.id, begrunnelse = "Gammel")
            forslagRepository.upsert(forslag)

            val oppdatert = forslag.copy(begrunnelse = "Ny begrunnelse")

            // Act
            forslagRepository.upsert(oppdatert)

            // Assert
            val resultat = forslagRepository.getForDeltaker(deltaker.id)
            resultat.size shouldBe 1
            resultat.single().begrunnelse shouldBe "Ny begrunnelse"
        }
    }

    @Nested
    inner class Get {
        @Test
        fun `get - finnes - returnerer success`() {
            // Arrange
            val deltaker = TestData.lagDeltaker()
            TestRepository.insert(deltaker)
            val forslag = TestData.lagForslag(deltakerId = deltaker.id)
            forslagRepository.upsert(forslag)

            // Act
            val resultat = forslagRepository.get(forslag.id)

            // Assert
            resultat.shouldBeSuccess()
            resultat.getOrThrow().id shouldBe forslag.id
        }

        @Test
        fun `get - finnes ikke - returnerer failure`() {
            // Arrange / Act
            val resultat = forslagRepository.get(UUID.randomUUID())

            // Assert
            resultat.shouldBeFailure<NoSuchElementException>()
        }
    }

    @Nested
    inner class GetForDeltaker {
        @Test
        fun `getForDeltaker - ingen forslag - returnerer tom liste`() {
            // Arrange
            val deltaker = TestData.lagDeltaker()
            TestRepository.insert(deltaker)

            // Act
            val resultat = forslagRepository.getForDeltaker(deltaker.id)

            // Assert
            resultat shouldBe emptyList()
        }

        @Test
        fun `getForDeltaker - flere forslag - returnerer alle uavhengig av status`() {
            // Arrange
            val deltaker = TestData.lagDeltaker()
            TestRepository.insert(deltaker)
            val venter = TestData.lagForslag(deltakerId = deltaker.id, status = Forslag.Status.VenterPaSvar)
            val godkjent = TestData.lagForslag(
                deltakerId = deltaker.id,
                status = Forslag.Status.Godkjent(
                    godkjentAv = Forslag.NavAnsatt(UUID.randomUUID(), UUID.randomUUID()),
                    godkjent = LocalDateTime.now(),
                ),
            )
            forslagRepository.upsert(venter)
            forslagRepository.upsert(godkjent)

            // Act
            val resultat = forslagRepository.getForDeltaker(deltaker.id)

            // Assert
            resultat.size shouldBe 2
        }

        @Test
        fun `getForDeltaker - filtrerer paa deltakerId`() {
            // Arrange
            val deltaker1 = TestData.lagDeltaker()
            val deltaker2 = TestData.lagDeltaker()
            TestRepository.insert(deltaker1)
            TestRepository.insert(deltaker2)
            forslagRepository.upsert(TestData.lagForslag(deltakerId = deltaker1.id))
            forslagRepository.upsert(TestData.lagForslag(deltakerId = deltaker2.id))

            // Act
            val resultat = forslagRepository.getForDeltaker(deltaker1.id)

            // Assert
            resultat.size shouldBe 1
            resultat.single().deltakerId shouldBe deltaker1.id
        }
    }

    @Nested
    inner class GetVenterPaSvarForDeltakere {
        @Test
        fun `tom set - returnerer tom map`() {
            // Arrange / Act
            val resultat = forslagRepository.getVenterPaSvarForDeltakere(emptySet())

            // Assert
            resultat.shouldBeEmpty()
        }

        @Test
        fun `ingen treff - returnerer tom map`() {
            // Arrange / Act
            val resultat = forslagRepository.getVenterPaSvarForDeltakere(setOf(UUID.randomUUID()))

            // Assert
            resultat.shouldBeEmpty()
        }

        @Test
        fun `filtrerer bort ikke-VenterPaSvar forslag i SQL`() {
            // Arrange
            val deltaker = TestData.lagDeltaker()
            TestRepository.insert(deltaker)
            val venter = TestData.lagForslag(deltakerId = deltaker.id, status = Forslag.Status.VenterPaSvar)
            val godkjent = TestData.lagForslag(
                deltakerId = deltaker.id,
                status = Forslag.Status.Godkjent(
                    godkjentAv = Forslag.NavAnsatt(UUID.randomUUID(), UUID.randomUUID()),
                    godkjent = LocalDateTime.now(),
                ),
            )
            val avvist = TestData.lagForslag(
                deltakerId = deltaker.id,
                status = Forslag.Status.Avvist(
                    avvistAv = Forslag.NavAnsatt(UUID.randomUUID(), UUID.randomUUID()),
                    avvist = LocalDateTime.now(),
                    begrunnelseFraNav = "Avvist",
                ),
            )
            forslagRepository.upsert(venter)
            forslagRepository.upsert(godkjent)
            forslagRepository.upsert(avvist)

            // Act
            val resultat = forslagRepository.getVenterPaSvarForDeltakere(setOf(deltaker.id))

            // Assert
            resultat shouldHaveSize 1
            resultat[deltaker.id]!!.size shouldBe 1
            resultat[deltaker.id]!!.single().id shouldBe venter.id
        }

        @Test
        fun `flere deltakere med VenterPaSvar forslag - grupperer per deltaker`() {
            // Arrange
            val deltaker1 = TestData.lagDeltaker()
            val deltaker2 = TestData.lagDeltaker()
            TestRepository.insert(deltaker1)
            TestRepository.insert(deltaker2)
            val f1 = TestData.lagForslag(deltakerId = deltaker1.id, status = Forslag.Status.VenterPaSvar)
            val f2a = TestData.lagForslag(deltakerId = deltaker2.id, status = Forslag.Status.VenterPaSvar)
            val f2b = TestData.lagForslag(deltakerId = deltaker2.id, status = Forslag.Status.VenterPaSvar)
            forslagRepository.upsert(f1)
            forslagRepository.upsert(f2a)
            forslagRepository.upsert(f2b)

            // Act
            val resultat = forslagRepository.getVenterPaSvarForDeltakere(setOf(deltaker1.id, deltaker2.id))

            // Assert
            resultat shouldHaveSize 2
            resultat[deltaker1.id]!!.size shouldBe 1
            resultat[deltaker2.id]!!.size shouldBe 2
        }

        @Test
        fun `filtrerer paa oppgitte deltakerIder`() {
            // Arrange
            val deltaker1 = TestData.lagDeltaker()
            val deltaker2 = TestData.lagDeltaker()
            TestRepository.insert(deltaker1)
            TestRepository.insert(deltaker2)
            forslagRepository.upsert(TestData.lagForslag(deltakerId = deltaker1.id))
            forslagRepository.upsert(TestData.lagForslag(deltakerId = deltaker2.id))

            // Act — spør kun etter deltaker1
            val resultat = forslagRepository.getVenterPaSvarForDeltakere(setOf(deltaker1.id))

            // Assert
            resultat shouldHaveSize 1
            resultat.keys.single() shouldBe deltaker1.id
        }

        @Test
        fun `deltaker uten VenterPaSvar forslag - ikke med i map`() {
            // Arrange
            val deltaker = TestData.lagDeltaker()
            TestRepository.insert(deltaker)
            forslagRepository.upsert(
                TestData.lagForslag(
                    deltakerId = deltaker.id,
                    status = Forslag.Status.Godkjent(
                        godkjentAv = Forslag.NavAnsatt(UUID.randomUUID(), UUID.randomUUID()),
                        godkjent = LocalDateTime.now(),
                    ),
                ),
            )

            // Act
            val resultat = forslagRepository.getVenterPaSvarForDeltakere(setOf(deltaker.id))

            // Assert
            resultat.shouldBeEmpty()
        }
    }

    @Nested
    inner class Delete {
        @Test
        fun `delete - sletter forslaget`() {
            // Arrange
            val deltaker = TestData.lagDeltaker()
            TestRepository.insert(deltaker)
            val forslag = TestData.lagForslag(deltakerId = deltaker.id)
            forslagRepository.upsert(forslag)

            // Act
            forslagRepository.delete(forslag.id)

            // Assert
            forslagRepository.getForDeltaker(deltaker.id) shouldBe emptyList()
        }
    }

    @Nested
    inner class DeleteForDeltaker {
        @Test
        fun `deleteForDeltaker - sletter alle forslag for deltaker`() {
            // Arrange
            val deltaker = TestData.lagDeltaker()
            TestRepository.insert(deltaker)
            forslagRepository.upsert(TestData.lagForslag(deltakerId = deltaker.id))
            forslagRepository.upsert(TestData.lagForslag(deltakerId = deltaker.id))

            // Act
            forslagRepository.deleteForDeltaker(deltaker.id)

            // Assert
            forslagRepository.getForDeltaker(deltaker.id) shouldBe emptyList()
        }

        @Test
        fun `deleteForDeltaker - sletter ikke forslag for andre deltakere`() {
            // Arrange
            val deltaker1 = TestData.lagDeltaker()
            val deltaker2 = TestData.lagDeltaker()
            TestRepository.insert(deltaker1)
            TestRepository.insert(deltaker2)
            forslagRepository.upsert(TestData.lagForslag(deltakerId = deltaker1.id))
            forslagRepository.upsert(TestData.lagForslag(deltakerId = deltaker2.id))

            // Act
            forslagRepository.deleteForDeltaker(deltaker1.id)

            // Assert
            forslagRepository.getForDeltaker(deltaker1.id) shouldBe emptyList()
            forslagRepository.getForDeltaker(deltaker2.id).size shouldBe 1
        }
    }

    @Nested
    inner class KanLagres {
        @Test
        fun `kanLagres - deltaker finnes - returnerer true`() {
            // Arrange
            val deltaker = TestData.lagDeltaker()
            TestRepository.insert(deltaker)

            // Act / Assert
            forslagRepository.kanLagres(deltaker.id) shouldBe true
        }

        @Test
        fun `kanLagres - deltaker finnes ikke - returnerer false`() {
            // Act / Assert
            forslagRepository.kanLagres(UUID.randomUUID()) shouldBe false
        }
    }
}
