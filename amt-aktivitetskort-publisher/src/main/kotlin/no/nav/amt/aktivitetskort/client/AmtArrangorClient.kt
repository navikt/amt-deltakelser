package no.nav.amt.aktivitetskort.client

import no.nav.amt.aktivitetskort.client.response.ArrangorMedOverordnetArrangorResponse
import no.nav.amt.lib.spring.boot.client.toExternalServiceException
import no.nav.amt.person.service.clients.AMT_ARRANGOR_CLIENT_ID
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientException
import java.util.UUID

@Service
class AmtArrangorClient(
    private val api: AmtArrangorApi,
) {
    fun hentArrangor(orgnummer: String): ArrangorMedOverordnetArrangorResponse = try {
        api.hentArrangorByOrgnummer(orgnummer)
    } catch (e: RestClientException) {
        throw e.toExternalServiceException(
            serviceName = AMT_ARRANGOR_CLIENT_ID,
            action = "hente arrangør med orgnummer $orgnummer",
        )
    }

    fun hentArrangor(arrangorId: UUID): ArrangorMedOverordnetArrangorResponse = try {
        api.hentArrangorById(arrangorId)
    } catch (e: RestClientException) {
        throw e.toExternalServiceException(
            serviceName = AMT_ARRANGOR_CLIENT_ID,
            action = "hente arrangør med id $arrangorId",
        )
    }
}
