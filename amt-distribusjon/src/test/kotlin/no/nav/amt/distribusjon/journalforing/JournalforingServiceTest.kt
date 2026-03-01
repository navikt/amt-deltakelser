package no.nav.amt.distribusjon.journalforing

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import no.nav.amt.distribusjon.Environment
import no.nav.amt.distribusjon.application.plugins.objectMapper
import no.nav.amt.distribusjon.distribusjonskanal.Distribusjonskanal
import no.nav.amt.distribusjon.hendelse.model.HendelseDto
import no.nav.amt.distribusjon.hendelse.model.toModel
import no.nav.amt.distribusjon.integrationTest
import no.nav.amt.distribusjon.journalforing.model.HendelseMedJournalforingstatus
import no.nav.amt.distribusjon.journalforing.model.Journalforingstatus
import no.nav.amt.distribusjon.utils.MockResponseHandler
import no.nav.amt.distribusjon.utils.data.HendelseTypeData
import no.nav.amt.distribusjon.utils.data.Hendelsesdata
import no.nav.amt.distribusjon.utils.data.Persondata
import no.nav.amt.distribusjon.utils.produceStringString
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class JournalforingServiceTest {
    @Test
    fun `handleHendelse - InnbyggerGodkjennUtkast - journalforer hovedvedtak`() = integrationTest { app, _ ->
        val hendelseDto = Hendelsesdata.lagHendelseDto(HendelseTypeData.innbyggerGodkjennUtkast())

        produce(hendelseDto)

        eventually {
            app.journalforingstatusRepository
                .get(hendelseDto.id)
                .shouldNotBeNull()
                .journalpostId shouldNotBe null
        }
    }

    @Test
    fun `handleHendelse - InnbyggerGodkjennUtkast, er allerede journalfort, skal ikke sende brev - ignorerer hendelse`() =
        integrationTest { app, _ ->
            val hendelse = Hendelsesdata.hendelse(HendelseTypeData.innbyggerGodkjennUtkast())

            val journalpostId = "12345"

            app.hendelseRepository.insert(hendelse)
            app.journalforingstatusRepository.upsert(Journalforingstatus(hendelse.id, journalpostId, null, null, false))

            app.journalforingService.handleHendelse(hendelse)

            val journalforingstatus = app.journalforingstatusRepository.get(hendelse.id)
            assertSoftly(journalforingstatus.shouldNotBeNull()) {
                it.journalpostId shouldBe journalpostId
                bestillingsId shouldBe null
                kanIkkeDistribueres shouldBe false
                kanIkkeJournalfores shouldBe false
            }
        }

    @Test
    fun `handleHendelse - NavGodkjennUtkast, er journalfort, ikke sendt brev - sender brev`() = integrationTest { app, _ ->
        val hendelse = Hendelsesdata.hendelse(HendelseTypeData.navGodkjennUtkast(), distribusjonskanal = Distribusjonskanal.PRINT)

        val journalpostId = "12345"

        app.hendelseRepository.insert(hendelse)
        app.journalforingstatusRepository.upsert(Journalforingstatus(hendelse.id, journalpostId, null, null, false))

        app.journalforingService.handleHendelse(hendelse)

        assertSoftly(app.journalforingstatusRepository.get(hendelse.id).shouldNotBeNull()) {
            it.journalpostId shouldBe journalpostId
            bestillingsId shouldNotBe null
            kanIkkeDistribueres shouldBe false
            kanIkkeJournalfores shouldBe false
        }
    }

    @Test
    fun `handleHendelse - NavGodkjennUtkast, manuell oppfolging - sender brev`() = integrationTest { app, _ ->
        val hendelse = Hendelsesdata.hendelse(
            HendelseTypeData.navGodkjennUtkast(),
            distribusjonskanal = Distribusjonskanal.SDP,
            manuellOppfolging = true,
        )

        app.hendelseRepository.insert(hendelse)

        app.journalforingService.handleHendelse(hendelse)

        assertSoftly(app.journalforingstatusRepository.get(hendelse.id).shouldNotBeNull()) {
            journalpostId shouldNotBe null
            bestillingsId shouldNotBe null
            kanIkkeDistribueres shouldBe false
            kanIkkeJournalfores shouldBe false
        }
    }

    @Test
    fun `handleHendelse - NavGodkjennUtkast, ikke digital, ingen adresse - sender ikke brev`() = integrationTest { app, _ ->
        val navBruker = Persondata.lagNavBruker(
            adresse = null,
        )

        val hendelse = Hendelsesdata.hendelse(
            HendelseTypeData.navGodkjennUtkast(),
            distribusjonskanal = Distribusjonskanal.PRINT,
            manuellOppfolging = false,
        )

        MockResponseHandler.addNavBrukerResponse(hendelse.deltaker.personident, navBruker)

        app.hendelseRepository.insert(hendelse)

        app.journalforingService.handleHendelse(hendelse)

        assertSoftly(app.journalforingstatusRepository.get(hendelse.id).shouldNotBeNull()) {
            journalpostId shouldNotBe null
            bestillingsId shouldBe null
            kanIkkeDistribueres shouldBe true
            kanIkkeJournalfores shouldBe false
        }
    }

    @Test
    fun `handleHendelse - AvsluttDeltakelse, er allerede journalfort, skal ikke sende brev - ignorerer hendelse`() =
        integrationTest { app, _ ->
            val hendelse = Hendelsesdata.hendelse(HendelseTypeData.avsluttDeltakelse())

            val journalpostId = "12345"

            app.hendelseRepository.insert(hendelse)
            app.journalforingstatusRepository.upsert(
                Journalforingstatus(
                    hendelse.id,
                    journalpostId,
                    null,
                    false,
                    kanIkkeJournalfores = false,
                ),
            )

            app.journalforingService.handleHendelse(hendelse)

            val status = app.journalforingstatusRepository.get(hendelse.id).shouldNotBeNull()
            status.journalpostId shouldNotBe null
            status.kanIkkeJournalfores shouldBe false
        }

    @Test
    fun `handleHendelse - AvsluttDeltakelse, er allerede journalfort, kan ikke sende brev - ignorerer hendelse`() =
        integrationTest { app, _ ->
            val hendelse = Hendelsesdata.hendelse(HendelseTypeData.avsluttDeltakelse())

            val journalpostId = "12345"

            app.hendelseRepository.insert(hendelse)
            app.journalforingstatusRepository.upsert(
                Journalforingstatus(
                    hendelseId = hendelse.id,
                    journalpostId = journalpostId,
                    bestillingsId = null,
                    kanIkkeDistribueres = true,
                    kanIkkeJournalfores = false,
                ),
            )

            app.journalforingService.handleHendelse(hendelse)

            val status = app.journalforingstatusRepository.get(hendelse.id).shouldNotBeNull()
            status.journalpostId shouldNotBe null
            status.kanIkkeJournalfores shouldBe false
        }

    @Test
    fun `handleHendelse - InnbyggerGodkjennUtkast, har ikke aktiv oppfolgingsperiode - feiler`() = integrationTest { app, _ ->
        val navBruker = Persondata.lagNavBruker(
            oppfolgingsperioder = listOf(
                Persondata.lagOppfolgingsperiode(
                    startdato = LocalDateTime.now().minusYears(2),
                    sluttdato = LocalDateTime.now().minusMonths(4),
                ),
            ),
        )

        val hendelse = Hendelsesdata.lagHendelseDto(HendelseTypeData.innbyggerGodkjennUtkast())

        MockResponseHandler.addNavBrukerResponse(hendelse.deltaker.personident, navBruker)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                app.journalforingService.handleHendelse(hendelse.toModel(Distribusjonskanal.DITT_NAV, false))
            }
        }
    }

    @Test
    fun `handleHendelse - EndreSluttarsak - journalforer ikke`() = integrationTest { app, _ ->
        val hendelse = Hendelsesdata.hendelse(HendelseTypeData.endreSluttarsak())

        app.journalforingService.handleHendelse(hendelse)
        app.journalforingstatusRepository.get(hendelse.id) shouldBe null
    }

    @Test
    fun `journalforOgDistribuerEndringsvedtak - deltakelsesmengde og forleng - journalforer endringsvedtak`() = integrationTest { app, _ ->
        val deltaker = Hendelsesdata.lagDeltaker()

        val hendelseDeltakelsesmengde = Hendelsesdata.hendelse(
            HendelseTypeData.endreDeltakelsesmengde(),
            deltaker = deltaker,
            opprettet = LocalDateTime.now().minusMinutes(20),
        )
        val journalforingstatusDeltakelsesmengde =
            Journalforingstatus(hendelseDeltakelsesmengde.id, null, null, null, kanIkkeJournalfores = null)
        app.journalforingstatusRepository.upsert(journalforingstatusDeltakelsesmengde)
        val hendelseForleng = Hendelsesdata.hendelse(
            HendelseTypeData.forlengDeltakelse(),
            deltaker = deltaker,
            ansvarlig = hendelseDeltakelsesmengde.ansvarlig,
            opprettet = LocalDateTime.now(),
        )
        val journalforingstatusForleng = Journalforingstatus(hendelseForleng.id, null, null, null, kanIkkeJournalfores = null)
        app.journalforingstatusRepository.upsert(journalforingstatusForleng)

        app.journalforingService.journalforOgDistribuerEndringsvedtak(
            listOf(
                HendelseMedJournalforingstatus(hendelseForleng, journalforingstatusForleng),
                HendelseMedJournalforingstatus(hendelseDeltakelsesmengde, journalforingstatusDeltakelsesmengde),
            ),
        )

        val journalpostForleng = app.journalforingstatusRepository.get(hendelseForleng.id).shouldNotBeNull()
        journalpostForleng.kanIkkeJournalfores shouldBe false

        assertSoftly(app.journalforingstatusRepository.get(hendelseDeltakelsesmengde.id).shouldNotBeNull()) {
            journalpostId shouldNotBe null
            journalpostId shouldBe journalpostForleng.journalpostId
            kanIkkeJournalfores shouldBe false
        }
    }

    @Test
    fun `journalforOgDistribuerEndringsvedtak - to endringer, en allerede journalfort - journalforer 1, distribuerer 2`() =
        integrationTest { app, _ ->
            val deltaker = Hendelsesdata.lagDeltaker()

            val hendelseDeltakelsesmengde = Hendelsesdata.hendelse(
                HendelseTypeData.endreDeltakelsesmengde(),
                deltaker = deltaker,
                opprettet = LocalDateTime.now().minusMinutes(20),
                distribusjonskanal = Distribusjonskanal.PRINT,
            )
            val journalforingstatusDeltakelsesmengde =
                Journalforingstatus(hendelseDeltakelsesmengde.id, "99887", null, null, kanIkkeJournalfores = false)
            app.journalforingstatusRepository.upsert(journalforingstatusDeltakelsesmengde)

            val hendelseForleng = Hendelsesdata.hendelse(
                HendelseTypeData.forlengDeltakelse(),
                deltaker = deltaker,
                ansvarlig = hendelseDeltakelsesmengde.ansvarlig,
                opprettet = LocalDateTime.now(),
                distribusjonskanal = Distribusjonskanal.PRINT,
            )
            val journalforingstatusForleng = Journalforingstatus(hendelseForleng.id, null, null, null, kanIkkeJournalfores = null)
            app.journalforingstatusRepository.upsert(journalforingstatusForleng)

            app.journalforingService.journalforOgDistribuerEndringsvedtak(
                listOf(
                    HendelseMedJournalforingstatus(hendelseForleng, journalforingstatusForleng),
                    HendelseMedJournalforingstatus(hendelseDeltakelsesmengde, journalforingstatusDeltakelsesmengde),
                ),
            )

            val journalpostDeltakelsesmengde = app.journalforingstatusRepository.get(hendelseDeltakelsesmengde.id).shouldNotBeNull()

            assertSoftly(journalpostDeltakelsesmengde) {
                journalpostId shouldBe journalforingstatusDeltakelsesmengde.journalpostId
                bestillingsId shouldNotBe null
                kanIkkeDistribueres shouldBe false
                kanIkkeJournalfores shouldBe false
            }

            assertSoftly(app.journalforingstatusRepository.get(hendelseForleng.id).shouldNotBeNull()) {
                journalpostId shouldNotBe journalpostDeltakelsesmengde.journalpostId
                bestillingsId shouldNotBe null
                journalpostId shouldNotBe null
                kanIkkeDistribueres shouldBe false
                kanIkkeJournalfores shouldBe false
            }
        }

    @Test
    fun `journalforOgDistribuerEndringsvedtak - avslutt deltakelse, ikke under oppfolging - journalforer endringsvedtak`() =
        integrationTest { app, _ ->
            val navBruker = Persondata.lagNavBruker(
                oppfolgingsperioder = listOf(
                    Persondata.lagOppfolgingsperiode(
                        startdato = LocalDateTime.now().minusYears(2),
                        sluttdato = LocalDateTime.now().minusMonths(4),
                    ),
                ),
            )
            val avsluttDeltakelseHendelse = Hendelsesdata.hendelse(HendelseTypeData.avsluttDeltakelse())
            MockResponseHandler.addNavBrukerResponse(avsluttDeltakelseHendelse.deltaker.personident, navBruker)

            val journalforingstatus = Journalforingstatus(avsluttDeltakelseHendelse.id, null, null, null, kanIkkeJournalfores = null)
            app.journalforingstatusRepository.upsert(journalforingstatus)

            app.journalforingService.journalforOgDistribuerEndringsvedtak(
                listOf(
                    HendelseMedJournalforingstatus(avsluttDeltakelseHendelse, journalforingstatus),
                ),
            )

            val oppdatertJournalforingstatus = app.journalforingstatusRepository.get(avsluttDeltakelseHendelse.id)
            assertSoftly(oppdatertJournalforingstatus.shouldNotBeNull()) {
                journalpostId shouldBe null
                bestillingsId shouldBe null
                kanIkkeJournalfores shouldBe true
            }
        }

    @Test
    fun `journalforOgDistribuerEndringsvedtak - forleng deltakelse, ikke under oppfolging - feiler`() = integrationTest { app, _ ->
        val navBruker = Persondata.lagNavBruker(
            oppfolgingsperioder = listOf(
                Persondata.lagOppfolgingsperiode(
                    startdato = LocalDateTime.now().minusYears(2),
                    sluttdato = LocalDateTime.now().minusMonths(4),
                ),
            ),
        )
        val hendelseForleng = Hendelsesdata.hendelse(HendelseTypeData.forlengDeltakelse())
        MockResponseHandler.addNavBrukerResponse(hendelseForleng.deltaker.personident, navBruker)

        val journalforingstatusForleng = Journalforingstatus(hendelseForleng.id, null, null, null, kanIkkeJournalfores = null)
        app.journalforingstatusRepository.upsert(journalforingstatusForleng)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                app.journalforingService.journalforOgDistribuerEndringsvedtak(
                    listOf(
                        HendelseMedJournalforingstatus(hendelseForleng, journalforingstatusForleng),
                    ),
                )
            }
        }
    }

    @Test
    fun `journalforOgDistribuerEndringsvedtak - ulik deltakerid - feiler`() = integrationTest { app, _ ->
        val hendelseDeltakelsesmengde = Hendelsesdata.hendelse(
            HendelseTypeData.endreDeltakelsesmengde(),
            opprettet = LocalDateTime.now().minusMinutes(20),
        )
        val hendelseForleng = Hendelsesdata.hendelse(HendelseTypeData.forlengDeltakelse(), opprettet = LocalDateTime.now())

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                app.journalforingService.journalforOgDistribuerEndringsvedtak(
                    listOf(
                        HendelseMedJournalforingstatus(
                            hendelse = hendelseForleng,
                            journalforingstatus = Journalforingstatus(hendelseForleng.id, null, null, null, null),
                        ),
                        HendelseMedJournalforingstatus(
                            hendelse = hendelseDeltakelsesmengde,
                            journalforingstatus = Journalforingstatus(
                                hendelseDeltakelsesmengde.id,
                                null,
                                null,
                                null,
                                null,
                            ),
                        ),
                    ),
                )
            }
        }
    }
}

private fun produce(hendelse: HendelseDto) = produceStringString(
    ProducerRecord(Environment.DELTAKER_HENDELSE_TOPIC, hendelse.deltaker.id.toString(), objectMapper.writeValueAsString(hendelse)),
)
