package no.nav.amt.pdfgen.util

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.matchers.nulls.shouldNotBeNull
import no.nav.pdfgen.core.pdf.createHtml
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.time.LocalDate
import java.time.Year
import java.time.format.DateTimeFormatter

object RenderUtils {
    val fixedDate: LocalDate = LocalDate.of(Year.now().value, 2, 15)

    private val dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    fun LocalDate.toNorwegianShortDate(): String = this.format(dateFormat)

    private const val DEFAULT_TEMPLATE_DIR = "amt"
    private const val SECTIONS_TEMPLATE_DIR = "sections"

    fun render(
        templateName: String,
        payload: Any,
        templateDir: String = DEFAULT_TEMPLATE_DIR,
    ): Document =
        Jsoup.parse(
            createHtml(
                template = templateName,
                directoryName = templateDir,
                jsonPayload = objectMapper.valueToTree<JsonNode>(payload),
            ).shouldNotBeNull(),
        )

    fun renderSection(
        templateName: String,
        payload: Any,
        templateDir: String = SECTIONS_TEMPLATE_DIR,
    ): Document =
        render(
            templateName = templateName,
            payload = payload,
            templateDir = templateDir,
        )

    fun Document.sectionText(): String = select("section").joinToString(" ") { it.text() }

    private val objectMapper =
        jacksonObjectMapper().apply {
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        }
}
