package no.nav.amt.lib.models.deltaker.deltakelsesmengde

import io.kotest.matchers.shouldBe
import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.utils.TestData
import org.junit.jupiter.api.Test

class DeltakelsesmengderStartdatoTest {
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
}
