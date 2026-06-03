package db

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.Table
import org.postgresql.util.PGobject

/**
 * Database tables for sim-nav simulators.
 * Add tables here as different simulators need dynamic data.
 */

object ValpGjennomforing : Table("valp_gjennomforing") {
    val id = text("id")
    val tiltakskode = text("tiltakskode")
    val arrangorOrganisasjonsnummer = text("arrangor_organisasjonsnummer")
    val pameldingType = text("pamelding_type")
    val status = text("status")
    val oppstart = text("oppstart")
    val opprettetTidspunkt = text("opprettet_tidspunkt")
    val oppdatertTidspunkt = text("oppdatert_tidspunkt")
    val prisinformasjon = text("prisinformasjon").nullable()
    val navn = text("navn").nullable()
    val startDato = text("start_dato").nullable()
    val sluttDato = text("slutt_dato").nullable()
    val tilgjengeligForArrangorFraOgMedDato = text("tilgjengelig_for_arrangor_fra_og_med_dato").nullable()
    val apentForPamelding = text("apent_for_pamelding").nullable()
    val antallPlasser = text("antall_plasser").nullable()
    val deltidsprosent = text("deltidsprosent").nullable()
    val oppmoteSted = text("oppmote_sted").nullable()
    val type = text("type") // "enkeltplass" or "gruppe"
    val createdAt = text("created_at")
    val updatedAt = text("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object ValpTiltakstype : Table("valp_tiltakstype") {
    val id = text("id")
    val navn = text("navn")
    val tiltakskode = text("tiltakskode")
    val innsatsgrupper = text("innsatsgrupper") // JSON-serialized list
    val createdAt = text("created_at")
    val updatedAt = text("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object VeilarboppfolgingPerson : Table("veilarboppfolging_person") {
    val fnr = text("fnr")
    val veilederIdent = text("veileder_ident")
    val oppfolgingsperioder = text("oppfolgingsperioder") // JSON-serialized list
    val erUnderManuellOppfolging = bool("er_under_manuell_oppfolging")
    val createdAt = text("created_at")
    val updatedAt = text("updated_at")

    override val primaryKey = PrimaryKey(fnr)
}

object NomRessurs : Table("nom_ressurs") {
    val navident = text("navident")
    val personident = text("personident")
    val visningsnavn = text("visningsnavn")
    val fornavn = text("fornavn")
    val etternavn = text("etternavn")
    val epost = text("epost")
    val primaryTelefon = text("primary_telefon").nullable()
    val telefon = jsonb("telefon")
    val orgTilknytning = jsonb("org_tilknytning")
    val createdAt = text("created_at")
    val updatedAt = text("updated_at")

    override val primaryKey = PrimaryKey(navident)
}

object AoOppfolgingskontorKontorTilhorighet : Table("ao_oppfolgingskontor_kontor_tilhorighet") {
    val ident = text("ident")
    val arbeidsoppfolgingKontorId = text("arbeidsoppfolging_kontor_id").nullable()
    val arbeidsoppfolgingKontorNavn = text("arbeidsoppfolging_kontor_navn").nullable()
    val createdAt = text("created_at")
    val updatedAt = text("updated_at")

    override val primaryKey = PrimaryKey(ident)
}

object VeilarbvedtaksstottePerson : Table("veilarbvedtaksstotte_person") {
    val fnr = text("fnr")
    val innsatsgruppe = text("innsatsgruppe").nullable()
    val createdAt = text("created_at")
    val updatedAt = text("updated_at")

    override val primaryKey = PrimaryKey(fnr)
}

object DokdistkanalPerson : Table("dokdistkanal_person") {
    val personident = text("personident")
    val distribusjonskanal = text("distribusjonskanal")
    val createdAt = text("created_at")
    val updatedAt = text("updated_at")

    override val primaryKey = PrimaryKey(personident)
}

private fun Table.jsonb(name: String): Column<String> {
    return this.registerColumn<String>(name, JsonbTextColumnType())
}

private class JsonbTextColumnType : ColumnType() {
    override fun sqlType(): String = "JSONB"

    override fun valueFromDB(value: Any): Any {
        return when (value) {
            is PGobject -> value.value ?: "null"
            else -> value.toString()
        }
    }

    override fun notNullValueToDB(value: Any): Any {
        return PGobject().apply {
            type = "jsonb"
            this.value = value.toString()
        }
    }
}

