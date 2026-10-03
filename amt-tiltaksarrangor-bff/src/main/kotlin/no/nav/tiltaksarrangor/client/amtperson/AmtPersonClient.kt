package no.nav.tiltaksarrangor.client.amtperson

import no.nav.amt.lib.models.kafka.Kontaktinformasjon
import no.nav.tiltaksarrangor.client.AMT_PERSON_SERVICE_CLIENT_ID
import no.nav.tiltaksarrangor.client.executeUpstreamCallWithUnauthorizedMapping
import no.nav.tiltaksarrangor.consumer.model.NavEnhet
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AmtPersonClient(
    private val api: AmtPersonApi,
) {
    fun hentEnhet(id: UUID): NavEnhet = executeUpstreamCallWithUnauthorizedMapping(
        serviceName = AMT_PERSON_SERVICE_CLIENT_ID,
        operation = "hente Nav-enhet",
        unauthorizedMessage = "Ikke tilgang til å hente Nav-enhet fra amt-person-service",
    ) {
        api.hentEnhet(id).body?.toNavEnhet()
            ?: throw RuntimeException("Kunne ikke hente Nav-enhet fra amt-person-service")
    }

    fun hentNavAnsatt(id: UUID): NavAnsattResponse = executeUpstreamCallWithUnauthorizedMapping(
        serviceName = AMT_PERSON_SERVICE_CLIENT_ID,
        operation = "hente Nav-ansatt",
        unauthorizedMessage = "Ikke tilgang til å hente Nav-ansatt fra amt-person-service",
    ) {
        api.hentNavAnsatt(id).body
            ?: throw RuntimeException("Kunne ikke hente Nav-ansatt fra amt-person-service")
    }

    fun hentOppdatertKontaktinfo(personident: String): Kontaktinformasjon = hentOppdatertKontaktinfo(setOf(personident)).let {
        it[personident] ?: throw NoSuchElementException("Klarte ikke hente kontaktinformasjon for person med ident")
    }

    fun hentOppdatertKontaktinfo(personidenter: Set<String>): Map<String, Kontaktinformasjon> = executeUpstreamCallWithUnauthorizedMapping(
        serviceName = AMT_PERSON_SERVICE_CLIENT_ID,
        operation = "hente kontaktinformasjon",
        unauthorizedMessage = "Ikke tilgang til å hente kontaktinformasjon fra amt-person-service",
    ) {
        api.hentKontaktinformasjon(personidenter).body
            ?: throw RuntimeException("Kunne ikke hente kontaktinformasjon fra amt-person-service.")
    }
}
