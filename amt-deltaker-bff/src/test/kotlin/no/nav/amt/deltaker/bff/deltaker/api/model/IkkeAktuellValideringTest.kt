package no.nav.amt.deltaker.bff.deltaker.api.model

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import no.nav.amt.deltaker.bff.utils.data.TestData
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class IkkeAktuellValideringTest {
    @Test
    fun `valider - deltaker er uendret - feiler`() {
        shouldThrow<IllegalArgumentException> {
            val deltaker = TestData.lagDeltaker(
                status = TestData.lagDeltakerStatus(
                    statusType = DeltakerStatus.Type.IKKE_AKTUELL,
                    aarsakType = DeltakerStatus.Aarsak.Type.FATT_JOBB,
                ),
            )
            val request = IkkeAktuellRequest(
                aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
                begrunnelse = "Begrunnelse",
                forslagId = UUID.randomUUID(),
            )

            request.valider(deltaker)
        }
    }

    @Test
    fun `valider - deltaker har annen sluttaarsak - ok`() {
        shouldNotThrow<IllegalArgumentException> {
            val deltaker = TestData.lagDeltaker(
                status = TestData.lagDeltakerStatus(
                    statusType = DeltakerStatus.Type.IKKE_AKTUELL,
                    aarsakType = DeltakerStatus.Aarsak.Type.FATT_JOBB,
                ),
            )
            val request = IkkeAktuellRequest(
                aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.IKKE_MOTT, null),
                begrunnelse = "Begrunnelse",
                forslagId = UUID.randomUUID(),
            )

            request.valider(deltaker)
        }
    }

    @Test
    fun `valider - deltaker deltar, mindre enn 15 dager siden, forslag ikke aktuell - ok`() {
        shouldNotThrow<IllegalArgumentException> {
            val deltaker = TestData.lagDeltaker(
                status = TestData.lagDeltakerStatus(
                    statusType = DeltakerStatus.Type.DELTAR,
                    gyldigFra = LocalDateTime.now().minusDays(10),
                ),
            )
            val request = IkkeAktuellRequest(
                aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.IKKE_MOTT, null),
                begrunnelse = "Begrunnelse",
                forslagId = UUID.randomUUID(),
            )

            request.valider(deltaker)
        }
    }

    @Test
    fun `valider - deltaker deltar, mer enn 15 dager siden, forslag ikke aktuell - ok`() {
        shouldThrow<IllegalArgumentException> {
            val deltaker = TestData.lagDeltaker(
                status = TestData.lagDeltakerStatus(
                    statusType = DeltakerStatus.Type.DELTAR,
                    gyldigFra = LocalDateTime.now().minusDays(16),
                ),
            )
            val request = IkkeAktuellRequest(
                aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.IKKE_MOTT, null),
                begrunnelse = "Begrunnelse",
                forslagId = UUID.randomUUID(),
            )

            request.valider(deltaker)
        }
    }

    @Test
    fun `valider - deltaker deltar, mindre enn 15 dager siden, ikke forslag - feiler`() {
        shouldThrow<IllegalArgumentException> {
            val deltaker = TestData.lagDeltaker(
                status = TestData.lagDeltakerStatus(
                    statusType = DeltakerStatus.Type.DELTAR,
                    gyldigFra = LocalDateTime.now().minusDays(10),
                ),
            )
            val request = IkkeAktuellRequest(
                aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.IKKE_MOTT, null),
                begrunnelse = "Begrunnelse",
                forslagId = null,
            )

            request.valider(deltaker)
        }
    }
}
