package no.nav.tiltaksarrangor.service

import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.tiltaksarrangor.client.amtperson.AmtPersonClient
import no.nav.tiltaksarrangor.consumer.model.NavEnhet
import no.nav.tiltaksarrangor.model.UlestEndring
import no.nav.tiltaksarrangor.repositories.NavEnhetRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.UUID

@Service
class NavEnhetService(
	private val repository: NavEnhetRepository,
	private val amtPersonClient: AmtPersonClient,
) {
	private val log = LoggerFactory.getLogger(javaClass)

	fun hentOpprettEllerOppdaterNavEnhet(id: UUID): NavEnhet {
		repository
			.get(id)
			?.takeIf { it.sistEndret.isAfter(LocalDateTime.now().minusMonths(1)) }
			?.let { return it.toNavEnhet() }

		log.info("Fant ikke oppdatert nav-enhet med nummer $id, henter fra amt-person-service")
		return fetchEnhet(id)
	}

	private fun fetchEnhet(id: UUID): NavEnhet {
		val enhet = amtPersonClient.hentEnhet(id)

		repository.upsert(enhet)
		log.info("Lagret nav-enhet $id")

		return enhet
	}

	fun hentEnheterForHistorikk(historikk: List<DeltakerHistorikk>): Map<UUID, NavEnhet> {
		val ider = historikk.flatMap { it.navEnheter() }.distinct()
		val enheterFraDb = hentEnheter(ider)

		return if (ider.size != enheterFraDb.size) {
			enheterFraDb + hentManglendeEnheter(ider, enheterFraDb).associateBy { it.id }
		} else {
			enheterFraDb
		}
	}

	private fun hentManglendeEnheter(ider: List<UUID>, lagredeEnheter: Map<UUID, NavEnhet>): List<NavEnhet> {
		val manglendeEnheter = ider.toSet() - lagredeEnheter.keys
		return manglendeEnheter.map { fetchEnhet(it) }
	}

	fun hentEnheterForUlesteEndringer(ulesteEndringer: List<UlestEndring>): Map<UUID, NavEnhet> {
		val ider = ulesteEndringer.mapNotNull { it.navEnheter() }.distinct()
		return hentEnheter(ider)
	}

	private fun hentEnheter(enhetIder: List<UUID>) = repository.getMany(enhetIder).map { it.toNavEnhet() }.associateBy { it.id }

	fun hentEnhet(id: UUID): NavEnhet? = repository.get(id)?.toNavEnhet()

	fun upsert(enhet: NavEnhet) = repository.upsert(enhet)
}
