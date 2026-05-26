package no.nav.amt.deltaker.veileder.endring.extensions

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.veileder.endring.extensions.EndringTestUtils.mockDeltakelsesmengdeProvider
import no.nav.amt.internapi.deltaker.request.SluttdatoRequest
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.testing.utils.TestData.randomEnhetsnummer
import no.nav.amt.lib.testing.utils.TestData.randomNavIdent
import org.junit.jupiter.api.Test
import java.time.LocalDate

class EndreSluttdatoExtensionsTest {
    @Test
    fun `oppdaterDeltaker - endret sluttdato`() {
        val deltaker = TestData.lagDeltaker()
        val endringsrequest = SluttdatoRequest(
            endretAv = randomNavIdent(),
            endretAvEnhet = randomEnhetsnummer(),
            forslagId = null,
            sluttdato = LocalDate.now().minusWeeks(1),
            begrunnelse = "begrunnelse",
        )

        val resultat = endringsrequest
            .toEndring()
            .anvendPaaDeltaker(
                deltaker = deltaker,
                getDeltakelsemengder = mockDeltakelsesmengdeProvider,
            ).shouldBeSuccess()

        assertSoftly(resultat.deltaker) {
            sluttdato shouldBe endringsrequest.sluttdato
            status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
        }
    }

    @Test
    fun `oppdaterDeltaker - endret sluttdato frem i tid - endrer status og sluttdato`() {
        val deltaker = TestData.lagDeltaker()
        val endringsrequest = SluttdatoRequest(
            endretAv = randomNavIdent(),
            endretAvEnhet = randomEnhetsnummer(),
            forslagId = null,
            sluttdato = LocalDate.now().plusWeeks(1),
            begrunnelse = "begrunnelse",
        )

        val resultat = endringsrequest
            .toEndring()
            .anvendPaaDeltaker(
                deltaker = deltaker,
                getDeltakelsemengder = mockDeltakelsesmengdeProvider,
            ).shouldBeSuccess()

        assertSoftly(resultat.deltaker) {
            sluttdato shouldBe endringsrequest.sluttdato
            status.type shouldBe DeltakerStatus.Type.DELTAR
        }
    }
}
