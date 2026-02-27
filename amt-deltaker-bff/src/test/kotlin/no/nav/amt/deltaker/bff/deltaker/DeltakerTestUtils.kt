package no.nav.amt.deltaker.bff.deltaker

import io.kotest.matchers.shouldBe
import no.nav.amt.lib.models.deltaker.DeltakerVedVedtak
import no.nav.amt.lib.models.deltaker.Vedtak
import no.nav.amt.lib.testing.shouldBeCloseTo

object DeltakerTestUtils {
    fun sammenlignDeltakereVedVedtak(a: DeltakerVedVedtak, b: DeltakerVedVedtak) {
        a.id shouldBe b.id
        a.startdato shouldBe b.startdato
        a.sluttdato shouldBe b.sluttdato
        a.dagerPerUke shouldBe b.dagerPerUke
        a.deltakelsesprosent shouldBe b.deltakelsesprosent
        a.bakgrunnsinformasjon shouldBe b.bakgrunnsinformasjon
        a.deltakelsesinnhold shouldBe b.deltakelsesinnhold
        a.status.id shouldBe b.status.id
        a.status.type shouldBe b.status.type
        a.status.aarsak shouldBe b.status.aarsak
        a.status.gyldigFra shouldBeCloseTo b.status.gyldigFra
        a.status.gyldigTil shouldBeCloseTo b.status.gyldigTil
        a.status.opprettet shouldBeCloseTo b.status.opprettet
    }

    fun sammenlignVedtak(a: Vedtak, b: Vedtak) {
        a.id shouldBe b.id
        a.deltakerId shouldBe b.deltakerId
        a.fattet shouldBeCloseTo b.fattet
        a.gyldigTil shouldBeCloseTo b.gyldigTil
        sammenlignDeltakereVedVedtak(a.deltakerVedVedtak, b.deltakerVedVedtak)
        a.fattetAvNav shouldBe b.fattetAvNav
        a.opprettet shouldBeCloseTo b.opprettet
        a.opprettetAv shouldBe b.opprettetAv
        a.opprettetAvEnhet shouldBe b.opprettetAvEnhet
        a.sistEndret shouldBeCloseTo b.sistEndret
        a.sistEndretAv shouldBe b.sistEndretAv
        a.sistEndretAvEnhet shouldBe b.sistEndretAvEnhet
    }
}
