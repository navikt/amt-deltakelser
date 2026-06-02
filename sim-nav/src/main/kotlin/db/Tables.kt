import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * Database tables for sim-nav simulators.
 * Add tables here as different simulators need dynamic data.
 */

object ValpGjennomforing : Table("valp_gjennomforing") {
    val id = text("id").primaryKey()
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
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
}

object ValpTiltakstype : Table("valp_tiltakstype") {
    val id = text("id").primaryKey()
    val navn = text("navn")
    val tiltakskode = text("tiltakskode")
    val innsatsgrupper = text("innsatsgrupper") // JSON-serialized list
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
}

