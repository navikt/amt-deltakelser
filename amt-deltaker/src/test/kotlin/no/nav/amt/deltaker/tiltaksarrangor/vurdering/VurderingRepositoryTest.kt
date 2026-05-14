package no.nav.amt.deltaker.tiltaksarrangor.vurdering

import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.models.arrangor.melding.Vurderingstype
import no.nav.amt.lib.testing.DatabaseTestExtension
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDateTime
import java.util.UUID

class VurderingRepositoryTest {
    private val vurderingRepository = VurderingRepository()

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Nested
    inner class Upsert {
        @Test
        fun `upsert - ny vurdering - inserter`() {
            // Arrange
            val deltaker = TestData.lagDeltaker()
            val vurdering = TestData.lagVurdering(deltakerId = deltaker.id)
            TestRepository.insert(deltaker)

            // Act
            vurderingRepository.upsert(vurdering)

            // Assert
            vurderingRepository.getForDeltaker(vurdering.deltakerId).size shouldBe 1
        }

        @Test
        fun `upsert - eksisterende vurdering - oppdaterer`() {
            // Arrange
            val deltaker = TestData.lagDeltaker()
            val vurdering = TestData.lagVurdering(
                deltakerId = deltaker.id,
                vurderingstype = Vurderingstype.OPPFYLLER_KRAVENE,
            )
            TestRepository.insert(deltaker)
            vurderingRepository.upsert(vurdering)

            val oppdatert = vurdering.copy(
                vurderingstype = Vurderingstype.OPPFYLLER_IKKE_KRAVENE,
                begrunnelse = "Ny begrunnelse",
            )

            // Act
            vurderingRepository.upsert(oppdatert)

            // Assert
            val resultat = vurderingRepository.getForDeltaker(deltaker.id)
            resultat.size shouldBe 1
            resultat.single().vurderingstype shouldBe Vurderingstype.OPPFYLLER_IKKE_KRAVENE
            resultat.single().begrunnelse shouldBe "Ny begrunnelse"
        }
    }

    @Nested
    inner class GetForDeltaker {
        @Test
        fun `getForDeltaker - ingen vurderinger - returnerer tom liste`() {
            // Arrange
            val deltaker = TestData.lagDeltaker()
            TestRepository.insert(deltaker)

            // Act
            val resultat = vurderingRepository.getForDeltaker(deltaker.id)

            // Assert
            resultat shouldBe emptyList()
        }

        @Test
        fun `getForDeltaker - flere vurderinger - returnerer alle`() {
            // Arrange
            val deltaker = TestData.lagDeltaker()
            TestRepository.insert(deltaker)
            val v1 = TestData.lagVurdering(
                deltakerId = deltaker.id,
                gyldigFra = LocalDateTime.now().minusDays(5),
            )
            val v2 = TestData.lagVurdering(
                deltakerId = deltaker.id,
                gyldigFra = LocalDateTime.now(),
            )
            vurderingRepository.upsert(v1)
            vurderingRepository.upsert(v2)

            // Act
            val resultat = vurderingRepository.getForDeltaker(deltaker.id)

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
            vurderingRepository.upsert(TestData.lagVurdering(deltakerId = deltaker1.id))
            vurderingRepository.upsert(TestData.lagVurdering(deltakerId = deltaker2.id))

            // Act
            val resultat = vurderingRepository.getForDeltaker(deltaker1.id)

            // Assert
            resultat.size shouldBe 1
            resultat.single().deltakerId shouldBe deltaker1.id
        }
    }

    @Nested
    inner class GetSisteVurderingForDeltakere {
        @Test
        fun `tom set - returnerer tom map`() {
            // Arrange / Act
            val resultat = vurderingRepository.getSisteVurderingForDeltakere(emptySet())

            // Assert
            resultat.shouldBeEmpty()
        }

        @Test
        fun `ingen treff - returnerer tom map`() {
            // Arrange / Act
            val resultat = vurderingRepository.getSisteVurderingForDeltakere(setOf(UUID.randomUUID()))

            // Assert
            resultat.shouldBeEmpty()
        }

        @Test
        fun `en deltaker med en vurdering - returnerer den`() {
            // Arrange
            val deltaker = TestData.lagDeltaker()
            TestRepository.insert(deltaker)
            val vurdering = TestData.lagVurdering(deltakerId = deltaker.id)
            vurderingRepository.upsert(vurdering)

            // Act
            val resultat = vurderingRepository.getSisteVurderingForDeltakere(setOf(deltaker.id))

            // Assert
            resultat shouldHaveSize 1
            resultat[deltaker.id]!!.id shouldBe vurdering.id
        }

        @Test
        fun `en deltaker med flere vurderinger - returnerer siste basert paa gyldigFra`() {
            // Arrange
            val deltaker = TestData.lagDeltaker()
            TestRepository.insert(deltaker)
            val eldre = TestData.lagVurdering(
                deltakerId = deltaker.id,
                gyldigFra = LocalDateTime.now().minusDays(10),
                vurderingstype = Vurderingstype.OPPFYLLER_IKKE_KRAVENE,
            )
            val nyeste = TestData.lagVurdering(
                deltakerId = deltaker.id,
                gyldigFra = LocalDateTime.now(),
                vurderingstype = Vurderingstype.OPPFYLLER_KRAVENE,
            )
            vurderingRepository.upsert(eldre)
            vurderingRepository.upsert(nyeste)

            // Act
            val resultat = vurderingRepository.getSisteVurderingForDeltakere(setOf(deltaker.id))

            // Assert
            resultat shouldHaveSize 1
            resultat[deltaker.id]!!.id shouldBe nyeste.id
            resultat[deltaker.id]!!.vurderingstype shouldBe Vurderingstype.OPPFYLLER_KRAVENE
        }

        @Test
        fun `flere deltakere - returnerer siste vurdering per deltaker`() {
            // Arrange
            val deltaker1 = TestData.lagDeltaker()
            val deltaker2 = TestData.lagDeltaker()
            TestRepository.insert(deltaker1)
            TestRepository.insert(deltaker2)

            val v1Eldre = TestData.lagVurdering(
                deltakerId = deltaker1.id,
                gyldigFra = LocalDateTime.now().minusDays(5),
                vurderingstype = Vurderingstype.OPPFYLLER_IKKE_KRAVENE,
            )
            val v1Nyeste = TestData.lagVurdering(
                deltakerId = deltaker1.id,
                gyldigFra = LocalDateTime.now(),
                vurderingstype = Vurderingstype.OPPFYLLER_KRAVENE,
            )
            val v2 = TestData.lagVurdering(
                deltakerId = deltaker2.id,
                gyldigFra = LocalDateTime.now().minusDays(1),
                vurderingstype = Vurderingstype.OPPFYLLER_IKKE_KRAVENE,
            )
            vurderingRepository.upsert(v1Eldre)
            vurderingRepository.upsert(v1Nyeste)
            vurderingRepository.upsert(v2)

            // Act
            val resultat = vurderingRepository.getSisteVurderingForDeltakere(
                setOf(deltaker1.id, deltaker2.id),
            )

            // Assert
            resultat shouldHaveSize 2
            resultat[deltaker1.id]!!.id shouldBe v1Nyeste.id
            resultat[deltaker2.id]!!.id shouldBe v2.id
        }

        @Test
        fun `filtrerer paa oppgitte deltakerIder`() {
            // Arrange
            val deltaker1 = TestData.lagDeltaker()
            val deltaker2 = TestData.lagDeltaker()
            TestRepository.insert(deltaker1)
            TestRepository.insert(deltaker2)
            vurderingRepository.upsert(TestData.lagVurdering(deltakerId = deltaker1.id))
            vurderingRepository.upsert(TestData.lagVurdering(deltakerId = deltaker2.id))

            // Act — spør kun etter deltaker1
            val resultat = vurderingRepository.getSisteVurderingForDeltakere(setOf(deltaker1.id))

            // Assert
            resultat shouldHaveSize 1
            resultat.keys.single() shouldBe deltaker1.id
        }
    }

    @Nested
    inner class DeleteForDeltaker {
        @Test
        fun `deleteForDeltaker - sletter alle vurderinger for deltaker`() {
            // Arrange
            val deltaker = TestData.lagDeltaker()
            TestRepository.insert(deltaker)
            vurderingRepository.upsert(TestData.lagVurdering(deltakerId = deltaker.id))
            vurderingRepository.upsert(TestData.lagVurdering(deltakerId = deltaker.id))

            // Act
            vurderingRepository.deleteForDeltaker(deltaker.id)

            // Assert
            vurderingRepository.getForDeltaker(deltaker.id) shouldBe emptyList()
        }

        @Test
        fun `deleteForDeltaker - sletter ikke vurderinger for andre deltakere`() {
            // Arrange
            val deltaker1 = TestData.lagDeltaker()
            val deltaker2 = TestData.lagDeltaker()
            TestRepository.insert(deltaker1)
            TestRepository.insert(deltaker2)
            vurderingRepository.upsert(TestData.lagVurdering(deltakerId = deltaker1.id))
            vurderingRepository.upsert(TestData.lagVurdering(deltakerId = deltaker2.id))

            // Act
            vurderingRepository.deleteForDeltaker(deltaker1.id)

            // Assert
            vurderingRepository.getForDeltaker(deltaker1.id) shouldBe emptyList()
            vurderingRepository.getForDeltaker(deltaker2.id).size shouldBe 1
        }
    }
}
