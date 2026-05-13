package no.nav.amt.deltaker.bff.veileder.api.response

import no.nav.amt.deltaker.bff.commonresponse.DeltakelsesinnholdResponse
import no.nav.amt.deltaker.bff.commonresponse.DeltakelsesmengdeResponse
import no.nav.amt.deltaker.bff.commonresponse.DeltakelsesmengderResponse
import no.nav.amt.deltaker.bff.commonresponse.ImportertFraArenaResponse
import no.nav.amt.deltaker.bff.model.Deltaker
import no.nav.amt.deltaker.bff.model.DeltakerModel
import no.nav.amt.internapi.deltaker.getInnholdselementer
import no.nav.amt.lib.ktor.clients.kodeverk.KodeverkResponse
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavEnhet
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
        // Brukes kun i tilfelle henting/lagring i lokal database
        // Vil fases sakte ut når oppgaver blir delegert til amt-deltaker
        fun fromDeltaker(
            deltaker: Deltaker,
            digitalBruker: Boolean,
            forslag: List<Forslag>,
            vedtakSistEndretAvEnhet: NavEnhet?,
            ansatte: Map<UUID, NavAnsatt>,
        ) = with(deltaker) {
            DeltakerResponse(
                deltakerId = id,
                fornavn = navBruker.fornavn,
                mellomnavn = navBruker.mellomnavn,
                etternavn = navBruker.etternavn,
                deltakerliste = DeltakerlisteResponse(
                    deltakerlisteId = deltakerliste.id,
                    deltakerlisteNavn = deltakerliste.navn,
                    tiltakskode = deltakerliste.tiltak.tiltakskode,
                    arrangorNavn = deltakerliste.arrangor.getArrangorNavn(),
                    arrangor = DeltakerlisteResponse.ArrangorResponse(
                        navn = deltakerliste.arrangor.getArrangorNavn(),
                        organisasjonsnummer = deltakerliste.arrangor.arrangor.organisasjonsnummer,
                    ),
                    oppstartstype = deltakerliste.oppstart,
                    startdato = deltakerliste.startDato,
                    sluttdato = deltakerliste.sluttDato,
                    status = deltakerliste.status,
                    tilgjengeligInnhold = TilgjengeligInnholdResponse.fromDeltakerRegistreringInnhold(
                        innhold = deltakerliste.tiltak.innhold,
                        tiltakstype = deltakerliste.tiltak.tiltakskode,
                    ),
                    // midlertidig løsning inntil vi vet ner om det foreligger rammeavtale eller ikke
                    erEnkeltplassUtenRammeavtale = deltakerliste.tiltak.tiltakskode.erEnkeltplass(),
                    erEnkeltplass = deltakerliste.tiltak.tiltakskode.erEnkeltplass(),
                    oppmoteSted = deltakerliste.oppmoteSted,
                    pameldingstype = deltakerliste.pameldingstype,
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
                            innholdselementer = deltakerliste.tiltak.innhold?.innholdselementer,
                            tiltakstype = deltakerliste.tiltak.tiltakskode,
                        ),
                    )
                },
                vedtaksinformasjon = vedtaksinformasjon?.let {
                    VedtaksinformasjonResponse.fromVedtak(
                        vedtak = it,
                        ansatte = ansatte,
                        vedtakSistEndretEnhet = vedtakSistEndretAvEnhet,
                    )
                },
                adresseDelesMedArrangor = adresseDelesMedArrangor(),
                kanEndres = kanEndres,
                digitalBruker = digitalBruker,
                maxVarighet = maxVarighet?.toMillis(),
                softMaxVarighet = softMaxVarighet?.toMillis(),
                forslag = forslag.map {
                    ForslagResponse.fromForslag(
                        forslag = it,
                        arrangornavn = deltakerliste.arrangor.getArrangorNavn(),
                        enheter = vedtakSistEndretAvEnhet?.let { enhet -> mapOf(enhet.id to enhet) } ?: emptyMap(),
                        ansatte = ansatte,
                    )
                },
                importertFraArena = ImportertFraArenaResponse.fromDeltaker(this),
                harAdresse = navBruker.adresse != null,
                deltakelsesmengder = DeltakelsesmengderResponse(
                    nesteDeltakelsesmengde = deltakelsesmengder.nesteGjeldende?.let { DeltakelsesmengdeResponse.fromDeltakelsesmengde(it) },
                    sisteDeltakelsesmengde = deltakelsesmengder.lastOrNull()?.let { DeltakelsesmengdeResponse.fromDeltakelsesmengde(it) },
                ),
                erUnderOppfolging = navBruker.harAktivOppfolgingsperiode,
                erManueltDeltMedArrangor = erManueltDeltMedArrangor,
                prisinformasjon = null, // Denne mapperen skal uansett ikke brukes for enkeltplasser
            )
        }

        fun fromDeltakerModel(
            deltaker: DeltakerModel,
            kodeverkResponse: KodeverkResponse? = null,
        ) = with(deltaker) {
            DeltakerResponse(
                deltakerId = id,
                fornavn = navBruker.fornavn,
                mellomnavn = navBruker.mellomnavn,
                etternavn = navBruker.etternavn,
                deltakerliste = DeltakerlisteResponse.fromModel(
                    gjennomforingModel = gjennomforing,
                    kodeverk = kodeverkResponse,
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
                importertFraArena = ImportertFraArenaResponse.fromDeltaker(this),
                harAdresse = navBruker.adresse != null,
                // Her bør det gjøres noen forenklinger
                // Kan dette utledes i amt-deltaker?
                deltakelsesmengder = DeltakelsesmengderResponse(
                    nesteDeltakelsesmengde = deltakelsesmengder.nesteGjeldende?.let {
                        DeltakelsesmengdeResponse
                            .fromDeltakelsesmengde(
                                it,
                            )
                    },
                    sisteDeltakelsesmengde = deltakelsesmengder.lastOrNull()?.let {
                        DeltakelsesmengdeResponse
                            .fromDeltakelsesmengde(
                                it,
                            )
                    },
                ),
                erUnderOppfolging = navBruker.harAktivOppfolgingsperiode,
                erManueltDeltMedArrangor = erManueltDeltMedArrangor,
                prisinformasjon = prisinformasjon,
            )
        }
    }
}
