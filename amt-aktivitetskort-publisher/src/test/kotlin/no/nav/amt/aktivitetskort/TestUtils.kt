package no.nav.amt.aktivitetskort

import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonObjectMapper

object TestUtils {
    val staticObjectMapper: ObjectMapper = jacksonObjectMapper()
}
