package no.nav.amt.deltaker.bff.navtiltakskoordinator.api.response

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.amt.deltaker.bff.navtiltakskoordinator.api.response.DeltakerResponseUtils.Companion.ADRESSEBESKYTTET_PLACEHOLDER_NAVN
import no.nav.amt.deltaker.bff.navtiltakskoordinator.api.response.DeltakerResponseUtils.Companion.SKJERMET_PERSON_PLACEHOLDER_NAVN
import no.nav.amt.deltaker.bff.navtiltakskoordinator.model.Tiltakskoordinator
import no.nav.amt.deltaker.bff.navtiltakskoordinator.ulestdeltakerhendelse.model.UlestHendelse
import no.nav.amt.deltaker.bff.navtiltakskoordinator.ulestdeltakerhendelse.model.UlestHendelseType
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltakerModel
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.bff.utils.TestData.lagForslag
import no.nav.amt.deltaker.bff.utils.TestData.lagGjennomforingResponse
import no.nav.amt.deltaker.bff.utils.TestData.lagNavBrukerResponse
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.person.Beskyttelsesmarkering
import no.nav.amt.lib.models.person.address.Adressebeskyttelse
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class ResponseBuilderTest {
    @Nested
    inner class BuildDeltakerDetaljerResponseTest {
        @Test
        fun `mapper grunnleggende felter korrekt`() {
            val deltaker = lagDeltakerModel()
            val response = ResponseBuilder.buildDeltakerDetaljerResponse(deltaker, true, emptyList())

            response.id shouldBe deltaker.id
            response.startdato shouldBe deltaker.startdato
            response.sluttdato shouldBe deltaker.sluttdato
            response.tiltakskode shouldBe deltaker.gjennomforing.tiltak.tiltakskode
            response.oppstartstype shouldBe deltaker.gjennomforing.oppstart
            response.pameldingstype shouldBe deltaker.gjennomforing.pameldingstype
        }

        @Test
        fun `mapper status med aarsak`() {
            val deltaker = lagDeltakerModel(
                status = lagDeltakerStatus(
                    statusType = DeltakerStatus.Type.HAR_SLUTTET,
                    aarsakType = DeltakerStatus.Aarsak.Type.FATT_JOBB,
                ),
            )
            val response = ResponseBuilder.buildDeltakerDetaljerResponse(deltaker, true, emptyList())

            response.status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
            response.status.aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.FATT_JOBB
        }

        @Test
        fun `mapper status uten aarsak`() {
            val deltaker = lagDeltakerModel(
                status = lagDeltakerStatus(statusType = DeltakerStatus.Type.DELTAR),
            )
            val response = ResponseBuilder.buildDeltakerDetaljerResponse(deltaker, true, emptyList())

            response.status.type shouldBe DeltakerStatus.Type.DELTAR
            response.status.aarsak shouldBe null
        }

        @Test
        fun `tilgangTilBruker true - viser personnavn og fodselsnummer`() {
            val navBruker = lagNavBrukerResponse(
                fornavn = "Ola",
                mellomnavn = null,
                etternavn = "Nordmann",
                personident = "12345678901",
            )
            val deltaker = lagDeltakerModel(navBrukerResponse = navBruker)
            val response = ResponseBuilder.buildDeltakerDetaljerResponse(deltaker, true, emptyList())

            response.fornavn shouldBe "Ola"
            response.mellomnavn shouldBe null
            response.etternavn shouldBe "Nordmann"
            response.fodselsnummer shouldBe "12345678901"
            response.tilgangTilBruker shouldBe true
        }

        @Test
        fun `tilgangTilBruker false - skjuler fodselsnummer`() {
            val deltaker = lagDeltakerModel()
            val response = ResponseBuilder.buildDeltakerDetaljerResponse(deltaker, false, emptyList())

            response.fodselsnummer shouldBe null
            response.tilgangTilBruker shouldBe false
        }

        @Test
        fun `adressebeskyttet bruker uten tilgang - viser placeholder navn`() {
            val navBruker = lagNavBrukerResponse(adressebeskyttelse = Adressebeskyttelse.STRENGT_FORTROLIG)
            val deltaker = lagDeltakerModel(navBrukerResponse = navBruker)
            val response = ResponseBuilder.buildDeltakerDetaljerResponse(deltaker, false, emptyList())

            response.fornavn shouldBe ADRESSEBESKYTTET_PLACEHOLDER_NAVN
            response.etternavn shouldBe ""
            response.mellomnavn shouldBe null
            response.fodselsnummer shouldBe null
        }

        @Test
        fun `skjermet bruker uten tilgang - viser placeholder navn`() {
            val navBruker = lagNavBrukerResponse(erSkjermet = true)
            val deltaker = lagDeltakerModel(navBrukerResponse = navBruker)
            val response = ResponseBuilder.buildDeltakerDetaljerResponse(deltaker, false, emptyList())

            response.fornavn shouldBe SKJERMET_PERSON_PLACEHOLDER_NAVN
            response.etternavn shouldBe ""
            response.mellomnavn shouldBe null
        }

        @Test
        fun `mapper beskyttelsesmarkering for adressebeskyttet bruker`() {
            val navBruker = lagNavBrukerResponse(adressebeskyttelse = Adressebeskyttelse.STRENGT_FORTROLIG)
            val deltaker = lagDeltakerModel(navBrukerResponse = navBruker)
            val response = ResponseBuilder.buildDeltakerDetaljerResponse(deltaker, true, emptyList())

            response.beskyttelsesmarkering shouldBe listOf(Beskyttelsesmarkering.STRENGT_FORTROLIG)
        }

        @Test
        fun `mapper beskyttelsesmarkering for skjermet bruker`() {
            val navBruker = lagNavBrukerResponse(erSkjermet = true)
            val deltaker = lagDeltakerModel(navBrukerResponse = navBruker)
            val response = ResponseBuilder.buildDeltakerDetaljerResponse(deltaker, true, emptyList())

            response.beskyttelsesmarkering shouldBe listOf(Beskyttelsesmarkering.SKJERMET)
        }

        @Test
        fun `mapper navEnhet og navVeileder`() {
            val navBruker = lagNavBrukerResponse()
            val deltaker = lagDeltakerModel(navBrukerResponse = navBruker)
            val response = ResponseBuilder.buildDeltakerDetaljerResponse(deltaker, true, emptyList())
            val navVeileder = deltaker.navBruker.navVeileder

            response.navEnhet shouldBe deltaker.navBruker.navEnhet
            navVeileder shouldNotBe null
            response.navVeileder.navn shouldBe navVeileder!!.navn
            response.navVeileder.telefonnummer shouldBe navVeileder.telefonnummer
            response.navVeileder.epost shouldBe navVeileder.epost
        }

        @Test
        fun `mapper innsatsgruppe`() {
            val deltaker = lagDeltakerModel()
            val response = ResponseBuilder.buildDeltakerDetaljerResponse(deltaker, true, emptyList())

            response.innsatsgruppe shouldBe deltaker.navBruker.innsatsgruppe
        }

        @Test
        fun `mapper ulesteHendelser`() {
            val ulesteHendelser = listOf(
                UlestHendelse(
                    id = UUID.randomUUID(),
                    opprettet = LocalDateTime.now(),
                    deltakerId = UUID.randomUUID(),
                    ansvarlig = null,
                    hendelse = UlestHendelseType.NavGodkjennUtkast,
                ),
            )
            val deltaker = lagDeltakerModel()
            val response = ResponseBuilder.buildDeltakerDetaljerResponse(deltaker, true, ulesteHendelser)

            response.ulesteHendelser shouldHaveSize 1
            response.ulesteHendelser shouldBe ulesteHendelser
        }

        @Test
        fun `filtrerer kun aktive forslag (VenterPaSvar)`() {
            val aktivtForslag = lagForslag(status = Forslag.Status.VenterPaSvar)
            val godkjentForslag = lagForslag(
                status = Forslag.Status.Godkjent(
                    godkjentAv = Forslag.NavAnsatt(id = UUID.randomUUID(), enhetId = UUID.randomUUID()),
                    godkjent = LocalDateTime.now(),
                ),
            )
            val tilbakekaltForslag = lagForslag(
                status = Forslag.Status.Tilbakekalt(
                    tilbakekaltAvArrangorAnsattId = UUID.randomUUID(),
                    tilbakekalt = LocalDateTime.now(),
                ),
            )
            val deltaker = lagDeltakerModel(
                endringsforslagFraArrangor = listOf(aktivtForslag, godkjentForslag, tilbakekaltForslag),
            )
            val response = ResponseBuilder.buildDeltakerDetaljerResponse(deltaker, true, emptyList())

            response.aktiveForslag shouldHaveSize 1
            response.aktiveForslag.first().id shouldBe aktivtForslag.id
        }

        @Test
        fun `ingen forslag gir tom liste`() {
            val deltaker = lagDeltakerModel(endringsforslagFraArrangor = emptyList())
            val response = ResponseBuilder.buildDeltakerDetaljerResponse(deltaker, true, emptyList())

            response.aktiveForslag.shouldBeEmpty()
        }

        @Test
        fun `deltakelsesinnhold - TRENGER_GODKJENNING med annet - returnerer beskrivelse`() {
            val innhold = Deltakelsesinnhold(
                ledetekst = null,
                innhold = listOf(
                    Innhold(tekst = "Annet", innholdskode = "annet", valgt = true, beskrivelse = "Spesiell tilrettelegging"),
                ),
            )
            val gjennomforing = lagGjennomforingResponse(pameldingType = GjennomforingPameldingType.TRENGER_GODKJENNING)
            val deltaker = lagDeltakerModel(gjennomforingResponse = gjennomforing, deltakelsesinnhold = innhold)
            val response = ResponseBuilder.buildDeltakerDetaljerResponse(deltaker, true, emptyList())

            response.deltakelsesinnhold shouldBe "Spesiell tilrettelegging"
        }

        @Test
        fun `deltakelsesinnhold - DIREKTE_VEDTAK - returnerer null`() {
            val innhold = Deltakelsesinnhold(
                ledetekst = null,
                innhold = listOf(
                    Innhold(tekst = "Annet", innholdskode = "annet", valgt = true, beskrivelse = "Spesiell tilrettelegging"),
                ),
            )
            val gjennomforing = lagGjennomforingResponse(pameldingType = GjennomforingPameldingType.DIREKTE_VEDTAK)
            val deltaker = lagDeltakerModel(gjennomforingResponse = gjennomforing, deltakelsesinnhold = innhold)
            val response = ResponseBuilder.buildDeltakerDetaljerResponse(deltaker, true, emptyList())

            response.deltakelsesinnhold shouldBe null
        }

        @Test
        fun `deltakelsesinnhold - ikke tilgang til bruker - returnerer null`() {
            val innhold = Deltakelsesinnhold(
                ledetekst = null,
                innhold = listOf(
                    Innhold(tekst = "Annet", innholdskode = "annet", valgt = true, beskrivelse = "Spesiell tilrettelegging"),
                ),
            )
            val gjennomforing = lagGjennomforingResponse(pameldingType = GjennomforingPameldingType.TRENGER_GODKJENNING)
            val deltaker = lagDeltakerModel(gjennomforingResponse = gjennomforing, deltakelsesinnhold = innhold)
            val response = ResponseBuilder.buildDeltakerDetaljerResponse(deltaker, false, emptyList())

            response.deltakelsesinnhold shouldBe null
        }

        @Test
        fun `deltakelsesinnhold - null innhold - returnerer null`() {
            val gjennomforing = lagGjennomforingResponse(pameldingType = GjennomforingPameldingType.TRENGER_GODKJENNING)
            val deltaker = lagDeltakerModel(gjennomforingResponse = gjennomforing, deltakelsesinnhold = null)
            val response = ResponseBuilder.buildDeltakerDetaljerResponse(deltaker, true, emptyList())

            response.deltakelsesinnhold shouldBe null
        }
    }

    @Nested
    inner class BuildGjennomforingTest {
        @Test
        fun `mapper grunnleggende felter korrekt`() {
            val gjennomforing = lagGjennomforingResponse()
            val koordinatorer = listOf(lagTiltakskoordinator())

            val response = ResponseBuilder.buildGjennomforing(gjennomforing, koordinatorer)

            response.id shouldBe gjennomforing.id
            response.navn shouldBe gjennomforing.navn
            response.tiltakskode shouldBe gjennomforing.tiltakstype.tiltakskode
            response.startdato shouldBe gjennomforing.startDato
            response.sluttdato shouldBe gjennomforing.sluttDato
            response.oppstartstype shouldBe gjennomforing.oppstart
            response.apentForPamelding shouldBe gjennomforing.apentForPamelding
            response.antallPlasser shouldBe gjennomforing.antallPlasser
            response.pameldingstype shouldBe gjennomforing.pameldingstype
        }

        @Test
        fun `mapper koordinatorer`() {
            val koordinator1 = lagTiltakskoordinator(navn = "Koordinator 1")
            val koordinator2 = lagTiltakskoordinator(navn = "Koordinator 2")
            val gjennomforing = lagGjennomforingResponse()

            val response = ResponseBuilder.buildGjennomforing(gjennomforing, listOf(koordinator1, koordinator2))

            response.koordinatorer shouldHaveSize 2
            response.koordinatorer[0].navn shouldBe "Koordinator 1"
            response.koordinatorer[1].navn shouldBe "Koordinator 2"
        }

        @Test
        fun `tom koordinatorliste gir tom liste`() {
            val gjennomforing = lagGjennomforingResponse()

            val response = ResponseBuilder.buildGjennomforing(gjennomforing, emptyList())

            response.koordinatorer.shouldBeEmpty()
        }

        @Test
        fun `pameldingstype null - bruker TRENGER_GODKJENNING som default`() {
            val gjennomforing = lagGjennomforingResponse(pameldingType = null)

            val response = ResponseBuilder.buildGjennomforing(gjennomforing, emptyList())

            response.pameldingstype shouldBe GjennomforingPameldingType.TRENGER_GODKJENNING
        }

        private fun lagTiltakskoordinator(
            id: UUID = UUID.randomUUID(),
            navn: String = "Test Koordinator",
            erAktiv: Boolean = true,
            kanFjernes: Boolean = false,
        ) = Tiltakskoordinator(
            id = id,
            navn = navn,
            erAktiv = erAktiv,
            kanFjernes = kanFjernes,
        )
    }
}
