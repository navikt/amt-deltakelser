package no.nav.amt.aktivitetskort.service

import no.nav.amt.aktivitetskort.domain.AktivitetStatus
import no.nav.amt.aktivitetskort.domain.DeltakerStatusModel
import no.nav.amt.aktivitetskort.domain.Tag
import no.nav.amt.lib.models.deltaker.DeltakerStatus

object StatusMapping {
    private val FORSLAG_STATUS = listOf(DeltakerStatus.Type.UTKAST_TIL_PAMELDING)

    private val PLANLEGGES_STATUS = listOf(
        DeltakerStatus.Type.PABEGYNT_REGISTRERING,
        DeltakerStatus.Type.SOKT_INN,
        DeltakerStatus.Type.VURDERES,
        DeltakerStatus.Type.VENTER_PA_OPPSTART,
        DeltakerStatus.Type.VENTELISTE,
    )

    private val GJENNOMFORER_STATUS = listOf(DeltakerStatus.Type.DELTAR)

    private val FULLFORT_STATUS = listOf(
        DeltakerStatus.Type.HAR_SLUTTET,
        DeltakerStatus.Type.FULLFORT,
    )

    private val AVBRUTT_STATUS = listOf(
        DeltakerStatus.Type.AVBRUTT,
        DeltakerStatus.Type.IKKE_AKTUELL,
        DeltakerStatus.Type.FEILREGISTRERT,
        DeltakerStatus.Type.AVBRUTT_UTKAST,
    )

    fun deltakerStatusTilAktivitetStatus(deltakerStatus: DeltakerStatus.Type): Result<AktivitetStatus> = when (deltakerStatus) {
        in FORSLAG_STATUS -> Result.success(AktivitetStatus.FORSLAG)
        in PLANLEGGES_STATUS -> Result.success(AktivitetStatus.PLANLAGT)
        in FULLFORT_STATUS -> Result.success(AktivitetStatus.FULLFORT)
        in GJENNOMFORER_STATUS -> Result.success(AktivitetStatus.GJENNOMFORES)
        in AVBRUTT_STATUS -> Result.success(AktivitetStatus.AVBRUTT)
        else -> Result.failure(RuntimeException("Deltakerstatus ${deltakerStatus.name} kunne ikke mappes til aktivitetsstatus"))
    }

    fun deltakerStatusTilEtikett(status: DeltakerStatusModel): Tag? = when (status.type) {
        DeltakerStatus.Type.VENTER_PA_OPPSTART -> Tag(
            tekst = "Venter på oppstart",
            sentiment = Tag.Sentiment.NEUTRAL,
            kode = Tag.Kode.VENTER_PA_OPPSTART,
        )

        DeltakerStatus.Type.SOKT_INN -> Tag(
            tekst = "Søkt inn",
            sentiment = Tag.Sentiment.NEUTRAL,
            kode = Tag.Kode.SOKT_INN,
        )

        DeltakerStatus.Type.VURDERES -> Tag(
            tekst = "Vurderes",
            sentiment = Tag.Sentiment.NEUTRAL,
            kode = Tag.Kode.VURDERES,
        )

        DeltakerStatus.Type.VENTELISTE -> Tag(
            tekst = "Venteliste",
            sentiment = Tag.Sentiment.NEUTRAL,
            kode = Tag.Kode.VENTELISTE,
        )

        DeltakerStatus.Type.IKKE_AKTUELL -> Tag(
            tekst = "Ikke aktuell",
            sentiment = Tag.Sentiment.NEGATIVE,
            kode = Tag.Kode.IKKE_AKTUELL,
        )

        DeltakerStatus.Type.DELTAR -> null

        DeltakerStatus.Type.HAR_SLUTTET -> null

        DeltakerStatus.Type.FEILREGISTRERT -> Tag(
            tekst = "Feilregistrert",
            sentiment = Tag.Sentiment.NEUTRAL,
            kode = Tag.Kode.FEILREGISTRERT,
        )

        DeltakerStatus.Type.AVBRUTT -> Tag(
            tekst = "Avbrutt",
            sentiment = Tag.Sentiment.NEUTRAL,
            kode = Tag.Kode.AVBRUTT,
        )

        DeltakerStatus.Type.FULLFORT -> Tag(
            tekst = "Fullført",
            sentiment = Tag.Sentiment.NEUTRAL,
            kode = Tag.Kode.FULLFORT,
        )

        DeltakerStatus.Type.PABEGYNT_REGISTRERING -> Tag(
            tekst = "Søkt inn",
            sentiment = Tag.Sentiment.NEUTRAL,
            kode = Tag.Kode.SOKT_INN,
        )

        DeltakerStatus.Type.UTKAST_TIL_PAMELDING -> Tag(
            tekst = "Utkast til påmelding",
            sentiment = Tag.Sentiment.NEUTRAL,
            kode = Tag.Kode.UTKAST_TIL_PAMELDING,
        )

        DeltakerStatus.Type.AVBRUTT_UTKAST -> Tag(
            tekst = "Avbrutt utkast",
            sentiment = Tag.Sentiment.NEUTRAL,
            kode = Tag.Kode.AVBRUTT_UTKAST,
        )

        else -> throw IllegalStateException("Deltakerstatus ${status.type.name} kunne ikke mappes til etikett")
    }
}
