package tjenester.nav.valp

import DbOperations
import db.ValpGjennomforing
import db.ValpTiltakstype
import no.nav.amt.lib.models.deltaker.InnsatsgruppeV2
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

data class GjennomforingRow(
    val id: String,
    val type: String,
    val tiltakskode: String,
    val arrangor: String,
    val status: String,
    val navn: String?,
    val startDato: String?,
    val sluttDato: String?,
)

data class TiltakstypeRow(
    val id: String,
    val navn: String,
    val tiltakskode: String,
    val innsatsgrupper: String,
)

data class GjennomforingFormDefaults(
    val id: UUID,
    val tiltakskode: Tiltakskode,
    val arrangorOrganisasjonsnummer: String,
    val pameldingType: GjennomforingPameldingType,
    val status: GjennomforingStatusType,
    val oppstart: Oppstartstype,
    val opprettetTidspunkt: LocalDateTime,
    val oppdatertTidspunkt: LocalDateTime,
    val prisinformasjon: String?,
    val navn: String,
    val startDato: String,
    val sluttDato: String,
    val tilgjengeligForArrangorFraOgMedDato: String,
    val apentForPamelding: Boolean,
    val antallPlasser: Int,
    val deltidsprosent: Double,
    val oppmoteSted: String,
)

data class TiltakstypeFormDefaults(
    val id: UUID,
    val navn: String,
    val tiltakskode: Tiltakskode,
    val innsatsgrupper: Set<InnsatsgruppeV2>,
)

fun defaultGjennomforingEnkeltplassFormDefaults(now: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)): GjennomforingFormDefaults {
    return GjennomforingFormDefaults(
        id = UUID.randomUUID(),
        tiltakskode = Tiltakskode.entries.first(),
        arrangorOrganisasjonsnummer = "",
        pameldingType = GjennomforingPameldingType.entries.first(),
        status = GjennomforingStatusType.entries.first(),
        oppstart = Oppstartstype.entries.first(),
        opprettetTidspunkt = now,
        oppdatertTidspunkt = now,
        prisinformasjon = "",
        navn = "",
        startDato = "",
        sluttDato = "",
        tilgjengeligForArrangorFraOgMedDato = "",
        apentForPamelding = true,
        antallPlasser = 0,
        deltidsprosent = 0.0,
        oppmoteSted = "",
    )
}

fun defaultGjennomforingGruppeFormDefaults(now: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)): GjennomforingFormDefaults {
    return GjennomforingFormDefaults(
        id = UUID.randomUUID(),
        tiltakskode = Tiltakskode.entries.first(),
        arrangorOrganisasjonsnummer = "",
        pameldingType = GjennomforingPameldingType.entries.first(),
        status = GjennomforingStatusType.entries.first(),
        oppstart = Oppstartstype.entries.first(),
        opprettetTidspunkt = now,
        oppdatertTidspunkt = now,
        prisinformasjon = "",
        navn = "",
        startDato = "",
        sluttDato = "",
        tilgjengeligForArrangorFraOgMedDato = "",
        apentForPamelding = true,
        antallPlasser = 10,
        deltidsprosent = 100.0,
        oppmoteSted = "",
    )
}

fun defaultTiltakstypeFormDefaults(): TiltakstypeFormDefaults {
    return TiltakstypeFormDefaults(
        id = UUID.randomUUID(),
        navn = "",
        tiltakskode = Tiltakskode.entries.first(),
        innsatsgrupper = emptySet(),
    )
}

fun fetchGjennomforinger(): List<GjennomforingRow> {
    return DbOperations.inTransaction {
        ValpGjennomforing.selectAll().map { row ->
            GjennomforingRow(
                id = row[ValpGjennomforing.id],
                type = row[ValpGjennomforing.type],
                tiltakskode = row[ValpGjennomforing.tiltakskode],
                arrangor = row[ValpGjennomforing.arrangorOrganisasjonsnummer],
                status = row[ValpGjennomforing.status],
                navn = row[ValpGjennomforing.navn],
                startDato = row[ValpGjennomforing.startDato],
                sluttDato = row[ValpGjennomforing.sluttDato],
            )
        }
    }
}

fun fetchTiltakstyper(): List<TiltakstypeRow> {
    return DbOperations.inTransaction {
        ValpTiltakstype.selectAll().map { row ->
            TiltakstypeRow(
                id = row[ValpTiltakstype.id],
                navn = row[ValpTiltakstype.navn],
                tiltakskode = row[ValpTiltakstype.tiltakskode],
                innsatsgrupper = row[ValpTiltakstype.innsatsgrupper],
            )
        }
    }
}

fun fetchGjennomforingById(id: UUID): GjennomforingFormInput? {
    return DbOperations.inTransaction {
        ValpGjennomforing.selectAll()
            .firstOrNull { row -> row[ValpGjennomforing.id] == id.toString() }
            ?.toGjennomforingFormInput()
    }
}

fun fetchTiltakstypeById(id: UUID): TiltakstypeFormInput? {
    return DbOperations.inTransaction {
        ValpTiltakstype.selectAll()
            .firstOrNull { row -> row[ValpTiltakstype.id] == id.toString() }
            ?.toTiltakstypeFormInput()
    }
}

fun insertGjennomforing(form: GjennomforingFormInput) {
    val now = LocalDateTime.now(ZoneOffset.UTC).toString()
    DbOperations.inTransaction {
        ValpGjennomforing.insert {
            it[id] = form.id.toString()
            it[type] = form.type
            it[tiltakskode] = form.tiltakskode.name
            it[arrangorOrganisasjonsnummer] = form.arrangorOrganisasjonsnummer
            it[pameldingType] = form.pameldingType.name
            it[status] = form.status.name
            it[oppstart] = form.oppstart.name
            it[opprettetTidspunkt] = form.opprettetTidspunkt.toString()
            it[oppdatertTidspunkt] = form.oppdatertTidspunkt.toString()
            it[prisinformasjon] = form.prisinformasjon
            it[navn] = form.navn
            it[startDato] = form.startDato?.toString()
            it[sluttDato] = form.sluttDato?.toString()
            it[tilgjengeligForArrangorFraOgMedDato] = form.tilgjengeligForArrangorFraOgMedDato?.toString()
            it[apentForPamelding] = form.apentForPamelding?.toString()
            it[antallPlasser] = form.antallPlasser?.toString()
            it[deltidsprosent] = form.deltidsprosent?.toString()
            it[oppmoteSted] = form.oppmoteSted
            it[createdAt] = now
            it[updatedAt] = now
        }
    }
}

fun insertTiltakstype(form: TiltakstypeFormInput) {
    val now = LocalDateTime.now(ZoneOffset.UTC).toString()
    DbOperations.inTransaction {
        ValpTiltakstype.insert {
            it[id] = form.id.toString()
            it[navn] = form.navn
            it[tiltakskode] = form.tiltakskode.name
            it[innsatsgrupper] = form.innsatsgrupper.toJsonArrayText()
            it[createdAt] = now
            it[updatedAt] = now
        }
    }
}

fun updateGjennomforing(form: GjennomforingFormInput): Boolean {
    val now = LocalDateTime.now(ZoneOffset.UTC).toString()
    val updatedRows = DbOperations.inTransaction {
        ValpGjennomforing.update({ ValpGjennomforing.id eq form.id.toString() }) {
            it[type] = form.type
            it[tiltakskode] = form.tiltakskode.name
            it[arrangorOrganisasjonsnummer] = form.arrangorOrganisasjonsnummer
            it[pameldingType] = form.pameldingType.name
            it[status] = form.status.name
            it[oppstart] = form.oppstart.name
            it[opprettetTidspunkt] = form.opprettetTidspunkt.toString()
            it[oppdatertTidspunkt] = form.oppdatertTidspunkt.toString()
            it[prisinformasjon] = form.prisinformasjon
            it[navn] = form.navn
            it[startDato] = form.startDato?.toString()
            it[sluttDato] = form.sluttDato?.toString()
            it[tilgjengeligForArrangorFraOgMedDato] = form.tilgjengeligForArrangorFraOgMedDato?.toString()
            it[apentForPamelding] = form.apentForPamelding?.toString()
            it[antallPlasser] = form.antallPlasser?.toString()
            it[deltidsprosent] = form.deltidsprosent?.toString()
            it[oppmoteSted] = form.oppmoteSted
            it[updatedAt] = now
        }
    }

    return updatedRows > 0
}

fun updateTiltakstype(form: TiltakstypeFormInput): Boolean {
    val now = LocalDateTime.now(ZoneOffset.UTC).toString()
    val updatedRows = DbOperations.inTransaction {
        ValpTiltakstype.update({ ValpTiltakstype.id eq form.id.toString() }) {
            it[navn] = form.navn
            it[tiltakskode] = form.tiltakskode.name
            it[innsatsgrupper] = form.innsatsgrupper.toJsonArrayText()
            it[updatedAt] = now
        }
    }

    return updatedRows > 0
}

fun GjennomforingFormInput.toFormDefaults(): GjennomforingFormDefaults {
    return GjennomforingFormDefaults(
        id = id,
        tiltakskode = tiltakskode,
        arrangorOrganisasjonsnummer = arrangorOrganisasjonsnummer,
        pameldingType = pameldingType,
        status = status,
        oppstart = oppstart,
        opprettetTidspunkt = opprettetTidspunkt.toLocalDateTime(),
        oppdatertTidspunkt = oppdatertTidspunkt.toLocalDateTime(),
        prisinformasjon = prisinformasjon,
        navn = navn.orEmpty(),
        startDato = startDato?.toString().orEmpty(),
        sluttDato = sluttDato?.toString().orEmpty(),
        tilgjengeligForArrangorFraOgMedDato = tilgjengeligForArrangorFraOgMedDato?.toString().orEmpty(),
        apentForPamelding = apentForPamelding ?: true,
        antallPlasser = antallPlasser ?: 0,
        deltidsprosent = deltidsprosent ?: 0.0,
        oppmoteSted = oppmoteSted.orEmpty(),
    )
}

fun TiltakstypeFormInput.toFormDefaults(): TiltakstypeFormDefaults {
    return TiltakstypeFormDefaults(
        id = id,
        navn = navn,
        tiltakskode = tiltakskode,
        innsatsgrupper = innsatsgrupper,
    )
}

private fun Set<InnsatsgruppeV2>.toJsonArrayText(): String {
    return joinToString(prefix = "[", postfix = "]") { "\"${it.name}\"" }
}

private fun ResultRow.toGjennomforingFormInput(): GjennomforingFormInput {
    return GjennomforingFormInput(
        id = UUID.fromString(this[ValpGjennomforing.id]),
        type = this[ValpGjennomforing.type],
        tiltakskode = enumValueOf(this[ValpGjennomforing.tiltakskode]),
        arrangorOrganisasjonsnummer = this[ValpGjennomforing.arrangorOrganisasjonsnummer],
        pameldingType = enumValueOf(this[ValpGjennomforing.pameldingType]),
        status = enumValueOf(this[ValpGjennomforing.status]),
        oppstart = enumValueOf(this[ValpGjennomforing.oppstart]),
        opprettetTidspunkt = OffsetDateTime.parse(this[ValpGjennomforing.opprettetTidspunkt]),
        oppdatertTidspunkt = OffsetDateTime.parse(this[ValpGjennomforing.oppdatertTidspunkt]),
        prisinformasjon = this[ValpGjennomforing.prisinformasjon],
        navn = this[ValpGjennomforing.navn],
        startDato = this[ValpGjennomforing.startDato]?.let(LocalDate::parse),
        sluttDato = this[ValpGjennomforing.sluttDato]?.let(LocalDate::parse),
        tilgjengeligForArrangorFraOgMedDato = this[ValpGjennomforing.tilgjengeligForArrangorFraOgMedDato]?.let(LocalDate::parse),
        apentForPamelding = this[ValpGjennomforing.apentForPamelding]?.toBooleanStrictOrNull(),
        antallPlasser = this[ValpGjennomforing.antallPlasser]?.toIntOrNull(),
        deltidsprosent = this[ValpGjennomforing.deltidsprosent]?.toDoubleOrNull(),
        oppmoteSted = this[ValpGjennomforing.oppmoteSted],
    )
}

private fun ResultRow.toTiltakstypeFormInput(): TiltakstypeFormInput {
    return TiltakstypeFormInput(
        id = UUID.fromString(this[ValpTiltakstype.id]),
        navn = this[ValpTiltakstype.navn],
        tiltakskode = enumValueOf(this[ValpTiltakstype.tiltakskode]),
        innsatsgrupper = this[ValpTiltakstype.innsatsgrupper].toInnsatsgrupperSet(),
    )
}

private fun String.toInnsatsgrupperSet(): Set<InnsatsgruppeV2> {
    return removePrefix("[")
        .removeSuffix("]")
        .split(',')
        .map { it.trim().removePrefix("\"").removeSuffix("\"") }
        .filter { it.isNotBlank() }
        .map { enumValueOf<InnsatsgruppeV2>(it) }
        .toSet()
}


