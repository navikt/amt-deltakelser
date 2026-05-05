package no.nav.amt.deltaker.bff.innbygger.api

import no.nav.amt.deltaker.bff.commonresponse.DeltakelsesinnholdResponse
import no.nav.amt.deltaker.bff.commonresponse.DeltakelsesmengderResponse
import no.nav.amt.deltaker.bff.commonresponse.ImportertFraArenaResponse
import no.nav.amt.deltaker.bff.model.Deltaker
import no.nav.amt.deltaker.bff.model.DeltakerModel
import no.nav.amt.deltaker.bff.veileder.api.response.ForslagResponse
import no.nav.amt.deltaker.bff.veileder.api.response.VedtaksinformasjonResponse
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Vedtak
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavEnhet
import java.time.LocalDate
import java.util.UUID

data class InnbyggerDeltakerResponse(
    val deltakerId: UUID,
    val deltakerliste: GjennomforingInnbyggerResponse,
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
                deltakerliste = GjennomforingInnbyggerResponse.fromModel(deltaker.gjennomforing),
                status = status,
                startdato = startdato,
                sluttdato = sluttdato,
                dagerPerUke = dagerPerUke,
                deltakelsesprosent = deltakelsesprosent,
                bakgrunnsinformasjon = bakgrunnsinformasjon,
                deltakelsesinnhold = deltakelsesinnhold?.let {
                    DeltakelsesinnholdResponse(
                        ledetekst = it.ledetekst,
                        innhold = it.innhold,
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
                importertFraArena = ImportertFraArenaResponse.fromDeltaker(this),
                deltakelsesmengder = DeltakelsesmengderResponse.fromDeltakelsesmengder(deltakelsesmengder),
                erManueltDeltMedArrangor = erManueltDeltMedArrangor,
                prisinformasjon = prisinformasjon,
            )
        }
    }
}

// Denne skal fases ut når når vi alltid kan hente data fra amt-deltaker
fun Deltaker.toInnbyggerDeltakerResponse(
    ansatte: Map<UUID, NavAnsatt>,
    vedtakSistEndretAvEnhet: NavEnhet?,
    forslag: List<Forslag>,
): InnbyggerDeltakerResponse = InnbyggerDeltakerResponse(
    deltakerId = id,
    deltakerliste = GjennomforingInnbyggerResponse(
        deltakerlisteId = deltakerliste.id,
        deltakerlisteNavn = deltakerliste.navn,
        tiltakskode = deltakerliste.tiltak.tiltakskode,
        arrangorNavn = deltakerliste.arrangor.getArrangorNavn(),
        oppstartstype = deltakerliste.oppstart,
        startdato = deltakerliste.startDato,
        sluttdato = deltakerliste.sluttDato,
        // midlertidig løsning inntil vi vet ner om det foreligger rammeavtale eller ikke
        erEnkeltplassUtenRammeavtale = deltakerliste.tiltak.tiltakskode.erEnkeltplass(),
        erEnkeltplass = deltakerliste.tiltak.tiltakskode.erEnkeltplass(),
        oppmoteSted = deltakerliste.oppmoteSted,
        pameldingstype = deltakerliste.pameldingstype ?: GjennomforingPameldingType.TRENGER_GODKJENNING,
    ),
    status = status,
    startdato = startdato,
    sluttdato = sluttdato,
    dagerPerUke = dagerPerUke,
    deltakelsesprosent = deltakelsesprosent,
    bakgrunnsinformasjon = bakgrunnsinformasjon,
    deltakelsesinnhold = DeltakelsesinnholdResponse(
        ledetekst = deltakelsesinnhold?.ledetekst,
        innhold = deltakelsesinnhold?.innhold ?: emptyList(),
    ),
    vedtaksinformasjon = vedtaksinformasjon?.toDto(ansatte, vedtakSistEndretAvEnhet),
    adresseDelesMedArrangor = adresseDelesMedArrangor(),
    forslag = forslag.map {
        ForslagResponse.fromForslag(
            forslag = it,
            arrangornavn = deltakerliste.arrangor.getArrangorNavn(),
            enheter = vedtakSistEndretAvEnhet?.let { enhet -> mapOf(enhet.id to enhet) } ?: emptyMap(),
            ansatte = ansatte,
        )
    },
    importertFraArena = ImportertFraArenaResponse.fromDeltaker(this),
    deltakelsesmengder = DeltakelsesmengderResponse.fromDeltakelsesmengder(deltakelsesmengder),
    erManueltDeltMedArrangor = erManueltDeltMedArrangor,
    prisinformasjon = null,
)

private fun Vedtak.toDto(
    ansatte: Map<UUID, NavAnsatt>,
    vedtakSistEndretEnhet: NavEnhet?,
) = VedtaksinformasjonResponse(
    fattet = fattet,
    fattetAvNav = fattetAvNav,
    opprettet = opprettet,
    opprettetAv = ansatte[opprettetAv]?.navn ?: opprettetAv.toString(),
    sistEndret = sistEndret,
    sistEndretAv = ansatte[sistEndretAv]?.navn ?: sistEndretAv.toString(),
    sistEndretAvEnhet = vedtakSistEndretEnhet?.navn ?: sistEndretAvEnhet.toString(),
)
