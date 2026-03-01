package no.nav.amt.distribusjon.varsel

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import no.nav.amt.distribusjon.integrationTest
import no.nav.amt.distribusjon.utils.data.HendelseTypeData
import no.nav.amt.distribusjon.utils.data.Hendelsesdata
import no.nav.amt.distribusjon.utils.data.Varselsdata
import no.nav.amt.distribusjon.varsel.model.Varsel
import no.nav.amt.distribusjon.varsel.model.innbyggerDeltakerUrl
import no.nav.amt.lib.testing.shouldBeCloseTo
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

class VarselServiceTest {
    @Nested
    inner class SendVentendeVarslerTests {
        @Test
        fun `sendVentendeVarsler - varsler er klare for sending - sender`() = integrationTest { app, _ ->
            val varsel = Varselsdata.varsel(
                type = Varsel.Type.BESKJED,
                status = Varsel.Status.VENTER_PA_UTSENDELSE,
                aktivFra = nowUTC().minusMinutes(5),
            )
            app.varselRepository.upsert(varsel)
            val hendelse = Hendelsesdata.hendelse(
                payload = HendelseTypeData.endreDeltakelsesmengde(),
                id = varsel.hendelser.first(),
            )
            app.hendelseRepository.insert(hendelse)
            val forventetUrl = innbyggerDeltakerUrl(varsel.deltakerId, true)

            app.varselService.sendVentendeVarsler()

            app.assertProducedBeskjed(varsel.id, forventetUrl)

            val oppdatertVarsel = app.varselRepository.get(varsel.id).shouldBeSuccess()
            oppdatertVarsel.aktivFra shouldBeCloseTo nowUTC()
        }

        @Test
        fun `sendVentendeVarsler - varsler er ikke klare for sending - sender ikke`() = integrationTest { app, _ ->
            val varsel = Varselsdata.varsel(
                Varsel.Type.BESKJED,
                Varsel.Status.VENTER_PA_UTSENDELSE,
                aktivFra = nowUTC().plusMinutes(5),
            )
            app.varselRepository.upsert(varsel)

            app.varselService.sendVentendeVarsler()

            val oppdatertVarsel = app.varselRepository.get(varsel.id).shouldBeSuccess()
            oppdatertVarsel.aktivFra shouldBeCloseTo varsel.aktivFra
        }

        @Test
        fun `sendVentendeVarsler - varsler klar for sending, det finnes ett aktivt varsel fra før - inaktiverer og sender nytt`() =
            integrationTest { app, _ ->
                val deltakerId = UUID.randomUUID()
                val aktivtVarsel = Varselsdata.varsel(
                    type = Varsel.Type.BESKJED,
                    status = Varsel.Status.AKTIV,
                    deltakerId = deltakerId,
                    aktivFra = nowUTC().minusMinutes(35),
                )
                app.varselRepository.upsert(aktivtVarsel)

                val nyttVarsel = Varselsdata.varsel(
                    type = Varsel.Type.BESKJED,
                    status = Varsel.Status.VENTER_PA_UTSENDELSE,
                    deltakerId = deltakerId,
                    aktivFra = nowUTC().minusMinutes(5),
                )
                app.varselRepository.upsert(nyttVarsel)
                val hendelse = Hendelsesdata.hendelse(
                    payload = HendelseTypeData.endreInnhold(),
                    id = nyttVarsel.hendelser.first(),
                )
                app.hendelseRepository.insert(hendelse)
                val forventetUrl = innbyggerDeltakerUrl(nyttVarsel.deltakerId, true)

                app.varselService.sendVentendeVarsler()

                app.varselRepository
                    .get(aktivtVarsel.id)
                    .shouldBeSuccess()
                    .erAktiv shouldBe false

                val oppdatertVarsel = app.varselRepository.get(nyttVarsel.id).shouldBeSuccess()
                oppdatertVarsel.aktivFra shouldBeCloseTo nowUTC()

                app.assertProducedInaktiver(aktivtVarsel.id)
                app.assertProducedBeskjed(nyttVarsel.id, forventetUrl)
            }
    }

    @Nested
    inner class SendRevarslerTests {
        @Test
        fun `sendRevarsler - inaktivert beskjed skal revarsles - oppretter og sender revarsel`() = integrationTest { app, _ ->
            val skalRevarsles = Varselsdata.beskjed(
                status = Varsel.Status.INAKTIVERT,
                aktivFra = nowUTC().minusDays(7).plusMinutes(1),
                revarsles = nowUTC().minusMinutes(1),
            )
            app.varselRepository.upsert(skalRevarsles)
            val hendelse = Hendelsesdata.hendelse(
                payload = HendelseTypeData.navGodkjennUtkast(),
                id = skalRevarsles.hendelser.first(),
            )
            app.hendelseRepository.insert(hendelse)
            val forventetUrl = innbyggerDeltakerUrl(skalRevarsles.deltakerId, false)

            app.varselService.sendRevarsler()

            app.varselRepository
                .get(skalRevarsles.id)
                .shouldBeSuccess()
                .revarsles shouldBe null

            val revarsel = app.varselRepository.getAktivt(skalRevarsles.deltakerId).shouldBeSuccess()
            assertSoftly(revarsel) {
                erRevarsel shouldBe true
                kanRevarsles shouldBe false
                aktivFra shouldBeCloseTo nowUTC()
                aktivTil.shouldNotBeNull() shouldBeCloseTo nowUTC().plus(Varsel.beskjedAktivLengde)
                revarselForVarsel shouldBe skalRevarsles.id
            }

            app.assertProducedBeskjed(revarsel.id, forventetUrl)
        }

        @Test
        fun `sendRevarsler - aktiv beskjed skal revarsles - inaktiverer beskjed, oppretter og sender revarsel`() =
            integrationTest { app, _ ->
                val skalRevarsles = Varselsdata.beskjed(
                    status = Varsel.Status.AKTIV,
                    aktivFra = nowUTC().minusDays(7).plusMinutes(1),
                    revarsles = nowUTC().minusMinutes(1),
                )

                app.varselRepository.upsert(skalRevarsles)
                val hendelse = Hendelsesdata.hendelse(
                    payload = HendelseTypeData.navGodkjennUtkast(),
                    id = skalRevarsles.hendelser.first(),
                )
                app.hendelseRepository.insert(hendelse)
                val forventetUrl = innbyggerDeltakerUrl(skalRevarsles.deltakerId, false)

                app.varselService.sendRevarsler()

                val oppdatertVarsel = app.varselRepository.get(skalRevarsles.id).shouldBeSuccess()
                assertSoftly(oppdatertVarsel) {
                    revarsles shouldBe null
                    status shouldBe Varsel.Status.INAKTIVERT
                    aktivTil.shouldNotBeNull() shouldBeCloseTo nowUTC()
                }

                app.assertProducedInaktiver(oppdatertVarsel.id)

                val revarsel = app.varselRepository.getAktivt(skalRevarsles.deltakerId).shouldBeSuccess()
                assertSoftly(revarsel) {
                    erRevarsel shouldBe true
                    kanRevarsles shouldBe false
                    aktivFra shouldBeCloseTo nowUTC()
                    aktivTil.shouldNotBeNull() shouldBeCloseTo nowUTC().plus(Varsel.beskjedAktivLengde)
                    revarselForVarsel shouldBe skalRevarsles.id
                }

                app.assertProducedBeskjed(revarsel.id, forventetUrl)
            }

        @Test
        fun `sendRevarsler - aktiv beskjed skal ikke revarsles enda - endrer ingenting`() = integrationTest { app, _ ->
            val skalIkkeRevarsles = Varselsdata.beskjed(
                Varsel.Status.AKTIV,
                aktivFra = nowUTC().minusDays(6).plusMinutes(1),
                revarsles = nowUTC().plusDays(1),
            )

            app.varselRepository.upsert(skalIkkeRevarsles)

            app.varselService.sendRevarsler()

            val ikkeOppdatertVarsel = app.varselRepository.get(skalIkkeRevarsles.id).shouldBeSuccess()
            assertSoftly(ikkeOppdatertVarsel) {
                revarsles.shouldNotBeNull() shouldBeCloseTo skalIkkeRevarsles.revarsles.shouldNotBeNull()
                status shouldBe skalIkkeRevarsles.status
                aktivTil.shouldNotBeNull() shouldBeCloseTo skalIkkeRevarsles.aktivTil
            }
        }
    }

    @Nested
    inner class UtlopBeskjedTests {
        @Test
        fun `utlopBeskjed - varsler kan ikke utløpes - feiler`() = integrationTest { app, _ ->
            val ugyldigeVarsler = listOf(
                Varselsdata.varsel(Varsel.Type.OPPGAVE),
                Varselsdata.beskjed(status = Varsel.Status.INAKTIVERT),
                Varselsdata.beskjed(status = Varsel.Status.AKTIV, aktivTil = nowUTC().plusMinutes(1)),
            )

            ugyldigeVarsler.forEach {
                assertThrows(IllegalArgumentException::class.java) {
                    app.varselService.utlopBeskjed(it)
                }
            }
        }

        @Test
        fun `utlopBeskjed - varsel er utløpt - utløper`() = integrationTest { app, _ ->
            val utloptBeskjed = Varselsdata.beskjed(
                Varsel.Status.AKTIV,
                aktivFra = nowUTC().minusDays(21),
                aktivTil = nowUTC().minusMinutes(1),
            )

            app.varselService.utlopBeskjed(utloptBeskjed)

            app.varselRepository
                .get(utloptBeskjed.id)
                .shouldBeSuccess()
                .status shouldBe Varsel.Status.UTLOPT
        }
    }
}
