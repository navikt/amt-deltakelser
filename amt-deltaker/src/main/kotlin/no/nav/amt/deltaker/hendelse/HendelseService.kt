package no.nav.amt.deltaker.hendelse

import no.nav.amt.deltaker.arrangor.ArrangorService
import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltaker.vurdering.VurderingService
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.hendelse.Hendelse
import no.nav.amt.lib.models.hendelse.HendelseAnsvarlig
import no.nav.amt.lib.models.hendelse.HendelseType
import no.nav.amt.lib.models.hendelse.UtkastDto
import no.nav.amt.lib.models.hendelse.toHendelseEndring
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.UUID

class HendelseService(
    private val hendelseProducer: HendelseProducer,
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

    suspend fun produserHendelseFraTiltaksansvarlig(deltaker: Deltaker, endring: EndringFraTiltakskoordinator) {
        val navAnsatt = navAnsattService.hentEllerOpprettNavAnsatt(endring.endretAv)
        val navEnhet = navEnhetService.hentEllerOpprettNavEnhet(endring.endretAvEnhet)
        produserHendelseFraTiltaksansvarlig(deltaker, navAnsatt, navEnhet, endring.endring)
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
                    HendelseType.Avslag.Vurdering(it.vurderingstype, it.begrunnelse)
                },
            )

            EndringFraTiltakskoordinator.DelMedArrangor -> return
        }

        hendelseProducer.produce(nyHendelseFraKoordinator(deltaker, navAnsatt, navEnhet, hendelseType))
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

        hendelseProducer.produce(nyHendelseFraNavAnsatt(deltaker, navAnsatt, navEnhet, endring))
    }

    fun hendelseForEndringFraArrangor(endringFraArrangor: EndringFraArrangor, deltaker: Deltaker) {
        val navEnhet = getNavEnhet(deltaker)
        val endring = endringFraArrangor.toHendelseEndring()

        hendelseProducer.produce(nyHendelseForEndringFraArrangor(deltaker, navEnhet, endring))
    }

    private fun getNavEnhet(deltaker: Deltaker): NavEnhet {
        val navEnhetId: UUID? = deltaker.navBruker.navEnhetId

        return when {
            deltaker.vedtaksinformasjon != null -> {
                navEnhetRepository.getOrThrow(deltaker.vedtaksinformasjon.sistEndretAvEnhet)
            }

            navEnhetId != null -> {
                log.info("Deltaker mangler vedtaksinformasjon, bruker oppfølgingsenhet som avsender")
                navEnhetRepository.getOrThrow(navEnhetId)
            }

            else -> {
                throw IllegalStateException(
                    "Kan ikke produsere hendelse for endring fra arrangør for deltaker uten vedtak og uten oppfølgingsenhet, id ${deltaker.id}",
                )
            }
        }
    }

    fun hendelseForUtkastGodkjentAvInnbygger(deltaker: Deltaker) {
        val vedtak = deltaker.vedtaksinformasjon ?: throw IllegalStateException(
            "Kan ikke produsere hendelse for utkast godkjent av innbygger for deltaker ${deltaker.id} uten vedtak",
        )

        val navAnsatt = navAnsattRepository.getOrThrow(vedtak.sistEndretAv)
        val navEnhet = navEnhetRepository.getOrThrow(vedtak.sistEndretAvEnhet)

        produceHendelseForUtkast(deltaker, navAnsatt, navEnhet) { utkastDto ->
            HendelseType.InnbyggerGodkjennUtkast(utkastDto)
        }
    }

    fun produceHendelseForUtkast(
        deltaker: Deltaker,
        navAnsatt: NavAnsatt,
        enhet: NavEnhet,
        block: (it: UtkastDto) -> HendelseType,
    ) {
        val endring = block(deltaker.toUtkastDto())
        hendelseProducer.produce(nyHendelseFraNavAnsatt(deltaker, navAnsatt, enhet, endring))
    }

    fun hendelseFraSystem(deltaker: Deltaker, block: (it: UtkastDto) -> HendelseType.HendelseSystemKanOpprette) {
        val endring = block(deltaker.toUtkastDto())
        hendelseProducer.produce(nyHendelseFraSystem(deltaker, endring))
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
            enhet = HendelseAnsvarlig.NavVeileder.Enhet(navEnhet.id, navEnhet.enhetsnummer),
        )

        return nyHendelse(deltaker, ansvarlig, endring)
    }

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

        return nyHendelse(deltaker, ansvarlig, endring)
    }

    private fun nyHendelseForEndringFraArrangor(
        deltaker: Deltaker,
        navEnhet: NavEnhet,
        endring: HendelseType,
    ): Hendelse {
        val ansvarlig =
            HendelseAnsvarlig.Arrangor(
                enhet = HendelseAnsvarlig.Arrangor.Enhet(navEnhet.id, navEnhet.enhetsnummer),
            )

        return nyHendelse(deltaker, ansvarlig, endring)
    }

    private fun nyHendelseFraSystem(deltaker: Deltaker, endring: HendelseType.HendelseSystemKanOpprette): Hendelse {
        val ansvarlig = HendelseAnsvarlig.System
        return nyHendelse(deltaker, ansvarlig, endring)
    }

    fun hendelseForSistBesokt(deltaker: Deltaker, sistBesokt: ZonedDateTime) {
        // hvis ikke Komet er master for tiltakskode
        if (!unleashToggle.erKometMasterForTiltakstype(deltaker.deltakerliste.tiltakstype.tiltakskode)) return

        val ansvarlig = HendelseAnsvarlig.Deltaker(
            id = deltaker.id,
            navn = deltaker.navBruker.fulltNavn,
        )

        val hendelse = nyHendelse(deltaker, ansvarlig, HendelseType.DeltakerSistBesokt(sistBesokt))
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
        val overordnetArrangor = deltaker.deltakerliste.arrangor.overordnetArrangorId
            ?.let { arrangorService.hentArrangor(it) }

        val forsteVedtakFattet = deltakerHistorikkService.getForsteVedtakFattet(deltaker.id)

        return Hendelse(
            id = UUID.randomUUID(),
            opprettet = LocalDateTime.now(),
            deltaker = deltaker.toHendelseDeltaker(overordnetArrangor, forsteVedtakFattet),
            ansvarlig = ansvarlig,
            payload = endring,
        )
    }
}
