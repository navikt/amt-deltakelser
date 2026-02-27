package no.nav.amt.deltaker.deltaker.endring.extensions

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.deltaker.endring.extensions.EndringTestUtils.mockDeltakelsesmengdeProvider
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestData.randomEnhetsnummer
import no.nav.amt.deltaker.utils.data.TestData.randomNavIdent
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.SluttarsakRequest
import org.junit.jupiter.api.Test

class EndreSluttarsakExtensionsTest {
    @Test
    fun `endret sluttarsak`() {
        val resultat = endringsrequest
            .toEndring()
            .oppdaterDeltaker(
                deltaker = deltaker,
                getDeltakelsemengder = mockDeltakelsesmengdeProvider,
            ).shouldBeSuccess()

        assertSoftly(resultat.deltaker) {
            status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
            status.aarsak
                .shouldNotBeNull()
                .type shouldBe DeltakerStatus.Aarsak.Type.FATT_JOBB
        }
    }

    @Test
    fun `kaller sluttarsak direkte med extension-metode`() {
        val resultat = endringsrequest.toEndring().endreSluttarsak(deltaker)

        val oppdatertDeltaker = resultat.deltaker

        oppdatertDeltaker.status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
        oppdatertDeltaker.status.aarsak
            .shouldNotBeNull()
            .type shouldBe DeltakerStatus.Aarsak.Type.FATT_JOBB
    }

    companion object {
        private val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(
                statusType = DeltakerStatus.Type.HAR_SLUTTET,
                aarsakType = DeltakerStatus.Aarsak.Type.SYK,
            ),
        )

        private val endringsrequest = SluttarsakRequest(
            endretAv = randomNavIdent(),
            endretAvEnhet = randomEnhetsnummer(),
            aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
            begrunnelse = null,
            forslagId = null,
        )
    }
}
