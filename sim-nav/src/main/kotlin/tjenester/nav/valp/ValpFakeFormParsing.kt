package tjenester.nav.valp

import io.ktor.http.*
import no.nav.amt.lib.models.deltaker.InnsatsgruppeV2
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*

private val DATE_TIME_INPUT_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

data class GjennomforingFormInput(
    val id: UUID,
    val type: String,
    val tiltakskode: Tiltakskode,
    val arrangorOrganisasjonsnummer: String,
    val pameldingType: GjennomforingPameldingType,
    val status: GjennomforingStatusType,
    val oppstart: Oppstartstype,
    val opprettetTidspunkt: OffsetDateTime,
    val oppdatertTidspunkt: OffsetDateTime,
    val prisinformasjon: String?,
    val navn: String?,
    val startDato: LocalDate?,
    val sluttDato: LocalDate?,
    val tilgjengeligForArrangorFraOgMedDato: LocalDate?,
    val apentForPamelding: Boolean?,
    val antallPlasser: Int?,
    val deltidsprosent: Double?,
    val oppmoteSted: String?,
)

data class TiltakstypeFormInput(
    val id: UUID,
    val navn: String,
    val tiltakskode: Tiltakskode,
    val innsatsgrupper: Set<InnsatsgruppeV2>,
)

fun Parameters.toGjennomforingEnkeltplassFormInput(): GjennomforingFormInput {
    return GjennomforingFormInput(
        id = required("id").toUuid(),
        type = "enkeltplass",
        tiltakskode = required("tiltakskode").toEnum(),
        arrangorOrganisasjonsnummer = required("arrangorOrganisasjonsnummer"),
        pameldingType = required("pameldingType").toEnum(),
        status = required("status").toEnum(),
        oppstart = required("oppstart").toEnum(),
        opprettetTidspunkt = required("opprettetTidspunkt").toOffsetDateTimeUtc(),
        oppdatertTidspunkt = required("oppdatertTidspunkt").toOffsetDateTimeUtc(),
        prisinformasjon = optional("prisinformasjon"),
        navn = null,
        startDato = null,
        sluttDato = null,
        tilgjengeligForArrangorFraOgMedDato = null,
        apentForPamelding = null,
        antallPlasser = null,
        deltidsprosent = null,
        oppmoteSted = null,
    )
}

fun Parameters.toGjennomforingGruppeFormInput(): GjennomforingFormInput {
    return GjennomforingFormInput(
        id = required("id").toUuid(),
        type = "gruppe",
        tiltakskode = required("tiltakskode").toEnum(),
        arrangorOrganisasjonsnummer = required("arrangorOrganisasjonsnummer"),
        pameldingType = required("pameldingType").toEnum(),
        status = required("status").toEnum(),
        oppstart = required("oppstart").toEnum(),
        opprettetTidspunkt = required("opprettetTidspunkt").toOffsetDateTimeUtc(),
        oppdatertTidspunkt = required("oppdatertTidspunkt").toOffsetDateTimeUtc(),
        prisinformasjon = optional("prisinformasjon"),
        navn = required("navn"),
        startDato = required("startDato").toLocalDate(),
        sluttDato = optional("sluttDato")?.toLocalDate(),
        tilgjengeligForArrangorFraOgMedDato = optional("tilgjengeligForArrangorFraOgMedDato")?.toLocalDate(),
        apentForPamelding = required("apentForPamelding").toBooleanStrict(),
        antallPlasser = required("antallPlasser").toInt(),
        deltidsprosent = required("deltidsprosent").toDouble(),
        oppmoteSted = optional("oppmoteSted"),
    )
}

fun Parameters.toGjennomforingEnkeltplassEditFormInput(id: UUID): GjennomforingFormInput {
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    return GjennomforingFormInput(
        id = id,
        type = "enkeltplass",
        tiltakskode = required("tiltakskode").toEnum(),
        arrangorOrganisasjonsnummer = required("arrangorOrganisasjonsnummer"),
        pameldingType = required("pameldingType").toEnum(),
        status = required("status").toEnum(),
        oppstart = required("oppstart").toEnum(),
        opprettetTidspunkt = required("opprettetTidspunkt").toOffsetDateTimeUtc(),
        oppdatertTidspunkt = now,
        prisinformasjon = optional("prisinformasjon"),
        navn = null,
        startDato = null,
        sluttDato = null,
        tilgjengeligForArrangorFraOgMedDato = null,
        apentForPamelding = null,
        antallPlasser = null,
        deltidsprosent = null,
        oppmoteSted = null,
    )
}

fun Parameters.toGjennomforingGruppeEditFormInput(id: UUID): GjennomforingFormInput {
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    return GjennomforingFormInput(
        id = id,
        type = "gruppe",
        tiltakskode = required("tiltakskode").toEnum(),
        arrangorOrganisasjonsnummer = required("arrangorOrganisasjonsnummer"),
        pameldingType = required("pameldingType").toEnum(),
        status = required("status").toEnum(),
        oppstart = required("oppstart").toEnum(),
        opprettetTidspunkt = required("opprettetTidspunkt").toOffsetDateTimeUtc(),
        oppdatertTidspunkt = now,
        prisinformasjon = optional("prisinformasjon"),
        navn = required("navn"),
        startDato = required("startDato").toLocalDate(),
        sluttDato = optional("sluttDato")?.toLocalDate(),
        tilgjengeligForArrangorFraOgMedDato = optional("tilgjengeligForArrangorFraOgMedDato")?.toLocalDate(),
        apentForPamelding = required("apentForPamelding").toBooleanStrict(),
        antallPlasser = required("antallPlasser").toInt(),
        deltidsprosent = required("deltidsprosent").toDouble(),
        oppmoteSted = optional("oppmoteSted"),
    )
}

fun Parameters.toTiltakstypeEditFormInput(id: UUID): TiltakstypeFormInput {
    return TiltakstypeFormInput(
        id = id,
        navn = required("navn"),
        tiltakskode = required("tiltakskode").toEnum(),
        innsatsgrupper = getAll("innsatsgrupper")
            ?.map { it.toEnum<InnsatsgruppeV2>() }
            ?.toSet()
            ?: emptySet(),
    )
}

fun Parameters.toTiltakstypeFormInput(): TiltakstypeFormInput {
    return TiltakstypeFormInput(
        id = required("id").toUuid(),
        navn = required("navn"),
        tiltakskode = required("tiltakskode").toEnum(),
        innsatsgrupper = getAll("innsatsgrupper")
            ?.map { it.toEnum<InnsatsgruppeV2>() }
            ?.toSet()
            ?: emptySet(),
    )
}

private fun Parameters.required(name: String): String =
    get(name)?.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("Missing field '$name'")

private fun Parameters.optional(name: String): String? = get(name)?.takeIf { it.isNotBlank() }

private fun String.toOffsetDateTimeUtc(): OffsetDateTime =
    LocalDateTime.parse(this, DATE_TIME_INPUT_FORMATTER).atOffset(ZoneOffset.UTC)

private fun String.toLocalDate(): LocalDate = LocalDate.parse(this)

private fun String.toUuid(): UUID = UUID.fromString(this)

private inline fun <reified E : Enum<E>> String.toEnum(): E = enumValueOf(this)

