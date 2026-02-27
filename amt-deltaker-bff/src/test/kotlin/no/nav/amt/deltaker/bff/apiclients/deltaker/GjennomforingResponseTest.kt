package no.nav.amt.deltaker.bff.apiclients.deltaker

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.bff.utils.data.TestData.lagGjennomforingResponse
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import org.junit.jupiter.api.Test

class GjennomforingResponseTest {
    @Test
    fun `skal mapppe response til model korrekt`() {
        val response = lagGjennomforingResponse(pameldingType = GjennomforingPameldingType.DIREKTE_VEDTAK)

        val model = ModelMapper.toGjennomforing(response)

        assertSoftly(model) {
            id shouldBe response.id
            navn shouldBe response.navn
            tiltak shouldBe response.tiltakstype
            status shouldBe response.status.shouldNotBeNull()
            startDato shouldBe response.startDato.shouldNotBeNull()
            sluttDato shouldBe response.sluttDato.shouldNotBeNull()
            oppstart shouldBe response.oppstart.shouldNotBeNull()
            apentForPamelding shouldBe response.apentForPamelding
            oppmoteSted shouldBe response.oppmoteSted.shouldNotBeNull()
            pameldingstype shouldBe response.pameldingstype.shouldNotBeNull()
            arrangor shouldBe ModelMapper.toArrangor(response.arrangor)
        }
    }
}
