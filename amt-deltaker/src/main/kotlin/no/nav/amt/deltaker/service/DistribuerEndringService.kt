package no.nav.amt.deltaker.service

import no.nav.amt.deltaker.innbygger.DistribuerEndringProducer
import no.nav.amt.deltaker.innbygger.toHendelseDeltaker
import no.nav.amt.deltaker.innbygger.toUtkastDto
import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.repository.OpplaringKategoriseringRepoAdapter
import no.nav.amt.deltaker.repository.PrisinfoRepoAdapter
import no.nav.amt.deltaker.tiltaksarrangor.ArrangorService
import no.nav.amt.deltaker.tiltaksarrangor.vurdering.VurderingService
import no.nav.amt.internapi.hendelse.Hendelse
import no.nav.amt.internapi.hendelse.HendelseAnsvarlig
import no.nav.amt.internapi.hendelse.HendelseType
import no.nav.amt.internapi.hendelse.UtkastDto
import no.nav.amt.internapi.hendelse.toHendelseEndring
import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.UUID

class DistribuerEndringService(
    private val hendelseProducer: DistribuerEndringProducer,
    private val navAnsattRepository: NavAnsattRepository,
    private val navAnsattService: NavAnsattService,
    private val navEnhetRepository: NavEnhetRepository,
    private val navEnhetService: NavEnhetService,
    private val arrangorService: ArrangorService,
    private val deltakerHistorikkService: DeltakerHistorikkService,
    private val vurderingService: VurderingService,
    private val unleashToggle: CommonUnleashToggle,
) {
    val log: Logger = LoggerFactory.getLogger(javaClass)

    suspend fun produserHendelseFraTiltaksansvarlig(
        deltaker: Deltaker,
        endring: EndringFraTiltakskoordinator,
    ) {
        val navAnsatt = navAnsattService.hentEllerOpprettNavAnsatt(endring.endretAv)
        val navEnhet = navEnhetService.hentEllerOpprettNavEnhet(endring.endretAvEnhet)

        produserHendelseFraTiltaksansvarlig(
            deltaker = deltaker,
            navAnsatt = navAnsatt,
            navEnhet = navEnhet,
            endringsType = endring.endring,
        )
    }

    fun produserHendelseFraTiltaksansvarlig(
        deltaker: Deltaker,
        navAnsatt: NavAnsatt,
        navEnhet: NavEnhet,
        endringsType: EndringFraTiltakskoordinator.Endring,
    ) {
        val hendelseType = when (endringsType) {
            EndringFraTiltakskoordinator.SettPaaVenteliste -> HendelseType.SettPaaVenteliste

            EndringFraTiltakskoordinator.TildelPlass -> HendelseType.TildelPlass

            is EndringFraTiltakskoordinator.Avslag -> HendelseType.Avslag(
                aarsak = endringsType.aarsak,
                begrunnelseFraNav = endringsType.begrunnelse,
                vurderingFraArrangor = vurderingService.getSisteForDeltaker(deltaker.id)?.let {
                    HendelseType.Avslag.Vurdering(
                        vurderingstype = it.vurderingstype,
                        begrunnelse = it.begrunnelse,
                    )
                },
            )

            EndringFraTiltakskoordinator.DelMedArrangor -> return
        }

        hendelseProducer.produce(
            hendelse = nyHendelseFraKoordinator(
                deltaker = deltaker,
                navAnsatt = navAnsatt,
                navEnhet = navEnhet,
                endring = hendelseType,
            ),
        )
    }

    fun hendelseForDeltakerEndring(
        deltakerEndring: DeltakerEndring,
        deltaker: Deltaker,
        navAnsatt: NavAnsatt,
        navEnhet: NavEnhet,
    ) {
        val endring: HendelseType = if (deltakerEndring.endring is DeltakerEndring.Endring.ReaktiverDeltakelse) {
            deltakerEndring.toHendelseEndring(deltaker.toUtkastDto())
        } else {
            deltakerEndring.toHendelseEndring()
        }

        hendelseProducer.produce(
            nyHendelseFraNavAnsatt(
                deltaker = deltaker,
                navAnsatt = navAnsatt,
                navEnhet = navEnhet,
                endring = endring,
            ),
        )
    }

    fun hendelseForEndringFraArrangor(
        endringFraArrangor: EndringFraArrangor,
        deltaker: Deltaker,
    ) {
        val navEnhet = getNavEnhet(deltaker)
        val endring = endringFraArrangor.toHendelseEndring()

        hendelseProducer.produce(
            nyHendelseForEndringFraArrangor(
                deltaker = deltaker,
                navEnhet = navEnhet,
                endring = endring,
            ),
        )
    }

    private fun getNavEnhet(deltaker: Deltaker): NavEnhet {
        val navEnhetId: UUID? = deltaker.navBruker.navEnhetId

        return when {
            deltaker.vedtaksinformasjon != null ->
                navEnhetRepository.getOrThrow(deltaker.vedtaksinformasjon.sistEndretAvEnhet)

            navEnhetId != null -> {
                log.info("Deltaker mangler vedtaksinformasjon, bruker oppfølgingsenhet som avsender")
                navEnhetRepository.getOrThrow(navEnhetId)
            }

            else ->
                throw IllegalStateException(
                    "Kan ikke produsere hendelse for endring fra arrangør for deltaker uten vedtak og uten oppfølgingsenhet, id ${deltaker.id}",
                )
        }
    }

    fun hendelseForUtkastGodkjentAvInnbygger(deltaker: Deltaker) {
        val vedtak = deltaker.vedtaksinformasjon ?: throw IllegalStateException(
            "Kan ikke produsere hendelse for utkast godkjent av innbygger for deltaker ${deltaker.id} uten vedtak",
        )

        val navAnsatt = navAnsattRepository.getOrThrow(vedtak.sistEndretAv)
        val navEnhet = navEnhetRepository.getOrThrow(vedtak.sistEndretAvEnhet)

        produceHendelseForUtkast(
            deltaker = deltaker,
            navAnsatt = navAnsatt,
            enhet = navEnhet,
        ) { utkastDto -> HendelseType.InnbyggerGodkjennUtkast(utkastDto) }
    }

    fun produceHendelse(
        deltaker: Deltaker,
        navAnsatt: NavAnsatt,
        enhet: NavEnhet,
        endring: HendelseType,
    ) {
        hendelseProducer.produce(
            nyHendelseFraNavAnsatt(
                deltaker = deltaker,
                navAnsatt = navAnsatt,
                navEnhet = enhet,
                endring = endring,
            ),
        )
    }

    /**
     * Produserer tilbakekall av prisendring uten å hente inn full historikk for deltaker.
     */
    fun produserHendelseForTilbakekallPrisendring(
        deltaker: Deltaker,
        navAnsatt: NavAnsatt,
        navEnhet: NavEnhet,
        prisinformasjonId: UUID,
    ) {
        hendelseProducer.produce(
            nyMinimalHendelseFraNavAnsatt(
                deltaker = deltaker,
                navAnsatt = navAnsatt,
                navEnhet = navEnhet,
                endring = HendelseType.EnkeltplassTilbakekallPrisendring(prisinformasjonId),
            ),
        )
    }

    fun produceHendelseForUtkast(
        deltaker: Deltaker,
        navAnsatt: NavAnsatt,
        enhet: NavEnhet,
        block: (it: UtkastDto) -> HendelseType,
    ) {
        val endring = block(deltaker.toUtkastDto())
        hendelseProducer.produce(
            nyHendelseFraNavAnsatt(
                deltaker = deltaker,
                navAnsatt = navAnsatt,
                navEnhet = enhet,
                endring = endring,
            ),
        )
    }

    fun hendelseFraSystem(
        deltaker: Deltaker,
        block: (it: UtkastDto) -> HendelseType.HendelseSystemKanOpprette,
    ) {
        val endring = block(deltaker.toUtkastDto())
        hendelseProducer.produce(
            nyHendelseFraSystem(
                deltaker = deltaker,
                endring = endring,
            ),
        )
    }

    private fun nyHendelseFraNavAnsatt(
        deltaker: Deltaker,
        navAnsatt: NavAnsatt,
        navEnhet: NavEnhet,
        endring: HendelseType,
    ): Hendelse {
        val ansvarlig = HendelseAnsvarlig.NavVeileder(
            id = navAnsatt.id,
            navIdent = navAnsatt.navIdent,
            navn = navAnsatt.navn,
            enhet = HendelseAnsvarlig.NavVeileder.Enhet(
                id = navEnhet.id,
                enhetsnummer = navEnhet.enhetsnummer,
            ),
        )

        return nyHendelse(
            deltaker = deltaker,
            ansvarlig = ansvarlig,
            endring = endring,
        )
    }

    private fun nyMinimalHendelseFraNavAnsatt(
        deltaker: Deltaker,
        navAnsatt: NavAnsatt,
        navEnhet: NavEnhet,
        endring: HendelseType,
    ) = Hendelse(
        id = UUID.randomUUID(),
        opprettet = LocalDateTime.now(),
        deltaker = deltaker.toHendelseDeltaker(
            overordnetArrangor = null,
            forsteVedtakFattet = null,
            opplaringKategoriseringValg = null,
            prisinformasjon = null,
        ),
        ansvarlig = HendelseAnsvarlig.NavVeileder(
            id = navAnsatt.id,
            navIdent = navAnsatt.navIdent,
            navn = navAnsatt.navn,
            enhet = HendelseAnsvarlig.NavVeileder.Enhet(
                id = navEnhet.id,
                enhetsnummer = navEnhet.enhetsnummer,
            ),
        ),
        payload = endring,
    )

    private fun nyHendelseFraKoordinator(
        deltaker: Deltaker,
        navAnsatt: NavAnsatt,
        navEnhet: NavEnhet,
        endring: HendelseType,
    ): Hendelse {
        val ansvarlig = HendelseAnsvarlig.NavTiltakskoordinator(
            id = navAnsatt.id,
            navIdent = navAnsatt.navIdent,
            navn = navAnsatt.navn,
            enhet = HendelseAnsvarlig.NavTiltakskoordinator.Enhet(
                navn = navEnhet.navn,
                id = navEnhet.id,
                enhetsnummer = navEnhet.enhetsnummer,
            ),
        )

        return nyHendelse(
            deltaker = deltaker,
            ansvarlig = ansvarlig,
            endring = endring,
        )
    }

    private fun nyHendelseForEndringFraArrangor(
        deltaker: Deltaker,
        navEnhet: NavEnhet,
        endring: HendelseType,
    ): Hendelse {
        val ansvarlig = HendelseAnsvarlig.Arrangor(
            enhet = HendelseAnsvarlig.Arrangor.Enhet(
                id = navEnhet.id,
                enhetsnummer = navEnhet.enhetsnummer,
            ),
        )

        return nyHendelse(
            deltaker = deltaker,
            ansvarlig = ansvarlig,
            endring = endring,
        )
    }

    private fun nyHendelseFraSystem(
        deltaker: Deltaker,
        endring: HendelseType.HendelseSystemKanOpprette,
    ): Hendelse = nyHendelse(
        deltaker = deltaker,
        ansvarlig = HendelseAnsvarlig.System,
        endring = endring,
    )

    fun hendelseForSistBesokt(
        deltaker: Deltaker,
        sistBesokt: ZonedDateTime,
    ) {
        // hvis ikke Komet er master for tiltakskode
        if (!unleashToggle.erKometMasterForTiltakstype(deltaker.deltakerliste.tiltakstype.tiltakskode)) return

        val ansvarlig = HendelseAnsvarlig.Deltaker(
            id = deltaker.id,
            navn = deltaker.navBruker.fulltNavn,
        )

        val hendelse = nyHendelse(
            deltaker = deltaker,
            ansvarlig = ansvarlig,
            endring = HendelseType.DeltakerSistBesokt(sistBesokt),
        )

        hendelseProducer.produce(
            hendelse = hendelse,
            suppressOutsideTxWarning = true, // OK at denne kalles utenfor transaksjon
        )
    }

    private fun nyHendelse(
        deltaker: Deltaker,
        ansvarlig: HendelseAnsvarlig,
        endring: HendelseType,
    ): Hendelse {
        val overordnetArrangor = deltaker.deltakerliste.arrangor!!
            .overordnetArrangorId
            ?.let { arrangorService.hentArrangor(it) }

        val forsteVedtakFattet = deltakerHistorikkService.getForsteVedtakFattet(deltaker.id)

        // for endringsvedtak er dette overflødig, men kalltreet er foreløpig for dypt til å skru dette av/på
        // bør refaktureres
        val (prisinformasjon, opplaringKategoriseringValg) = if (deltaker.deltakerliste.erNyForskriftOpplaring) {
            Pair(
                PrisinfoRepoAdapter.hentPrisinfo(deltaker.deltakerliste.id),
                OpplaringKategoriseringRepoAdapter.hentOpplaringKategoriseringValg(deltaker.deltakerliste.id),
            )
        } else {
            Pair(null, null)
        }

        return Hendelse(
            id = UUID.randomUUID(),
            opprettet = LocalDateTime.now(),
            deltaker = deltaker.toHendelseDeltaker(
                overordnetArrangor = overordnetArrangor,
                forsteVedtakFattet = forsteVedtakFattet,
                opplaringKategoriseringValg = opplaringKategoriseringValg,
                prisinformasjon = prisinformasjon,
            ),
            ansvarlig = ansvarlig,
            payload = endring,
        )
    }
}
