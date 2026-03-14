package no.nav.tiltaksarrangor.melding.forslag

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.arrangor.melding.Melding
import no.nav.tiltaksarrangor.consumer.model.NavAnsatt
import no.nav.tiltaksarrangor.consumer.model.NavEnhet
import no.nav.tiltaksarrangor.kafka.stringStringConsumer
import no.nav.tiltaksarrangor.melding.MELDING_TOPIC
import no.nav.tiltaksarrangor.repositories.NavAnsattRepository
import no.nav.tiltaksarrangor.repositories.NavEnhetRepository
import no.nav.tiltaksarrangor.repositories.model.AnsattDbo
import no.nav.tiltaksarrangor.repositories.model.ArrangorDbo
import no.nav.tiltaksarrangor.repositories.model.DeltakerDbo
import no.nav.tiltaksarrangor.repositories.model.DeltakerlisteDbo
import no.nav.tiltaksarrangor.testutils.DeltakerContext
import no.nav.tiltaksarrangor.testutils.getArrangor
import no.nav.tiltaksarrangor.testutils.getDeltaker
import no.nav.tiltaksarrangor.testutils.getDeltakerliste
import no.nav.tiltaksarrangor.testutils.getKoordinator
import no.nav.tiltaksarrangor.testutils.getNavAnsatt
import no.nav.tiltaksarrangor.testutils.getNavEnhet
import no.nav.tiltaksarrangor.utils.objectMapper
import org.awaitility.Awaitility.await
import org.springframework.context.ApplicationContext
import tools.jackson.module.kotlin.readValue
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.reflect.KClass

class ForslagCtx(
	applicationContext: ApplicationContext,
	var forslag: Forslag,
	arrangor: ArrangorDbo = getArrangor(),
	deltakerliste: DeltakerlisteDbo = getDeltakerliste(arrangorId = arrangor.id),
	koordinator: AnsattDbo = getKoordinator(
		id = forslag.opprettetAvArrangorAnsattId,
		arrangorId = arrangor.id,
		deltakerlisteId = deltakerliste.id,
	),
	deltaker: DeltakerDbo = getDeltaker(forslag.deltakerId, deltakerlisteId = deltakerliste.id),
) : DeltakerContext(
		applicationContext,
		arrangor = arrangor,
		deltakerliste = deltakerliste,
		koordinator = koordinator,
		deltaker = deltaker,
	) {
	var navAnsatt: NavAnsatt? = null

	var navEnhet: NavEnhet? = null

	private val navAnsattRepository = getOrCreateBean { template -> NavAnsattRepository(template) }
	private val forslagRepository = getOrCreateBean { template -> ForslagRepository(template) }
	private val navEnhetRepository = getOrCreateBean { template -> NavEnhetRepository(template) }

	init {
		opprettNavAnsattForForslag()
	}

	fun setForslagGodkjent() {
		val status = Forslag.Status.Godkjent(
			Forslag.NavAnsatt(UUID.randomUUID(), UUID.randomUUID()),
			LocalDateTime.now(),
		)

		forslag = forslag.copy(status = status)
		opprettNavAnsattForForslag()
	}

	fun medInaktiveForslag() {
		leggTilForslagMedStatus(
			Forslag.Status.Tilbakekalt(
				UUID.randomUUID(),
				LocalDateTime.now(),
			),
		)
		leggTilForslagMedStatus(
			Forslag.Status.Godkjent(
				Forslag.NavAnsatt(UUID.randomUUID(), UUID.randomUUID()),
				LocalDateTime.now(),
			),
		)
		leggTilForslagMedStatus(
			Forslag.Status.Avvist(
				Forslag.NavAnsatt(UUID.randomUUID(), UUID.randomUUID()),
				LocalDateTime.now(),
				"Avvist!",
			),
		)
	}

	fun leggTilForslagMedStatus(status: Forslag.Status) {
		forslagRepository.upsert(
			forlengDeltakelseForslag(
				deltakerId = deltaker.id,
				opprettetAvArrangorAnsattId = koordinator.id,
				status = status,
			),
		)
	}

	fun upsertForslag() = forslagRepository.upsert(forslag)

	private fun opprettNavAnsattForForslag() {
		val forslagNavAnsatt = when (val status = forslag.status) {
			is Forslag.Status.Avvist -> status.avvistAv
			is Forslag.Status.Godkjent -> status.godkjentAv
			is Forslag.Status.Tilbakekalt -> null
			Forslag.Status.VenterPaSvar -> null
			is Forslag.Status.Erstattet -> null
		}

		if (forslagNavAnsatt != null) {
			navAnsatt = getNavAnsatt(id = forslagNavAnsatt.id)
			navEnhet = getNavEnhet(id = forslagNavAnsatt.enhetId)
			navAnsatt?.let { navAnsattRepository.upsert(it) }
			navEnhet?.let { navEnhetRepository.upsert(it) }
		} else {
			navAnsatt = null
			navEnhet = null
		}
	}
}

fun forlengDeltakelseForslag(
	deltakerId: UUID = UUID.randomUUID(),
	opprettetAvArrangorAnsattId: UUID = UUID.randomUUID(),
	sluttdato: LocalDate = LocalDate.now(),
	begrunnelse: String = "Fordi...",
	status: Forslag.Status = Forslag.Status.VenterPaSvar,
) = Forslag(
	id = UUID.randomUUID(),
	deltakerId = deltakerId,
	opprettetAvArrangorAnsattId = opprettetAvArrangorAnsattId,
	opprettet = LocalDateTime.now(),
	begrunnelse = begrunnelse,
	endring = Forslag.ForlengDeltakelse(sluttdato),
	status = status,
)

fun <T : Forslag.Endring> assertProducedForslag(forslagId: UUID, endringstype: KClass<T>) {
	val cache = mutableMapOf<UUID, Melding>()

	val consumer = stringStringConsumer(MELDING_TOPIC) { k, v ->
		cache[UUID.fromString(k)] = objectMapper.readValue(v)
	}

	consumer.start()

	await().untilAsserted {
		val cachedForslag = cache[forslagId]!! as Forslag
		cachedForslag.id shouldBe forslagId
		cachedForslag.endring::class shouldBe endringstype
	}

	runBlocking { consumer.close() }
}

fun getProducedForslag(id: UUID): Forslag {
	val cache = mutableMapOf<UUID, Melding>()

	val consumer = stringStringConsumer(MELDING_TOPIC) { k, v ->
		cache[UUID.fromString(k)] = objectMapper.readValue(v)
	}

	consumer.start()

	await().untilAsserted {
		cache[id] shouldNotBe null
	}

	runBlocking { consumer.close() }

	return cache[id]!! as Forslag
}
