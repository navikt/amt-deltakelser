package no.nav.amt.distribusjon.hendelse.consumer

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import no.nav.amt.distribusjon.Environment
import no.nav.amt.distribusjon.TestApp
import no.nav.amt.distribusjon.application.plugins.objectMapper
import no.nav.amt.distribusjon.distribusjonskanal.Distribusjonskanal
import no.nav.amt.distribusjon.hendelse.model.HendelseDto
import no.nav.amt.distribusjon.hendelse.model.toModel
import no.nav.amt.distribusjon.integrationTest
import no.nav.amt.distribusjon.utils.data.HendelseTypeData
import no.nav.amt.distribusjon.utils.data.Hendelsesdata
import no.nav.amt.distribusjon.utils.data.Varselsdata
import no.nav.amt.distribusjon.utils.produceStringString
import no.nav.amt.distribusjon.varsel.model.Varsel
import no.nav.amt.distribusjon.varsel.model.beskjedTekst
import no.nav.amt.distribusjon.varsel.model.innbyggerDeltakerUrl
import no.nav.amt.distribusjon.varsel.model.oppgaveTekst
import no.nav.amt.distribusjon.varsel.nowUTC
import no.nav.amt.distribusjon.varsel.skalVarslesEksternt
import no.nav.amt.lib.models.hendelse.HendelseType
import no.nav.amt.lib.testing.shouldBeCloseTo
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime
import java.util.UUID

class VarselTest {
    @Nested
    inner class OpprettUtkastTests {
        @Test
        fun `opprettUtkast - oppretter nytt varsel og produserer`() = integrationTest { app, _ ->
            val hendelse = Hendelsesdata.lagHendelseDto(HendelseTypeData.opprettUtkast())

            produce(hendelse)

            eventually {
                val varsel = app.varselRepository
                    .getSisteVarsel(
                        deltakerId = hendelse.deltaker.id,
                        type = Varsel.Type.OPPGAVE,
                    ).shouldBeSuccess()

                assertSoftly(varsel) {
                    aktivTil shouldBe null
                    tekst shouldBe oppgaveTekst(hendelse.toModel(Distribusjonskanal.DITT_NAV, false))
                    aktivFra shouldBeCloseTo nowUTC()
                    deltakerId shouldBe hendelse.deltaker.id
                    personident shouldBe hendelse.deltaker.personident
                    erEksterntVarsel shouldBe hendelse.skalVarslesEksternt()
                }

                app.assertProducedOppgave(varsel.id)
            }
        }

        @Test
        fun `opprettUtkast - hendelsen er håndtert tidligere - sender ikke nytt varsel`() = integrationTest { app, _ ->
            val hendelse = Hendelsesdata.lagHendelseDto(HendelseTypeData.opprettUtkast())
            val forrigeVarsel = Varselsdata.varsel(
                Varsel.Type.OPPGAVE,
                hendelser = listOf(hendelse.id),
                aktivFra = nowUTC().minusMinutes(30),
                aktivTil = nowUTC().minusMinutes(20),
                deltakerId = hendelse.deltaker.id,
            )
            app.varselRepository.upsert(forrigeVarsel)

            produce(hendelse)

            eventually {
                val varsel = app.varselRepository
                    .getSisteVarsel(
                        deltakerId = hendelse.deltaker.id,
                        type = Varsel.Type.OPPGAVE,
                    ).shouldBeSuccess()

                varsel.erAktiv shouldBe false
                app.assertNotProducedHendelse(varsel.id)
            }
        }
    }

    @Nested
    inner class NavGodkjennUtkastTests {
        @Test
        fun `navGodkjennUtkast - innbyggers distribusjonskanal er ikke digital - oppretter ikke varsel`() = integrationTest { app, _ ->
            val hendelse = Hendelsesdata.hendelse(
                payload = HendelseTypeData.navGodkjennUtkast(),
                distribusjonskanal = Distribusjonskanal.PRINT,
            )

            app.varselService.handleHendelse(hendelse)

            val varsel = app.varselRepository
                .getSisteVarsel(
                    deltakerId = hendelse.deltaker.id,
                    type = Varsel.Type.BESKJED,
                ).getOrNull()

            varsel shouldBe null
        }

        @Test
        fun `navGodkjennUtkast - innbyggers er under manuell oppfolging - oppretter ikke varsel`() = integrationTest { app, _ ->
            val hendelse = Hendelsesdata.hendelse(
                HendelseTypeData.navGodkjennUtkast(),
                distribusjonskanal = Distribusjonskanal.SDP,
                manuellOppfolging = true,
            )

            app.varselService.handleHendelse(hendelse)

            val varsel = app.varselRepository
                .getSisteVarsel(
                    deltakerId = hendelse.deltaker.id,
                    type = Varsel.Type.BESKJED,
                ).getOrNull()

            varsel shouldBe null
        }

        @Test
        fun `navGodkjennUtkast - ingen tidligere varsel - oppretter beskjed`() = integrationTest { app, _ ->
            val hendelse = Hendelsesdata.lagHendelseDto(HendelseTypeData.navGodkjennUtkast())

            produce(hendelse)

            eventually {
                assertNyBeskjed(app, hendelse, nowUTC())
            }
        }

        @Test
        fun `navGodkjennUtkast - tidligere varsel - inaktiverer varsel og oppretter beskjed`() = integrationTest { app, _ ->
            val hendelse = Hendelsesdata.lagHendelseDto(HendelseTypeData.navGodkjennUtkast())

            val forrigeVarsel = Varselsdata.varsel(
                type = Varsel.Type.OPPGAVE,
                status = Varsel.Status.AKTIV,
                aktivFra = nowUTC().minusDays(1),
                deltakerId = hendelse.deltaker.id,
            )
            app.varselRepository.upsert(forrigeVarsel)

            produce(hendelse)

            eventually {
                assertNyBeskjed(app, hendelse, nowUTC())

                val inaktivertVarsel = app.varselRepository.get(forrigeVarsel.id).shouldBeSuccess()

                inaktivertVarsel.aktivTil.shouldNotBeNull()
                inaktivertVarsel.aktivTil shouldBeCloseTo nowUTC()

                app.assertProducedInaktiver(forrigeVarsel.id)
            }
        }
    }

    @Test
    fun `avbrytUtkast - varsel er aktivt - inaktiverer varsel og produserer`() = integrationTest { app, _ ->
        val hendelse = Hendelsesdata.lagHendelseDto(HendelseTypeData.avbrytUtkast())
        val forrigeVarsel = Varselsdata.varsel(
            Varsel.Type.OPPGAVE,
            Varsel.Status.AKTIV,
            aktivFra = nowUTC().minusDays(1),
            deltakerId = hendelse.deltaker.id,
        )
        app.varselRepository.upsert(forrigeVarsel)
        produce(hendelse)

        eventually {
            val varsel = app.varselRepository
                .getSisteVarsel(
                    deltakerId = hendelse.deltaker.id,
                    type = Varsel.Type.OPPGAVE,
                ).shouldBeSuccess()

            assertSoftly(varsel) {
                id shouldBe forrigeVarsel.id

                aktivTil.shouldNotBeNull()
                aktivTil shouldBeCloseTo nowUTC()
            }

            app.assertProducedInaktiver(varsel.id)
        }
    }

    @Test
    fun `innbyggerGodkjennerUtkast - inaktiverer varsel`() = integrationTest { app, _ ->
        val hendelse = Hendelsesdata.lagHendelseDto(HendelseTypeData.innbyggerGodkjennUtkast())
        val forrigeVarsel = Varselsdata.varsel(
            Varsel.Type.OPPGAVE,
            Varsel.Status.AKTIV,
            aktivFra = nowUTC().minusDays(1),
            deltakerId = hendelse.deltaker.id,
        )
        app.varselRepository.upsert(forrigeVarsel)
        produce(hendelse)

        eventually {
            val varsel = app.varselRepository
                .getSisteVarsel(
                    deltakerId = hendelse.deltaker.id,
                    type = Varsel.Type.OPPGAVE,
                ).shouldBeSuccess()

            assertSoftly(varsel) {
                id shouldBe forrigeVarsel.id

                aktivTil.shouldNotBeNull()
                aktivTil shouldBeCloseTo nowUTC()
            }

            app.assertProducedInaktiver(varsel.id)
        }
    }

    @Test
    fun `avsluttDeltakelse - nytt varsel med ekstern varsling, tidligere varsel skal revarsles - stopper revarsling av tidligere varsel`() =
        integrationTest { app, _ ->
            val deltakerId = UUID.randomUUID()
            val hendelse = Hendelsesdata.hendelse(HendelseTypeData.avsluttDeltakelse(), deltaker = Hendelsesdata.lagDeltaker(deltakerId))

            val forrigeVarsel = Varselsdata.beskjed(
                status = Varsel.Status.INAKTIVERT,
                deltakerId = deltakerId,
                aktivFra = nowUTC().minusDays(6),
                aktivTil = nowUTC().plusDays(3),
                revarsles = nowUTC().plusDays(1),
            )

            app.varselRepository.upsert(forrigeVarsel)
            app.varselService.handleHendelse(hendelse)

            app.varselRepository
                .get(forrigeVarsel.id)
                .shouldBeSuccess()
                .revarsles shouldBe null
        }

    @Test
    fun `endreSluttdato - ingen tidligere varsel - oppretter forsinket varsel`() = integrationTest { app, _ ->
        val hendelse = Hendelsesdata.lagHendelseDto(HendelseTypeData.endreSluttdato())

        produce(hendelse)

        eventually {
            assertNyBeskjed(app, hendelse, Varsel.nesteUtsendingstidspunkt())
        }
    }

    @Test
    fun `endreStartdato - ingen tidligere varsel - oppretter forsinket varsel`() = integrationTest { app, _ ->
        val hendelse = Hendelsesdata.lagHendelseDto(HendelseTypeData.endreStartdato())

        produce(hendelse)

        eventually {
            assertNyBeskjed(app, hendelse, Varsel.nesteUtsendingstidspunkt())
        }
    }

    @Nested
    inner class DeltakerSistBesoktTests {
        @Test
        fun `deltakerSistBesokt - aktiv beskjed - inaktiverer`() = integrationTest { app, _ ->
            val hendelse = Hendelsesdata.lagHendelseDto(HendelseTypeData.sistBesokt())
            val varsel = Varselsdata.varsel(
                type = Varsel.Type.BESKJED,
                status = Varsel.Status.AKTIV,
                deltakerId = hendelse.deltaker.id,
                aktivFra = nowUTC().minusMinutes(1),
            )

            app.varselRepository.upsert(varsel)
            produce(hendelse)

            eventually {
                val oppdatertVarsel = app.varselRepository.get(varsel.id).shouldBeSuccess()

                oppdatertVarsel.aktivTil.shouldNotBeNull()
                oppdatertVarsel.aktivTil shouldBeCloseTo nowUTC()
            }

            app.assertProducedInaktiver(varsel.id)
        }

        @Test
        fun `deltakerSistBesokt - beskjed venter på å bli sendt - inaktiverer`() = integrationTest { app, _ ->
            val hendelse = Hendelsesdata.lagHendelseDto(HendelseTypeData.sistBesokt())
            val varsel = Varselsdata.varsel(
                type = Varsel.Type.BESKJED,
                status = Varsel.Status.VENTER_PA_UTSENDELSE,
                deltakerId = hendelse.deltaker.id,
                aktivFra = nowUTC().plusMinutes(10),
            )

            app.varselRepository.upsert(varsel)
            produce(hendelse)

            eventually {
                val oppdatertVarsel = app.varselRepository.get(varsel.id).shouldBeSuccess()

                assertSoftly(oppdatertVarsel) {
                    status shouldBe Varsel.Status.UTFORT
                    aktivFra shouldBeCloseTo nowUTC()

                    aktivTil.shouldNotBeNull()
                    aktivTil shouldBeCloseTo nowUTC()
                }
            }
        }

        @Test
        fun `deltakerSistBesokt - to beskjeder, en aktiv og en venter - inaktiverer begge`() = integrationTest { app, _ ->
            val hendelse = Hendelsesdata.lagHendelseDto(HendelseTypeData.sistBesokt())
            val aktivtVarsel = Varselsdata.varsel(
                type = Varsel.Type.BESKJED,
                status = Varsel.Status.AKTIV,
                deltakerId = hendelse.deltaker.id,
                aktivFra = nowUTC().minusMinutes(1),
            )
            val ventendeVarsel = Varselsdata.varsel(
                type = Varsel.Type.BESKJED,
                status = Varsel.Status.VENTER_PA_UTSENDELSE,
                deltakerId = hendelse.deltaker.id,
                aktivFra = nowUTC().plusMinutes(10),
            )
            app.varselRepository.upsert(aktivtVarsel)
            app.varselRepository.upsert(ventendeVarsel)

            produce(hendelse)

            eventually {
                assertSoftly(app.varselRepository.get(aktivtVarsel.id).shouldBeSuccess()) {
                    status shouldBe Varsel.Status.UTFORT
                    aktivFra shouldBeCloseTo aktivtVarsel.aktivFra

                    aktivTil.shouldNotBeNull()
                    aktivTil shouldBeCloseTo nowUTC()
                }

                assertSoftly(app.varselRepository.get(ventendeVarsel.id).shouldBeSuccess()) {
                    status shouldBe Varsel.Status.UTFORT
                    aktivFra shouldBeCloseTo nowUTC()

                    aktivTil.shouldNotBeNull()
                    aktivTil shouldBeCloseTo nowUTC()
                }
            }
            app.assertProducedInaktiver(aktivtVarsel.id)
        }

        @Test
        fun `deltakerSistBesokt - siste besøk er før beskjed var sendt - inaktiverer ikke`() = integrationTest { app, _ ->
            val hendelse = Hendelsesdata.hendelse(HendelseTypeData.sistBesokt(sistBesokt = ZonedDateTime.now().minusMinutes(10)))
            val varsel = Varselsdata.varsel(
                type = Varsel.Type.BESKJED,
                status = Varsel.Status.AKTIV,
                deltakerId = hendelse.deltaker.id,
                aktivFra = nowUTC(),
                aktivTil = nowUTC().plus(Varsel.beskjedAktivLengde),
            )

            app.varselRepository.upsert(varsel)
            app.varselService.handleHendelse(hendelse)

            assertSoftly(app.varselRepository.get(varsel.id).shouldBeSuccess()) {
                status shouldBe Varsel.Status.AKTIV
                aktivFra shouldBeCloseTo varsel.aktivFra

                aktivTil.shouldNotBeNull()
                aktivTil shouldBeCloseTo varsel.aktivTil
            }
        }

        @Test
        fun `deltakerSistBesokt - siste besøk er før beskjed - inaktiverer ikke`() = integrationTest { app, _ ->
            val hendelse = Hendelsesdata.hendelse(HendelseTypeData.sistBesokt(sistBesokt = ZonedDateTime.now().minusMinutes(10)))

            val varsel = Varselsdata.varsel(
                type = Varsel.Type.BESKJED,
                status = Varsel.Status.VENTER_PA_UTSENDELSE,
                deltakerId = hendelse.deltaker.id,
                aktivFra = nowUTC().plusMinutes(30),
                aktivTil = nowUTC().plus(Varsel.beskjedAktivLengde),
            )

            app.varselRepository.upsert(varsel)
            app.varselService.handleHendelse(hendelse)

            assertSoftly(app.varselRepository.get(varsel.id).shouldBeSuccess()) {
                aktivFra shouldBeCloseTo varsel.aktivFra
                aktivTil.shouldNotBeNull()
                aktivTil shouldBeCloseTo varsel.aktivTil
            }
        }
    }

    companion object {
        fun HendelseDto.skalVarslesEksternt() = this.toModel(Distribusjonskanal.DITT_NAV, false).skalVarslesEksternt()

        private fun assertNyBeskjed(
            app: TestApp,
            hendelse: HendelseDto,
            aktivFra: ZonedDateTime,
        ) {
            val varsel = app.varselRepository
                .getSisteVarsel(
                    deltakerId = hendelse.deltaker.id,
                    type = Varsel.Type.BESKJED,
                ).shouldBeSuccess()

            assertSoftly(varsel) {
                aktivTil.shouldNotBeNull()
                aktivTil shouldBeCloseTo Varsel.nesteUtsendingstidspunkt().plus(Varsel.beskjedAktivLengde)

                tekst shouldBe beskjedTekst(hendelse.toModel(Distribusjonskanal.DITT_NAV, false))
                it.aktivFra shouldBeCloseTo aktivFra
                deltakerId shouldBe hendelse.deltaker.id
                personident shouldBe hendelse.deltaker.personident

                erEksterntVarsel shouldBe hendelse.skalVarslesEksternt()

                if (erAktiv) {
                    val forventetUrl = innbyggerDeltakerUrl(varsel.deltakerId, hendelse.payload !is HendelseType.NavGodkjennUtkast)
                    app.assertProducedBeskjed(varsel.id, forventetUrl)
                }
            }
        }
    }

    private fun produce(hendelse: HendelseDto) = produceStringString(
        ProducerRecord(Environment.DELTAKER_HENDELSE_TOPIC, hendelse.deltaker.id.toString(), objectMapper.writeValueAsString(hendelse)),
    )
}
