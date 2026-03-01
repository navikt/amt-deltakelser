package no.nav.amt.deltaker.bff.utils

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.bff.deltaker.model.Deltaker
import no.nav.amt.lib.testing.shouldBeCloseTo

object DeltakerTestUtils {
    fun sammenlignDeltakere(actual: Deltaker, expected: Deltaker) {
        assertSoftly(actual) {
            id shouldBe expected.id
            navBruker shouldBe expected.navBruker
            startdato shouldBe expected.startdato
            sluttdato shouldBe expected.sluttdato
            dagerPerUke shouldBe expected.dagerPerUke
            deltakelsesprosent shouldBe expected.deltakelsesprosent
            bakgrunnsinformasjon shouldBe expected.bakgrunnsinformasjon
            deltakelsesinnhold shouldBe expected.deltakelsesinnhold
            historikk shouldBe expected.historikk
            sistEndret shouldBeCloseTo expected.sistEndret
            erManueltDeltMedArrangor shouldBe expected.erManueltDeltMedArrangor
            kanEndres shouldBe expected.kanEndres

            assertSoftly(status) {
                id shouldBe expected.status.id
                type shouldBe expected.status.type
                aarsak shouldBe expected.status.aarsak
                gyldigFra shouldBeCloseTo expected.status.gyldigFra
                gyldigTil shouldBeCloseTo expected.status.gyldigTil
                opprettet shouldBeCloseTo expected.status.opprettet
            }
        }
    }
}
