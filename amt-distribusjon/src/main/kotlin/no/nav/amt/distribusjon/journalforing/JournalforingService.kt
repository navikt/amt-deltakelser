package no.nav.amt.distribusjon.journalforing

import no.nav.amt.distribusjon.amtdeltaker.AmtDeltakerClient
import no.nav.amt.distribusjon.digitalbruker.DigitalBrukerService
import no.nav.amt.distribusjon.distribusjonskanal.Distribusjonskanal
import no.nav.amt.distribusjon.hendelse.model.Hendelse
import no.nav.amt.distribusjon.journalforing.dokarkiv.DokarkivClient
import no.nav.amt.distribusjon.journalforing.dokdistfordeling.DistribuerJournalpostRequest
import no.nav.amt.distribusjon.journalforing.dokdistfordeling.DokdistfordelingClient
import no.nav.amt.distribusjon.journalforing.model.HendelseMedJournalforingstatus
import no.nav.amt.distribusjon.journalforing.model.Journalforingstatus
import no.nav.amt.distribusjon.journalforing.pdf.PdfgenClient
import no.nav.amt.distribusjon.journalforing.pdf.lagEndringsvedtakPdfDto
import no.nav.amt.distribusjon.journalforing.pdf.lagHovedopptakForTildeltPlass
import no.nav.amt.distribusjon.journalforing.pdf.lagHovedvedtakPdfDto
import no.nav.amt.distribusjon.journalforing.pdf.lagInnsokingsbrevPdfDto
import no.nav.amt.distribusjon.journalforing.pdf.lagVentelistebrevPdfDto
import no.nav.amt.distribusjon.journalforing.person.AmtPersonClient
import no.nav.amt.distribusjon.journalforing.person.model.DokumentType
import no.nav.amt.distribusjon.journalforing.person.model.NavBruker
import no.nav.amt.distribusjon.veilarboppfolging.VeilarboppfolgingClient
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.hendelse.HendelseAnsvarlig
import no.nav.amt.lib.models.hendelse.HendelseDeltaker
import no.nav.amt.lib.models.hendelse.HendelseType
import no.nav.amt.lib.models.hendelse.UtkastDto
import org.slf4j.LoggerFactory
import java.util.UUID

class JournalforingService(
    private val journalforingstatusRepository: JournalforingstatusRepository,
    private val amtPersonClient: AmtPersonClient,
    private val pdfgenClient: PdfgenClient,
    private val veilarboppfolgingClient: VeilarboppfolgingClient,
    private val dokarkivClient: DokarkivClient,
    private val dokdistfordelingClient: DokdistfordelingClient,
    private val amtDeltakerClient: AmtDeltakerClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun handleHendelse(hendelse: Hendelse) {
        val journalforingstatus = journalforingstatusRepository.get(hendelse.id)
        if (hendelseErBehandlet(journalforingstatus, hendelse.distribusjonskanal, hendelse.manuellOppfolging)) {
            log.info("Hendelse med id ${hendelse.id} for deltaker ${hendelse.deltaker.id} er allerede behandlet")
            return
        }
        when (hendelse.payload) {
            is HendelseType.InnbyggerGodkjennUtkast -> {
                handleUtkastGodkjent(
                    hendelse,
                    hendelse.payload.utkast,
                    journalforingstatus,
                )
            }

            is HendelseType.ReaktiverDeltakelse -> {
                handleUtkastGodkjent(
                    hendelse,
                    hendelse.payload.utkast,
                    journalforingstatus,
                )
            }

            is HendelseType.NavGodkjennUtkast -> {
                handleUtkastGodkjent(
                    hendelse,
                    hendelse.payload.utkast,
                    journalforingstatus,
                )
            }

            is HendelseType.AvsluttDeltakelse,
            is HendelseType.EndreAvslutning,
            is HendelseType.AvbrytDeltakelse,
            is HendelseType.EndreDeltakelsesmengde,
            is HendelseType.EndreSluttdato,
            is HendelseType.EndreStartdato,
            is HendelseType.ForlengDeltakelse,
            is HendelseType.IkkeAktuell,
            is HendelseType.EndreInnhold,
            is HendelseType.EndreBakgrunnsinformasjon,
            is HendelseType.LeggTilOppstartsdato,
            is HendelseType.FjernOppstartsdato,
            -> {
                handleEndringsvedtak(hendelse, journalforingstatus)
            }

            is HendelseType.EndreSluttarsak,
            is HendelseType.EndreUtkast,
            is HendelseType.OpprettUtkast,
            is HendelseType.AvbrytUtkast,
            is HendelseType.DeltakerSistBesokt,
            -> {
            }
            is HendelseType.SettPaaVenteliste -> journalforOgSendVentelisteBrev(hendelse, journalforingstatus)
            is HendelseType.TildelPlass -> journalforHovedvedtakForTildeltPlass(hendelse, journalforingstatus)
            is HendelseType.Avslag -> journalforAvslag(hendelse, journalforingstatus)
        }
    }

    suspend fun handleUtkastGodkjent(
        hendelse: Hendelse,
        utkast: UtkastDto,
        journalforingstatus: Journalforingstatus?,
    ) {
        when (val pameldingType = hendelse.deltaker.deltakerliste.pameldingstype) {
            GjennomforingPameldingType.DIREKTE_VEDTAK -> journalforHovedvedtak(hendelse, utkast, journalforingstatus)
            GjennomforingPameldingType.TRENGER_GODKJENNING -> journalforOgSendInnsokingsbrev(hendelse, utkast, journalforingstatus)
            else -> throw IllegalStateException("Pameldingstype $pameldingType er ikke implementert")
        }
    }

    private suspend fun journalforAvslag(hendelse: Hendelse, journalforingstatus: Journalforingstatus?) {
        val navBruker = amtPersonClient.hentNavBruker(hendelse.deltaker.personident)
        val hendelseAnsvarlig = hendelse.ansvarlig.hentTiltakskoordinator()

        val pdf: suspend () -> ByteArray = {
            pdfgenClient.endringsvedtak(
                lagEndringsvedtakPdfDto(
                    hendelse.deltaker,
                    navBruker,
                    hendelseAnsvarlig,
                    listOf(hendelse),
                    hendelse.opprettet.toLocalDate(),
                ),
            )
        }

        journalforOgSend(
            pdf,
            hendelse,
            hendelseAnsvarlig.enhet.enhetsnummer,
            journalforingstatus,
            DokumentType.AVSLAG,
            DistribuerJournalpostRequest.Distribusjonstype.VEDTAK,
        )

        log.info("Journalførte avslag for deltaker ${hendelse.deltaker.id}")
    }

    private suspend fun journalforHovedvedtakForTildeltPlass(hendelse: Hendelse, journalforingstatus: Journalforingstatus?) {
        val navBruker = amtPersonClient.hentNavBruker(hendelse.deltaker.personident)
        val hendelseAnsvarlig = hendelse.ansvarlig.hentTiltakskoordinator()
        val deltaker = amtDeltakerClient.getDeltaker(hendelse.deltaker.id)
        val hovedvedtakInput = lagHovedopptakForTildeltPlass(
            deltaker = hendelse.deltaker,
            navBruker = navBruker,
            ansvarlig = hendelseAnsvarlig,
            opprettetDato = hendelse.opprettet.toLocalDate(),
            deltakelseInnhold = deltaker.deltakelsesinnhold,
        )

        val pdf: suspend () -> ByteArray = {
            if (hendelse.deltaker.deltakerliste.oppstartstype == HendelseDeltaker.Deltakerliste.Oppstartstype.FELLES) {
                pdfgenClient.genererHovedvedtakTildeltPlassFellesOppstart(hovedvedtakInput)
            } else {
                pdfgenClient.genererHovedvedtakTildeltPlassLoependeOppstart(hovedvedtakInput)
            }
        }

        journalforOgSend(
            pdf,
            hendelse,
            hendelseAnsvarlig.enhet.enhetsnummer,
            journalforingstatus,
            DokumentType.HOVEDVEDTAK,
            DistribuerJournalpostRequest.Distribusjonstype.VEDTAK,
        )

        log.info("Journalførte hovedvedtak for deltaker ${hendelse.deltaker.id}")
    }

    private suspend fun journalforOgSendInnsokingsbrev(
        hendelse: Hendelse,
        utkast: UtkastDto,
        journalforingstatus: Journalforingstatus?,
    ) {
        val navBruker = amtPersonClient.hentNavBruker(hendelse.deltaker.personident)
        val veileder = hendelse.ansvarlig.hentVeileder()
        val pdf: suspend () -> ByteArray = {
            pdfgenClient.genererInnsokingsbrevPDF(
                lagInnsokingsbrevPdfDto(
                    deltaker = hendelse.deltaker,
                    navBruker = navBruker,
                    veileder = hendelse.ansvarlig.hentVeileder(),
                    opprettetDato = hendelse.opprettet.toLocalDate(),
                    utkast = utkast,
                ),
            )
        }

        journalforOgSend(
            pdf,
            hendelse,
            veileder.enhet.enhetsnummer,
            journalforingstatus,
            DokumentType.INNSOKINGSBREV,
            DistribuerJournalpostRequest.Distribusjonstype.ANNET,
        )

        log.info("Journalførte innsøkingsbrev for deltaker ${hendelse.deltaker.id}")
    }

    private suspend fun journalforOgSendVentelisteBrev(hendelse: Hendelse, journalforingstatus: Journalforingstatus?) {
        val navBruker = amtPersonClient.hentNavBruker(hendelse.deltaker.personident)
        val tiltakskoordinator = hendelse.ansvarlig.hentTiltakskoordinator()
        val pdf: suspend () -> ByteArray = {
            pdfgenClient.genererVentelistebrevPDF(
                lagVentelistebrevPdfDto(
                    deltaker = hendelse.deltaker,
                    navBruker = navBruker,
                    endretAv = tiltakskoordinator,
                    hendelseOpprettetDato = hendelse.opprettet.toLocalDate(),
                ),
            )
        }

        journalforOgSend(
            pdf,
            hendelse,
            tiltakskoordinator.enhet.enhetsnummer,
            journalforingstatus,
            DokumentType.VENTELISTEBREV,
            DistribuerJournalpostRequest.Distribusjonstype.ANNET,
        )

        log.info("Journalførte ventelistebrev for deltaker ${hendelse.deltaker.id}")
    }

    private suspend fun journalforHovedvedtak(
        hendelse: Hendelse,
        utkast: UtkastDto,
        journalforingstatus: Journalforingstatus?,
    ) {
        val navBruker = amtPersonClient.hentNavBruker(hendelse.deltaker.personident)
        val veileder = hendelse.ansvarlig.hentVeileder()
        val pdf: suspend () -> ByteArray = {
            pdfgenClient.genererHovedvedtakForIndividuellOppfolging(
                lagHovedvedtakPdfDto(
                    deltaker = hendelse.deltaker,
                    navBruker = navBruker,
                    utkast = utkast,
                    veileder = veileder,
                    vedtaksdato = hendelse.opprettet.toLocalDate(),
                    begrunnelseFraNav = hendelse.getBegrunnelseForHovedvedtak(),
                ),
            )
        }

        journalforOgSend(
            pdf,
            hendelse,
            veileder.enhet.enhetsnummer,
            journalforingstatus,
            DokumentType.HOVEDVEDTAK,
            DistribuerJournalpostRequest.Distribusjonstype.VEDTAK,
        )

        log.info("Journalførte hovedvedtak for deltaker ${hendelse.deltaker.id}")
    }

    private suspend fun journalforOgSend(
        genererPDF: suspend () -> ByteArray,
        hendelse: Hendelse,
        journalforendeEnhet: String,
        journalforingstatus: Journalforingstatus?,
        dokumentType: DokumentType,
        distribusjonstype: DistribuerJournalpostRequest.Distribusjonstype,
    ) {
        val navBruker = amtPersonClient.hentNavBruker(hendelse.deltaker.personident)
        var journalpostId = if (journalforingstatus?.erJournalfort() == true) journalforingstatus.journalpostId else null

        if (journalpostId == null) {
            val pdf = genererPDF()
            journalpostId = journalfor(listOf(hendelse), journalforendeEnhet, navBruker, pdf, dokumentType)
        }

        sendBrev(hendelse, journalpostId, navBruker.harAdresse(), distribusjonstype)
        log.info("Journalførte brev for deltaker ${hendelse.deltaker.id}")
    }

    private fun handleEndringsvedtak(hendelse: Hendelse, journalforingstatus: Journalforingstatus?) {
        journalforingstatusRepository.upsert(
            Journalforingstatus(
                hendelseId = hendelse.id,
                journalpostId = journalforingstatus?.journalpostId,
                bestillingsId = journalforingstatus?.bestillingsId,
                kanIkkeDistribueres = journalforingstatus?.kanIkkeDistribueres,
                kanIkkeJournalfores = journalforingstatus?.kanIkkeJournalfores,
            ),
        )
        log.info("Endringsvedtak for hendelse ${hendelse.id} er lagret og plukkes opp av asynkron jobb")
    }

    suspend fun journalforOgDistribuerEndringsvedtak(hendelseMedJournalforingstatuser: List<HendelseMedJournalforingstatus>) {
        if (hendelseMedJournalforingstatuser.isEmpty()) {
            return
        }

        val sisteHendelse = hendelseMedJournalforingstatuser.maxBy { it.hendelse.opprettet }
        if (hendelseMedJournalforingstatuser.find { it.hendelse.deltaker.id != sisteHendelse.hendelse.deltaker.id } != null) {
            throw IllegalArgumentException("Alle hendelser må tilhøre samme deltaker!")
        }

        val journalforteHendelser = hendelseMedJournalforingstatuser.filter { it.journalforingstatus.erJournalfort() }
        val ikkeJournalforteHendelser = hendelseMedJournalforingstatuser
            .filter { !it.journalforingstatus.erJournalfort() }
            .map { it.hendelse }

        val navBruker = amtPersonClient.hentNavBruker(sisteHendelse.hendelse.deltaker.personident)
        if (ikkeJournalforteHendelser.isNotEmpty()) {
            val journalpostId = journalforEndringsvedtak(ikkeJournalforteHendelser, navBruker) ?: return
            sendBrev(
                ikkeJournalforteHendelser,
                journalpostId,
                navBruker.harAdresse(),
                DistribuerJournalpostRequest.Distribusjonstype.VEDTAK,
            )
        }

        if (journalforteHendelser.isNotEmpty()) {
            val unikeJournalpostIder = journalforteHendelser
                .distinctBy { it.journalforingstatus.journalpostId }
                .mapNotNull { it.journalforingstatus.journalpostId }
            val journalpostHendelseMap =
                unikeJournalpostIder.associateWith { journalpostid ->
                    journalforteHendelser.filter { it.journalforingstatus.journalpostId == journalpostid }
                }
            journalpostHendelseMap.entries.forEach { entry ->
                sendBrev(
                    journalpostId = entry.key,
                    hendelser = entry.value.map { it.hendelse },
                    harAdresse = navBruker.harAdresse(),
                    distribusjonstype = DistribuerJournalpostRequest.Distribusjonstype.VEDTAK,
                )
            }
        }

        log.info(
            "Journalførte og distribuerte endringsvedtak for deltaker ${hendelseMedJournalforingstatuser.first().hendelse.deltaker.id}, " +
                "hendelser ${hendelseMedJournalforingstatuser.map { it.hendelse.id }.joinToString()}",
        )
    }

    private suspend fun journalforEndringsvedtak(ikkeJournalforteHendelser: List<Hendelse>, navBruker: NavBruker): String? {
        val nyesteHendelse = ikkeJournalforteHendelser.maxBy { it.opprettet }
        val ansvarlig = getAnsvarlig(nyesteHendelse, ikkeJournalforteHendelser)
        val journalforendeEnhet = getJournalforendeEnhet(ansvarlig)
        val aktivOppfolgingsperiode = navBruker.getAktivOppfolgingsperiode()

        if (aktivOppfolgingsperiode == null) {
            val kanIkkeJournalfores = ikkeJournalforteHendelser.filter { it.tillattEndringUtenAktivOppfolgingsperiode() }
            kanIkkeJournalfores.forEach {
                upsertJournalforingsstatus(hendelseId = it.id, journalpostId = null, kanIkkeJournalfores = true)
                log.warn("Lar være å journalføre endring som kan gjøres uten oppfølging, hendelseId ${it.id}")
            }
            if (kanIkkeJournalfores.size != ikkeJournalforteHendelser.size) {
                throw IllegalArgumentException(
                    "Kan ikke endre på deltaker ${nyesteHendelse.deltaker.id} som ikke har aktiv oppfølgingsperiode",
                )
            }
            return null
        }
        val pdf = pdfgenClient.endringsvedtak(
            lagEndringsvedtakPdfDto(
                nyesteHendelse.deltaker,
                navBruker,
                ansvarlig,
                ikkeJournalforteHendelser,
                nyesteHendelse.opprettet.toLocalDate(),
            ),
        )
        val journalpostId = journalfor(ikkeJournalforteHendelser, journalforendeEnhet, navBruker, pdf, DokumentType.ENDRINGSVEDTAK)

        log.info(
            "Journalførte endringsvedtak for deltaker ${ikkeJournalforteHendelser.first().deltaker.id}, " +
                "hendelser ${ikkeJournalforteHendelser.map { it.id }.joinToString()}",
        )
        return journalpostId
    }

    private suspend fun sendBrev(
        hendelse: Hendelse,
        journalpostId: String,
        harAdresse: Boolean,
        distribusjonstype: DistribuerJournalpostRequest.Distribusjonstype,
    ) = sendBrev(listOf(hendelse), journalpostId, harAdresse, distribusjonstype)

    private suspend fun sendBrev(
        hendelser: List<Hendelse>,
        journalpostId: String,
        harAdresse: Boolean,
        distribusjonstype: DistribuerJournalpostRequest.Distribusjonstype,
    ) {
        if (hendelser.isEmpty()) {
            return
        }
        val nyesteHendelse = hendelser.maxBy { it.opprettet }
        if (DigitalBrukerService.skalDistribueresDigitalt(nyesteHendelse.distribusjonskanal, nyesteHendelse.manuellOppfolging)) {
            return
        }
        val bestillingsId = if (harAdresse) dokdistfordelingClient.distribuerJournalpost(journalpostId, distribusjonstype) else null
        val kanDistribueres = bestillingsId != null
        if (!kanDistribueres) {
            log.warn("Kan ikke distribuere journalpost $journalpostId. Har adresse: $harAdresse")
        }
        hendelser.forEach {
            journalforingstatusRepository.upsert(
                Journalforingstatus(
                    hendelseId = it.id,
                    journalpostId = journalpostId,
                    bestillingsId = bestillingsId,
                    kanIkkeDistribueres = !kanDistribueres,
                    kanIkkeJournalfores = false,
                ),
            )
        }
    }

    private suspend fun journalfor(
        hendelse: List<Hendelse>,
        journalforendeEnhet: String,
        navBruker: NavBruker,
        pdf: ByteArray,
        dokumentType: DokumentType,
    ): String {
        val nyesteHendelse = hendelse.maxBy { it.opprettet }
        val aktivOppfolgingsperiode = navBruker.getAktivOppfolgingsperiode()
            ?: throw IllegalArgumentException(
                "Kan ikke endre på deltaker ${nyesteHendelse.deltaker.id} som ikke har aktiv oppfølgingsperiode",
            )
        val sak = veilarboppfolgingClient.opprettEllerHentSak(aktivOppfolgingsperiode.id)

        val journalpostId = dokarkivClient.opprettJournalpost(
            hendelseId = nyesteHendelse.id,
            fnr = nyesteHendelse.deltaker.personident,
            sak = sak,
            pdf = pdf,
            journalforendeEnhet = journalforendeEnhet,
            journalpostNavn = getJournalpostNavn(nyesteHendelse.deltaker.deltakerliste.tiltak, dokumentType),
        )

        hendelse.forEach {
            upsertJournalforingsstatus(hendelseId = it.id, journalpostId = journalpostId)
        }

        return journalpostId
    }

    private fun upsertJournalforingsstatus(
        hendelseId: UUID,
        journalpostId: String?,
        kanIkkeJournalfores: Boolean = false,
    ) {
        val nyJournalforingstatus = Journalforingstatus(
            hendelseId = hendelseId,
            journalpostId = journalpostId,
            bestillingsId = null,
            kanIkkeDistribueres = null,
            kanIkkeJournalfores = kanIkkeJournalfores,
        )
        journalforingstatusRepository.upsert(nyJournalforingstatus)
    }
}

private fun hendelseErBehandlet(
    journalforingstatus: Journalforingstatus?,
    distribusjonskanal: Distribusjonskanal,
    manuellOppfolging: Boolean,
): Boolean = journalforingstatus != null &&
    journalforingstatus.erJournalfort() &&
    journalforingstatus.erDistribuert(
        distribusjonskanal,
        manuellOppfolging,
    )

private fun getAnsvarlig(nyesteHendelse: Hendelse, ikkeJournalforteHendelser: List<Hendelse>): HendelseAnsvarlig {
    if (nyesteHendelse.ansvarlig is HendelseAnsvarlig.NavVeileder) {
        return nyesteHendelse.ansvarlig
    }
    ikkeJournalforteHendelser.firstOrNull { it.ansvarlig is HendelseAnsvarlig.NavVeileder }?.let { return it.ansvarlig }
    ikkeJournalforteHendelser.firstOrNull { it.ansvarlig is HendelseAnsvarlig.Arrangor }?.let { return it.ansvarlig }

    throw IllegalArgumentException("Må ha en ansvarlig som er enten veileder eller arrangør")
}

private fun getJournalpostNavn(tiltakstype: HendelseDeltaker.Deltakerliste.Tiltak, dokumentType: DokumentType): String {
    val tiltaknavn = if (tiltakstype.tiltakskode == Tiltakskode.JOBBKLUBB) "Jobbsøkerkurs" else tiltakstype.navn
    return when (dokumentType) {
        DokumentType.HOVEDVEDTAK -> "Vedtak - $tiltaknavn"
        DokumentType.ENDRINGSVEDTAK -> "Endringsvedtak - $tiltaknavn"
        DokumentType.INNSOKINGSBREV -> "Søknad - $tiltaknavn"
        DokumentType.AVSLAG -> "Avslag - $tiltaknavn"
        DokumentType.VENTELISTEBREV -> "Venteliste - $tiltaknavn"
    }
}

private fun getJournalforendeEnhet(ansvarlig: HendelseAnsvarlig): String = when (ansvarlig) {
    is HendelseAnsvarlig.NavVeileder -> ansvarlig.enhet.enhetsnummer
    is HendelseAnsvarlig.Arrangor -> ansvarlig.enhet.enhetsnummer
    else -> throw IllegalArgumentException("Kan ikke journalføre endringsvedtak fra ${ansvarlig.javaClass}")
}

private fun HendelseAnsvarlig.hentVeileder(): HendelseAnsvarlig.NavVeileder = when (this) {
    is HendelseAnsvarlig.NavVeileder -> this

    else -> throw IllegalArgumentException(
        "Deltaker, system eller arrangør kan ikke være ansvarlig for vedtaket",
    )
}

private fun HendelseAnsvarlig.hentTiltakskoordinator(): HendelseAnsvarlig.NavTiltakskoordinator = when (this) {
    is HendelseAnsvarlig.NavTiltakskoordinator -> this

    else -> throw IllegalArgumentException(
        "Deltaker, system eller arrangør kan ikke være ansvarlig for vedtaket",
    )
}
