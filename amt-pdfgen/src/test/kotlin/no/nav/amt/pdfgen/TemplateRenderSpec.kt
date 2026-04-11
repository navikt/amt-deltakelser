package no.nav.amt.pdfgen

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotContain
import no.nav.pdfgen.core.Environment
import no.nav.pdfgen.core.PDFGenCore
import no.nav.pdfgen.core.pdf.createHtml
import org.jsoup.Jsoup
import java.nio.file.Files
import java.nio.file.Path

class TemplateRenderSpec :
    StringSpec({

        beforeSpec { PDFGenCore.init(Environment()) }

        "alle amt-templates rendrer gyldig HTML uten handlebars-rester" {
            val templatesDir = Path.of("templates/amt")
            val dataDir = Path.of("data/amt")
            val objectMapper = jacksonObjectMapper()

            Files
                .list(templatesDir)
                .filter { it.toString().endsWith(".hbs") }
                .forEach { templatePath ->

                    val templateName = templatePath.fileName.toString().removeSuffix(".hbs")
                    val dataPath = dataDir.resolve("$templateName.json")

                    val jsonNode =
                        if (Files.exists(dataPath)) {
                            val data: Map<String, Any> = objectMapper.readValue(Files.readString(dataPath))
                            objectMapper.valueToTree(data)
                        } else {
                            objectMapper.createObjectNode()
                        }

                    val html =
                        createHtml(
                            template = templateName,
                            directoryName = "amt",
                            jsonPayload = jsonNode,
                        )

                    html.shouldNotBeNull()
                    html.shouldNotContain("{{")

                    val doc = Jsoup.parse(html)
                    doc.selectFirst("body") shouldNotBe null
                }
        }
    })
