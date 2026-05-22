package no.nav.amt.deltaker.bff.veileder.api.request

import no.nav.amt.deltaker.bff.model.Deltaker
import no.nav.amt.deltaker.bff.model.DeltakerModel
import no.nav.amt.deltaker.bff.veileder.api.utils.validerBegrunnelse
import no.nav.amt.deltaker.bff.veileder.api.utils.validerDeltakerKanEndres
import no.nav.amt.deltaker.bff.veileder.api.utils.validerForslagEllerBegrunnelse
import no.nav.amt.deltaker.bff.veileder.api.utils.validerSluttdatoForDeltaker
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import java.time.LocalDate
import java.util.UUID

data class ForlengDeltakelseRequest(
    val sluttdato: LocalDate,
    val begrunnelse: String?,
    override val forslagId: UUID?,
) : EndringMedForslagRequest {
    override fun valider(deltaker: Deltaker) {
        require(!nySluttdatoErTidligereEnnForrigeSluttdato(deltaker.sluttdato)) {
            "Ny sluttdato må være etter opprinnelig sluttdato ved forlengelse"
        }
        validerSluttdatoForDeltaker(sluttdato, deltaker.startdato, deltaker)
        require(deltakerDeltarEllerHarSluttet(deltaker.status)) {
            "Kan ikke forlenge deltakelse for deltaker med status ${deltaker.status.type}"
        }
        require(deltaker.sluttdato != sluttdato) {
            "Ny sluttdato kan ikke være lik som forrige sluttdato"
        }
        validerDeltakerKanEndres(deltaker)
        validerForslagEllerBegrunnelse(forslagId, begrunnelse)
        validerBegrunnelse(begrunnelse)
    }

    override fun valider(deltaker: DeltakerModel) {
        require(!nySluttdatoErTidligereEnnForrigeSluttdato(deltaker.sluttdato)) {
            "Ny sluttdato må være etter opprinnelig sluttdato ved forlengelse"
        }
        validerSluttdatoForDeltaker(sluttdato, deltaker.startdato, deltaker)
        require(deltakerDeltarEllerHarSluttet(deltaker.status)) {
            "Kan ikke forlenge deltakelse for deltaker med status ${deltaker.status.type}"
        }
        require(deltaker.sluttdato != sluttdato) {
            "Ny sluttdato kan ikke være lik som forrige sluttdato"
        }
        validerDeltakerKanEndres(deltaker)
        validerForslagEllerBegrunnelse(forslagId, begrunnelse)
        validerBegrunnelse(begrunnelse)
    }

    private fun nySluttdatoErTidligereEnnForrigeSluttdato(opprinneligDeltakerSluttdato: LocalDate?) =
        opprinneligDeltakerSluttdato != null && opprinneligDeltakerSluttdato.isAfter(sluttdato)

    private fun deltakerDeltarEllerHarSluttet(opprinneligDeltakerStatus: DeltakerStatus) =
        opprinneligDeltakerStatus.type == DeltakerStatus.Type.DELTAR ||
            opprinneligDeltakerStatus.type == DeltakerStatus.Type.HAR_SLUTTET ||
            opprinneligDeltakerStatus.type == DeltakerStatus.Type.AVBRUTT ||
            opprinneligDeltakerStatus.type == DeltakerStatus.Type.FULLFORT
}
