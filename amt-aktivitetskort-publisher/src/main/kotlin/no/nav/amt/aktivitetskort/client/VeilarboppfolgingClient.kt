package no.nav.amt.aktivitetskort.client

import no.nav.amt.aktivitetskort.client.request.PersonRequest
import no.nav.amt.aktivitetskort.domain.Oppfolgingsperiode
import no.nav.amt.aktivitetskort.utils.toSystemZoneLocalDateTime
import no.nav.amt.lib.spring.boot.client.executeUpstreamCall
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service

@Service
class VeilarboppfolgingClient(
    private val api: VeilarboppfolgingApi,
) {
    fun hentOppfolgingperiode(fnr: String): Oppfolgingsperiode? = executeUpstreamCall(
        serviceName = VEILARBOPPFOLGING_CLIENT_ID,
        operation = "hente oppfølgingsperiode",
    ) {
        val response = api.hentGjeldendePeriode(PersonRequest(fnr))

        if (response.statusCode == HttpStatus.NO_CONTENT) {
            null
        } else {
            response.body?.let {
                Oppfolgingsperiode(
                    id = it.uuid,
                    startDato = it.startDato.toSystemZoneLocalDateTime(),
                    sluttDato = it.sluttDato?.toSystemZoneLocalDateTime(),
                )
            }
        }
    }
}
