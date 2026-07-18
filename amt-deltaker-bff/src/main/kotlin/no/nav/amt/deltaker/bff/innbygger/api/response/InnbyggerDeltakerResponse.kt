package no.nav.amt.deltaker.bff.innbygger.api.response

import no.nav.amt.deltaker.bff.commonresponse.DeltakelsesinnholdResponse
import no.nav.amt.deltaker.bff.commonresponse.DeltakerlisteResponse
import no.nav.amt.deltaker.bff.commonresponse.ImportertFraArenaResponse
import no.nav.amt.deltaker.bff.model.DeltakerModel
import no.nav.amt.deltaker.bff.veileder.api.response.DeltakerStatusResponse
import no.nav.amt.deltaker.bff.veileder.api.response.ForslagResponse
import no.nav.amt.deltaker.bff.veileder.api.response.VedtaksinformasjonResponse
import no.nav.amt.deltaker.bff.veileder.api.response.toDeltakerStatusResponse
import java.time.LocalDate
import java.util.UUID
import no.nav.amt.deltaker.bff.veileder.api.response.DeltakelsesmengderResponse as DeltakelsesmengderVeilederResponse

data class InnbyggerDeltakerResponse(
    val deltakerId: UUID,
    val deltakerliste: DeltakerlisteResponse,
    val status: DeltakerStatusResponse,
    val startdato: LocalDate?,
    val sluttdato: LocalDate?,
    val dagerPerUke: Float?,
    val deltakelsesprosent: Float?,
    val bakgrunnsinformasjon: String?,
    val deltakelsesinnhold: DeltakelsesinnholdResponse?,
    val vedtaksinformasjon: VedtaksinformasjonResponse?,
    val adresseDelesMedArrangor: Boolean,
    val forslag: List<ForslagResponse>,
    val importertFraArena: ImportertFraArenaResponse?,
    val deltakelsesmengder: DeltakelsesmengderVeilederResponse,
    val erManueltDeltMedArrangor: Boolean,
    val prisinformasjon: String?,
) {
    companion object {
        fun fromModel(deltaker: DeltakerModel) = with(deltaker) {
            InnbyggerDeltakerResponse(
                deltakerId = id,
                deltakerliste = deltaker.gjennomforing.let(::DeltakerlisteResponse),
                status = status.toDeltakerStatusResponse(),
                startdato = startdato,
                sluttdato = sluttdato,
                dagerPerUke = dagerPerUke,
                deltakelsesprosent = deltakelsesprosent,
                bakgrunnsinformasjon = bakgrunnsinformasjon,
                deltakelsesinnhold = deltakelsesinnhold?.let(::DeltakelsesinnholdResponse),
                vedtaksinformasjon = vedtaksinformasjon?.let(::VedtaksinformasjonResponse),
                adresseDelesMedArrangor = adresseDelesMedArrangor,
                forslag = endringsforslagFraArrangor.map {
                    ForslagResponse.fromForslag(
                        forslag = it,
                        arrangornavn = gjennomforing.arrangor?.navn ?: "Ukjent arrangør",
                        enheter = emptyMap(),
                        ansatte = emptyMap(),
                    )
                },
                importertFraArena = importertFraArena?.let { ImportertFraArenaResponse(importertFraArena.deltakerVedImport.innsoktDato) },
                // Frontend støtter ikke at DeltakelsesmengderResponse er nullable
                deltakelsesmengder = deltakelsesmengder?.let(::DeltakelsesmengderVeilederResponse) ?: DeltakelsesmengderVeilederResponse(),
                erManueltDeltMedArrangor = erManueltDeltMedArrangor,
                prisinformasjon = prisinformasjon,
            )
        }
    }
}
