package no.nav.amt.deltaker.bff.deltaker.api.model

import no.nav.amt.deltaker.bff.deltaker.api.utils.validerBegrunnelse
import no.nav.amt.deltaker.bff.deltaker.api.utils.validerDeltakerKanEndres
import no.nav.amt.deltaker.bff.deltaker.api.utils.validerForslagEllerBegrunnelse
import no.nav.amt.deltaker.bff.deltaker.api.utils.validerSluttdatoForDeltaker
import no.nav.amt.deltaker.bff.deltaker.model.Deltaker
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import java.time.LocalDate
import java.util.UUID

data class ForlengDeltakelseRequest(
    val sluttdato: LocalDate,
    val begrunnelse: String?,
    override val forslagId: UUID?,
) : EndringsforslagRequest {
    override fun valider(deltaker: Deltaker) {
        require(!nySluttdatoErTidligereEnnForrigeSluttdato(deltaker)) {
            "Ny sluttdato må være etter opprinnelig sluttdato ved forlengelse"
        }
        validerSluttdatoForDeltaker(sluttdato, deltaker.startdato, deltaker)
        require(deltakerDeltarEllerHarSluttet(deltaker)) {
            "Kan ikke forlenge deltakelse for deltaker med status ${deltaker.status.type}"
        }
        require(deltaker.sluttdato != sluttdato) {
            "Ny sluttdato kan ikke være lik som forrige sluttdato"
        }
        validerDeltakerKanEndres(deltaker)
        validerForslagEllerBegrunnelse(forslagId, begrunnelse)
        validerBegrunnelse(begrunnelse)
    }

    private fun nySluttdatoErTidligereEnnForrigeSluttdato(opprinneligDeltaker: Deltaker) =
        opprinneligDeltaker.sluttdato != null && opprinneligDeltaker.sluttdato.isAfter(sluttdato)

    private fun deltakerDeltarEllerHarSluttet(opprinneligDeltaker: Deltaker) =
        opprinneligDeltaker.status.type == DeltakerStatus.Type.DELTAR ||
            opprinneligDeltaker.status.type == DeltakerStatus.Type.HAR_SLUTTET ||
            opprinneligDeltaker.status.type == DeltakerStatus.Type.AVBRUTT ||
            opprinneligDeltaker.status.type == DeltakerStatus.Type.FULLFORT
}
