package no.nav.amt.aktivitetskort.client

import no.nav.amt.aktivitetskort.domain.Arrangor
import no.nav.common.rest.client.RestClient.baseClient
import okhttp3.OkHttpClient
import okhttp3.Request
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import java.util.UUID
import java.util.function.Supplier

class AmtArrangorClient(
    private val baseUrl: String,
    private val tokenProvider: Supplier<String>,
    private val objectMapper: ObjectMapper,
    private val httpClient: OkHttpClient = baseClient(),
) {
    fun hentArrangor(orgnummer: String): ArrangorMedOverordnetArrangorDto {
        val request = Request
            .Builder()
            .url("$baseUrl/api/service/arrangor/organisasjonsnummer/$orgnummer")
            .addHeader("Authorization", "Bearer ${tokenProvider.get()}")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("Kunne ikke hente arrangør med orgnummer $orgnummer fra amt-arrangør. Status=${response.code}")
            }
            return objectMapper.readValue<ArrangorMedOverordnetArrangorDto>(response.body.string())
        }
    }

    fun hentArrangor(arrangorId: UUID): ArrangorMedOverordnetArrangorDto {
        val request = Request
            .Builder()
            .url("$baseUrl/api/service/arrangor/$arrangorId")
            .addHeader("Authorization", "Bearer ${tokenProvider.get()}")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("Kunne ikke hente arrangør med id $arrangorId fra amt-arrangør. Status=${response.code}")
            }
            return objectMapper.readValue<ArrangorMedOverordnetArrangorDto>(response.body.string())
        }
    }

    data class ArrangorMedOverordnetArrangorDto(
        val id: UUID,
        val navn: String,
        val organisasjonsnummer: String,
        val overordnetArrangor: Arrangor?,
    )
}
