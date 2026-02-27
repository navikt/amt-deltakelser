package no.nav.amt.deltaker.utils

import no.nav.amt.lib.utils.objectMapper
import org.postgresql.util.PGobject

fun toPGObject(value: Any?) = PGobject().also {
    it.type = "json"
    it.value = value?.let { v -> objectMapper.writeValueAsString(v) }
}
