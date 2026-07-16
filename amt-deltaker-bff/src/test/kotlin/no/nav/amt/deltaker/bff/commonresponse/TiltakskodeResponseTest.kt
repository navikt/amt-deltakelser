package no.nav.amt.deltaker.bff.commonresponse

import io.kotest.matchers.shouldNotBe
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import org.junit.jupiter.api.Test

class TiltakskodeResponseTest {
    @Test
    fun `visningsnavn skal være satt for alle tiltakskoder`() {
        Tiltakskode.entries.forEach {
            TiltakskodeResponse(it).visningsnavn shouldNotBe ""
        }
    }
}
