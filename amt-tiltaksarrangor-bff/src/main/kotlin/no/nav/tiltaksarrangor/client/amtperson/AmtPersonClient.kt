package no.nav.tiltaksarrangor.client.amtperson

import no.nav.amt.lib.models.kafka.Kontaktinformasjon
import no.nav.amt.lib.spring.boot.client.executeUpstreamCallWithRequiredBody
import no.nav.tiltaksarrangor.client.AMT_PERSON_SERVICE_CLIENT_ID
import no.nav.tiltaksarrangor.consumer.model.NavEnhet
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AmtPersonClient(
    private val api: AmtPersonApi,
) {
    fun hentEnhet(id: UUID): NavEnhet = executeUpstreamCallWithRequiredBody(
        serviceName = AMT_PERSON_SERVICE_CLIENT_ID,
        operation = "hente Nav-enhet",
    ) { api.hentEnhet(id) }.toNavEnhet()

    fun hentNavAnsatt(id: UUID): NavAnsattResponse = executeUpstreamCallWithRequiredBody(
        serviceName = AMT_PERSON_SERVICE_CLIENT_ID,
        operation = "hente Nav-ansatt",
    ) { api.hentNavAnsatt(id) }

    fun hentOppdatertKontaktinfo(personident: String): Kontaktinformasjon = hentOppdatertKontaktinfo(setOf(personident))
        .let {
            it[personident]
                ?: throw NoSuchElementException("Klarte ikke hente kontaktinformasjon for person med ident")
        }

    fun hentOppdatertKontaktinfo(personidenter: Set<String>): Map<String, Kontaktinformasjon> = executeUpstreamCallWithRequiredBody(
        serviceName = AMT_PERSON_SERVICE_CLIENT_ID,
        operation = "hente kontaktinformasjon",
    ) { api.hentKontaktinformasjon(personidenter) }
}
