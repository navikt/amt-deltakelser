package no.nav.amt.deltaker.bff.innbygger.api

import no.nav.amt.deltaker.bff.commonresponse.DeltakelsesinnholdResponse
import no.nav.amt.deltaker.bff.commonresponse.DeltakerlisteResponse
import no.nav.amt.deltaker.bff.commonresponse.ImportertFraArenaResponse
import no.nav.amt.deltaker.bff.commonresponse.toDeltakerlisteResponse
import no.nav.amt.deltaker.bff.model.DeltakerModel
import no.nav.amt.deltaker.bff.veileder.api.response.ForslagResponse
import no.nav.amt.deltaker.bff.veileder.api.response.VedtaksinformasjonResponse
import no.nav.amt.internapi.deltaker.response.DeltakelsesmengderResponse
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import java.time.LocalDate
import java.util.UUID

data class InnbyggerDeltakerResponse(
    val deltakerId: UUID,
    val deltakerliste: DeltakerlisteResponse,
    val status: DeltakerStatus,
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
    val deltakelsesmengder: DeltakelsesmengderResponse,
    val erManueltDeltMedArrangor: Boolean,
    val prisinformasjon: String?,
) {
    companion object {
        fun fromModel(deltaker: DeltakerModel) = with(deltaker) {
            InnbyggerDeltakerResponse(
                deltakerId = id,
                deltakerliste = deltaker.gjennomforing.toDeltakerlisteResponse(),
                status = status,
                startdato = startdato,
                sluttdato = sluttdato,
                dagerPerUke = dagerPerUke,
                deltakelsesprosent = deltakelsesprosent,
                bakgrunnsinformasjon = bakgrunnsinformasjon,
                deltakelsesinnhold = deltakelsesinnhold?.let {
                    DeltakelsesinnholdResponse(
                        ledetekst = it.ledetekst,
                        innhold = it.innhold.map { innhold -> DeltakelsesinnholdResponse.InnholdResponse.fromInnhold(innhold) },
                    )
                },
                vedtaksinformasjon = vedtaksinformasjon?.let {
                    VedtaksinformasjonResponse.fromVedtak(it)
                },
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
                deltakelsesmengder = deltakelsesmengder ?: DeltakelsesmengderResponse(),
                erManueltDeltMedArrangor = erManueltDeltMedArrangor,
                prisinformasjon = prisinformasjon,
            )
        }
    }
}
