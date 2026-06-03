package tjenester.brreg

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.server.application.*
import shared.loadJsonResource

private const val BRONNOYSUND_DATA_PATH = "/bronnoysund-data.json"
private val objectMapper = jacksonObjectMapper()

class BronnoysundSimulator {
    private val data: BronnoysundData = loadJsonResource(objectMapper, BRONNOYSUND_DATA_PATH)
    private val moderenheterByOrgNr: Map<String, Map<String, Any?>> =
        data.enheter.associateBy { it["organisasjonsnummer"].toString() }
    private val underenheterByOrgNr: Map<String, Map<String, Any?>> =
        data.underenheter.associateBy { it["organisasjonsnummer"].toString() }

    fun firstOrganisasjonsnummer(): String =
        data.enheter.firstOrNull()?.get("organisasjonsnummer")?.toString()
            ?: throw IllegalStateException("No enheter found in Bronnoysund data")

    /** Returns all enheter as (organisasjonsnummer, navn) pairs for display in dropdowns. */
    fun allEnheter(): List<Pair<String, String>> =
        data.enheter.map {
            it["organisasjonsnummer"].toString() to it["navn"].toString()
        }

    fun moderenhetOppdateringer(call: ApplicationCall): Map<String, Any> {
        val filtered = filterOppdateringer(call, data.oppdateringer.enheter)
        return mapOf("_embedded" to mapOf("oppdaterteEnheter" to filtered))
    }

    fun underenhetOppdateringer(call: ApplicationCall): Map<String, Any> {
        val filtered = filterOppdateringer(call, data.oppdateringer.underenheter)
        return mapOf("_embedded" to mapOf("oppdaterteUnderenheter" to filtered))
    }

    fun enheter(): List<Map<String, Any?>> = data.enheter

    fun underenheter(): List<Map<String, Any?>> = data.underenheter

    fun lookupModerenhet(organisasjonsnummer: String?): Map<String, Any?>? =
        moderenheterByOrgNr[organisasjonsnummer]

    fun lookupUnderenhet(organisasjonsnummer: String?): Map<String, Any?>? =
        underenheterByOrgNr[organisasjonsnummer]

    private fun filterOppdateringer(call: ApplicationCall, source: List<Oppdatering>): List<Oppdatering> {
        val fraOppdateringsId = call.request.queryParameters["oppdateringsid"]?.toIntOrNull() ?: 0
        val size = call.request.queryParameters["size"]?.toIntOrNull()?.coerceAtLeast(0) ?: source.size
        return source
            .filter { it.oppdateringsid >= fraOppdateringsId }
            .sortedBy { it.oppdateringsid }
            .take(size)
    }


    private data class BronnoysundData(
        val enheter: List<Map<String, Any?>>,
        val underenheter: List<Map<String, Any?>>,
        val oppdateringer: Oppdateringer,
    )

    private data class Oppdateringer(
        val enheter: List<Oppdatering>,
        val underenheter: List<Oppdatering>,
    )

    private data class Oppdatering(
        val oppdateringsid: Int,
        val dato: String,
        val organisasjonsnummer: String,
        val endringstype: String,
    )
}