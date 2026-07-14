package no.nav.amt.lib.models.deltakerliste.tiltakstype

import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

class TiltakskodeTest {
    @Test
    fun `visningsnavn skal være satt for alle tiltakskoder`() {
        Tiltakskode.entries.forEach {
            it.visningsnavn shouldNotBe ""
        }
    }
}
