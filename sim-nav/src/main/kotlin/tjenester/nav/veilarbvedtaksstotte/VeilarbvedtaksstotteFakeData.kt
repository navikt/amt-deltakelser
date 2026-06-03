package tjenester.nav.veilarbvedtaksstotte

import DbOperations
import db.VeilarbvedtaksstottePerson
import no.nav.amt.lib.models.deltaker.InnsatsgruppeV2
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.LocalDateTime
import java.time.ZoneOffset

data class VeilarbvedtaksstottePersonRow(
    val fnr: String,
    val innsatsgruppe: InnsatsgruppeV2?,
)

data class VeilarbvedtaksstottePersonFormDefaults(
    val fnr: String,
    val innsatsgruppe: InnsatsgruppeV2?,
)

data class VeilarbvedtaksstottePersonFormInput(
    val fnr: String,
    val innsatsgruppe: InnsatsgruppeV2?,
)

fun fetchVeilarbvedtaksstottePersons(): List<VeilarbvedtaksstottePersonRow> {
    return DbOperations.inTransaction {
        VeilarbvedtaksstottePerson.selectAll()
            .map { it.toPersonRow() }
            .sortedBy { it.fnr }
    }
}

fun fetchVeilarbvedtaksstottePersonByFnr(fnr: String): VeilarbvedtaksstottePersonRow? {
    return DbOperations.inTransaction {
        VeilarbvedtaksstottePerson.selectAll()
            .firstOrNull { it[VeilarbvedtaksstottePerson.fnr] == fnr }
            ?.toPersonRow()
    }
}

fun insertVeilarbvedtaksstottePerson(form: VeilarbvedtaksstottePersonFormInput) {
    val now = LocalDateTime.now(ZoneOffset.UTC).toString()
    DbOperations.inTransaction {
        VeilarbvedtaksstottePerson.insert {
            it[fnr] = form.fnr
            it[innsatsgruppe] = form.innsatsgruppe?.name
            it[createdAt] = now
            it[updatedAt] = now
        }
    }
}

fun updateVeilarbvedtaksstottePerson(form: VeilarbvedtaksstottePersonFormInput): Boolean {
    val now = LocalDateTime.now(ZoneOffset.UTC).toString()
    val updatedRows = DbOperations.inTransaction {
        VeilarbvedtaksstottePerson.update({ VeilarbvedtaksstottePerson.fnr eq form.fnr }) {
            it[innsatsgruppe] = form.innsatsgruppe?.name
            it[updatedAt] = now
        }
    }
    return updatedRows > 0
}

fun deleteVeilarbvedtaksstottePerson(fnr: String): Boolean {
    val deletedRows = DbOperations.inTransaction {
        VeilarbvedtaksstottePerson.deleteWhere { VeilarbvedtaksstottePerson.fnr eq fnr }
    }
    return deletedRows > 0
}

fun VeilarbvedtaksstottePersonRow.toFormDefaults(): VeilarbvedtaksstottePersonFormDefaults {
    return VeilarbvedtaksstottePersonFormDefaults(
        fnr = fnr,
        innsatsgruppe = innsatsgruppe,
    )
}

private fun ResultRow.toPersonRow(): VeilarbvedtaksstottePersonRow {
    return VeilarbvedtaksstottePersonRow(
        fnr = this[VeilarbvedtaksstottePerson.fnr],
        innsatsgruppe = this[VeilarbvedtaksstottePerson.innsatsgruppe]?.let { enumValueOf(it) },
    )
}

