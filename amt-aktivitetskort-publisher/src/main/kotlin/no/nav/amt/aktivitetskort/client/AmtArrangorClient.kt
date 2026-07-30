package no.nav.amt.aktivitetskort.client

import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientResponseException
import java.util.UUID

@Service
class AmtArrangorClient(
    private val api: AmtArrangorApi,
) {
    fun hentArrangor(orgnummer: String): ArrangorMedOverordnetArrangorDto = try {
        api.hentArrangorByOrgnummer(orgnummer)
    } catch (e: RestClientResponseException) {
        throw RuntimeException("Kunne ikke hente arrangør med orgnummer $orgnummer fra amt-arrangør. Status=${e.statusCode}", e)
    }

    fun hentArrangor(arrangorId: UUID): ArrangorMedOverordnetArrangorDto = try {
        api.hentArrangorById(arrangorId)
    } catch (e: RestClientResponseException) {
        throw RuntimeException("Kunne ikke hente arrangør med id $arrangorId fra amt-arrangør. Status=${e.statusCode}", e)
    }
}
