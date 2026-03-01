package no.nav.amt.deltaker.bff.auth

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.amt.deltaker.bff.deltakerliste.DeltakerlisteService
import no.nav.amt.deltaker.bff.kafka.utils.assertProduced
import no.nav.amt.deltaker.bff.kafka.utils.assertProducedTombstone
import no.nav.amt.deltaker.bff.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.bff.navansatt.NavAnsattService
import no.nav.amt.deltaker.bff.tiltakskoordinator.TiltakskoordinatorService
import no.nav.amt.lib.kafka.Producer
import no.nav.amt.lib.kafka.config.LocalKafkaConfig
import no.nav.amt.lib.ktor.auth.exceptions.AuthorizationException
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.SingletonKafkaProvider
import no.nav.amt.lib.testing.TestOutboxEnvironment
import no.nav.poao_tilgang.client.Decision
import no.nav.poao_tilgang.client.NavAnsattBehandleFortroligBrukerePolicyInput
import no.nav.poao_tilgang.client.NavAnsattBehandleSkjermedePersonerPolicyInput
import no.nav.poao_tilgang.client.NavAnsattBehandleStrengtFortroligBrukerePolicyInput
import no.nav.poao_tilgang.client.PoaoTilgangCachedClient
import no.nav.poao_tilgang.client.PolicyInput
import no.nav.poao_tilgang.client.api.ApiResult
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.UUID
import kotlin.test.assertFailsWith

class TilgangskontrollServiceTest {
    private val poaoTilgangCachedClient = mockk<PoaoTilgangCachedClient>()

    private val kafkaProducer = Producer<String, String>(LocalKafkaConfig(SingletonKafkaProvider.getHost()))
    private val tiltakskoordinatorsDeltakerlisteProducer = TiltakskoordinatorsDeltakerlisteProducer(
        TestOutboxEnvironment.outboxService,
        kafkaProducer,
    )

    private val navAnsattService = NavAnsattService(NavAnsattRepository(), mockk())
    private val tiltakskoordinatorTilgangRepository = TiltakskoordinatorTilgangRepository()
    private val tilgangskontrollService = TilgangskontrollService(
        poaoTilgangCachedClient,
        navAnsattService,
        tiltakskoordinatorTilgangRepository,
        tiltakskoordinatorsDeltakerlisteProducer,
        mockk<TiltakskoordinatorService>(),
        mockk<DeltakerlisteService>(),
    )

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Test
    fun `verifiserSkrivetilgang - har tilgang - kaster ingen feil`() {
        mockPoaoTilgangPermit()

        tilgangskontrollService.verifiserSkrivetilgang(UUID.randomUUID(), "12345")
    }

    @Test
    fun `verifiserSkrivetilgang - har ikke tilgang - kaster AuthorizationException`() {
        mockPoaoTilgangDeny()

        assertFailsWith<AuthorizationException> {
            tilgangskontrollService.verifiserSkrivetilgang(UUID.randomUUID(), "12345")
        }
    }

    @Test
    fun `verifiserLesetilgang - har tilgang - kaster ingen feil`() {
        mockPoaoTilgangPermit()

        tilgangskontrollService.verifiserLesetilgang(UUID.randomUUID(), "12345")
    }

    @Test
    fun `verifiserLesetilgang - har ikke tilgang - kaster AuthorizationException`() {
        mockPoaoTilgangDeny()

        assertFailsWith<AuthorizationException> {
            tilgangskontrollService.verifiserLesetilgang(UUID.randomUUID(), "12345")
        }
    }

    @Nested
    inner class LeggTilTiltakskoordinatorTilgang {
        @Test
        fun `har ikke tilgang fra for - returnerer success`(): Unit = runBlocking {
            with(TiltakskoordinatorTilgangContext()) {
                val actual = tilgangskontrollService.leggTilTiltakskoordinatorTilgang(navAnsatt.navIdent, deltakerliste.id)

                actual.isSuccess shouldBe true

                val expected = TiltakskoordinatorsDeltakerlisteDto.fromModel(actual.getOrThrow(), navAnsatt.navIdent)
                assertProduced(expected)
            }
        }

        @Test
        fun `med inaktiv tilgang fra for - returnerer success`(): Unit = runBlocking {
            with(TiltakskoordinatorTilgangContext()) {
                medInaktivTilgang()

                val actual = tilgangskontrollService.leggTilTiltakskoordinatorTilgang(navAnsatt.navIdent, deltakerliste.id)
                actual.isSuccess shouldBe true

                val expected = TiltakskoordinatorsDeltakerlisteDto.fromModel(actual.getOrThrow(), navAnsatt.navIdent)
                assertProduced(expected)
            }
        }

        @Test
        fun `har tilgang fra for - returnerer failure`(): Unit = runBlocking {
            with(TiltakskoordinatorTilgangContext()) {
                medAktivTilgang()
                val actual = tilgangskontrollService.leggTilTiltakskoordinatorTilgang(navAnsatt.navIdent, deltakerliste.id)
                actual.isFailure shouldBe true
            }
        }
    }

    @Nested
    inner class FjernTiltakskoordinatorTilgang {
        @Test
        fun `har tilgang fra for - returnerer success`(): Unit = runBlocking {
            with(TiltakskoordinatorTilgangContext()) {
                medAktivTilgang()
                val actual = tilgangskontrollService.fjernTiltakskoordinatorTilgang(navAnsatt.navIdent, deltakerliste.id)
                actual.isSuccess shouldBe true

                val expected = TiltakskoordinatorsDeltakerlisteDto.fromModel(model = actual.getOrThrow(), navIdent = navAnsatt.navIdent)
                assertProduced(tilgang = expected, tombstoneExpected = true)
            }
        }

        @Test
        fun `har ikke tilgang fra for - returnerer failure`(): Unit = runBlocking {
            with(TiltakskoordinatorTilgangContext()) {
                val actual = tilgangskontrollService.fjernTiltakskoordinatorTilgang(
                    navIdent = navAnsatt.navIdent,
                    deltakerlisteId = deltakerliste.id,
                )
                actual.isFailure shouldBe true
            }
        }

        @Test
        fun `med inaktiv tilgang fra for - returnerer failure`(): Unit = runBlocking {
            with(TiltakskoordinatorTilgangContext()) {
                medInaktivTilgang()
                val actual = tilgangskontrollService.fjernTiltakskoordinatorTilgang(
                    navIdent = navAnsatt.navIdent,
                    deltakerlisteId = deltakerliste.id,
                )
                actual.isFailure shouldBe true
            }
        }
    }

    @Test
    fun `verifiserTiltakskoordinatorTilgang - har tilgang - kaster ikke exception`(): Unit = runBlocking {
        with(TiltakskoordinatorTilgangContext()) {
            medAktivTilgang()
            tilgangskontrollService.verifiserTiltakskoordinatorTilgang(navAnsatt.navIdent, deltakerliste.id)
        }
    }

    @Test
    fun `verifiserTiltakskoordinatorTilgang - har ingen tilgang - kaster exception`(): Unit = runBlocking {
        with(TiltakskoordinatorTilgangContext()) {
            assertThrows<AuthorizationException> {
                tilgangskontrollService.verifiserTiltakskoordinatorTilgang(navAnsatt.navIdent, deltakerliste.id)
            }
        }
    }

    @Test
    fun `verifiserTiltakskoordinatorTilgang - har inaktiv tilgang - kaster exception`(): Unit = runBlocking {
        with(TiltakskoordinatorTilgangContext()) {
            medInaktivTilgang()
            assertThrows<AuthorizationException> {
                tilgangskontrollService.verifiserTiltakskoordinatorTilgang(navAnsatt.navIdent, deltakerliste.id)
            }
        }
    }

    @Test
    fun `koordinatorTilgangTilDeltaker - mangler tilgang - deltaker er kode 7 - tilgang er false`() {
        with(TiltakskoordinatorTilgangContext()) {
            medFortroligDeltaker()
            mockPoaoTilgangDeny(NavAnsattBehandleFortroligBrukerePolicyInput(navAnsattAzureId))
            val tilgangTilDeltaker = tilgangskontrollService.harKoordinatorTilgangTilDeltaker(navAnsattAzureId, deltaker)
            tilgangTilDeltaker shouldBe false
        }
    }

    @Test
    fun `koordinatorTilgangTilDeltaker - mangler tilgang - deltaker er kode 6 - tilgang er false`() {
        with(TiltakskoordinatorTilgangContext()) {
            medStrengtFortroligDeltaker()
            mockPoaoTilgangDeny(NavAnsattBehandleStrengtFortroligBrukerePolicyInput(navAnsattAzureId))
            val tilgangTilDeltaker = tilgangskontrollService.harKoordinatorTilgangTilDeltaker(navAnsattAzureId, deltaker)
            tilgangTilDeltaker shouldBe false
        }
    }

    @Test
    fun `koordinatorTilgangTilDeltaker - har tilgang - deltaker er kode 7 - tilgang er true`() {
        with(TiltakskoordinatorTilgangContext()) {
            medFortroligDeltaker()
            mockPoaoTilgangPermit(NavAnsattBehandleFortroligBrukerePolicyInput(navAnsattAzureId))
            val tilgangTilDeltaker = tilgangskontrollService.harKoordinatorTilgangTilDeltaker(navAnsattAzureId, deltaker)
            tilgangTilDeltaker shouldBe true
        }
    }

    @Test
    fun `koordinatorTilgangTilDeltaker - har tilgang - deltaker er kode 6 - tilgang er true`() {
        with(TiltakskoordinatorTilgangContext()) {
            medStrengtFortroligDeltaker()
            mockPoaoTilgangPermit(NavAnsattBehandleStrengtFortroligBrukerePolicyInput(navAnsattAzureId))
            val tilgangTilDeltaker = tilgangskontrollService.harKoordinatorTilgangTilDeltaker(navAnsattAzureId, deltaker)
            tilgangTilDeltaker shouldBe true
        }
    }

    @Test
    fun `koordinatorTilgangTilDeltaker - har tilgang - deltaker er skjermet - tilgang er true`() {
        with(TiltakskoordinatorTilgangContext()) {
            medSkjermetDeltaker()
            mockPoaoTilgangPermit(NavAnsattBehandleSkjermedePersonerPolicyInput(navAnsattAzureId))
            val tilgangTilDeltaker = tilgangskontrollService.harKoordinatorTilgangTilDeltaker(navAnsattAzureId, deltaker)
            tilgangTilDeltaker shouldBe true
        }
    }

    @Test
    fun `koordinatorTilgangTilDeltaker - har ikke tilgang - deltaker er skjermet - tilgang er false`() {
        with(TiltakskoordinatorTilgangContext()) {
            medSkjermetDeltaker()
            mockPoaoTilgangDeny(NavAnsattBehandleSkjermedePersonerPolicyInput(navAnsattAzureId))
            val tilgangTilDeltaker = tilgangskontrollService.harKoordinatorTilgangTilDeltaker(navAnsattAzureId, deltaker)
            tilgangTilDeltaker shouldBe false
        }
    }

    @Test
    fun `koordinatorTilgangTilDeltaker - deltaker er ikke adressebeskyttet eller skjermet - tilgang er true`() {
        with(TiltakskoordinatorTilgangContext()) {
            val tilgangTilDeltaker = tilgangskontrollService.harKoordinatorTilgangTilDeltaker(navAnsattAzureId, deltaker)
            tilgangTilDeltaker shouldBe true
        }
    }

    @Test
    fun `stengTiltakskoordinatorTilgang - aktiv tilgang - tilgang stenges`() {
        with(TiltakskoordinatorTilgangContext()) {
            medAktivTilgang()
            val stengtTilgang = tilgangskontrollService.stengTiltakskoordinatorTilgang(tilgang.id)

            runBlocking {
                assertThrows<AuthorizationException> {
                    tilgangskontrollService.verifiserTiltakskoordinatorTilgang(navAnsatt.navIdent, deltakerliste.id)
                }
            }

            assertProducedTombstone(stengtTilgang.getOrThrow())
        }
    }

    @Test
    fun `stengTiltakskoordinatorTilgang - ikke aktiv tilgang - tilgang stenges ikke pa nytt`() {
        with(TiltakskoordinatorTilgangContext()) {
            medInaktivTilgang()
            val resultat = tilgangskontrollService.stengTiltakskoordinatorTilgang(secondTilgang.id)

            resultat.isFailure shouldBe true
        }
    }

    private fun mockPoaoTilgangDeny(policyInput: PolicyInput? = null) {
        every { poaoTilgangCachedClient.evaluatePolicy(policyInput ?: any()) } returns ApiResult(null, Decision.Deny("Ikke tilgang", ""))
    }

    private fun mockPoaoTilgangPermit(policyInput: PolicyInput? = null) {
        every { poaoTilgangCachedClient.evaluatePolicy(policyInput ?: any()) } returns ApiResult(null, Decision.Permit)
    }
}
