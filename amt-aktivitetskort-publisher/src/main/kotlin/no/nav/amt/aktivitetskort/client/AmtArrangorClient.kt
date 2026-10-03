package no.nav.amt.aktivitetskort.client

import no.nav.amt.aktivitetskort.client.response.ArrangorMedOverordnetArrangorResponse
import no.nav.amt.lib.spring.boot.client.executeUpstreamCallWithRequiredBody
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AmtArrangorClient(
    private val api: AmtArrangorApi,
) {
    fun hentArrangor(orgnummer: String): ArrangorMedOverordnetArrangorResponse = executeUpstreamCallWithRequiredBody(
        serviceName = AMT_ARRANGOR_CLIENT_ID,
        operation = "hente arrangør med orgnummer",
    ) { api.hentArrangorByOrgnummer(orgnummer) }

    fun hentArrangor(arrangorId: UUID): ArrangorMedOverordnetArrangorResponse = executeUpstreamCallWithRequiredBody(
        serviceName = AMT_ARRANGOR_CLIENT_ID,
        operation = "hente arrangør med id",
    ) { api.hentArrangorById(arrangorId) }
}
