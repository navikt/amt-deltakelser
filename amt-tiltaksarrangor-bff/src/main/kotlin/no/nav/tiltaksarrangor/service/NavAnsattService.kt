package no.nav.tiltaksarrangor.service

import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.tiltaksarrangor.client.amtperson.AmtPersonClient
import no.nav.tiltaksarrangor.client.amtperson.NavAnsattResponse
import no.nav.tiltaksarrangor.consumer.model.NavAnsatt
import no.nav.tiltaksarrangor.model.UlestEndring
import no.nav.tiltaksarrangor.repositories.NavAnsattRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class NavAnsattService(
	private val navAnsattRepository: NavAnsattRepository,
	private val amtPersonClient: AmtPersonClient,
) {
	private val log = LoggerFactory.getLogger(javaClass)

	fun hentEllerOpprettNavAnsatt(id: UUID): NavAnsatt {
		navAnsattRepository.get(id)?.let { return it }

		log.info("Fant ikke Nav-ansatt med id $id i databasen, henter fra amt-person-service")

		return amtPersonClient.hentNavAnsatt(id).toModel().also { navAnsatt ->
			navAnsattRepository.upsert(navAnsatt)
			log.info("Lagret Nav-ansatt $id")
		}
	}

	fun hentAnsatteForHistorikk(historikk: List<DeltakerHistorikk>): Map<UUID, NavAnsatt> {
		val ider = historikk.flatMap { it.navAnsatte() }.distinct()
		return hentAnsatte(ider)
	}

	fun hentAnsatteForUlesteEndringer(ulesteEndringer: List<UlestEndring>): Map<UUID, NavAnsatt> {
		val ider = ulesteEndringer.mapNotNull { it.hentNavAnsattId() }.distinct()
		return hentAnsatte(ider)
	}

	private fun hentAnsatte(veilederIder: List<UUID>): Map<UUID, NavAnsatt> = navAnsattRepository
		.getMany(veilederIder)
		.associateBy { it.id }
}

fun NavAnsattResponse.toModel() = NavAnsatt(
	id = id,
	navident = navIdent,
	navn = navn,
	epost = epost,
	telefon = telefon,
)
