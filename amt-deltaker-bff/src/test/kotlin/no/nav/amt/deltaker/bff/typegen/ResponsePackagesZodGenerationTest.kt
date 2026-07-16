package no.nav.amt.deltaker.bff.typegen

import com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import no.nav.amt.deltaker.bff.architecture.responsePakker
import no.nav.amt.felles.typegenerering.TypeDefinitionToZod
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * TODO: ta i bruk genererte typer i frontend
 */
class ResponsePackagesZodGenerationTest {
    @Disabled("Manuell generator for konsolidert Zod; køyr lokalt ved behov")
    @Test
    fun `genererer samlet zod-definisjon for alle response-pakker`() {
        val importedClasses = ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .importPackages("no.nav.amt.deltaker.bff")

        val responseClasses = importedClasses
            .filter { javaClass ->
                resideInAnyPackage(*responsePakker).test(javaClass) && shouldIncludeInGeneration(javaClass.name)
            }.mapNotNull { javaClass ->
                runCatching { javaClass.reflect().kotlin }.getOrNull()
            }.distinct()
            .sortedBy { it.qualifiedName ?: it.simpleName ?: "" }

        val zodOutput = TypeDefinitionToZod.toZodExpressions(responseClasses, prettyPrint = true)

        println("Generated consolidated Zod for ${responseClasses.size} classes from response packages:")
        println(zodOutput)
    }

    private fun shouldIncludeInGeneration(className: String): Boolean {
        if (className.endsWith("Kt")) return false
        if (className.contains("\$Companion")) return false
        if (className.contains("\$WhenMappings")) return false
        if (className.contains("$$")) return false
        if (className.substringAfterLast('.').matches(Regex(".*\\$\\d+$"))) return false
        return true
    }
}
