package no.nav.amt.deltaker.deltaker.db

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.testing.DatabaseTestExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDateTime
import java.util.UUID

class DeltakerStatusRepositoryTest {
    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Nested
    inner class DeaktiverTidligereStatuserTests {
        val deltaker = lagDeltaker(
            status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
        )

        @BeforeEach
        fun setup() = TestRepository.insert(deltaker)

        @Test
        fun `har fremtidig avsluttende status, deaktiverer ikke fremtidig status`() = runTest {
            val avsluttendeFremtidigStatus = lagDeltakerStatus(
                statusType = DeltakerStatus.Type.HAR_SLUTTET,
                gyldigFra = LocalDateTime.now().plusDays(3),
            )
            DeltakerStatusRepository.lagreStatus(deltaker.id, avsluttendeFremtidigStatus)

            // act
            DeltakerStatusRepository.deaktiverTidligereStatuser(
                deltakerId = deltaker.id,
                excludeStatusId = UUID.randomUUID(),
                erDeltakerSluttdatoEndret = false,
            )

            // assert
            DeltakerStatusRepository.get(deltaker.status.id).gyldigTil.shouldNotBeNull()
            DeltakerStatusRepository.get(avsluttendeFremtidigStatus.id).gyldigTil.shouldBeNull()
        }

        @Test
        fun `har fremtidig avsluttende status, deaktiverer fremtidig status`() = runTest {
            val avsluttendeFremtidigStatus = lagDeltakerStatus(
                statusType = DeltakerStatus.Type.HAR_SLUTTET,
                gyldigFra = LocalDateTime.now().plusDays(3),
            )
            DeltakerStatusRepository.lagreStatus(deltaker.id, avsluttendeFremtidigStatus)

            // act
            DeltakerStatusRepository.deaktiverTidligereStatuser(
                deltakerId = deltaker.id,
                excludeStatusId = UUID.randomUUID(),
                erDeltakerSluttdatoEndret = true,
            )

            // assert
            DeltakerStatusRepository.get(deltaker.status.id).gyldigTil.shouldNotBeNull()
            DeltakerStatusRepository.get(avsluttendeFremtidigStatus.id).gyldigTil.shouldNotBeNull()
        }

        @Test
        fun `har fremtidig ikke-avsluttende status, deaktiverer fremtidig status`() = runTest {
            val ikkeAvsluttendeFremtidigStatus = lagDeltakerStatus(
                statusType = DeltakerStatus.Type.UTKAST_TIL_PAMELDING,
                gyldigFra = LocalDateTime.now().plusDays(3),
            )
            DeltakerStatusRepository.lagreStatus(deltaker.id, ikkeAvsluttendeFremtidigStatus)

            // act
            DeltakerStatusRepository.deaktiverTidligereStatuser(
                deltakerId = deltaker.id,
                excludeStatusId = UUID.randomUUID(),
                erDeltakerSluttdatoEndret = false,
            )

            // assert
            DeltakerStatusRepository.get(deltaker.status.id).gyldigTil.shouldNotBeNull()
            DeltakerStatusRepository.get(ikkeAvsluttendeFremtidigStatus.id).gyldigTil.shouldNotBeNull()
        }
    }

    @Test
    fun `slettTidligereFremtidigeStatuser - skal slette fremtidige statuser`() {
        val deltaker = lagDeltaker(
            status = lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
        )
        TestRepository.insert(deltaker)

        DeltakerStatusRepository.lagreStatus(
            deltaker.id,
            lagDeltakerStatus(
                statusType = DeltakerStatus.Type.DELTAR,
                gyldigFra = LocalDateTime.now().plusDays(1),
            ),
        )

        DeltakerStatusRepository.getFremtidige(deltaker.id).size shouldBe 1

        // act
        DeltakerStatusRepository.slettTidligereFremtidigeStatuser(
            deltakerId = deltaker.id,
            excludeStatusId = UUID.randomUUID(),
        )

        // assert
        DeltakerStatusRepository.getFremtidige(deltaker.id).shouldBeEmpty()
        DeltakerStatusRepository.get(deltaker.status.id).gyldigTil.shouldBeNull()
    }

    @Test
    fun `slett - skal slette status`() {
        val deltakerStatus = lagDeltakerStatus(DeltakerStatus.Type.DELTAR)

        val deltaker = lagDeltaker(status = deltakerStatus)
        TestRepository.insert(deltaker)

        DeltakerStatusRepository.get(deltakerStatus.id).shouldNotBeNull()

        // act
        DeltakerStatusRepository.slettStatus(deltakerId = deltaker.id)

        // assert
        shouldThrow<NoSuchElementException> {
            DeltakerStatusRepository.get(deltakerStatus.id)
        }
    }
}
