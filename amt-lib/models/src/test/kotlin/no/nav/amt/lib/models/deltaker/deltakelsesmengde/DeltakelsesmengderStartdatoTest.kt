package no.nav.amt.lib.models.deltaker.deltakelsesmengde

import io.kotest.matchers.shouldBe
import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.utils.TestData
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class DeltakelsesmengderStartdatoTest {
    @Test
    fun `tilbakedatert endring gjenoppretter senere periode med samme mengde som grunnmengden`() {
        val vedtak = TestData.lagVedtak(
            deltakelsesprosent = 100F,
            dagerPerUke = 5F,
            fattet = "2024-01-01".toDateTime(),
        )
        val senereLikMengde = TestData.lagEndreDeltakelsesmengde(
            deltakelsesprosent = 100,
            dagerPerUke = 5,
            gyldigFra = "2024-01-10".toDate(),
            opprettet = "2024-01-10".toDateTime(),
        )
        val tilbakedatertEndring = TestData.lagEndreDeltakelsesmengde(
            deltakelsesprosent = 50,
            dagerPerUke = 3,
            gyldigFra = "2024-01-05".toDate(),
            opprettet = "2024-01-11".toDateTime(),
        )
        val startdato = "2024-01-04".toDate()

        val deltakelsesmengder = TestData.lagDeltakerHistorikk(
            vedtak = listOf(vedtak),
            endringer = listOf(senereLikMengde, tilbakedatertEndring),
            endringerFraArrangor = listOf(
                TestData.lagLeggTilOppstartsdato(
                    startdato = startdato,
                    opprettet = "2024-01-02".toDateTime(),
                ),
            ),
        ).toDeltakelsesmengder()

        deltakelsesmengder.map { it.gyldigFra } shouldBe listOf(
            startdato,
            "2024-01-05".toDate(),
            "2024-01-10".toDate(),
        )
        deltakelsesmengder.map { it.deltakelsesprosent } shouldBe listOf(100F, 50F, 100F)
    }

    @Test
    fun `manglende gyldigFra i historisk endring bruker opprettelsesdato`() {
        val opprettet = LocalDateTime.parse("2024-01-05T12:00:00")
        val endring = DeltakerEndring.Endring.EndreDeltakelsesmengde(
            gyldigFra = null,
            deltakelsesprosent = 50F,
            dagerPerUke = null,
            begrunnelse = null,
        )

        endring.toDeltakelsesmengde(opprettet).gyldigFra shouldBe opprettet.toLocalDate()
        endring.toDeltakelsesmengdeEkstern(opprettet)?.gyldigFra shouldBe opprettet.toLocalDate()
    }

    @Test
    fun `startdato endres før planlagt mengde - beholder gjeldende mengde og fremtidig mengde`() {
        val deltakelsesmengder = Deltakelsesmengder(
            mengder = listOf(
                Deltakelsesmengde(
                    deltakelsesprosent = 100F,
                    dagerPerUke = null,
                    gyldigFra = "2024-01-01".toDate(),
                    opprettet = "2024-01-01".toDateTime(),
                ),
                Deltakelsesmengde(
                    deltakelsesprosent = 50F,
                    dagerPerUke = null,
                    gyldigFra = "2024-03-01".toDate(),
                    opprettet = "2024-02-01".toDateTime(),
                ),
            ),
            startdatoer = listOf("2024-01-15".toDate()),
        )

        deltakelsesmengder.map { it.gyldigFra } shouldBe listOf(
            "2024-01-15".toDate(),
            "2024-03-01".toDate(),
        )
        deltakelsesmengder.map { it.deltakelsesprosent } shouldBe listOf(100F, 50F)
    }

    @Test
    fun `startdato flyttes frem - beholder senere planlagte mengder selv om de ble opprettet tidligere`() {
        val deltakelsesmengder = Deltakelsesmengder(
            mengder = listOf(
                Deltakelsesmengde(
                    deltakelsesprosent = 40F,
                    dagerPerUke = null,
                    gyldigFra = "2024-01-01".toDate(),
                    opprettet = "2024-01-01".toDateTime(),
                ),
                Deltakelsesmengde(
                    deltakelsesprosent = 80F,
                    dagerPerUke = null,
                    gyldigFra = "2024-01-20".toDate(),
                    opprettet = "2024-01-02".toDateTime(),
                ),
                Deltakelsesmengde(
                    deltakelsesprosent = 50F,
                    dagerPerUke = null,
                    gyldigFra = "2024-01-05".toDate(),
                    opprettet = "2024-01-30".toDateTime(),
                ),
            ),
            startdatoer = listOf("2024-01-10".toDate()),
        )

        deltakelsesmengder.map { it.gyldigFra } shouldBe listOf(
            "2024-01-10".toDate(),
            "2024-01-20".toDate(),
        )
        deltakelsesmengder.map { it.deltakelsesprosent } shouldBe listOf(50F, 80F)
    }

    @Test
    fun `startdato flyttes frem - velger siste effektive periode selv om eldre korreksjon er nyere`() {
        val deltakelsesmengder = Deltakelsesmengder(
            mengder = listOf(
                Deltakelsesmengde(
                    deltakelsesprosent = 100F,
                    dagerPerUke = null,
                    gyldigFra = "2024-01-01".toDate(),
                    opprettet = "2024-01-01".toDateTime(),
                ),
                Deltakelsesmengde(
                    deltakelsesprosent = 60F,
                    dagerPerUke = null,
                    gyldigFra = "2024-01-05".toDate(),
                    opprettet = "2024-01-06".toDateTime(),
                ),
                Deltakelsesmengde(
                    deltakelsesprosent = 40F,
                    dagerPerUke = null,
                    gyldigFra = "2024-01-01".toDate(),
                    opprettet = "2024-01-07".toDateTime(),
                ),
                Deltakelsesmengde(
                    deltakelsesprosent = 80F,
                    dagerPerUke = null,
                    gyldigFra = "2024-01-20".toDate(),
                    opprettet = "2024-01-08".toDateTime(),
                ),
            ),
            startdatoer = listOf("2024-01-10".toDate()),
        )

        deltakelsesmengder.map { it.gyldigFra } shouldBe listOf(
            "2024-01-10".toDate(),
            "2024-01-20".toDate(),
        )
        deltakelsesmengder.map { it.deltakelsesprosent } shouldBe listOf(60F, 80F)
    }

    @Test
    fun `første deltakelsesmengde skal være gyldig fra deltakers startdato`() {
        val vedtak = TestData.lagVedtak(fattet = "2024-01-01".toDate().atStartOfDay())
        val startdato = TestData.lagLeggTilOppstartsdato(startdato = "2024-01-05".toDate())
        val historikk = TestData.lagDeltakerHistorikk(listOf(vedtak), endringerFraArrangor = listOf(startdato))

        val deltakelsesmengder = historikk.toDeltakelsesmengder()

        val endring = startdato.endring as EndringFraArrangor.LeggTilOppstartsdato
        deltakelsesmengder.first().gyldigFra shouldBe endring.startdato
    }

    @Test
    fun `flere deltakelsesmengder før startdato - skal kun bruke deltakelsesmengde nærmest før eller lik startdato`() {
        val vedtak = TestData.lagVedtak(fattet = "2024-01-01".toDate().atStartOfDay())
        val startdato = "2024-01-05".toDate()
        val endreDeltakelsesmengde = TestData.lagEndreDeltakelsesmengde(
            deltakelsesprosent = 42,
            gyldigFra = "2024-01-02".toDate(),
            opprettet = "2024-01-02".toDateTime(),
        )

        val historikk = TestData.lagDeltakerHistorikk(
            listOf(vedtak),
            endringer = listOf(endreDeltakelsesmengde),
            endringerFraArrangor = listOf(TestData.lagLeggTilOppstartsdato(startdato)),
        )

        val deltakelsesmengder = historikk.toDeltakelsesmengder()
        deltakelsesmengder.size shouldBe 1

        val endring = endreDeltakelsesmengde.endring as DeltakerEndring.Endring.EndreDeltakelsesmengde
        deltakelsesmengder.first().gyldigFra shouldBe startdato
        deltakelsesmengder.first().deltakelsesprosent shouldBe endring.deltakelsesprosent
    }

    @Test
    fun `startdato er før første periode - skal sette gyldig fra lik startdato`() {
        val vedtak = TestData.lagVedtak(fattet = "2024-01-10".toDate().atStartOfDay())
        val startdato = "2024-01-05".toDate()

        val historikk = TestData.lagDeltakerHistorikk(
            listOf(vedtak),
            endringerFraArrangor = listOf(TestData.lagLeggTilOppstartsdato(startdato)),
        )

        val deltakelsesmengder = historikk.toDeltakelsesmengder()
        deltakelsesmengder.size shouldBe 1

        deltakelsesmengder.first().gyldigFra shouldBe startdato
        deltakelsesmengder.first().deltakelsesprosent shouldBe vedtak.deltakerVedVedtak.deltakelsesprosent
    }

    @Test
    fun `startdato endres tilbake i tid - bruker siste endring fra forrige periode`() {
        val vedtak = TestData.lagVedtak(fattet = "2024-10-15".toDateTime())
        val startdato1 = "2024-10-30".toDate()
        val startdato2 = "2024-10-23".toDate()

        val endreDeltakelsesmengde = TestData.lagEndreDeltakelsesmengde(
            deltakelsesprosent = 42,
            gyldigFra = "2024-10-30".toDate(),
            opprettet = "2024-11-01".toDateTime(),
        )

        val historikk = TestData.lagDeltakerHistorikk(
            listOf(vedtak),
            endringerFraArrangor = listOf(
                TestData.lagLeggTilOppstartsdato(startdato1, opprettet = "2024-10-25".toDateTime()),
            ),
            endringer = listOf(
                TestData.lagEndreStartdato(startdato2, opprettet = "2024-11-07".toDateTime()),
                endreDeltakelsesmengde,
            ),
        )

        val deltakelsesmengder = historikk.toDeltakelsesmengder()
        deltakelsesmengder.size shouldBe 1

        val endring = endreDeltakelsesmengde.endring as DeltakerEndring.Endring.EndreDeltakelsesmengde
        deltakelsesmengder.first().gyldigFra shouldBe startdato2
        deltakelsesmengder.first().deltakelsesprosent shouldBe endring.deltakelsesprosent
    }

    @Test
    fun `startdato endres tilbake i tid 2 ganger - bruker siste endring fra forrige periode`() {
        val vedtak = TestData.lagVedtak(fattet = "2024-10-15".toDateTime())
        val startdato1 = "2024-10-30".toDate()
        val startdato2 = "2024-10-23".toDate()
        val startdato3 = "2024-10-20".toDate()

        val endreDeltakelsesmengde = TestData.lagEndreDeltakelsesmengde(
            deltakelsesprosent = 42,
            gyldigFra = "2024-10-29".toDate(),
            opprettet = "2024-11-01".toDateTime(),
        )

        val historikk = TestData.lagDeltakerHistorikk(
            listOf(vedtak),
            endringerFraArrangor = listOf(
                TestData.lagLeggTilOppstartsdato(startdato1, opprettet = "2024-10-25".toDateTime()),
            ),
            endringer = listOf(
                TestData.lagEndreStartdato(startdato2, opprettet = "2024-11-07".toDateTime()),
                TestData.lagEndreStartdato(startdato3, opprettet = "2024-11-08".toDateTime()),
                endreDeltakelsesmengde,
            ),
        )

        val deltakelsesmengder = historikk.toDeltakelsesmengder()
        deltakelsesmengder.size shouldBe 1

        val endring = endreDeltakelsesmengde.endring as DeltakerEndring.Endring.EndreDeltakelsesmengde
        deltakelsesmengder.first().gyldigFra shouldBe startdato3
        deltakelsesmengder.first().deltakelsesprosent shouldBe endring.deltakelsesprosent
    }

    @Test
    fun `startdato endres frem i tid - bruker siste endring fra forrige periode`() {
        val vedtak = TestData.lagVedtak(fattet = "2024-10-15".toDateTime())
        val startdato1 = "2024-10-30".toDate()
        val startdato2 = "2024-11-01".toDate()

        val endreDeltakelsesmengde = TestData.lagEndreDeltakelsesmengde(
            deltakelsesprosent = 42,
            gyldigFra = "2024-10-29".toDate(),
            opprettet = "2024-10-29".toDateTime(),
        )

        val historikk = TestData.lagDeltakerHistorikk(
            listOf(vedtak),
            endringerFraArrangor = listOf(
                TestData.lagLeggTilOppstartsdato(startdato1, opprettet = "2024-10-25".toDateTime()),
            ),
            endringer = listOf(
                TestData.lagEndreStartdato(startdato2, opprettet = "2024-11-07".toDateTime()),
                endreDeltakelsesmengde,
            ),
        )

        val deltakelsesmengder = historikk.toDeltakelsesmengder()
        deltakelsesmengder.size shouldBe 1

        val endring = endreDeltakelsesmengde.endring as DeltakerEndring.Endring.EndreDeltakelsesmengde
        deltakelsesmengder.first().gyldigFra shouldBe startdato2
        deltakelsesmengder.first().deltakelsesprosent shouldBe endring.deltakelsesprosent
    }

    @Test
    fun `startdato endres frem i tid - fremtidig deltakelsesmengde - bruker fremtidig deltakelsesmengde`() {
        val vedtak = TestData.lagVedtak(fattet = "2024-10-15".toDateTime())
        val startdato1 = "2024-10-30".toDate()
        val startdato2 = "2024-11-10".toDate()

        val endreDeltakelsesmengde1 = TestData.lagEndreDeltakelsesmengde(
            deltakelsesprosent = 42,
            gyldigFra = "2024-10-29".toDate(),
            opprettet = "2024-10-29".toDateTime(),
        )

        val endreDeltakelsesmengde2 = TestData.lagEndreDeltakelsesmengde(
            deltakelsesprosent = 50,
            gyldigFra = startdato2,
            opprettet = "2024-10-30".toDateTime(),
        )

        val historikk = TestData.lagDeltakerHistorikk(
            listOf(vedtak),
            endringerFraArrangor = listOf(
                TestData.lagLeggTilOppstartsdato(startdato1, opprettet = "2024-10-25".toDateTime()),
            ),
            endringer = listOf(
                TestData.lagEndreStartdato(startdato2, opprettet = "2024-11-07".toDateTime()),
                endreDeltakelsesmengde1,
                endreDeltakelsesmengde2,
            ),
        )

        val deltakelsesmengder = historikk.toDeltakelsesmengder()
        deltakelsesmengder.size shouldBe 1

        val endring = endreDeltakelsesmengde2.endring as DeltakerEndring.Endring.EndreDeltakelsesmengde
        deltakelsesmengder.first().gyldigFra shouldBe startdato2
        deltakelsesmengder.first().deltakelsesprosent shouldBe endring.deltakelsesprosent
    }

    @Test
    fun `startdato endres frem og tilbake i tid - bruker nyeste startdato til å avgrense perioden`() {
        val vedtak = TestData.lagVedtak(fattet = "2024-10-15".toDateTime())
        val startdato1 = "2024-10-30".toDate()
        val startdato2 = "2024-11-10".toDate()

        val ugyldigDeltakelsesmengde = TestData.lagEndreDeltakelsesmengde(
            deltakelsesprosent = 42,
            gyldigFra = "2024-10-29".toDate(),
            opprettet = "2024-10-29".toDateTime(),
        )

        val forsteDeltakelsesmengde = TestData.lagEndreDeltakelsesmengde(
            deltakelsesprosent = 50,
            gyldigFra = startdato2,
            opprettet = "2024-10-30".toDateTime(),
        )

        val andreDeltakelsesmengde = TestData.lagEndreDeltakelsesmengde(
            deltakelsesprosent = 51,
            gyldigFra = "2024-11-01".toDate(),
            opprettet = "2024-11-09".toDateTime(),
        )

        val historikk = TestData.lagDeltakerHistorikk(
            listOf(vedtak),
            endringerFraArrangor = listOf(
                TestData.lagLeggTilOppstartsdato(startdato1, opprettet = "2024-10-25".toDateTime()),
            ),
            endringer = listOf(
                TestData.lagEndreStartdato(startdato2, opprettet = "2024-11-07".toDateTime()),
                TestData.lagEndreStartdato(startdato1, opprettet = "2024-11-08".toDateTime()),
                ugyldigDeltakelsesmengde,
                forsteDeltakelsesmengde,
                andreDeltakelsesmengde,
            ),
        )

        val deltakelsesmengder = historikk.toDeltakelsesmengder()
        deltakelsesmengder.size shouldBe 2

        val deltakelesesmengde1 = forsteDeltakelsesmengde.endring as DeltakerEndring.Endring.EndreDeltakelsesmengde
        deltakelsesmengder.first().gyldigFra shouldBe startdato1
        deltakelsesmengder.first().deltakelsesprosent shouldBe deltakelesesmengde1.deltakelsesprosent

        val deltakelsesmengde2 = andreDeltakelsesmengde.endring as DeltakerEndring.Endring.EndreDeltakelsesmengde
        deltakelsesmengder.last().gyldigFra shouldBe deltakelsesmengde2.gyldigFra
        deltakelsesmengder.last().deltakelsesprosent shouldBe deltakelsesmengde2.deltakelsesprosent
    }

    @Test
    fun `startdato fjernet og lagt til pa nytt - mengde justeres til ny startdato`() {
        // Scenario: mengde uten startdato (gyldigFra = opprettelsesdato), startdato settes,
        // fjernes (FjernOppstartsdato), og settes igjen – resultatet skal ha gyldigFra = ny startdato
        val vedtak = TestData.lagVedtak(fattet = "2024-10-15".toDateTime())
        val startdato1 = "2024-10-30".toDate()
        val startdato2 = "2024-11-05".toDate()

        val endreDeltakelsesmengde = TestData.lagEndreDeltakelsesmengde(
            deltakelsesprosent = 42,
            gyldigFra = "2024-10-15".toDate(), // Satt uten startdato, gyldigFra = opprettelsesdato
            opprettet = "2024-10-15".toDateTime(),
        )

        val historikk = TestData.lagDeltakerHistorikk(
            listOf(vedtak),
            endringerFraArrangor = listOf(
                TestData.lagLeggTilOppstartsdato(startdato1, opprettet = "2024-10-25".toDateTime()),
            ),
            endringer = listOf(
                endreDeltakelsesmengde,
                TestData.lagFjernOppstartsdato(opprettet = "2024-10-28".toDateTime()),
                TestData.lagEndreStartdato(startdato2, opprettet = "2024-10-29".toDateTime()),
            ),
        )

        val deltakelsesmengder = historikk.toDeltakelsesmengder()

        deltakelsesmengder.size shouldBe 1
        // gyldigFra skal justeres til ny startdato (startdato2), ikke forbli på gammel dato
        deltakelsesmengder.first().gyldigFra shouldBe startdato2
    }

    @Test
    fun `startdato fjernet - ingen ny startdato - mengde finnes fortsatt i historikk`() {
        // Scenario: startdato settes og fjernes – deltaker.startdato = null
        // Mengden skal fortsatt finnes i toDeltakelsesmengder()
        val vedtak = TestData.lagVedtak(fattet = "2024-10-15".toDateTime())
        val startdato = "2024-10-30".toDate()

        val historikk = TestData.lagDeltakerHistorikk(
            listOf(vedtak),
            endringerFraArrangor = listOf(
                TestData.lagLeggTilOppstartsdato(startdato, opprettet = "2024-10-25".toDateTime()),
            ),
            endringer = listOf(
                TestData.lagFjernOppstartsdato(opprettet = "2024-10-28".toDateTime()),
            ),
        )

        // Med startdato=null brukes toDeltakelsesmengder() direkte uten periode-filter
        val deltakelsesmengder = historikk.toDeltakelsesmengder()

        // Mengde fra vedtak skal fortsatt finnes
        deltakelsesmengder.size shouldBe 1
    }

    @Test
    fun `startdato fjernet - fjerner startdatobegrensningen fra deltakelsesmengdene`() {
        val vedtak = TestData.lagVedtak(
            fattet = "2024-10-15".toDateTime(),
        )
        val endreDeltakelsesmengde = TestData.lagEndreDeltakelsesmengde(
            deltakelsesprosent = 60,
            gyldigFra = "2024-11-01".toDate(),
            opprettet = "2024-10-27".toDateTime(),
        )
        val historikk = TestData.lagDeltakerHistorikk(
            vedtak = listOf(vedtak),
            endringerFraArrangor = listOf(
                TestData.lagLeggTilOppstartsdato(
                    startdato = "2024-10-20".toDate(),
                    opprettet = "2024-10-20".toDateTime(),
                ),
            ),
            endringer = listOf(
                endreDeltakelsesmengde,
                TestData.lagFjernOppstartsdato(opprettet = "2024-11-02".toDateTime()),
            ),
        )

        val deltakelsesmengder = historikk.toDeltakelsesmengder()

        deltakelsesmengder.map { it.gyldigFra } shouldBe listOf(
            "2024-10-15".toDate(),
            "2024-11-01".toDate(),
        )
        deltakelsesmengder.map { it.deltakelsesprosent } shouldBe listOf(100F, 60F)
    }
}
