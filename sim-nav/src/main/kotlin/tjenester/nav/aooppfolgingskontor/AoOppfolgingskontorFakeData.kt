package tjenester.nav.aooppfolgingskontor

import DbOperations
import db.AoOppfolgingskontorKontorTilhorighet
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.LocalDateTime
import java.time.ZoneOffset

data class AoOppfolgingskontorKontorTilhorighetRow(
    val ident: String,
    val arbeidsoppfolging: ArbeidsoppfolgingFixture?,
)

data class AoOppfolgingskontorPersonidentOption(
    val personident: String,
    val label: String,
)

data class AoOppfolgingskontorNorgKontorOption(
    val kontorId: String,
    val kontorNavn: String,
    val label: String,
)

data class AoOppfolgingskontorFormDefaults(
    val ident: String,
    val arbeidsoppfolgingKontorId: String,
)

data class AoOppfolgingskontorFormInput(
    val ident: String,
    val arbeidsoppfolging: ArbeidsoppfolgingFixture?,
)

data class KontorTilhorigheterFixture(
    val arbeidsoppfolging: ArbeidsoppfolgingFixture? = null,
)

data class ArbeidsoppfolgingFixture(
    val kontorId: String,
    val kontorNavn: String,
)

fun fetchKontorTilhorigheterByIdent(ident: String): KontorTilhorigheterFixture? {
    return DbOperations.inTransaction {
        AoOppfolgingskontorKontorTilhorighet
            .selectAll()
            .firstOrNull { it[AoOppfolgingskontorKontorTilhorighet.ident] == ident }
            ?.toFixture()
    }
}

fun fetchAoOppfolgingskontorKontorTilhorigheter(): List<AoOppfolgingskontorKontorTilhorighetRow> {
    return DbOperations.inTransaction {
        AoOppfolgingskontorKontorTilhorighet
            .selectAll()
            .map { row ->
                AoOppfolgingskontorKontorTilhorighetRow(
                    ident = row[AoOppfolgingskontorKontorTilhorighet.ident],
                    arbeidsoppfolging = row.toFixture().arbeidsoppfolging,
                )
            }
            .sortedBy { it.ident }
    }
}

fun fetchAoOppfolgingskontorByIdent(ident: String): AoOppfolgingskontorFormInput? {
    return DbOperations.inTransaction {
        AoOppfolgingskontorKontorTilhorighet
            .selectAll()
            .firstOrNull { it[AoOppfolgingskontorKontorTilhorighet.ident] == ident }
            ?.toFormInput()
    }
}

fun insertAoOppfolgingskontorKontorTilhorighet(form: AoOppfolgingskontorFormInput) {
    val now = LocalDateTime.now(ZoneOffset.UTC).toString()
    DbOperations.inTransaction {
        AoOppfolgingskontorKontorTilhorighet.insert {
            it[ident] = form.ident
            it[arbeidsoppfolgingKontorId] = form.arbeidsoppfolging?.kontorId
            it[arbeidsoppfolgingKontorNavn] = form.arbeidsoppfolging?.kontorNavn
            it[createdAt] = now
            it[updatedAt] = now
        }
    }
}

fun updateAoOppfolgingskontorKontorTilhorighet(form: AoOppfolgingskontorFormInput): Boolean {
    val now = LocalDateTime.now(ZoneOffset.UTC).toString()
    val updatedRows = DbOperations.inTransaction {
        AoOppfolgingskontorKontorTilhorighet.update({ AoOppfolgingskontorKontorTilhorighet.ident eq form.ident }) {
            it[arbeidsoppfolgingKontorId] = form.arbeidsoppfolging?.kontorId
            it[arbeidsoppfolgingKontorNavn] = form.arbeidsoppfolging?.kontorNavn
            it[updatedAt] = now
        }
    }

    return updatedRows > 0
}

fun deleteAoOppfolgingskontorKontorTilhorighet(ident: String): Boolean {
    val deletedRows = DbOperations.inTransaction {
        AoOppfolgingskontorKontorTilhorighet.deleteWhere { AoOppfolgingskontorKontorTilhorighet.ident eq ident }
    }

    return deletedRows > 0
}

fun defaultAoOppfolgingskontorFormDefaults(personidentOptions: List<AoOppfolgingskontorPersonidentOption>): AoOppfolgingskontorFormDefaults {
    return AoOppfolgingskontorFormDefaults(
        ident = personidentOptions.firstOrNull()?.personident.orEmpty(),
        arbeidsoppfolgingKontorId = "",
    )
}

fun AoOppfolgingskontorFormInput.toFormDefaults(): AoOppfolgingskontorFormDefaults {
    return AoOppfolgingskontorFormDefaults(
        ident = ident,
        arbeidsoppfolgingKontorId = arbeidsoppfolging?.kontorId.orEmpty(),
    )
}

private fun ResultRow.toFixture(): KontorTilhorigheterFixture {
    val kontorId = this[AoOppfolgingskontorKontorTilhorighet.arbeidsoppfolgingKontorId]
    val kontorNavn = this[AoOppfolgingskontorKontorTilhorighet.arbeidsoppfolgingKontorNavn]

    val arbeidsoppfolging = if (kontorId != null && kontorNavn != null) {
        ArbeidsoppfolgingFixture(
            kontorId = kontorId,
            kontorNavn = kontorNavn,
        )
    } else {
        null
    }

    return KontorTilhorigheterFixture(arbeidsoppfolging = arbeidsoppfolging)
}

private fun ResultRow.toFormInput(): AoOppfolgingskontorFormInput {
    return AoOppfolgingskontorFormInput(
        ident = this[AoOppfolgingskontorKontorTilhorighet.ident],
        arbeidsoppfolging = this.toFixture().arbeidsoppfolging,
    )
}


