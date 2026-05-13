package no.nav.amt.deltaker.bff.clients

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltakerResponse
import org.junit.jupiter.api.Test

class DeltakerAmtDeltakerResponseTest {
    @Test
    fun `skal mapppe response til model korrekt`() {
        val responseInTest = lagDeltakerResponse()

        val model = ModelMapper.toDeltaker(responseInTest)

        responseInTest.startdato.shouldNotBeNull()
        responseInTest.sluttdato.shouldNotBeNull()
        responseInTest.dagerPerUke.shouldNotBeNull()
        responseInTest.deltakelsesprosent.shouldNotBeNull()
        responseInTest.bakgrunnsinformasjon.shouldNotBeNull()
        responseInTest.deltakelsesinnhold.shouldNotBeNull()
        responseInTest.vedtaksinformasjon.shouldNotBeNull()
        responseInTest.endringsforslagFraArrangor.shouldNotBeEmpty()
        responseInTest.soktInnDato.shouldNotBeNull()
        responseInTest.deltakelsesmengder.shouldNotBeNull()

        assertSoftly(model) {
            id shouldBe responseInTest.id
            navBruker shouldBe ModelMapper.toNavBruker(responseInTest.navBruker)
            gjennomforing shouldBe ModelMapper.toGjennomforing(responseInTest.gjennomforing)
            startdato shouldBe responseInTest.startdato
            sluttdato shouldBe responseInTest.sluttdato
            dagerPerUke shouldBe responseInTest.dagerPerUke
            deltakelsesprosent shouldBe responseInTest.deltakelsesprosent
            bakgrunnsinformasjon shouldBe responseInTest.bakgrunnsinformasjon
            deltakelsesinnhold shouldBe responseInTest.deltakelsesinnhold
            status shouldBe responseInTest.status
            sistEndret shouldBe responseInTest.sistEndret
            erManueltDeltMedArrangor shouldBe responseInTest.erManueltDeltMedArrangor
            erLaastForEndringer shouldBe responseInTest.erLaastForEndringer
            vedtaksinformasjon shouldBe ModelMapper.toVedtaksinformasjon(responseInTest.vedtaksinformasjon!!)
            endringsforslagFraArrangor shouldBe responseInTest.endringsforslagFraArrangor
            prisinformasjon shouldBe responseInTest.prisinformasjon
            soktInnDato shouldBe responseInTest.soktInnDato
            deltakelsesmengder shouldBe responseInTest.deltakelsesmengder
            importertFraArena shouldBe responseInTest.importertFraArena
        }
    }
}
