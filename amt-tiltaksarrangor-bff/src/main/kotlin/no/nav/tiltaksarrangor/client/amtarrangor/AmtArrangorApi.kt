package no.nav.tiltaksarrangor.client.amtarrangor

import no.nav.tiltaksarrangor.client.AMT_ARRANGOR_TOKENX_CLIENT_ID
import no.nav.tiltaksarrangor.client.amtarrangor.dto.OppdaterVeiledereForDeltakerRequest
import no.nav.tiltaksarrangor.consumer.model.AnsattDto
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.client.annotation.ClientRegistrationId
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.service.annotation.DeleteExchange
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange
import org.springframework.web.service.annotation.PostExchange
import java.util.UUID

@HttpExchange
@ClientRegistrationId(AMT_ARRANGOR_TOKENX_CLIENT_ID)
interface AmtArrangorApi {
    @GetExchange("/api/ansatt")
    fun getAnsatt(): ResponseEntity<AnsattDto>

    @PostExchange("/api/ansatt/koordinator/{arrangorId}/{deltakerlisteId}")
    fun leggTilDeltakerlisteForKoordinator(
        @PathVariable arrangorId: UUID,
        @PathVariable deltakerlisteId: UUID,
    ): ResponseEntity<Void>

    @DeleteExchange("/api/ansatt/koordinator/{arrangorId}/{deltakerlisteId}")
    fun fjernDeltakerlisteForKoordinator(
        @PathVariable arrangorId: UUID,
        @PathVariable deltakerlisteId: UUID,
    ): ResponseEntity<Void>

    @PostExchange("/api/ansatt/veiledere/{deltakerId}")
    fun oppdaterVeilederForDeltaker(
        @PathVariable deltakerId: UUID,
        @RequestBody request: OppdaterVeiledereForDeltakerRequest,
    ): ResponseEntity<Void>
}
