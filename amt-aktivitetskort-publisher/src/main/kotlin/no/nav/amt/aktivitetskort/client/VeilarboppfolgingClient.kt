package no.nav.amt.aktivitetskort.client

import no.nav.amt.aktivitetskort.domain.Oppfolgingsperiode
import no.nav.amt.aktivitetskort.utils.toSystemZoneLocalDateTime
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientResponseException

@Service
class VeilarboppfolgingClient(
    private val api: VeilarboppfolgingApi,
) {
    fun hentOppfolgingperiode(fnr: String): Oppfolgingsperiode? = try {
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
    } catch (e: RestClientResponseException) {
        throw RuntimeException("Uventet status ved hent status-kall mot veilarboppfolging ${e.statusCode.value()}", e)
    }
}
