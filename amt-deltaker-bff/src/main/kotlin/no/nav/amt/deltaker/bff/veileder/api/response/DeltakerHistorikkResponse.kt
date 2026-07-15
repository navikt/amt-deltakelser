package no.nav.amt.deltaker.bff.veileder.api.response

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.amt.deltaker.bff.commonresponse.DeltakelsesinnholdResponse
import no.nav.amt.deltaker.bff.commonresponse.PrisinformasjonResponse
import no.nav.amt.internapi.deltaker.Innsok
import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.arrangor.melding.ForslagDecorator
import no.nav.amt.lib.models.arrangor.melding.Vurderingstype
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.ImportertFraArena
import no.nav.amt.lib.models.deltaker.Vedtak
import no.nav.amt.lib.models.deltaker.VurderingFraArrangorData
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = DeltakerEndringResponse::class, name = "Endring"),
    JsonSubTypes.Type(value = VedtakResponse::class, name = "Vedtak"),
    JsonSubTypes.Type(value = ForslagResponse::class, name = "Forslag"),
    JsonSubTypes.Type(value = EndringFraArrangorResponse::class, name = "EndringFraArrangor"),
    JsonSubTypes.Type(value = ImportertFraArenaResponse::class, name = "ImportertFraArena"),
    JsonSubTypes.Type(value = VurderingFraArrangorResponse::class, name = "VurderingFraArrangor"),
    JsonSubTypes.Type(value = EndringFraTiltakskoordinatorResponse::class, name = "EndringFraTiltakskoordinator"),
    JsonSubTypes.Type(value = InnsokPaaFellesOppstartResponse::class, name = "InnsokPaaFellesOppstart"),
)
sealed interface DeltakerHistorikkResponse {
    companion object {
        fun fromModels(
            models: List<DeltakerHistorikk>,
            arrangornavn: String,
            oppstartstype: Oppstartstype?,
            pameldingstype: GjennomforingPameldingType?,
            enheter: Map<UUID, NavEnhet>,
            ansatte: Map<UUID, NavAnsatt>,
        ) = models.mapNotNull {
            fromModel(
                model = it,
                arrangornavn = arrangornavn,
                oppstartstype = oppstartstype,
                pameldingstype = pameldingstype,
                enheter = enheter,
                ansatte = ansatte,
            )
        }

        fun fromModel(
            model: DeltakerHistorikk,
            arrangornavn: String,
            oppstartstype: Oppstartstype?,
            pameldingstype: GjennomforingPameldingType?,
            enheter: Map<UUID, NavEnhet>,
            ansatte: Map<UUID, NavAnsatt>,
        ) = when (model) {
            is DeltakerHistorikk.Endring -> DeltakerEndringResponse.fromModel(
                model = model.endring,
                arrangornavn = arrangornavn,
                oppstartstype = oppstartstype,
                enheter = enheter,
                ansatte = ansatte,
            )

            is DeltakerHistorikk.Vedtak -> {
                if (pameldingstype == GjennomforingPameldingType.DIREKTE_VEDTAK) {
                    VedtakResponse.fromModel(
                        model = model.vedtak,
                        enheter = enheter,
                        ansatte = ansatte,
                    )
                } else {
                    null
                }
            }

            is DeltakerHistorikk.Forslag -> model.forslag.let {
                ForslagResponse.fromForslag(
                    forslag = it,
                    arrangornavn = arrangornavn,
                    enheter = enheter,
                    ansatte = ansatte,
                )
            }
            is DeltakerHistorikk.EndringFraArrangor -> EndringFraArrangorResponse.fromModel(
                model = model.endringFraArrangor,
                arrangornavn = arrangornavn,
            )

            is DeltakerHistorikk.ImportertFraArena -> ImportertFraArenaResponse.fromModel(
                model.importertFraArena,
            )

            is DeltakerHistorikk.VurderingFraArrangor -> VurderingFraArrangorResponse.fromModel(
                model = model.data,
                arrangornavn = arrangornavn,
            )

            is DeltakerHistorikk.EndringFraTiltakskoordinator -> EndringFraTiltakskoordinatorResponse.fromModel(
                model = model.endringFraTiltakskoordinator,
                enheter = enheter,
                ansatte = ansatte,
            )

            is DeltakerHistorikk.InnsokPaaFellesOppstart -> InnsokPaaFellesOppstartResponse.fromModel(
                model = model.data,
                enheter = enheter,
                ansatte = ansatte,
            )
        }
    }
}

data class DeltakerEndringResponse(
    val endring: DeltakerEndringEndringResponse,
    val endretAv: String,
    val endretAvEnhet: String,
    val endret: LocalDateTime,
    val forslag: ForslagResponse?,
) : DeltakerHistorikkResponse {
    companion object {
        fun fromModel(
            model: DeltakerEndring,
            arrangornavn: String,
            oppstartstype: Oppstartstype?,
            enheter: Map<UUID, NavEnhet>,
            ansatte: Map<UUID, NavAnsatt>,
        ) = DeltakerEndringResponse(
            endring = DeltakerEndringEndringResponse.fromEndring(model.endring, oppstartstype),
            forslag = model.forslag?.let { ForslagResponse.fromForslag(it, arrangornavn, enheter, ansatte) },
            endret = model.endret,
            endretAvEnhet = enheter[model.endretAvEnhet]!!.navn,
            endretAv = ansatte[model.endretAv]!!.navn,
        )
    }
}

data class VedtakResponse(
    val fattet: LocalDateTime?,
    val bakgrunnsinformasjon: String?,
    val fattetAvNav: Boolean,
    val deltakelsesinnhold: DeltakelsesinnholdResponse?,
    val dagerPerUke: Float?,
    val deltakelsesprosent: Float?,
    val opprettetAv: String,
    val opprettetAvEnhet: String,
    val opprettet: LocalDateTime,
) : DeltakerHistorikkResponse {
    companion object {
        fun fromModel(
            model: Vedtak,
            enheter: Map<UUID, NavEnhet>,
            ansatte: Map<UUID, NavAnsatt>,
        ) = VedtakResponse(
            fattet = model.fattet,
            bakgrunnsinformasjon = model.deltakerVedVedtak.bakgrunnsinformasjon,
            deltakelsesinnhold = model.deltakerVedVedtak.deltakelsesinnhold?.let {
                DeltakelsesinnholdResponse(ledetekst = it.ledetekst, innhold = it.innhold)
            },
            dagerPerUke = model.deltakerVedVedtak.dagerPerUke,
            deltakelsesprosent = model.deltakerVedVedtak.deltakelsesprosent,
            fattetAvNav = model.fattetAvNav,
            opprettet = model.opprettet,
            opprettetAvEnhet = enheter[model.opprettetAvEnhet]!!.navn,
            opprettetAv = ansatte[model.opprettetAv]!!.navn,
        )
    }
}

data class EndringFraArrangorResponse(
    val id: UUID,
    val opprettet: LocalDateTime,
    val arrangorNavn: String,
    val endring: EndringFraArrangorEndringResponse,
) : DeltakerHistorikkResponse {
    companion object {
        fun fromModel(
            model: EndringFraArrangor,
            arrangornavn: String,
        ) = EndringFraArrangorResponse(
            id = model.id,
            opprettet = model.opprettet,
            arrangorNavn = arrangornavn,
            endring = model.endring.toResponse(),
        )
    }
}

data class ImportertFraArenaResponse(
    val importertDato: LocalDateTime,
    val startdato: LocalDate?,
    val sluttdato: LocalDate?,
    val dagerPerUke: Float?,
    val deltakelsesprosent: Float?,
    val status: DeltakerStatusResponse,
) : DeltakerHistorikkResponse {
    companion object {
        fun fromModel(model: ImportertFraArena) = ImportertFraArenaResponse(
            importertDato = model.importertDato,
            startdato = model.deltakerVedImport.startdato,
            sluttdato = model.deltakerVedImport.sluttdato,
            dagerPerUke = model.deltakerVedImport.dagerPerUke,
            deltakelsesprosent = model.deltakerVedImport.deltakelsesprosent,
            status = model.deltakerVedImport.status.toResponse(),
        )
    }
}

data class VurderingFraArrangorResponse(
    val vurderingstype: Vurderingstype,
    val begrunnelse: String?,
    val opprettetDato: LocalDateTime,
    val endretAv: String,
) : DeltakerHistorikkResponse {
    companion object {
        fun fromModel(
            model: VurderingFraArrangorData,
            arrangornavn: String,
        ) = VurderingFraArrangorResponse(
            vurderingstype = model.vurderingstype,
            begrunnelse = model.begrunnelse,
            opprettetDato = model.opprettet,
            endretAv = arrangornavn,
        )
    }
}

data class EndringFraTiltakskoordinatorResponse(
    val endring: EndringFraTiltakskoordinatorEndringResponse,
    val endretAv: String,
    val endretAvEnhet: String,
    val endret: LocalDateTime,
) : DeltakerHistorikkResponse {
    companion object {
        fun fromModel(
            model: EndringFraTiltakskoordinator,
            enheter: Map<UUID, NavEnhet>,
            ansatte: Map<UUID, NavAnsatt>,
        ) = EndringFraTiltakskoordinatorResponse(
            endring = model.endring.toResponse(),
            endret = model.endret,
            endretAvEnhet = enheter[model.endretAvEnhet]!!.navn,
            endretAv = ansatte[model.endretAv]!!.navn,
        )
    }
}

data class InnsokPaaFellesOppstartResponse(
    val innsokt: LocalDateTime,
    val innsoktAv: String,
    val innsoktAvEnhet: String,
    val startdato: LocalDate?,
    val sluttdato: LocalDate?,
    val dagerPerUkeVedInnsok: Int?,
    val deltakelsesinnholdVedInnsok: DeltakelsesinnholdResponse?,
    val prisinformasjonVedInnsok: PrisinformasjonResponse?,
    val opplaringKategorisering: OpplaringKategoriseringValgResponse?,
    val utkastDelt: LocalDateTime?,
    val utkastGodkjentAvNav: Boolean,
) : DeltakerHistorikkResponse {
    companion object {
        fun fromModel(
            model: Innsok,
            enheter: Map<UUID, NavEnhet>,
            ansatte: Map<UUID, NavAnsatt>,
        ) = InnsokPaaFellesOppstartResponse(
            innsokt = model.innsokt,
            innsoktAv = ansatte[model.innsoktAv]!!.navn,
            innsoktAvEnhet = enheter[model.innsoktAvEnhet]!!.navn,
            startdato = model.startdato,
            sluttdato = model.sluttdato,
            dagerPerUkeVedInnsok = model.dagerPerUkeVedInnsok,
            deltakelsesinnholdVedInnsok = model.deltakelsesinnholdVedInnsok?.let {
                DeltakelsesinnholdResponse(ledetekst = it.ledetekst, innhold = it.innhold)
            },
            prisinformasjonVedInnsok = model.prisinformasjonVedInnsok?.toPrisinformasjonResponse(),
            utkastDelt = model.utkastDelt,
            utkastGodkjentAvNav = model.utkastGodkjentAvNav,
            opplaringKategorisering = OpplaringKategoriseringValgResponse.fromOpplaringKategoriseringValg(
                model.opplaringKategoriseringVedInnsok,
            ),
        )
    }
}

data class ForslagResponse(
    val id: UUID,
    val opprettet: LocalDateTime,
    val begrunnelse: String?,
    val arrangorNavn: String,
    val endring: ForslagEndringResponse,
    val status: ForslagResponseStatus,
) : DeltakerHistorikkResponse {
    companion object {
        /** Extension som mapper Forslag.Status til ForslagResponseStatus */
        private fun Forslag.Status.toResponseStatus(
            decorator: ForslagDecorator,
            avvistAvNavnProvider: (UUID) -> String,
            avvistAvEnhetNavnProvider: (UUID) -> String,
        ): ForslagResponseStatus = when (this) {
            is Forslag.Status.VenterPaSvar -> ForslagResponseStatus.VenterPaSvar
            is Forslag.Status.Godkjent -> ForslagResponseStatus.Godkjent(godkjent)
            is Forslag.Status.Tilbakekalt -> ForslagResponseStatus.Tilbakekalt(tilbakekalt)
            is Forslag.Status.Erstattet -> ForslagResponseStatus.Erstattet(erstattet)
            is Forslag.Status.Avvist -> {
                // Hent avvist-info fra decorator hvis det er AvvistStatusDecorator
                val (avvistAvNavn, avvistAvEnhet) = (decorator as? ForslagDecorator.AvvistStatusDecorator)
                    ?.let { it.avvistAvAnsattNavn to it.avvistAvEnhetNavn }
                    ?: (avvistAvNavnProvider(avvistAv.id) to avvistAvEnhetNavnProvider(avvistAv.enhetId))

                ForslagResponseStatus.Avvist(
                    avvistAv = avvistAvNavn,
                    avvistAvEnhet = avvistAvEnhet,
                    avvist = avvist,
                    begrunnelseFraNav = begrunnelseFraNav,
                )
            }
        }

        /** Lager en ForslagResponse fra et ForslagDecorator */
        fun fromForslagDecorator(
            dekorertForslag: ForslagDecorator,
            arrangornavn: String,
            avvistAvNavnProvider: (UUID) -> String = { it.toString() },
            avvistAvEnhetNavnProvider: (UUID) -> String = { it.toString() },
        ): ForslagResponse {
            val forslag = dekorertForslag.forslag
            return ForslagResponse(
                id = forslag.id,
                opprettet = forslag.opprettet,
                begrunnelse = forslag.begrunnelse,
                arrangorNavn = arrangornavn,
                endring = forslag.endring.toResponse(),
                status = forslag.status.toResponseStatus(
                    decorator = dekorertForslag,
                    avvistAvNavnProvider = avvistAvNavnProvider,
                    avvistAvEnhetNavnProvider = avvistAvEnhetNavnProvider,
                ),
            )
        }

        /** Lager en ForslagResponse direkte fra et Forslag */
        fun fromForslag(
            forslag: Forslag,
            arrangornavn: String,
            enheter: Map<UUID, NavEnhet>,
            ansatte: Map<UUID, NavAnsatt>,
        ): ForslagResponse = fromForslagDecorator(
            dekorertForslag = ForslagDecorator.DefaultDecorator(forslag),
            arrangornavn = arrangornavn,
            // NOTE: Fallback til UUID-string hvis ikke funnet i ansatte
            avvistAvNavnProvider = { ansatte[it]?.navn ?: it.toString() },
            // NOTE: Fallback til UUID-string hvis ikke funnet i enheter
            avvistAvEnhetNavnProvider = { enheter[it]?.navn ?: it.toString() },
        )
    }
}
