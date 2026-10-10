package no.nav.amt.deltaker.bff.innbygger.api

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.amt.deltaker.bff.clients.ModelMapper
import no.nav.amt.deltaker.bff.commonresponse.DeltakelsesinnholdResponse.InnholdResponse
import no.nav.amt.deltaker.bff.commonresponse.TiltakskodeResponse
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltakelsesinnhold
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltakerResponse
import no.nav.amt.deltaker.bff.utils.TestData.lagForslag
import no.nav.amt.deltaker.bff.utils.TestData.lagGjennomforingResponse
import no.nav.amt.deltaker.bff.veileder.api.response.DeltakerStatusResponse
import no.nav.amt.internapi.deltaker.response.DeltakelsesmengdeResponse
import org.junit.jupiter.api.Test
import java.time.LocalDate
import no.nav.amt.deltaker.bff.veileder.api.response.DeltakelsesmengdeResponse as DeltakelsesmengdeVeilederResponse
import no.nav.amt.deltaker.bff.veileder.api.response.DeltakelsesmengderResponse as DeltakelsesmengderVeilederResponse

class InnbyggerDeltakerResponseTest {
    @Test
    fun `fromModel - deltaker med alle felter - mapper alle felter korrekt`() {
        val deltakerResponse = lagDeltakerResponse()
        val gyldigeDeltakelsesmengder = listOf(
            DeltakelsesmengdeResponse(60F, 3F, LocalDate.now()),
            DeltakelsesmengdeResponse(80F, 4F, LocalDate.now().plusDays(7)),
        )
        val model = ModelMapper.toDeltaker(
            deltakerResponse.copy(gyldigeDeltakelsesmengder = gyldigeDeltakelsesmengder),
        )

        val result = InnbyggerDeltakerResponse.fromModel(deltaker = model)
        val deltakelsesmengder = model.deltakelsesmengder
            ?.let(::DeltakelsesmengderVeilederResponse)
            ?: DeltakelsesmengderVeilederResponse()
        val gyldigeDeltakelsesmengderResponse = gyldigeDeltakelsesmengder.map(::DeltakelsesmengdeVeilederResponse)

        result.deltakerId shouldBe model.id
        result.status shouldBe DeltakerStatusResponse(model.status)
        result.startdato shouldBe model.startdato
        result.sluttdato shouldBe model.sluttdato
        result.dagerPerUke shouldBe model.dagerPerUke
        result.deltakelsesprosent shouldBe model.deltakelsesprosent
        result.bakgrunnsinformasjon shouldBe model.bakgrunnsinformasjon
        result.erManueltDeltMedArrangor shouldBe model.erManueltDeltMedArrangor
        result.adresseDelesMedArrangor shouldBe model.adresseDelesMedArrangor
        result.deltakelsesmengder shouldBe deltakelsesmengder
        result.gyldigeDeltakelsesmengder shouldBe gyldigeDeltakelsesmengderResponse
    }

    @Test
    fun `fromModel - deltaker med deltakelsesinnhold - mapper innhold`() {
        val deltakerResponse = lagDeltakerResponse(deltakelsesinnhold = lagDeltakelsesinnhold())
        val model = ModelMapper.toDeltaker(deltakerResponse)

        val result = InnbyggerDeltakerResponse.fromModel(deltaker = model)

        result.deltakelsesinnhold shouldNotBe null
        result.deltakelsesinnhold!!.ledetekst shouldBe model.deltakelsesinnhold!!.ledetekst
        result.deltakelsesinnhold.innhold shouldBe model.deltakelsesinnhold.innhold
            .map { InnholdResponse(it) }
    }

    @Test
    fun `fromModel - deltaker uten deltakelsesinnhold - returnerer null`() {
        val deltakerResponse = lagDeltakerResponse(deltakelsesinnhold = null)
        val model = ModelMapper.toDeltaker(deltakerResponse)

        val result = InnbyggerDeltakerResponse.fromModel(deltaker = model)

        result.deltakelsesinnhold shouldBe null
    }

    @Test
    fun `fromModel - deltaker med vedtaksinformasjon - mapper vedtaksinformasjon`() {
        val deltakerResponse = lagDeltakerResponse()
        val model = ModelMapper.toDeltaker(deltakerResponse)

        val result = InnbyggerDeltakerResponse.fromModel(deltaker = model)

        result.vedtaksinformasjon shouldNotBe null
        result.vedtaksinformasjon!!.fattet shouldBe model.vedtaksinformasjon!!.fattet
        result.vedtaksinformasjon.fattetAvNav shouldBe model.vedtaksinformasjon.fattetAvNav
        result.vedtaksinformasjon.opprettet shouldBe model.vedtaksinformasjon.opprettet
        result.vedtaksinformasjon.opprettetAv shouldBe model.vedtaksinformasjon.opprettetAv
        result.vedtaksinformasjon.sistEndret shouldBe model.vedtaksinformasjon.sistEndret
        result.vedtaksinformasjon.sistEndretAv shouldBe model.vedtaksinformasjon.sistEndretAv
        result.vedtaksinformasjon.sistEndretAvEnhet shouldBe model.vedtaksinformasjon.sistEndretAvEnhet
    }

    @Test
    fun `fromModel - deltaker uten vedtaksinformasjon - returnerer null`() {
        val deltakerResponse = lagDeltakerResponse(vedtaksinformasjon = null)
        val model = ModelMapper.toDeltaker(deltakerResponse)

        val result = InnbyggerDeltakerResponse.fromModel(deltaker = model)

        result.vedtaksinformasjon shouldBe null
    }

    @Test
    fun `fromModel - deltaker med forslag - mapper forslag med tom ansatte og enheter`() {
        val forslag = lagForslag()
        val gjennomforing = lagGjennomforingResponse()
        val deltakerResponse = lagDeltakerResponse(
            deltakerliste = gjennomforing,
            endringsforslagFraArrangor = listOf(forslag),
        )
        val model = ModelMapper.toDeltaker(deltakerResponse)

        val result = InnbyggerDeltakerResponse.fromModel(deltaker = model)

        result.forslag.size shouldBe 1
        result.forslag[0].id shouldBe forslag.id
    }

    @Test
    fun `fromModel - deltaker uten forslag - returnerer tom liste`() {
        val deltakerResponse = lagDeltakerResponse(endringsforslagFraArrangor = emptyList())
        val model = ModelMapper.toDeltaker(deltakerResponse)

        val result = InnbyggerDeltakerResponse.fromModel(deltaker = model)

        result.forslag shouldBe emptyList()
    }

    @Test
    fun `fromModel - mapper deltakerliste korrekt`() {
        val gjennomforing = lagGjennomforingResponse()
        val deltakerResponse = lagDeltakerResponse(deltakerliste = gjennomforing)
        val model = ModelMapper.toDeltaker(deltakerResponse)

        val result = InnbyggerDeltakerResponse.fromModel(deltaker = model)

        result.deltakerliste.deltakerlisteId shouldBe gjennomforing.id
        result.deltakerliste.deltakerlisteNavn shouldBe gjennomforing.navn
        result.deltakerliste.tiltakskode shouldBe TiltakskodeResponse(gjennomforing.tiltakstype.tiltakskode)
        result.deltakerliste.oppstartstype shouldBe gjennomforing.oppstart
        result.deltakerliste.startdato shouldBe gjennomforing.startDato
        result.deltakerliste.sluttdato shouldBe gjennomforing.sluttDato
        result.deltakerliste.oppmoteSted shouldBe gjennomforing.oppmoteSted
    }
}
