package no.nav.amt.deltaker.bff.navtiltakskoordinator.auth

import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.bff.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.bff.navansatt.NavAnsattService
import no.nav.amt.deltaker.bff.navtiltakskoordinator.TiltakskoordinatorsDeltakerlistePayload
import no.nav.amt.deltaker.bff.navtiltakskoordinator.TiltakskoordinatorsDeltakerlisteProducer
import no.nav.amt.deltaker.bff.utils.assertProduced
import no.nav.amt.deltaker.bff.utils.assertProducedTombstone
import no.nav.amt.lib.kafka.Producer
import no.nav.amt.lib.kafka.config.LocalKafkaConfig
import no.nav.amt.lib.ktor.auth.exceptions.AuthorizationException
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.SingletonKafkaProvider
import no.nav.amt.lib.testing.TestOutboxEnvironment
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension

class SelfServiceTilgangskontrollServiceTest {
    private val kafkaProducer = Producer<String, String>(LocalKafkaConfig(SingletonKafkaProvider.getHost()))
    private val tiltakskoordinatorsDeltakerlisteProducer = TiltakskoordinatorsDeltakerlisteProducer(
        TestOutboxEnvironment.outboxService,
        kafkaProducer,
    )

    private val navAnsattService = NavAnsattService(NavAnsattRepository(), mockk())
    private val tiltakskoordinatorTilgangRepository = TiltakskoordinatorTilgangRepository()

    private val selfServiceTilgangService = SelfServiceTilgangService(
        navAnsattService,
        tiltakskoordinatorTilgangRepository,
        tiltakskoordinatorsDeltakerlisteProducer,
    )

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Nested
    inner class LeggTilTiltakskoordinatorTilgang {
        @Test
        fun `har ikke tilgang fra for - returnerer success`() = runTest {
            with(TiltakskoordinatorTilgangContext()) {
                val actual = selfServiceTilgangService.leggTilTiltakskoordinatorTilgang(navAnsatt.navIdent, deltakerliste.id)

                actual.isSuccess shouldBe true

                val expected = TiltakskoordinatorsDeltakerlistePayload.fromModel(actual.getOrThrow(), navAnsatt.navIdent)
                assertProduced(expected)
            }
        }

        @Test
        fun `med inaktiv tilgang fra for - returnerer success`() = runTest {
            with(TiltakskoordinatorTilgangContext()) {
                medInaktivTilgang()

                val actual = selfServiceTilgangService.leggTilTiltakskoordinatorTilgang(navAnsatt.navIdent, deltakerliste.id)
                actual.isSuccess shouldBe true

                val expected = TiltakskoordinatorsDeltakerlistePayload.fromModel(actual.getOrThrow(), navAnsatt.navIdent)
                assertProduced(expected)
            }
        }

        @Test
        fun `har tilgang fra for - returnerer failure`() = runTest {
            with(TiltakskoordinatorTilgangContext()) {
                medAktivTilgang()
                val actual = selfServiceTilgangService.leggTilTiltakskoordinatorTilgang(navAnsatt.navIdent, deltakerliste.id)
                actual.isFailure shouldBe true
            }
        }
    }

    @Nested
    inner class FjernTiltakskoordinatorTilgang {
        @Test
        fun `har tilgang fra for - returnerer success`() = runTest {
            with(TiltakskoordinatorTilgangContext()) {
                medAktivTilgang()
                val actual = selfServiceTilgangService.fjernTiltakskoordinatorTilgang(navAnsatt.navIdent, deltakerliste.id)
                actual.isSuccess shouldBe true

                val expected = TiltakskoordinatorsDeltakerlistePayload.fromModel(model = actual.getOrThrow(), navIdent = navAnsatt.navIdent)
                assertProduced(tilgang = expected, tombstoneExpected = true)
            }
        }

        @Test
        fun `har ikke tilgang fra for - returnerer failure`() = runTest {
            with(TiltakskoordinatorTilgangContext()) {
                val actual = selfServiceTilgangService.fjernTiltakskoordinatorTilgang(
                    navIdent = navAnsatt.navIdent,
                    deltakerlisteId = deltakerliste.id,
                )
                actual.isFailure shouldBe true
            }
        }

        @Test
        fun `med inaktiv tilgang fra for - returnerer failure`() = runTest {
            with(TiltakskoordinatorTilgangContext()) {
                medInaktivTilgang()
                val actual = selfServiceTilgangService.fjernTiltakskoordinatorTilgang(
                    navIdent = navAnsatt.navIdent,
                    deltakerlisteId = deltakerliste.id,
                )
                actual.isFailure shouldBe true
            }
        }
    }

    @Nested
    inner class VerifiserTiltakskoordinatorTilgang {
        @Test
        fun `verifiserTiltakskoordinatorTilgang - har tilgang - kaster ikke exception`() = runTest {
            with(TiltakskoordinatorTilgangContext()) {
                medAktivTilgang()
                selfServiceTilgangService.verifiserTiltakskoordinatorTilgang(navAnsatt.navIdent, deltakerliste.id)
            }
        }

        @Test
        fun `verifiserTiltakskoordinatorTilgang - har ingen tilgang - kaster exception`() = runTest {
            with(TiltakskoordinatorTilgangContext()) {
                assertThrows<AuthorizationException> {
                    selfServiceTilgangService.verifiserTiltakskoordinatorTilgang(navAnsatt.navIdent, deltakerliste.id)
                }
            }
        }

        @Test
        fun `verifiserTiltakskoordinatorTilgang - har inaktiv tilgang - kaster exception`() = runTest {
            with(TiltakskoordinatorTilgangContext()) {
                medInaktivTilgang()
                assertThrows<AuthorizationException> {
                    selfServiceTilgangService.verifiserTiltakskoordinatorTilgang(navAnsatt.navIdent, deltakerliste.id)
                }
            }
        }
    }

    @Nested
    inner class StengTiltakskoordinatorTilgang {
        @Test
        fun `stengTiltakskoordinatorTilgang - aktiv tilgang - tilgang stenges`() {
            with(TiltakskoordinatorTilgangContext()) {
                medAktivTilgang()
                val stengtTilgang = selfServiceTilgangService.stengTiltakskoordinatorTilgang(tilgang.id)

                runTest {
                    assertThrows<AuthorizationException> {
                        selfServiceTilgangService.verifiserTiltakskoordinatorTilgang(navAnsatt.navIdent, deltakerliste.id)
                    }
                }

                assertProducedTombstone(stengtTilgang.getOrThrow())
            }
        }

        @Test
        fun `stengTiltakskoordinatorTilgang - ikke aktiv tilgang - tilgang stenges ikke pa nytt`() {
            with(TiltakskoordinatorTilgangContext()) {
                medInaktivTilgang()
                val resultat = selfServiceTilgangService.stengTiltakskoordinatorTilgang(secondTilgang.id)

                resultat.isFailure shouldBe true
            }
        }
    }
}
