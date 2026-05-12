package no.nav.amt.deltaker.api.response

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.model.Deltakerliste
import no.nav.amt.deltaker.model.Vedtaksinformasjon
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.repository.KodeverkValgRepository
import no.nav.amt.deltaker.service.DeltakerHistorikkService
import no.nav.amt.deltaker.tiltaksarrangor.ArrangorService
import no.nav.amt.deltaker.tiltaksarrangor.forslag.ForslagRepository
import no.nav.amt.deltaker.tiltaksarrangor.vurdering.VurderingRepository
import no.nav.amt.deltaker.veileder.DeltakerLaaseService
import no.nav.amt.internapi.deltaker.response.ArrangorResponse
import no.nav.amt.internapi.deltaker.response.DeltakerResponse
import no.nav.amt.internapi.deltaker.response.DeltakereResponse
import no.nav.amt.internapi.deltaker.response.GjennomforingResponse
import no.nav.amt.internapi.deltaker.response.NavBrukerResponse
import no.nav.amt.internapi.deltaker.response.NavVeilederResponse
import no.nav.amt.internapi.deltaker.response.VedtaksinformasjonResponse
import no.nav.amt.internapi.deltaker.response.VurderingResponse
import no.nav.amt.lib.ktor.clients.distribusjon.AmtDistribusjonClient
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavBruker
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.utils.GenericCache
import org.slf4j.LoggerFactory
import java.util.UUID

class ResponseBuilder(
    private val arrangorService: ArrangorService,
    private val navAnsattService: NavAnsattService,
    private val navEnhetService: NavEnhetService,
    private val amtDistribusjonClient: AmtDistribusjonClient,
    private val deltakerHistorikkService: DeltakerHistorikkService,
    private val forslagRepository: ForslagRepository,
    private val deltakerLaaseService: DeltakerLaaseService,
    private val vurderingRepository: VurderingRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // Delt semafor på tvers av samtidige forespørsler — uten dette ville hver request fått
    // egne 8 permits, og N samtidige requests = N*8 permits, som kunne sulte Hikari-poolen (10).
    private val deltakerBuildSemaphore = Semaphore(MAX_CONCURRENT_DELTAKER_BUILDS)

    companion object {
        // Maks samtidige buildDeltakerResponse-kall på tvers av alle forespørsler — bevisst lavere
        // enn HikariCP poolStørrelse (10) for å la andre endepunkter få DB-connections samtidig.
        private const val MAX_CONCURRENT_DELTAKER_BUILDS = 8

        // Logger advarsel når antall deltakere overstiger denne grensen — gir oss synlighet
        // på problematiske gjennomføringer som risikerer timeout.
        private const val LARGE_LIST_WARNING_THRESHOLD = 200
    }

    suspend fun buildDeltakerResponse(
        deltaker: Deltaker,
        kodeverkValg: Set<UUID>? = null,
    ): DeltakerResponse {
        // hent alle entries som behøver navn på Nav-ansatt eller -enhet
        val endringsforslagForDeltaker = forslagRepository
            .getForDeltaker(deltaker.id)
            .filter { it.status is Forslag.Status.VenterPaSvar }

        val navAnsatte = navAnsattService.hentNavAnsatteForDeltaker(deltaker)
        val navEnheter = navEnhetService.hentNavEnheterForDeltaker(deltaker)
        val sisteVurdering = vurderingRepository
            .getForDeltaker(deltaker.id)
            .maxByOrNull { it.gyldigFra }

        return DeltakerResponse(
            id = deltaker.id,
            navBruker = buildNavBrukerResponseFromNavBruker(
                navBruker = deltaker.navBruker,
                navEnheter = navEnheter,
                navAnsatte = navAnsatte,
            ),
            gjennomforing = buildGjennomforingResponse(deltaker.deltakerliste, kodeverkValg),
            startdato = deltaker.startdato,
            sluttdato = deltaker.sluttdato,
            dagerPerUke = deltaker.dagerPerUke,
            deltakelsesprosent = deltaker.deltakelsesprosent,
            bakgrunnsinformasjon = deltaker.bakgrunnsinformasjon,
            deltakelsesinnhold = deltaker.deltakelsesinnhold,
            status = deltaker.status,
            vedtaksinformasjon = deltaker.vedtaksinformasjon?.let {
                buildVedtaksinformasjonResponse(
                    vedtaksinformasjon = it,
                    navEnheter = navEnheter,
                    navAnsatte = navAnsatte,
                )
            },
            sistEndret = deltaker.sistEndret,
            kilde = deltaker.kilde,
            erManueltDeltMedArrangor = deltaker.erManueltDeltMedArrangor,
            opprettet = deltaker.opprettet,
            historikk = deltakerHistorikkService.getForDeltaker(deltaker.id),
            erLaastForEndringer = deltakerLaaseService.erLaastForEndringer(deltaker),
            endringsforslagFraArrangor = endringsforslagForDeltaker,
            prisinformasjon = deltaker.deltakerliste.prisinformasjon,
            sisteVurdering = sisteVurdering?.let { VurderingResponse.fromVurdering(it) },
        )
    }

    suspend fun buildDeltakereResponse(deltakere: List<Deltaker>): DeltakereResponse = coroutineScope {
        val startTime = System.currentTimeMillis()

        if (deltakere.size >= LARGE_LIST_WARNING_THRESHOLD) {
            log.warn(
                "Bygger respons for {} deltakere — risiko for timeout. " +
                    "Vurder batch-fetching eller paginering. " +
                    "TODO: DeltakerHistorikkService.getForDeltaker gjør 8 separate DB-queries per deltaker, " +
                    "og amtDistribusjonClient.digitalBruker gjør 1 HTTP-kall per deltaker (cachet 15min).",
                deltakere.size,
            )
        }

        // Hoist kodeverkValg-oppslag ut av loopen — alle deltakere på samme gjennomføring deler kodeverkValg.
        // Tabellen er nøklet på deltakerliste_id, så vi henter én gang per unik deltakerliste.
        val kodeverkValgPerDeltakerliste = deltakere
            .map { it.deltakerliste }
            .distinctBy { it.id }
            .filter { it.tiltakstype.tiltakskode.erOpplaeringstiltak() }
            .associate { it.id to KodeverkValgRepository.hentKodeverkValg(it.id) }

        // Parallelliser per-deltaker arbeid med begrenset samtidighet via delt semafor.
        // Hver buildDeltakerResponse gjør ~14 sekvensielle DB/HTTP-kall, så for store
        // gjennomføringer (500+ deltakere) gir parallellisering 6-8x speedup.
        // Kjører på Dispatchers.IO siden Database.query og HTTP-klient er blokkerende —
        // unngår tråd-sult på Ktor sin request-dispatcher.
        val response = DeltakereResponse(
            deltakere
                .map { deltaker ->
                    async(Dispatchers.IO) {
                        deltakerBuildSemaphore.withPermit {
                            buildDeltakerResponse(
                                deltaker = deltaker,
                                kodeverkValg = kodeverkValgPerDeltakerliste[deltaker.deltakerliste.id] ?: emptySet(),
                            )
                        }
                    }
                }.awaitAll(),
        )

        val elapsed = System.currentTimeMillis() - startTime
        if (deltakere.size >= LARGE_LIST_WARNING_THRESHOLD || elapsed > 5_000) {
            log.info("Bygde respons for {} deltakere på {}ms", deltakere.size, elapsed)
        }

        response
    }

    internal fun buildGjennomforingResponse(
        deltakerliste: Deltakerliste,
        kodeverkValg: Set<UUID>? = null,
    ) = GjennomforingResponse(
        id = deltakerliste.id,
        tiltakstype = deltakerliste.tiltakstype,
        navn = deltakerliste.navn,
        status = deltakerliste.status,
        startDato = deltakerliste.startDato,
        sluttDato = deltakerliste.sluttDato,
        antallPlasser = deltakerliste.antallPlasser,
        oppstart = deltakerliste.oppstart,
        apentForPamelding = deltakerliste.apentForPamelding,
        oppmoteSted = deltakerliste.oppmoteSted,
        arrangor = deltakerliste.arrangor?.let {
            ArrangorResponse(
                navn = arrangorService.getArrangorNavn(deltakerliste.arrangor),
                deltakerliste.arrangor.organisasjonsnummer,
            )
        },
        pameldingstype = deltakerliste.pameldingstype,
        type = deltakerliste.gjennomforingstype,
        kodeverkValg = kodeverkValg ?: if (deltakerliste.tiltakstype.tiltakskode.erOpplaeringstiltak()) {
            KodeverkValgRepository.hentKodeverkValg(deltakerliste.id)
        } else {
            emptySet()
        },
    )

    internal fun buildVedtaksinformasjonResponse(
        vedtaksinformasjon: Vedtaksinformasjon,
        navEnheter: GenericCache<NavEnhet>,
        navAnsatte: GenericCache<NavAnsatt>,
    ) = VedtaksinformasjonResponse(
        fattet = vedtaksinformasjon.fattet,
        fattetAvNav = vedtaksinformasjon.fattetAvNav,
        opprettet = vedtaksinformasjon.opprettet,
        opprettetAv = navAnsatte.getOrThrow(vedtaksinformasjon.opprettetAv).navn,
        opprettetAvEnhet = navEnheter.getOrThrow(vedtaksinformasjon.opprettetAvEnhet).navn,
        sistEndret = vedtaksinformasjon.sistEndret,
        sistEndretAv = navAnsatte.getOrThrow(vedtaksinformasjon.sistEndretAv).navn,
        sistEndretAvEnhet = navEnheter.getOrThrow(vedtaksinformasjon.sistEndretAvEnhet).navn,
    )

    internal suspend fun buildNavBrukerResponseFromNavBruker(
        navBruker: NavBruker,
        navAnsatte: GenericCache<NavAnsatt>,
        navEnheter: GenericCache<NavEnhet>,
    ) = NavBrukerResponse(
        personident = navBruker.personident,
        fornavn = navBruker.fornavn,
        mellomnavn = navBruker.mellomnavn,
        etternavn = navBruker.etternavn,
        telefon = navBruker.telefon,
        epost = navBruker.epost,
        erSkjermet = navBruker.erSkjermet,
        adresse = navBruker.adresse,
        adressebeskyttelse = navBruker.adressebeskyttelse,
        oppfolgingsperioder = navBruker.oppfolgingsperioder,
        innsatsgruppe = navBruker.innsatsgruppe,
        erDigital = amtDistribusjonClient.digitalBruker(navBruker.personident),
        navVeileder = navBruker.navVeilederId
            ?.let { navAnsatte.getOrThrow(it) }
            ?.let { veileder ->
                NavVeilederResponse(
                    navn = veileder.navn,
                    epost = veileder.epost,
                    telefonnummer = veileder.telefon,
                )
            },
        navEnhet = navBruker.navEnhetId?.let { navEnheter.getOrThrow(it).navn },
    )
}
