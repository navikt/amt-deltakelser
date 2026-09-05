package no.nav.amt.deltaker.model

import com.fasterxml.jackson.annotation.JsonIgnore
import no.nav.amt.lib.models.deltaker.Arrangor
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringValg
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype
import java.time.LocalDate
import java.util.UUID

data class Deltakerliste(
    val id: UUID,
    val gjennomforingstype: GjennomforingType,
    val tiltakstype: Tiltakstype,
    val navn: String,
    val status: GjennomforingStatusType,
    val startDato: LocalDate?,
    val sluttDato: LocalDate?,
    val antallPlasser: Int?,
    val oppstart: Oppstartstype,
    val apentForPamelding: Boolean,
    val oppmoteSted: String?,
    val arrangor: Arrangor?,
    val pameldingstype: GjennomforingPameldingType,
    val prisinformasjon: String?, // dette er ikke enkeltplass prisinformasjon
    val opplaringKategorisering: OpplaringKategoriseringValg? = null,
) {
    fun erAvlystEllerAvbrutt(): Boolean = status == GjennomforingStatusType.AVLYST ||
        status == GjennomforingStatusType.AVBRUTT

    fun erAvsluttet(): Boolean = erAvlystEllerAvbrutt() || status == GjennomforingStatusType.AVSLUTTET

    // enkeltplass opplæring etter ny forskrift
    @get:JsonIgnore
    val erNyForskriftOpplaring get() = gjennomforingstype == GjennomforingType.Enkeltplass &&
        !tiltakstype.tiltakskode.erArenaEnkeltplass()

    @get:JsonIgnore
    val erFellesOppstart get() = oppstart == Oppstartstype.FELLES

    @get:JsonIgnore
    val deltakelserMaaGodkjennes get() = pameldingstype == GjennomforingPameldingType.TRENGER_GODKJENNING

    @get:JsonIgnore
    val avslutningstype get() = if (erFellesOppstart || tiltakstype.erOpplaeringstiltak) Avslutningstype.FELLES else Avslutningstype.LOPENDE

    @get:JsonIgnore
    val harFellesAvslutning = avslutningstype == Avslutningstype.FELLES

    @get:JsonIgnore
    val erDeltMedValp get() = status != GjennomforingStatusType.KLADD
}
