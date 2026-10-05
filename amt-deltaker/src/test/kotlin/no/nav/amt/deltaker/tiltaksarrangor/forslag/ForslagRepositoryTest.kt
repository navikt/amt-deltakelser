package no.nav.amt.deltaker.tiltaksarrangor.forslag

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

        @Test
        fun `upsert - foreldet VenterPaSvar etter annen status - beholder lagret forslag uendret`() {
            val avgjorteStatuser = listOf(
                Forslag.Status.Godkjent(
                    godkjentAv = Forslag.NavAnsatt(UUID.randomUUID(), UUID.randomUUID()),
                    godkjent = LocalDateTime.now(),
                ),
                Forslag.Status.Avvist(
                    avvistAv = Forslag.NavAnsatt(UUID.randomUUID(), UUID.randomUUID()),
                    avvist = LocalDateTime.now(),
                    begrunnelseFraNav = "Avslått",
                ),
                Forslag.Status.Erstattet(
                    erstattetMedForslagId = UUID.randomUUID(),
                    erstattet = LocalDateTime.now(),
                ),
                Forslag.Status.Tilbakekalt(
                    tilbakekaltAvArrangorAnsattId = UUID.randomUUID(),
                    tilbakekalt = LocalDateTime.now(),
                ),
            )

            avgjorteStatuser.forEach { status ->
                val deltaker = TestData.lagDeltaker()
                TestRepository.insert(deltaker)
                val lagretForslag = TestData.lagForslag(deltakerId = deltaker.id, status = status)
                forslagRepository.upsert(lagretForslag)
                val lagretForslagFraDb = forslagRepository.get(lagretForslag.id).getOrThrow()

                val foreldetForslag = lagretForslag.copy(
                    begrunnelse = "Foreldet melding",
                    status = Forslag.Status.VenterPaSvar,
                )

                forslagRepository.upsert(foreldetForslag)

                forslagRepository.get(lagretForslag.id).getOrThrow() shouldBe lagretForslagFraDb
            }
        }

        @Test
        fun `upsert - VenterPaSvar endres til avgjort status - oppdaterer`() {
            val deltaker = TestData.lagDeltaker()
            TestRepository.insert(deltaker)
            val ventendeForslag = TestData.lagForslag(deltakerId = deltaker.id)
            forslagRepository.upsert(ventendeForslag)
            val ventendeForslagFraDb = forslagRepository.get(ventendeForslag.id).getOrThrow()

            val godkjentForslag = ventendeForslag.copy(
                status = Forslag.Status.Godkjent(
                    godkjentAv = Forslag.NavAnsatt(UUID.randomUUID(), UUID.randomUUID()),
                    godkjent = LocalDateTime.now(),
                ),
            )

            forslagRepository.upsert(godkjentForslag)

            forslagRepository.get(ventendeForslag.id).getOrThrow() shouldBe
                ventendeForslagFraDb.copy(status = godkjentForslag.status)
        }

        @Test
        fun `upsert - avgjort forslag mottar annen ikke-ventende status - oppdaterer`() {
            val ikkeVentendeStatuser = listOf(
                Forslag.Status.Godkjent(
                    godkjentAv = Forslag.NavAnsatt(UUID.randomUUID(), UUID.randomUUID()),
                    godkjent = LocalDateTime.now(),
                ),
                Forslag.Status.Avvist(
                    avvistAv = Forslag.NavAnsatt(UUID.randomUUID(), UUID.randomUUID()),
                    avvist = LocalDateTime.now(),
                    begrunnelseFraNav = "Avslått",
                ),
                Forslag.Status.Erstattet(
                    erstattetMedForslagId = UUID.randomUUID(),
                    erstattet = LocalDateTime.now(),
                ),
                Forslag.Status.Tilbakekalt(
                    tilbakekaltAvArrangorAnsattId = UUID.randomUUID(),
                    tilbakekalt = LocalDateTime.now(),
                ),
            )

            ikkeVentendeStatuser.forEach { nyStatus ->
                val deltaker = TestData.lagDeltaker()
                TestRepository.insert(deltaker)
                val opprinneligForslag = TestData.lagForslag(
                    deltakerId = deltaker.id,
                    status = Forslag.Status.Godkjent(
                        godkjentAv = Forslag.NavAnsatt(UUID.randomUUID(), UUID.randomUUID()),
                        godkjent = LocalDateTime.now(),
                    ),
                )
                forslagRepository.upsert(opprinneligForslag)
                val lagretForslagFraDb = forslagRepository.get(opprinneligForslag.id).getOrThrow()
                val oppdatertForslag = opprinneligForslag.copy(
                    begrunnelse = "Ny begrunnelse",
                    status = nyStatus,
                )

                forslagRepository.upsert(oppdatertForslag)

                forslagRepository.get(opprinneligForslag.id).getOrThrow() shouldBe
                    lagretForslagFraDb.copy(
                        begrunnelse = oppdatertForslag.begrunnelse,
                        status = oppdatertForslag.status,
                    )
            }
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
