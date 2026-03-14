package no.nav.tiltaksarrangor.client.amtperson

import no.nav.amt.lib.models.deltaker.Kontaktinformasjon
import no.nav.tiltaksarrangor.consumer.model.NavEnhet
import no.nav.tiltaksarrangor.utils.objectMapper
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import tools.jackson.core.type.TypeReference
import tools.jackson.module.kotlin.readValue
import java.util.UUID

@Component
class AmtPersonClient(
	@Value($$"${amt-person.url}") private val url: String,
	private val amtPersonAADHttpClient: OkHttpClient,
) {
	private val log = LoggerFactory.getLogger(javaClass)

	fun hentEnhet(id: UUID): NavEnhet {
		val request =
			Request
				.Builder()
				.url("$url/api/nav-enhet/$id")
				.get()
				.build()

		amtPersonAADHttpClient.newCall(request).execute().use { response ->
			if (!response.isSuccessful) {
				log.error(
					"Kunne ikke hente nav-enhet med id $id fra amt-person-service. " +
						"Status=${response.code} error=${response.body.string()}",
				)
				error("Kunne ikke hente NAV-enhet fra amt-person-service")
			}

			return objectMapper.readValue<NavEnhetDto>(response.body.string()).toNavEnhet()
		}
	}

	fun hentNavAnsatt(id: UUID): NavAnsattResponse {
		val request =
			Request
				.Builder()
				.url("$url/api/nav-ansatt/$id")
				.get()
				.build()

		amtPersonAADHttpClient.newCall(request).execute().use { response ->
			if (!response.isSuccessful) {
				log.error(
					"Kunne ikke hente nav-ansatt med id $id fra amt-person-service. " +
						"Status=${response.code} error=${response.body.string()}",
				)
				error("Kunne ikke hente NAV-ansatt fra amt-person-service")
			}

			return objectMapper.readValue(response.body.string())
		}
	}

	fun hentOppdatertKontaktinfo(personident: String): Result<Kontaktinformasjon> = hentOppdatertKontaktinfo(setOf(personident)).mapCatching {
		it[personident] ?: throw NoSuchElementException("Klarte ikke hente kontaktinformasjon for person med ident")
	}

	fun hentOppdatertKontaktinfo(personidenter: Set<String>): Result<Map<String, Kontaktinformasjon>> = runCatching {
		val requestBody = objectMapper
			.writeValueAsString(personidenter)
			.toRequestBody(MediaType.APPLICATION_JSON_VALUE.toMediaType())

		val request = Request
			.Builder()
			.url("$url/api/nav-bruker/kontaktinformasjon")
			.post(requestBody)
			.build()

		amtPersonAADHttpClient.newCall(request).execute().use { response ->
			val responseBodyString = response.body.string()

			if (!response.isSuccessful) {
				log.error("$KONTAKTINFO_ERROR_MSG Status=${response.code} error=$responseBodyString")
				throw RuntimeException(KONTAKTINFO_ERROR_MSG)
			}

			objectMapper.readValue(responseBodyString, personIdentToKontaktinfoTypeRef)
		}
	}

	companion object {
		private val personIdentToKontaktinfoTypeRef = object : TypeReference<Map<String, Kontaktinformasjon>>() {}
		private const val KONTAKTINFO_ERROR_MSG = "Kunne ikke hente kontakinformasjon fra amt-person-service."
	}
}
