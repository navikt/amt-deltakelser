import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import db.VeilarboppfolgingPerson
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.time.ZoneOffset

private val veilarboppfolgingObjectMapper = jacksonObjectMapper().findAndRegisterModules()

data class VeilarboppfolgingPersonRow(
    val fnr: String,
    val veilederIdent: String,
    val oppfolgingsperioder: List<OppfolgingsperiodeFixture>,
    val erUnderManuellOppfolging: Boolean,
)

data class VeilarboppfolgingPersonFormDefaults(
    val fnr: String,
    val veilederIdent: String,
    val oppfolgingsperioderJson: String,
    val erUnderManuellOppfolging: Boolean,
)

data class VeilarboppfolgingPersonFormInput(
    val fnr: String,
    val veilederIdent: String,
    val oppfolgingsperioder: List<OppfolgingsperiodeFixture>,
    val erUnderManuellOppfolging: Boolean,
)

data class VeilarboppfolgingPersonFixture(
    val veilederIdent: String,
    val oppfolgingsperioder: List<OppfolgingsperiodeFixture>,
    val erUnderManuellOppfolging: Boolean = false,
)

data class OppfolgingsperiodeFixture(
    val uuid: String,
    val startDato: String,
    val sluttDato: String?,
)

fun fetchVeilarboppfolgingPersons(): List<VeilarboppfolgingPersonRow> {
    return DbOperations.inTransaction {
        VeilarboppfolgingPerson.selectAll()
            .map { it.toPersonRow() }
            .sortedBy { it.fnr }
    }
}

fun fetchVeilarboppfolgingPersonByFnr(fnr: String): VeilarboppfolgingPersonFixture? {
    return DbOperations.inTransaction {
        VeilarboppfolgingPerson.selectAll()
            .firstOrNull { it[VeilarboppfolgingPerson.fnr] == fnr }
            ?.toPersonRow()
            ?.toFixture()
    }
}

fun insertVeilarboppfolgingPerson(form: VeilarboppfolgingPersonFormInput) {
    val now = LocalDateTime.now(ZoneOffset.UTC).toString()
    DbOperations.inTransaction {
        VeilarboppfolgingPerson.insert {
            it[fnr] = form.fnr
            it[veilederIdent] = form.veilederIdent
            it[oppfolgingsperioder] = veilarboppfolgingObjectMapper.writeValueAsString(form.oppfolgingsperioder)
            it[erUnderManuellOppfolging] = form.erUnderManuellOppfolging
            it[createdAt] = now
            it[updatedAt] = now
        }
    }
}

fun updateVeilarboppfolgingPerson(form: VeilarboppfolgingPersonFormInput): Boolean {
    val now = LocalDateTime.now(ZoneOffset.UTC).toString()
    val updatedRows = DbOperations.inTransaction {
        VeilarboppfolgingPerson.update({ VeilarboppfolgingPerson.fnr eq form.fnr }) {
            it[veilederIdent] = form.veilederIdent
            it[oppfolgingsperioder] = veilarboppfolgingObjectMapper.writeValueAsString(form.oppfolgingsperioder)
            it[erUnderManuellOppfolging] = form.erUnderManuellOppfolging
            it[updatedAt] = now
        }
    }

    return updatedRows > 0
}

fun deleteVeilarboppfolgingPerson(fnr: String): Boolean {
    val deletedRows = DbOperations.inTransaction {
        VeilarboppfolgingPerson.deleteWhere { VeilarboppfolgingPerson.fnr eq fnr }
    }

    return deletedRows > 0
}

fun VeilarboppfolgingPersonRow.toFormDefaults(): VeilarboppfolgingPersonFormDefaults {
    return VeilarboppfolgingPersonFormDefaults(
        fnr = fnr,
        veilederIdent = veilederIdent,
        oppfolgingsperioderJson = veilarboppfolgingObjectMapper
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(oppfolgingsperioder),
        erUnderManuellOppfolging = erUnderManuellOppfolging,
    )
}

private fun VeilarboppfolgingPersonRow.toFixture(): VeilarboppfolgingPersonFixture {
    return VeilarboppfolgingPersonFixture(
        veilederIdent = veilederIdent,
        oppfolgingsperioder = oppfolgingsperioder,
        erUnderManuellOppfolging = erUnderManuellOppfolging,
    )
}

private fun ResultRow.toPersonRow(): VeilarboppfolgingPersonRow {
    return VeilarboppfolgingPersonRow(
        fnr = this[VeilarboppfolgingPerson.fnr],
        veilederIdent = this[VeilarboppfolgingPerson.veilederIdent],
        oppfolgingsperioder = veilarboppfolgingObjectMapper.readValue(this[VeilarboppfolgingPerson.oppfolgingsperioder]),
        erUnderManuellOppfolging = this[VeilarboppfolgingPerson.erUnderManuellOppfolging],
    )
}

