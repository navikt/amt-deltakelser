package no.nav.amt.deltaker.bff.veileder.api.response

import no.nav.amt.deltaker.bff.commonresponse.DeltakelsesinnholdResponse
import no.nav.amt.deltaker.bff.commonresponse.DeltakerlisteResponse
import no.nav.amt.deltaker.bff.commonresponse.ImportertFraArenaResponse
import no.nav.amt.deltaker.bff.model.DeltakerModel
import no.nav.amt.internapi.deltaker.getInnholdselementer
import no.nav.amt.internapi.deltaker.response.DeltakelsesmengderResponse
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import java.time.LocalDate
import java.util.UUID

data class DeltakerResponse(
    val deltakerId: UUID,
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
    val deltakerliste: DeltakerlisteResponse,
    val status: DeltakerStatus,
    val startdato: LocalDate?,
    val sluttdato: LocalDate?,
    val dagerPerUke: Float?,
    val deltakelsesprosent: Float?,
    val bakgrunnsinformasjon: String?,
    val deltakelsesinnhold: DeltakelsesinnholdResponse?,
    val adresseDelesMedArrangor: Boolean,
    val kanEndres: Boolean,
    val digitalBruker: Boolean,
    val maxVarighet: Long?,
    val softMaxVarighet: Long?,
    val forslag: List<ForslagResponse>,
    val vedtaksinformasjon: VedtaksinformasjonResponse?,
    val importertFraArena: ImportertFraArenaResponse?,
    val harAdresse: Boolean,
    val deltakelsesmengder: DeltakelsesmengderResponse,
    val erUnderOppfolging: Boolean,
    val erManueltDeltMedArrangor: Boolean,
    val prisinformasjon: String?,
) {
    companion object {
        fun fromDeltakerModel(deltaker: DeltakerModel) = with(deltaker) {
            DeltakerResponse(
                deltakerId = id,
                fornavn = navBruker.fornavn,
                mellomnavn = navBruker.mellomnavn,
                etternavn = navBruker.etternavn,
                deltakerliste = DeltakerlisteResponse.fromModel(
                    gjennomforingModel = gjennomforing,
                ),
                status = status,
                startdato = startdato,
                sluttdato = sluttdato,
                dagerPerUke = dagerPerUke,
                deltakelsesprosent = deltakelsesprosent,
                bakgrunnsinformasjon = bakgrunnsinformasjon,
                deltakelsesinnhold = deltakelsesinnhold?.let {
                    DeltakelsesinnholdResponse.fromDeltakelsesinnhold(
                        deltakelsesinnhold = it,
                        tiltaksInnhold = getInnholdselementer(
                            innholdselementer = gjennomforing.tiltak.innhold
                                ?.innholdselementer,
                            tiltakstype = gjennomforing.tiltak.tiltakskode,
                        ),
                    )
                },
                vedtaksinformasjon = vedtaksinformasjon?.let {
                    VedtaksinformasjonResponse.fromVedtak(it)
                },
                adresseDelesMedArrangor = adresseDelesMedArrangor,
                kanEndres = !erLaastForEndringer,
                digitalBruker = navBruker.erDigital,
                maxVarighet = maxVarighet?.toMillis(),
                softMaxVarighet = softMaxVarighet?.toMillis(),
                forslag = endringsforslagFraArrangor.map {
                    ForslagResponse.fromForslag(
                        forslag = it,
                        arrangornavn = gjennomforing.arrangor?.navn ?: "Ukjent arrangør",
                        enheter = emptyMap(),
                        ansatte = emptyMap(),
                    )
                },
                importertFraArena = importertFraArena?.let { ImportertFraArenaResponse(importertFraArena.deltakerVedImport.innsoktDato) },
                harAdresse = navBruker.adresse != null,
                // Her bør det gjøres noen forenklinger
                // Kan dette utledes i amt-deltaker?
                deltakelsesmengder = deltakelsesmengder ?: DeltakelsesmengderResponse(),
                erUnderOppfolging = navBruker.harAktivOppfolgingsperiode,
                erManueltDeltMedArrangor = erManueltDeltMedArrangor,
                prisinformasjon = prisinformasjon,
            )
        }
    }
}
