package no.nav.amt.deltaker.bff.deltaker

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldStartWith
import no.nav.amt.deltaker.bff.utils.TestData
import no.nav.amt.deltaker.bff.utils.TestRepository
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.testing.DatabaseTestExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.postgresql.util.PSQLException
import java.time.LocalDate

class DeltakerStatusRepositoryTest {
    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Test
    fun `insertIfNotExists - kaster feil hvis flere aktive statuser`() {
        val gammelStatus = TestData.lagDeltakerStatus(
            statusType = DeltakerStatus.Type.HAR_SLUTTET,
            aarsakType = DeltakerStatus.Aarsak.Type.ANNET,
            gyldigFra = LocalDate.of(2024, 10, 5).atStartOfDay(),
        )

        val deltaker = TestData.lagDeltaker(status = gammelStatus)
        TestRepository.insert(deltaker)

        val nyStatus = TestData.lagDeltakerStatus(
            statusType = DeltakerStatus.Type.HAR_SLUTTET,
            gyldigFra = LocalDate.of(2024, 10, 5).atStartOfDay(),
        )

        val thrown = shouldThrow<PSQLException> {
            DeltakerStatusRepository.insertIfNotExists(deltaker.id, nyStatus)
        }

        thrown.message shouldStartWith "ERROR: duplicate key value violates unique constraint"
    }

    @Test
    fun `slettTidligereStatuser - skal slette alle andre statuser`() {
        val gammelStatus = TestData.lagDeltakerStatus(
            statusType = DeltakerStatus.Type.HAR_SLUTTET,
            aarsakType = DeltakerStatus.Aarsak.Type.ANNET,
            gyldigFra = LocalDate.of(2024, 10, 5).atStartOfDay(),
        )

        val nyStatus = TestData.lagDeltakerStatus(
            statusType = DeltakerStatus.Type.HAR_SLUTTET,
            gyldigFra = LocalDate.of(2024, 10, 5).atStartOfDay(),
        )

        val deltaker = TestData.lagDeltaker(status = gammelStatus)
        TestRepository.insert(deltaker)

        DeltakerStatusRepository.slettTidligereStatuser(deltaker.id, nyStatus)

        val inaktivStatus = DeltakerStatusRepository.getAktivDeltakerStatus(deltaker.id)
        inaktivStatus.shouldBeNull()

        DeltakerStatusRepository.insertIfNotExists(deltaker.id, nyStatus)
        val aktivStatus = DeltakerStatusRepository.getAktivDeltakerStatus(deltaker.id)
        aktivStatus.shouldNotBeNull()
    }
}
