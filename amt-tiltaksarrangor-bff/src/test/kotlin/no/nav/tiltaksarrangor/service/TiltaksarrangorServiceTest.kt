package no.nav.tiltaksarrangor.service

import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.every
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.arrangor.melding.Vurdering
import no.nav.amt.lib.models.arrangor.melding.Vurderingstype
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import no.nav.tiltaksarrangor.IntegrationTest
import no.nav.tiltaksarrangor.api.request.RegistrerVurderingRequest
import no.nav.tiltaksarrangor.api.response.OppdateringResponse
import no.nav.tiltaksarrangor.client.amtarrangor.AmtArrangorClient
import no.nav.tiltaksarrangor.consumer.model.AdresseJsonDbo
import no.nav.tiltaksarrangor.consumer.model.AnsattRolle
import no.nav.tiltaksarrangor.consumer.model.BostedsadresseJsonDbo
import no.nav.tiltaksarrangor.consumer.model.EndringsmeldingType
import no.nav.tiltaksarrangor.consumer.model.Innhold
import no.nav.tiltaksarrangor.consumer.model.KontaktadresseJsonDbo
import no.nav.tiltaksarrangor.consumer.model.MatrikkeladresseJsonDbo
import no.nav.tiltaksarrangor.consumer.model.OppholdsadresseJsonDbo
import no.nav.tiltaksarrangor.consumer.model.PostboksadresseJsonDbo
import no.nav.tiltaksarrangor.consumer.model.VegadresseJsonDbo
import no.nav.tiltaksarrangor.melding.MeldingProducer
import no.nav.tiltaksarrangor.melding.forslag.forlengDeltakelseForslag
import no.nav.tiltaksarrangor.model.Adressetype
import no.nav.tiltaksarrangor.model.Endringsmelding
import no.nav.tiltaksarrangor.model.Oppdatering
import no.nav.tiltaksarrangor.model.Veiledertype
import no.nav.tiltaksarrangor.model.exceptions.SkjultDeltakerException
import no.nav.tiltaksarrangor.model.exceptions.UnauthorizedException
import no.nav.tiltaksarrangor.model.exceptions.ValidationException
import no.nav.tiltaksarrangor.repositories.AnsattRepository
import no.nav.tiltaksarrangor.repositories.ArrangorRepository
import no.nav.tiltaksarrangor.repositories.DeltakerRepository
import no.nav.tiltaksarrangor.repositories.DeltakerlisteRepository
import no.nav.tiltaksarrangor.repositories.EndringsmeldingRepository
import no.nav.tiltaksarrangor.repositories.UlestEndringRepository
import no.nav.tiltaksarrangor.repositories.model.AnsattDbo
import no.nav.tiltaksarrangor.repositories.model.AnsattRolleDbo
import no.nav.tiltaksarrangor.repositories.model.EndringsmeldingDbo
import no.nav.tiltaksarrangor.repositories.model.KoordinatorDeltakerlisteDbo
import no.nav.tiltaksarrangor.repositories.model.VeilederDeltakerDbo
import no.nav.tiltaksarrangor.testutils.getArrangor
import no.nav.tiltaksarrangor.testutils.getDeltaker
import no.nav.tiltaksarrangor.testutils.getDeltakerliste
import no.nav.tiltaksarrangor.testutils.getNavAnsatt
import no.nav.tiltaksarrangor.testutils.getNavEnhet
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class TiltaksarrangorServiceTest(
	private val ansattRepository: AnsattRepository,
	private val deltakerRepository: DeltakerRepository,
	private val deltakerlisteRepository: DeltakerlisteRepository,
	private val arrangorRepository: ArrangorRepository,
	private val endringsmeldingRepository: EndringsmeldingRepository,
	private val ulestEndringRepository: UlestEndringRepository,
	private val tiltaksarrangorService: TiltaksarrangorService,
	@MockkBean @Suppress("unused") private val amtArrangorClient: AmtArrangorClient,
	@MockkBean(relaxed = true) @Suppress("unused") private val metricsService: MetricsService,
	@MockkBean(relaxed = true) @Suppress("unused") private val auditLoggerService: AuditLoggerService,
	@MockkBean(relaxUnitFun = true) private val navAnsattService: NavAnsattService,
	@MockkBean(relaxUnitFun = true) private val navEnhetService: NavEnhetService,
	@MockkBean private val unleashToggle: CommonUnleashToggle,
	@MockkBean(relaxUnitFun = true) @Suppress("unused") private val meldingProducer: MeldingProducer,
) : IntegrationTest() {
	@BeforeEach
	fun setup() {
		every { unleashToggle.erKometMasterForTiltakstype(any<Tiltakskode>()) } returns false
	}

	@Test
	fun `getDeltaker - ansatt har ikke rolle hos arrangor - returnerer unauthorized`() {
		val personIdent = "12345678910"
		val arrangorId = UUID.randomUUID()
		val deltakerliste = getDeltakerliste(arrangorId)
		deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste)
		val deltakerId = UUID.randomUUID()
		deltakerRepository.insertOrUpdateDeltaker(getDeltaker(deltakerId, deltakerliste.id))
		ansattRepository.insertOrUpdateAnsatt(
			AnsattDbo(
				id = UUID.randomUUID(),
				personIdent = personIdent,
				fornavn = "Fornavn",
				mellomnavn = null,
				etternavn = "Etternavn",
				roller =
					listOf(
						AnsattRolleDbo(UUID.randomUUID(), AnsattRolle.KOORDINATOR),
					),
				deltakerlister = emptyList(),
				veilederDeltakere = emptyList(),
			),
		)

		assertThrows<UnauthorizedException> {
			tiltaksarrangorService.getDeltaker(personIdent, deltakerId)
		}
	}

	@Test
	fun `getDeltaker - deltaker er skjult - returnerer skjult deltaker exception`() {
		val personIdent = "12345678910"
		val arrangorId = UUID.randomUUID()
		val deltakerliste = getDeltakerliste(arrangorId)
		deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste)
		val deltakerId = UUID.randomUUID()
		val deltaker =
			getDeltaker(deltakerId, deltakerliste.id).copy(skjultDato = LocalDateTime.now(), skjultAvAnsattId = UUID.randomUUID())
		deltakerRepository.insertOrUpdateDeltaker(deltaker)
		ansattRepository.insertOrUpdateAnsatt(
			AnsattDbo(
				id = UUID.randomUUID(),
				personIdent = personIdent,
				fornavn = "Fornavn",
				mellomnavn = null,
				etternavn = "Etternavn",
				roller =
					listOf(
						AnsattRolleDbo(arrangorId, AnsattRolle.KOORDINATOR),
					),
				deltakerlister = listOf(KoordinatorDeltakerlisteDbo(deltakerliste.id)),
				veilederDeltakere = emptyList(),
			),
		)

		assertThrows<SkjultDeltakerException> {
			tiltaksarrangorService.getDeltaker(personIdent, deltakerId)
		}
	}

	@Test
	fun `getDeltaker - deltaker finnes og ansatt har tilgang - returnerer deltaker`() {
		val personIdent = "12345678910"
		val arrangorId = UUID.randomUUID()
		val deltakerliste = getDeltakerliste(arrangorId)
		deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste)
		val deltakerId = UUID.randomUUID()
		deltakerRepository.insertOrUpdateDeltaker(
			getDeltaker(deltakerId, deltakerliste.id).copy(
				vurderingerFraArrangor =
					listOf(
						Vurdering(
							id = UUID.randomUUID(),
							deltakerId = deltakerId,
							vurderingstype = Vurderingstype.OPPFYLLER_IKKE_KRAVENE,
							begrunnelse = "Mangler førerkort",
							opprettetAvArrangorAnsattId = UUID.randomUUID(),
							opprettet = LocalDateTime.now().minusWeeks(2),
						),
						Vurdering(
							id = UUID.randomUUID(),
							deltakerId = deltakerId,
							vurderingstype = Vurderingstype.OPPFYLLER_KRAVENE,
							begrunnelse = null,
							opprettetAvArrangorAnsattId = UUID.randomUUID(),
							opprettet = LocalDateTime.now(),
						),
					),
			),
		)
		ansattRepository.insertOrUpdateAnsatt(
			AnsattDbo(
				id = UUID.randomUUID(),
				personIdent = personIdent,
				fornavn = "Fornavn",
				mellomnavn = null,
				etternavn = "Etternavn",
				roller =
					listOf(
						AnsattRolleDbo(arrangorId, AnsattRolle.KOORDINATOR),
					),
				deltakerlister = listOf(KoordinatorDeltakerlisteDbo(deltakerliste.id)),
				veilederDeltakere = emptyList(),
			),
		)

		val deltaker = tiltaksarrangorService.getDeltaker(personIdent, deltakerId)

		deltaker.id shouldBe deltakerId
		deltaker.deltakerliste.id shouldBe deltakerliste.id
		deltaker.dagerPerUke shouldBe null
		deltaker.soktInnPa shouldBe deltakerliste.navn
		deltaker.tiltakskode shouldBe deltakerliste.tiltakskode
		deltaker.aktiveEndringsmeldinger.size shouldBe 0
		deltaker.veiledere.size shouldBe 0
		deltaker.adresse?.adressetype shouldBe Adressetype.KONTAKTADRESSE
		deltaker.adresse?.postnummer shouldBe "1234"
		deltaker.adresse?.poststed shouldBe "MOSS"
		deltaker.adresse?.tilleggsnavn shouldBe null
		deltaker.adresse?.adressenavn shouldBe "Gate 1"
		deltaker.gjeldendeVurderingFraArrangor?.vurderingstype shouldBe Vurderingstype.OPPFYLLER_KRAVENE
		deltaker.adressebeskyttet shouldBe false
	}

	@Test
	fun `getDeltaker - deltakerliste er ikke tilgjengelig - returnerer NoSuchElementException`() {
		val personIdent = "12345678910"
		val arrangorId = UUID.randomUUID()
		val deltakerliste = getDeltakerliste(arrangorId).copy(
			startDato = LocalDate.now().plusMonths(1),
		)
		deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste)
		val deltakerId = UUID.randomUUID()
		deltakerRepository.insertOrUpdateDeltaker(
			getDeltaker(deltakerId, deltakerliste.id).copy(
				vurderingerFraArrangor =
					listOf(
						Vurdering(
							id = UUID.randomUUID(),
							deltakerId = deltakerId,
							vurderingstype = Vurderingstype.OPPFYLLER_IKKE_KRAVENE,
							begrunnelse = "Mangler førerkort",
							opprettetAvArrangorAnsattId = UUID.randomUUID(),
							opprettet = LocalDateTime.now().minusWeeks(2),
						),
						Vurdering(
							id = UUID.randomUUID(),
							deltakerId = deltakerId,
							vurderingstype = Vurderingstype.OPPFYLLER_KRAVENE,
							begrunnelse = null,
							opprettetAvArrangorAnsattId = UUID.randomUUID(),
							opprettet = LocalDateTime.now(),
						),
					),
			),
		)
		ansattRepository.insertOrUpdateAnsatt(
			AnsattDbo(
				id = UUID.randomUUID(),
				personIdent = personIdent,
				fornavn = "Fornavn",
				mellomnavn = null,
				etternavn = "Etternavn",
				roller =
					listOf(
						AnsattRolleDbo(arrangorId, AnsattRolle.KOORDINATOR),
					),
				deltakerlister = listOf(KoordinatorDeltakerlisteDbo(deltakerliste.id)),
				veilederDeltakere = emptyList(),
			),
		)

		assertThrows<NoSuchElementException> {
			tiltaksarrangorService.getDeltaker(personIdent, deltakerId)
		}
	}

	@Test
	fun `getDeltaker - deltaker som har veiledere og endringsmeldinger finnes og ansatt har tilgang - returnerer deltaker`() {
		val personIdent = "12345678910"
		val arrangorId = UUID.randomUUID()
		val deltakerliste = getDeltakerliste(arrangorId)
		deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste)
		val deltakerId = UUID.randomUUID()
		deltakerRepository.insertOrUpdateDeltaker(getDeltaker(deltakerId, deltakerliste.id))
		endringsmeldingRepository.insertOrUpdateEndringsmelding(
			EndringsmeldingDbo(
				id = UUID.randomUUID(),
				deltakerId = deltakerId,
				type = EndringsmeldingType.ENDRE_SLUTTDATO,
				innhold = Innhold.EndreSluttdatoInnhold(sluttdato = LocalDate.now()),
				status = Endringsmelding.Status.AKTIV,
				sendt = LocalDateTime.now(),
			),
		)
		val veilederId = UUID.randomUUID()
		ansattRepository.insertOrUpdateAnsatt(
			AnsattDbo(
				id = veilederId,
				personIdent = UUID.randomUUID().toString(),
				fornavn = "Vei",
				mellomnavn = null,
				etternavn = "Leder",
				roller =
					listOf(
						AnsattRolleDbo(arrangorId, AnsattRolle.VEILEDER),
					),
				deltakerlister = emptyList(),
				veilederDeltakere = listOf(VeilederDeltakerDbo(deltakerId, Veiledertype.VEILEDER)),
			),
		)
		ansattRepository.insertOrUpdateAnsatt(
			AnsattDbo(
				id = UUID.randomUUID(),
				personIdent = personIdent,
				fornavn = "Fornavn",
				mellomnavn = null,
				etternavn = "Etternavn",
				roller =
					listOf(
						AnsattRolleDbo(arrangorId, AnsattRolle.KOORDINATOR),
					),
				deltakerlister = listOf(KoordinatorDeltakerlisteDbo(deltakerliste.id)),
				veilederDeltakere = emptyList(),
			),
		)

		val deltaker = tiltaksarrangorService.getDeltaker(personIdent, deltakerId)

		deltaker.id shouldBe deltakerId
		deltaker.deltakerliste.id shouldBe deltakerliste.id
		deltaker.dagerPerUke shouldBe null
		deltaker.soktInnPa shouldBe deltakerliste.navn
		deltaker.tiltakskode shouldBe deltakerliste.tiltakskode
		deltaker.aktiveEndringsmeldinger.size shouldBe 1
		val endringsmelding = deltaker.aktiveEndringsmeldinger.first()
		endringsmelding.type shouldBe Endringsmelding.Type.ENDRE_SLUTTDATO
		deltaker.historiskeEndringsmeldinger.size shouldBe 0
		val innhold = endringsmelding.innhold as Endringsmelding.Innhold.EndreSluttdatoInnhold
		innhold.sluttdato shouldBe LocalDate.now()
		deltaker.veiledere.size shouldBe 1
		val veileder = deltaker.veiledere.first()
		veileder.ansattId shouldBe veilederId
		veileder.veiledertype shouldBe Veiledertype.VEILEDER
		deltaker.gjeldendeVurderingFraArrangor?.vurderingstype shouldBe Vurderingstype.OPPFYLLER_IKKE_KRAVENE
	}

	@Test
	fun `getDeltaker - deltaker har endringsmeldinger og ansatt har tilgang, komet er master - returnerer deltaker`() {
		every { unleashToggle.erKometMasterForTiltakstype(any<Tiltakskode>()) } returns true
		val personIdent = "12345678910"
		val arrangorId = UUID.randomUUID()
		val deltakerliste = getDeltakerliste(arrangorId)
		deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste)
		val deltakerId = UUID.randomUUID()
		deltakerRepository.insertOrUpdateDeltaker(getDeltaker(deltakerId, deltakerliste.id))
		endringsmeldingRepository.insertOrUpdateEndringsmelding(
			EndringsmeldingDbo(
				id = UUID.randomUUID(),
				deltakerId = deltakerId,
				type = EndringsmeldingType.ENDRE_SLUTTDATO,
				innhold = Innhold.EndreSluttdatoInnhold(sluttdato = LocalDate.now()),
				status = Endringsmelding.Status.AKTIV,
				sendt = LocalDateTime.now(),
			),
		)
		val veilederId = UUID.randomUUID()
		ansattRepository.insertOrUpdateAnsatt(
			AnsattDbo(
				id = veilederId,
				personIdent = UUID.randomUUID().toString(),
				fornavn = "Vei",
				mellomnavn = null,
				etternavn = "Leder",
				roller =
					listOf(
						AnsattRolleDbo(arrangorId, AnsattRolle.VEILEDER),
					),
				deltakerlister = emptyList(),
				veilederDeltakere = listOf(VeilederDeltakerDbo(deltakerId, Veiledertype.VEILEDER)),
			),
		)
		ansattRepository.insertOrUpdateAnsatt(
			AnsattDbo(
				id = UUID.randomUUID(),
				personIdent = personIdent,
				fornavn = "Fornavn",
				mellomnavn = null,
				etternavn = "Etternavn",
				roller =
					listOf(
						AnsattRolleDbo(arrangorId, AnsattRolle.KOORDINATOR),
					),
				deltakerlister = listOf(KoordinatorDeltakerlisteDbo(deltakerliste.id)),
				veilederDeltakere = emptyList(),
			),
		)

		val deltaker = tiltaksarrangorService.getDeltaker(personIdent, deltakerId)

		deltaker.id shouldBe deltakerId
		deltaker.deltakerliste.id shouldBe deltakerliste.id
		deltaker.dagerPerUke shouldBe null
		deltaker.soktInnPa shouldBe deltakerliste.navn
		deltaker.tiltakskode shouldBe deltakerliste.tiltakskode
		deltaker.aktiveEndringsmeldinger.size shouldBe 0
		deltaker.historiskeEndringsmeldinger.size shouldBe 0
		deltaker.veiledere.size shouldBe 1
		val veileder = deltaker.veiledere.first()
		veileder.ansattId shouldBe veilederId
		veileder.veiledertype shouldBe Veiledertype.VEILEDER
		deltaker.gjeldendeVurderingFraArrangor?.vurderingstype shouldBe Vurderingstype.OPPFYLLER_IKKE_KRAVENE
	}

	@Test
	fun `getDeltaker - deltaker har uleste forslag og ansatt har tilgang, komet er master - returnerer deltaker`() {
		every { unleashToggle.erKometMasterForTiltakstype(any<Tiltakskode>()) } returns true
		val personIdent = "12345678910"
		val arrangorId = UUID.randomUUID()
		arrangorRepository.insertOrUpdateArrangor(getArrangor(arrangorId))
		val deltakerliste = getDeltakerliste(arrangorId).copy(tilgjengeligForArrangorFraOgMedDato = LocalDate.now().minusDays(1))
		deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste)
		val deltakerId = UUID.randomUUID()
		val deltakerDbo = getDeltaker(deltakerId, deltakerliste.id)
		deltakerRepository.insertOrUpdateDeltaker(deltakerDbo)
		ansattRepository.insertOrUpdateAnsatt(
			AnsattDbo(
				id = UUID.randomUUID(),
				personIdent = personIdent,
				fornavn = "Fornavn",
				mellomnavn = null,
				etternavn = "Etternavn",
				roller =
					listOf(
						AnsattRolleDbo(arrangorId, AnsattRolle.KOORDINATOR),
					),
				deltakerlister = listOf(KoordinatorDeltakerlisteDbo(deltakerliste.id)),
				veilederDeltakere = emptyList(),
			),
		)

		val navAnsattId = UUID.randomUUID()
		val navEnhetId = UUID.randomUUID()
		coEvery { navAnsattService.hentAnsatteForUlesteEndringer(any()) } returns mapOf(navAnsattId to getNavAnsatt(navAnsattId))
		coEvery { navEnhetService.hentEnheterForUlesteEndringer(any()) } returns mapOf(navEnhetId to getNavEnhet(navEnhetId))

		ulestEndringRepository.insert(
			deltakerId,
			Oppdatering.AvvistForslag(
				forlengDeltakelseForslag(
					status = Forslag.Status.Avvist(
						Forslag.NavAnsatt(
							navAnsattId,
							navEnhetId,
						),
						LocalDateTime.now(),
						"Fordi...",
					),
				),
			),
		)

		val deltaker = tiltaksarrangorService.getDeltaker(personIdent, deltakerId)

		deltaker.id shouldBe deltakerId
		deltaker.ulesteEndringer.size shouldBe 1
		deltaker.ulesteEndringer[0].deltakerId shouldBe deltakerId
		val avvistForslagResponse = deltaker.ulesteEndringer[0].oppdatering as OppdateringResponse.AvvistForslagResponse
		avvistForslagResponse.forslag.begrunnelse shouldBe "Fordi..."
	}

	@Test
	fun `getDeltaker - deltaker er adressebeskyttet og ansatt er veileder - returnerer deltaker uten adresse`() {
		val personIdent = "12345678910"
		val arrangorId = UUID.randomUUID()
		val deltakerliste = getDeltakerliste(arrangorId)
		deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste)
		val deltakerId = UUID.randomUUID()
		val deltakerDbo = getDeltaker(deltakerId, deltakerliste.id, adressebeskyttet = true).copy(
			vurderingerFraArrangor =
				listOf(
					Vurdering(
						id = UUID.randomUUID(),
						deltakerId = deltakerId,
						vurderingstype = Vurderingstype.OPPFYLLER_IKKE_KRAVENE,
						begrunnelse = "Mangler førerkort",
						opprettetAvArrangorAnsattId = UUID.randomUUID(),
						opprettet = LocalDateTime.now().minusWeeks(2),
					),
					Vurdering(
						id = UUID.randomUUID(),
						deltakerId = deltakerId,
						vurderingstype = Vurderingstype.OPPFYLLER_KRAVENE,
						begrunnelse = null,
						opprettetAvArrangorAnsattId = UUID.randomUUID(),
						opprettet = LocalDateTime.now(),
					),
				),
		)
		deltakerRepository.insertOrUpdateDeltaker(deltakerDbo)
		ansattRepository.insertOrUpdateAnsatt(
			AnsattDbo(
				id = UUID.randomUUID(),
				personIdent = personIdent,
				fornavn = "Fornavn",
				mellomnavn = null,
				etternavn = "Etternavn",
				roller =
					listOf(
						AnsattRolleDbo(arrangorId, AnsattRolle.VEILEDER),
					),
				deltakerlister = emptyList(),
				veilederDeltakere = listOf(VeilederDeltakerDbo(deltakerId, Veiledertype.MEDVEILEDER)),
			),
		)
		endringsmeldingRepository.insertOrUpdateEndringsmelding(
			EndringsmeldingDbo(
				id = UUID.randomUUID(),
				deltakerId = deltakerId,
				type = EndringsmeldingType.ENDRE_SLUTTDATO,
				innhold = Innhold.EndreSluttdatoInnhold(sluttdato = LocalDate.now()),
				status = Endringsmelding.Status.AKTIV,
				sendt = LocalDateTime.now(),
			),
		)

		val deltaker = tiltaksarrangorService.getDeltaker(personIdent, deltakerId)

		deltaker.id shouldBe deltakerId
		deltaker.fornavn shouldBe deltakerDbo.fornavn
		deltaker.fodselsnummer shouldBe deltakerDbo.personident
		deltaker.bestillingTekst shouldNotBe null
		deltaker.deltakerliste.id shouldBe deltakerliste.id
		deltaker.soktInnPa shouldBe deltakerliste.navn
		deltaker.tiltakskode shouldBe deltakerliste.tiltakskode
		deltaker.aktiveEndringsmeldinger.size shouldBe 1
		deltaker.veiledere.size shouldBe 1
		deltaker.adresse shouldBe null
		deltaker.gjeldendeVurderingFraArrangor?.vurderingstype shouldBe Vurderingstype.OPPFYLLER_KRAVENE
		deltaker.adressebeskyttet shouldBe true
	}

	@Test
	fun `getDeltaker - deltaker er adressebeskyttet og ansatt er koordinator - returnerer deltaker med tomme felter`() {
		val personIdent = "12345678910"
		val arrangorId = UUID.randomUUID()
		val deltakerliste = getDeltakerliste(arrangorId)
		deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste)
		val deltakerId = UUID.randomUUID()
		val deltakerDbo = getDeltaker(deltakerId, deltakerliste.id, adressebeskyttet = true).copy(
			vurderingerFraArrangor =
				listOf(
					Vurdering(
						id = UUID.randomUUID(),
						deltakerId = deltakerId,
						vurderingstype = Vurderingstype.OPPFYLLER_IKKE_KRAVENE,
						begrunnelse = "Mangler førerkort",
						opprettetAvArrangorAnsattId = UUID.randomUUID(),
						opprettet = LocalDateTime.now().minusWeeks(2),
					),
					Vurdering(
						id = UUID.randomUUID(),
						deltakerId = deltakerId,
						vurderingstype = Vurderingstype.OPPFYLLER_KRAVENE,
						begrunnelse = null,
						opprettetAvArrangorAnsattId = UUID.randomUUID(),
						opprettet = LocalDateTime.now(),
					),
				),
		)
		deltakerRepository.insertOrUpdateDeltaker(deltakerDbo)
		ansattRepository.insertOrUpdateAnsatt(
			AnsattDbo(
				id = UUID.randomUUID(),
				personIdent = personIdent,
				fornavn = "Fornavn",
				mellomnavn = null,
				etternavn = "Etternavn",
				roller =
					listOf(
						AnsattRolleDbo(arrangorId, AnsattRolle.KOORDINATOR),
					),
				deltakerlister = listOf(KoordinatorDeltakerlisteDbo(deltakerliste.id)),
				veilederDeltakere = emptyList(),
			),
		)
		endringsmeldingRepository.insertOrUpdateEndringsmelding(
			EndringsmeldingDbo(
				id = UUID.randomUUID(),
				deltakerId = deltakerId,
				type = EndringsmeldingType.ENDRE_SLUTTDATO,
				innhold = Innhold.EndreSluttdatoInnhold(sluttdato = LocalDate.now()),
				status = Endringsmelding.Status.AKTIV,
				sendt = LocalDateTime.now(),
			),
		)

		val deltaker = tiltaksarrangorService.getDeltaker(personIdent, deltakerId)

		deltaker.id shouldBe deltakerId
		deltaker.fornavn shouldBe ""
		deltaker.fodselsnummer shouldBe ""
		deltaker.bestillingTekst shouldBe null
		deltaker.deltakerliste.id shouldBe deltakerliste.id
		deltaker.soktInnPa shouldBe deltakerliste.navn
		deltaker.tiltakskode shouldBe deltakerliste.tiltakskode
		deltaker.aktiveEndringsmeldinger.size shouldBe 0
		deltaker.veiledere.size shouldBe 0
		deltaker.adresse shouldBe null
		deltaker.gjeldendeVurderingFraArrangor?.vurderingstype shouldBe null
		deltaker.adressebeskyttet shouldBe true
	}

	@Test
	fun `fjernDeltaker - ansatt har ikke rolle hos arrangor - returnerer unauthorized`() {
		val personIdent = "12345678910"
		val arrangorId = UUID.randomUUID()
		val deltakerliste = getDeltakerliste(arrangorId)
		deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste)
		val deltakerId = UUID.randomUUID()
		deltakerRepository.insertOrUpdateDeltaker(getDeltaker(deltakerId, deltakerliste.id))
		ansattRepository.insertOrUpdateAnsatt(
			AnsattDbo(
				id = UUID.randomUUID(),
				personIdent = personIdent,
				fornavn = "Fornavn",
				mellomnavn = null,
				etternavn = "Etternavn",
				roller =
					listOf(
						AnsattRolleDbo(UUID.randomUUID(), AnsattRolle.KOORDINATOR),
					),
				deltakerlister = emptyList(),
				veilederDeltakere = emptyList(),
			),
		)

		assertThrows<UnauthorizedException> {
			tiltaksarrangorService.fjernDeltaker(personIdent, deltakerId)
		}
	}

	@Test
	fun `fjernDeltaker - deltaker er ikke aktuell og ansatt har tilgang - skjuler deltaker`() {
		val personIdent = "12345678910"
		val arrangorId = UUID.randomUUID()
		val deltakerliste = getDeltakerliste(arrangorId)
		deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste)
		val deltakerId = UUID.randomUUID()
		val deltaker = getDeltaker(deltakerId, deltakerliste.id).copy(status = DeltakerStatus.Type.IKKE_AKTUELL)
		deltakerRepository.insertOrUpdateDeltaker(deltaker)
		val ansattId = UUID.randomUUID()
		ansattRepository.insertOrUpdateAnsatt(
			AnsattDbo(
				id = ansattId,
				personIdent = personIdent,
				fornavn = "Fornavn",
				mellomnavn = null,
				etternavn = "Etternavn",
				roller =
					listOf(
						AnsattRolleDbo(arrangorId, AnsattRolle.KOORDINATOR),
					),
				deltakerlister = listOf(KoordinatorDeltakerlisteDbo(deltakerliste.id)),
				veilederDeltakere = emptyList(),
			),
		)

		tiltaksarrangorService.fjernDeltaker(personIdent, deltakerId)

		val deltakerFraDb = deltakerRepository.getDeltaker(deltakerId)
		deltakerFraDb?.skjultAvAnsattId shouldBe ansattId
		deltakerFraDb?.skjultDato shouldNotBe null
	}

	@Test
	fun `fjernDeltaker - deltaker venter pa oppstart og ansatt har tilgang - returnerer illegal state exception`() {
		val personIdent = "12345678910"
		val arrangorId = UUID.randomUUID()
		val deltakerliste = getDeltakerliste(arrangorId)
		deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste)
		val deltakerId = UUID.randomUUID()
		val deltaker = getDeltaker(deltakerId, deltakerliste.id).copy(status = DeltakerStatus.Type.VENTER_PA_OPPSTART)
		deltakerRepository.insertOrUpdateDeltaker(deltaker)
		val ansattId = UUID.randomUUID()
		ansattRepository.insertOrUpdateAnsatt(
			AnsattDbo(
				id = ansattId,
				personIdent = personIdent,
				fornavn = "Fornavn",
				mellomnavn = null,
				etternavn = "Etternavn",
				roller =
					listOf(
						AnsattRolleDbo(arrangorId, AnsattRolle.KOORDINATOR),
					),
				deltakerlister = listOf(KoordinatorDeltakerlisteDbo(deltakerliste.id)),
				veilederDeltakere = emptyList(),
			),
		)

		assertThrows<IllegalStateException> {
			tiltaksarrangorService.fjernDeltaker(personIdent, deltakerId)
		}

		val deltakerFraDb = deltakerRepository.getDeltaker(deltakerId)
		deltakerFraDb?.skjultAvAnsattId shouldBe null
		deltakerFraDb?.skjultDato shouldBe null
	}

	@Test
	fun `registrerVurdering - ansatt har ikke rolle hos arrangor - returnerer unauthorized`() {
		val personIdent = "12345678910"
		val arrangorId = UUID.randomUUID()
		val deltakerliste = getDeltakerliste(arrangorId)
		deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste)
		val deltakerId = UUID.randomUUID()
		deltakerRepository.insertOrUpdateDeltaker(getDeltaker(deltakerId, deltakerliste.id).copy(status = DeltakerStatus.Type.VURDERES))
		ansattRepository.insertOrUpdateAnsatt(
			AnsattDbo(
				id = UUID.randomUUID(),
				personIdent = personIdent,
				fornavn = "Fornavn",
				mellomnavn = null,
				etternavn = "Etternavn",
				roller =
					listOf(
						AnsattRolleDbo(UUID.randomUUID(), AnsattRolle.KOORDINATOR),
					),
				deltakerlister = emptyList(),
				veilederDeltakere = emptyList(),
			),
		)

		assertThrows<UnauthorizedException> {
			tiltaksarrangorService.registrerVurdering(
				personIdent,
				deltakerId,
				RegistrerVurderingRequest(Vurderingstype.OPPFYLLER_IKKE_KRAVENE, "Ikke gode nok norskkunnskaper"),
			)
		}
	}

	@Test
	fun `registrerVurdering - deltaker har status vurderes og ansatt har tilgang - vurdering blir lagret`() {
		val deltakerId = UUID.randomUUID()
		val ansattId = UUID.randomUUID()
		val forsteVurdering =
			Vurdering(
				id = UUID.randomUUID(),
				deltakerId = deltakerId,
				vurderingstype = Vurderingstype.OPPFYLLER_IKKE_KRAVENE,
				begrunnelse = "Mangler grunnkurs",
				opprettetAvArrangorAnsattId = UUID.randomUUID(),
				opprettet = LocalDateTime.now().minusWeeks(1),
			)
		val personIdent = "12345678910"
		val arrangorId = UUID.randomUUID()
		val deltakerliste = getDeltakerliste(arrangorId)
		deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste)
		val deltaker =
			getDeltaker(deltakerId, deltakerliste.id).copy(
				status = DeltakerStatus.Type.VURDERES,
				vurderingerFraArrangor = listOf(forsteVurdering),
			)
		deltakerRepository.insertOrUpdateDeltaker(deltaker)
		ansattRepository.insertOrUpdateAnsatt(
			AnsattDbo(
				id = ansattId,
				personIdent = personIdent,
				fornavn = "Fornavn",
				mellomnavn = null,
				etternavn = "Etternavn",
				roller =
					listOf(
						AnsattRolleDbo(arrangorId, AnsattRolle.KOORDINATOR),
					),
				deltakerlister = listOf(KoordinatorDeltakerlisteDbo(deltakerliste.id)),
				veilederDeltakere = emptyList(),
			),
		)

		tiltaksarrangorService.registrerVurdering(
			personIdent,
			deltakerId,
			RegistrerVurderingRequest(Vurderingstype.OPPFYLLER_KRAVENE, null),
		)

		val deltakerFraDb = deltakerRepository.getDeltaker(deltakerId)
		deltakerFraDb?.vurderingerFraArrangor?.size shouldBe 2
		deltakerFraDb?.vurderingerFraArrangor?.maxBy { it.opprettet }?.vurderingstype shouldBe Vurderingstype.OPPFYLLER_KRAVENE
		deltakerFraDb?.vurderingerFraArrangor?.minBy { it.opprettet }?.vurderingstype shouldBe Vurderingstype.OPPFYLLER_IKKE_KRAVENE
	}

	@Test
	fun `registrerVurdering - deltaker venter pa oppstart og ansatt har tilgang - returnerer illegal state exception`() {
		val personIdent = "12345678910"
		val arrangorId = UUID.randomUUID()
		val deltakerliste = getDeltakerliste(arrangorId)
		deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste)
		val deltakerId = UUID.randomUUID()
		val deltaker = getDeltaker(
			deltakerId,
			deltakerliste.id,
		).copy(status = DeltakerStatus.Type.VENTER_PA_OPPSTART, vurderingerFraArrangor = null)
		deltakerRepository.insertOrUpdateDeltaker(deltaker)
		val ansattId = UUID.randomUUID()
		ansattRepository.insertOrUpdateAnsatt(
			AnsattDbo(
				id = ansattId,
				personIdent = personIdent,
				fornavn = "Fornavn",
				mellomnavn = null,
				etternavn = "Etternavn",
				roller =
					listOf(
						AnsattRolleDbo(arrangorId, AnsattRolle.KOORDINATOR),
					),
				deltakerlister = listOf(KoordinatorDeltakerlisteDbo(deltakerliste.id)),
				veilederDeltakere = emptyList(),
			),
		)

		assertThrows<IllegalStateException> {
			tiltaksarrangorService.registrerVurdering(
				personIdent,
				deltakerId,
				RegistrerVurderingRequest(Vurderingstype.OPPFYLLER_KRAVENE, null),
			)
		}

		val deltakerFraDb = deltakerRepository.getDeltaker(deltakerId)
		deltakerFraDb?.vurderingerFraArrangor shouldBe null
	}

	@Test
	fun `registrerVurdering - reguest har type OPPFYLLER_IKKE_KRAVENE og mangler begrunnelse - returnerer ValidationException`() {
		val personIdent = "12345678910"
		val arrangorId = UUID.randomUUID()
		val deltakerliste = getDeltakerliste(arrangorId)
		deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste)
		val deltakerId = UUID.randomUUID()
		val deltaker = getDeltaker(deltakerId, deltakerliste.id).copy(status = DeltakerStatus.Type.VURDERES, vurderingerFraArrangor = null)
		deltakerRepository.insertOrUpdateDeltaker(deltaker)
		val ansattId = UUID.randomUUID()
		ansattRepository.insertOrUpdateAnsatt(
			AnsattDbo(
				id = ansattId,
				personIdent = personIdent,
				fornavn = "Fornavn",
				mellomnavn = null,
				etternavn = "Etternavn",
				roller =
					listOf(
						AnsattRolleDbo(arrangorId, AnsattRolle.KOORDINATOR),
					),
				deltakerlister = listOf(KoordinatorDeltakerlisteDbo(deltakerliste.id)),
				veilederDeltakere = emptyList(),
			),
		)

		assertThrows<ValidationException> {
			tiltaksarrangorService.registrerVurdering(
				personIdent,
				deltakerId,
				RegistrerVurderingRequest(Vurderingstype.OPPFYLLER_IKKE_KRAVENE, null),
			)
		}

		val deltakerFraDb = deltakerRepository.getDeltaker(deltakerId)
		deltakerFraDb?.vurderingerFraArrangor shouldBe null
	}

	@Test
	fun `registrerVurdering - deltaker har status vurderes, er adressebeskyttet, ansatt er veileder - vurdering blir lagret`() {
		val deltakerId = UUID.randomUUID()
		val ansattId = UUID.randomUUID()
		val forsteVurdering =
			Vurdering(
				id = UUID.randomUUID(),
				deltakerId = deltakerId,
				vurderingstype = Vurderingstype.OPPFYLLER_IKKE_KRAVENE,
				begrunnelse = "Mangler grunnkurs",
				opprettetAvArrangorAnsattId = UUID.randomUUID(),
				opprettet = LocalDateTime.now().minusWeeks(1),
			)
		val personIdent = "12345678910"
		val arrangorId = UUID.randomUUID()
		val deltakerliste = getDeltakerliste(arrangorId)
		deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste)
		val deltaker =
			getDeltaker(deltakerId, deltakerliste.id, adressebeskyttet = true).copy(
				status = DeltakerStatus.Type.VURDERES,
				vurderingerFraArrangor = listOf(forsteVurdering),
			)
		deltakerRepository.insertOrUpdateDeltaker(deltaker)
		ansattRepository.insertOrUpdateAnsatt(
			AnsattDbo(
				id = ansattId,
				personIdent = personIdent,
				fornavn = "Fornavn",
				mellomnavn = null,
				etternavn = "Etternavn",
				roller =
					listOf(
						AnsattRolleDbo(arrangorId, AnsattRolle.VEILEDER),
					),
				deltakerlister = emptyList(),
				veilederDeltakere = listOf(VeilederDeltakerDbo(deltakerId, Veiledertype.VEILEDER)),
			),
		)

		tiltaksarrangorService.registrerVurdering(
			personIdent,
			deltakerId,
			RegistrerVurderingRequest(Vurderingstype.OPPFYLLER_KRAVENE, null),
		)

		val deltakerFraDb = deltakerRepository.getDeltaker(deltakerId)
		deltakerFraDb?.vurderingerFraArrangor?.size shouldBe 2
		deltakerFraDb?.vurderingerFraArrangor?.maxBy { it.opprettet }?.vurderingstype shouldBe Vurderingstype.OPPFYLLER_KRAVENE
		deltakerFraDb?.vurderingerFraArrangor?.minBy { it.opprettet }?.vurderingstype shouldBe Vurderingstype.OPPFYLLER_IKKE_KRAVENE
	}

	@Test
	fun `registrerVurdering - deltaker har status vurderes, er adressebeskyttet, ansatt er koordinator - returnerer UnauthorizedException`() {
		val deltakerId = UUID.randomUUID()
		val ansattId = UUID.randomUUID()
		val forsteVurdering =
			Vurdering(
				id = UUID.randomUUID(),
				deltakerId = deltakerId,
				vurderingstype = Vurderingstype.OPPFYLLER_IKKE_KRAVENE,
				begrunnelse = "Mangler grunnkurs",
				opprettetAvArrangorAnsattId = UUID.randomUUID(),
				opprettet = LocalDateTime.now().minusWeeks(1),
			)
		val personIdent = "12345678910"
		val arrangorId = UUID.randomUUID()
		val deltakerliste = getDeltakerliste(arrangorId)
		deltakerlisteRepository.insertOrUpdateDeltakerliste(deltakerliste)
		val deltaker =
			getDeltaker(deltakerId, deltakerliste.id, adressebeskyttet = true).copy(
				status = DeltakerStatus.Type.VURDERES,
				vurderingerFraArrangor = listOf(forsteVurdering),
			)
		deltakerRepository.insertOrUpdateDeltaker(deltaker)
		ansattRepository.insertOrUpdateAnsatt(
			AnsattDbo(
				id = ansattId,
				personIdent = personIdent,
				fornavn = "Fornavn",
				mellomnavn = null,
				etternavn = "Etternavn",
				roller =
					listOf(
						AnsattRolleDbo(arrangorId, AnsattRolle.KOORDINATOR),
					),
				deltakerlister = listOf(KoordinatorDeltakerlisteDbo(deltakerliste.id)),
				veilederDeltakere = emptyList(),
			),
		)

		assertThrows<UnauthorizedException> {
			tiltaksarrangorService.registrerVurdering(
				personIdent,
				deltakerId,
				RegistrerVurderingRequest(Vurderingstype.OPPFYLLER_KRAVENE, null),
			)
		}
	}

	@Test
	fun `getAdresse - deltaker har adresse, tiltakstype jobbklubb - returnerer null`() {
		val arrangorId = UUID.randomUUID()
		val deltakerliste = getDeltakerliste(arrangorId).copy(tiltakskode = Tiltakskode.JOBBKLUBB)
		val deltakerId = UUID.randomUUID()
		val deltaker = getDeltaker(deltakerId, deltakerliste.id)

		val adresse = deltaker.getAdresse(deltakerliste)

		adresse shouldBe null
	}

	@Test
	fun `getAdresse - deltaker har kontaktadresse, bostedsadresse og oppholdsadresse, tiltakstype AFT - returnerer kontaktadresse`() {
		val arrangorId = UUID.randomUUID()
		val deltakerliste = getDeltakerliste(arrangorId).copy(tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING)
		val deltakerId = UUID.randomUUID()
		val deltaker =
			getDeltaker(deltakerId, deltakerliste.id).copy(
				adresse =
					AdresseJsonDbo(
						bostedsadresse =
							BostedsadresseJsonDbo(
								coAdressenavn = "C/O Gutterommet",
								vegadresse = null,
								matrikkeladresse =
									MatrikkeladresseJsonDbo(
										tilleggsnavn = "Gården",
										postnummer = "0484",
										poststed = "OSLO",
									),
							),
						oppholdsadresse =
							OppholdsadresseJsonDbo(
								coAdressenavn = null,
								vegadresse =
									VegadresseJsonDbo(
										husnummer = "1",
										husbokstav = "B",
										adressenavn = "Veien",
										tilleggsnavn = null,
										postnummer = "1234",
										poststed = "MOSS",
									),
								matrikkeladresse =
									MatrikkeladresseJsonDbo(
										tilleggsnavn = "Fortet",
										postnummer = "0101",
										poststed = "ANDEBY",
									),
							),
						kontaktadresse =
							KontaktadresseJsonDbo(
								coAdressenavn = null,
								vegadresse = null,
								postboksadresse =
									PostboksadresseJsonDbo(
										postboks = "45451",
										postnummer = "3312",
										poststed = "VESTØYA",
									),
							),
					),
			)

		val adresse = deltaker.getAdresse(deltakerliste)

		adresse?.adressetype shouldBe Adressetype.KONTAKTADRESSE
		adresse?.postnummer shouldBe "3312"
		adresse?.poststed shouldBe "VESTØYA"
		adresse?.tilleggsnavn shouldBe null
		adresse?.adressenavn shouldBe "Postboks 45451"
	}

	@Test
	fun `getAdresse - deltaker har bostedsadresse og oppholdsadresse, tiltakstype AFT - returnerer oppholdsadresse`() {
		val arrangorId = UUID.randomUUID()
		val deltakerliste = getDeltakerliste(arrangorId).copy(tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING)
		val deltakerId = UUID.randomUUID()
		val deltaker =
			getDeltaker(deltakerId, deltakerliste.id).copy(
				adresse =
					AdresseJsonDbo(
						bostedsadresse =
							BostedsadresseJsonDbo(
								coAdressenavn = "C/O Gutterommet",
								vegadresse = null,
								matrikkeladresse =
									MatrikkeladresseJsonDbo(
										tilleggsnavn = "Gården",
										postnummer = "0484",
										poststed = "OSLO",
									),
							),
						oppholdsadresse =
							OppholdsadresseJsonDbo(
								coAdressenavn = "C/O Pappa",
								vegadresse =
									VegadresseJsonDbo(
										husnummer = "1",
										husbokstav = "B",
										adressenavn = "Veien",
										tilleggsnavn = null,
										postnummer = "1234",
										poststed = "MOSS",
									),
								matrikkeladresse =
									MatrikkeladresseJsonDbo(
										tilleggsnavn = "Fortet",
										postnummer = "0101",
										poststed = "ANDEBY",
									),
							),
						kontaktadresse = null,
					),
			)

		val adresse = deltaker.getAdresse(deltakerliste)

		adresse?.adressetype shouldBe Adressetype.OPPHOLDSADRESSE
		adresse?.postnummer shouldBe "1234"
		adresse?.poststed shouldBe "MOSS"
		adresse?.tilleggsnavn shouldBe null
		adresse?.adressenavn shouldBe "C/O Pappa, Veien 1B"
	}

	@Test
	fun `getAdresse - deltaker har bare bostedsadresse, tiltakstype AFT - returnerer bostedsadresse`() {
		val arrangorId = UUID.randomUUID()
		val deltakerliste = getDeltakerliste(arrangorId).copy(tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING)
		val deltakerId = UUID.randomUUID()
		val deltaker =
			getDeltaker(deltakerId, deltakerliste.id).copy(
				adresse =
					AdresseJsonDbo(
						bostedsadresse =
							BostedsadresseJsonDbo(
								coAdressenavn = "C/O Gutterommet",
								vegadresse = null,
								matrikkeladresse =
									MatrikkeladresseJsonDbo(
										tilleggsnavn = "Gården",
										postnummer = "0484",
										poststed = "OSLO",
									),
							),
						oppholdsadresse = null,
						kontaktadresse = null,
					),
			)

		val adresse = deltaker.getAdresse(deltakerliste)

		adresse?.adressetype shouldBe Adressetype.BOSTEDSADRESSE
		adresse?.postnummer shouldBe "0484"
		adresse?.poststed shouldBe "OSLO"
		adresse?.tilleggsnavn shouldBe "Gården"
		adresse?.adressenavn shouldBe "C/O Gutterommet"
	}
}
