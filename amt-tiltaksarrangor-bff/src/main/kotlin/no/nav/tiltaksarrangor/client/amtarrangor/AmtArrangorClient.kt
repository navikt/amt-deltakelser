package no.nav.tiltaksarrangor.client.amtarrangor

import no.nav.tiltaksarrangor.client.amtarrangor.dto.ArrangorMedOverordnetArrangor
import no.nav.tiltaksarrangor.client.amtarrangor.dto.OppdaterVeiledereForDeltakerRequest
import no.nav.tiltaksarrangor.consumer.model.AnsattDto
import no.nav.tiltaksarrangor.model.exceptions.UnauthorizedException
import no.nav.tiltaksarrangor.utils.objectMapper
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import tools.jackson.module.kotlin.readValue
import java.util.UUID

@Component
class AmtArrangorClient(
	@Value($$"${amt-arrangor.url}") private val amtArrangorUrl: String,
	private val amtArrangorHttpClient: OkHttpClient,
	private val amtArrangorAADHttpClient: OkHttpClient,
) {
	private val log = LoggerFactory.getLogger(javaClass)
	private val mediaTypeJson = "application/json".toMediaType()

	fun getAnsatt(personIdent: String): AnsattDto? {
		val request =
			Request
				.Builder()
				.url("$amtArrangorUrl/api/ansatt")
				.get()
				.build()

		amtArrangorHttpClient.newCall(request).execute().use { response ->
			if (!response.isSuccessful) {
				when (response.code) {
					401 -> throw UnauthorizedException("Ikke tilgang til å hente ansatt")
					403 -> throw UnauthorizedException("Ikke tilgang til å hente ansatt")
					404 -> return null
					else -> {
						log.error("Kunne ikke hente ansatt fra amt-arrangør, responsekode: ${response.code}")
						throw RuntimeException("Kunne ikke hente ansatt")
					}
				}
			}

			return objectMapper.readValue(response.body.string())
		}
	}

	fun leggTilDeltakerlisteForKoordinator(
		ansattId: UUID,
		deltakerlisteId: UUID,
		arrangorId: UUID,
	) {
		val request =
			Request
				.Builder()
				.url("$amtArrangorUrl/api/ansatt/koordinator/$arrangorId/$deltakerlisteId")
				.post("".toRequestBody(mediaTypeJson))
				.build()

		amtArrangorHttpClient.newCall(request).execute().use { response ->
			if (!response.isSuccessful) {
				when (response.code) {
					401 -> throw UnauthorizedException("Ikke tilgang til å legge til deltakerliste i amt-arrangør")
					403 -> throw UnauthorizedException("Ikke tilgang til å legge til deltakerliste i amt-arrangør")
					else -> {
						log.error("Kunne ikke legge til deltakerliste $deltakerlisteId i amt-arrangør, responsekode: ${response.code}")
						throw RuntimeException("Kunne ikke legge til deltakerliste")
					}
				}
			}
		}
		log.info("Oppdatert amt-arrangor med deltakerliste $deltakerlisteId for ansatt $ansattId")
	}

	fun fjernDeltakerlisteForKoordinator(
		ansattId: UUID,
		deltakerlisteId: UUID,
		arrangorId: UUID,
	) {
		val request =
			Request
				.Builder()
				.url("$amtArrangorUrl/api/ansatt/koordinator/$arrangorId/$deltakerlisteId")
				.delete()
				.build()

		amtArrangorHttpClient.newCall(request).execute().use { response ->
			if (!response.isSuccessful) {
				when (response.code) {
					401 -> throw UnauthorizedException("Ikke tilgang til å fjerne deltakerliste i amt-arrangør")
					403 -> throw UnauthorizedException("Ikke tilgang til å fjerne deltakerliste i amt-arrangør")
					else -> {
						log.error("Kunne ikke fjerne deltakerliste $deltakerlisteId i amt-arrangør, responsekode: ${response.code}")
						throw RuntimeException("Kunne ikke fjerne deltakerliste")
					}
				}
			}
		}
		log.info("Fjernet amt-arrangor deltakerliste $deltakerlisteId for ansatt $ansattId")
	}

	fun oppdaterVeilederForDeltaker(deltakerId: UUID, oppdaterVeiledereForDeltakerRequest: OppdaterVeiledereForDeltakerRequest) {
		val request =
			Request
				.Builder()
				.url("$amtArrangorUrl/api/ansatt/veiledere/$deltakerId")
				.post(objectMapper.writeValueAsString(oppdaterVeiledereForDeltakerRequest).toRequestBody(mediaTypeJson))
				.build()

		amtArrangorHttpClient.newCall(request).execute().use { response ->
			if (!response.isSuccessful) {
				when (response.code) {
					401 -> throw UnauthorizedException("Ikke tilgang til å oppdatere veiledere i amt-arrangør")
					403 -> throw UnauthorizedException("Ikke tilgang til å oppdatere veiledere i amt-arrangør")
					else -> {
						log.error("Kunne ikke oppdatere veiledere for deltaker $deltakerId i amt-arrangør, responsekode: ${response.code}")
						throw RuntimeException("Kunne ikke oppdatere veiledere")
					}
				}
			}
		}
		log.info("Oppdatert amt-arrangor med veiledere for $deltakerId")
	}

	fun getArrangor(orgnummer: String): ArrangorMedOverordnetArrangor? {
		val request =
			Request
				.Builder()
				.url("$amtArrangorUrl/api/service/arrangor/organisasjonsnummer/$orgnummer")
				.get()
				.build()

		amtArrangorAADHttpClient.newCall(request).execute().use { response ->
			if (!response.isSuccessful) {
				if (response.code == 404) {
					log.info("Arrangør med orgnummer $orgnummer finnes ikke hos amt-arrangør")
					return null
				}
				log.error("Kunne ikke hente arrangør med orgnummer $orgnummer fra amt-arrangør. Status=${response.code}")
				throw RuntimeException("Kunne ikke hente arrangør med orgnummer $orgnummer fra amt-arrangør. Status=${response.code}")
			}

			return objectMapper.readValue(response.body.string())
		}
	}
}
