package no.nav.amt.deltaker.tiltaksarrangor.vurdering

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.models.arrangor.melding.Vurderingstype
import no.nav.amt.lib.testing.DatabaseTestExtension
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDateTime

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
