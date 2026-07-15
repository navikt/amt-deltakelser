package no.nav.amt.deltaker.bff.typegen

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import no.nav.amt.deltaker.bff.commonresponse.DeltakerlisteResponse
import no.nav.amt.deltaker.bff.commonresponse.DeltakerlisteResponse as CommonDeltakerlisteResponse
import no.nav.amt.deltaker.bff.navtiltakskoordinator.api.response.DeltakerlisteResponse as TiltakskoordinatorDeltakerlisteResponse
import org.junit.jupiter.api.Test

class TypeDefinitionToZodTest {
    enum class Status {
        ACTIVE,
        INACTIVE,
    }

    data class SupportedExample(
        val id: Long,
        val optionalName: String?,
        val tags: List<String>,
        val metadata: Map<String, Int?>,
        val status: Status,
    )

    data class Nested(
        val name: String,
    )

    data class HasCustomType(
        val nested: Nested,
    )

    sealed interface Decision {
        object Approved : Decision

        data class Rejected(
            val reason: String,
        ) : Decision
    }

    data class HasSealedType(
        val decision: Decision,
    )

    data class RecursiveNode(
        val next: RecursiveNode?,
    )

    data class HasUnsupportedMapKey(
        val metadata: Map<Int, String>,
    )

    @Test
    fun `toZodExpression skal generere zod-uttrykk for støttede typer`() {
        val typeDefinition = KotlinTypeDefinitionParser.parse(SupportedExample::class)

        val result = TypeDefinitionToZod.toZodExpression(typeDefinition, prettyPrint = false)

        result shouldBe
            "/** no.nav.amt.deltaker.bff.typegen.TypeDefinitionToZodTest.SupportedExample */\nconst typeDefinitionToZodTest_SupportedExampleSchema = z.object({id: z.number(), metadata: z.record(z.number().nullable()), optionalName: z.string().nullable(), status: z.enum([\"ACTIVE\", \"INACTIVE\"]), tags: z.array(z.string())})"
    }

    @Test
    fun `toZodExpression skal prettyprinte som default`() {
        val typeDefinition = KotlinTypeDefinitionParser.parse(SupportedExample::class)

        val result = TypeDefinitionToZod.toZodExpression(typeDefinition)

        result shouldBe
            """
            /** no.nav.amt.deltaker.bff.typegen.TypeDefinitionToZodTest.SupportedExample */
            const typeDefinitionToZodTest_SupportedExampleSchema = z.object({
              id: z.number(),
              metadata: z.record(z.number().nullable()),
              optionalName: z.string().nullable(),
              status: z.enum(["ACTIVE", "INACTIVE"]),
              tags: z.array(z.string())
            })
            """.trimIndent()
    }

    @Test
    fun `toZodExpression skal referere custom klasse-typer ved navn`() {
        val typeDefinition = KotlinTypeDefinitionParser.parse(HasCustomType::class)

        val result = TypeDefinitionToZod.toZodExpression(typeDefinition, prettyPrint = false)
        result shouldBe
            """
            /** no.nav.amt.deltaker.bff.typegen.TypeDefinitionToZodTest.Nested */
            const typeDefinitionToZodTest_NestedSchema = z.object({name: z.string()})
            /** no.nav.amt.deltaker.bff.typegen.TypeDefinitionToZodTest.HasCustomType */
            const typeDefinitionToZodTest_HasCustomTypeSchema = z.object({nested: typeDefinitionToZodTest_NestedSchema})
            """.trimIndent()
    }

    @Test
    fun `toZodExpression skal støtte sealed typer via navngitt union schema`() {
        val typeDefinition = KotlinTypeDefinitionParser.parse(HasSealedType::class)

        val result = TypeDefinitionToZod.toZodExpression(typeDefinition, prettyPrint = false)
        result shouldBe
            """
            /** no.nav.amt.deltaker.bff.typegen.TypeDefinitionToZodTest.Decision.Approved */
            const typeDefinitionToZodTest_Decision_ApprovedSchema = z.object({})
            /** no.nav.amt.deltaker.bff.typegen.TypeDefinitionToZodTest.Decision.Rejected */
            const typeDefinitionToZodTest_Decision_RejectedSchema = z.object({reason: z.string()})
            /** no.nav.amt.deltaker.bff.typegen.TypeDefinitionToZodTest.Decision */
            const typeDefinitionToZodTest_DecisionSchema = z.union([typeDefinitionToZodTest_Decision_ApprovedSchema, typeDefinitionToZodTest_Decision_RejectedSchema])
            /** no.nav.amt.deltaker.bff.typegen.TypeDefinitionToZodTest.HasSealedType */
            const typeDefinitionToZodTest_HasSealedTypeSchema = z.object({decision: typeDefinitionToZodTest_DecisionSchema})
            """.trimIndent()
    }

    @Test
    fun `toZodExpression skal feile for map med ikke-string nøkkel`() {
        val typeDefinition = KotlinTypeDefinitionParser.parse(HasUnsupportedMapKey::class)

        val exception = shouldThrow<IllegalArgumentException> {
            TypeDefinitionToZod.toZodExpression(typeDefinition)
        }

        exception.message shouldBe "Only non-null String map keys are supported: kotlin.collections.Map<kotlin.Int, kotlin.String>"
    }

    @Test
    fun `toZodExpression skal bruke z-lazy for rekursive custom typer`() {
        val typeDefinition = KotlinTypeDefinitionParser.parse(RecursiveNode::class)

        val result = TypeDefinitionToZod.toZodExpression(typeDefinition, prettyPrint = false)
        result shouldBe "/** no.nav.amt.deltaker.bff.typegen.TypeDefinitionToZodTest.RecursiveNode */\nconst typeDefinitionToZodTest_RecursiveNodeSchema = z.object({next: z.lazy(() => typeDefinitionToZodTest_RecursiveNodeSchema).nullable()})"
    }

    @Test
    fun `toZodExpression`() {
        val typeDefinition = KotlinTypeDefinitionParser.parse(DeltakerlisteResponse::class)

        val result = TypeDefinitionToZod.toZodExpression(typeDefinition, prettyPrint = false)
        result shouldContain "/** no.nav.amt.deltaker.bff.commonresponse.DeltakerlisteResponse */"
        result shouldContain "const deltakerlisteResponseSchema = z.object({"
    }

    data class Parent(
        val child: Child,
    ) {
        data class Child(
            val value: String,
        )
    }

    @Test
    fun `toZodExpression skal bruke parent-navn for nested classes`() {
        val typeDefinition = KotlinTypeDefinitionParser.parse(Parent::class)

        val result = TypeDefinitionToZod.toZodExpression(typeDefinition, prettyPrint = false)
        result shouldContain "const typeDefinitionToZodTest_Parent_ChildSchema = z.object({value: z.string()})"
        result shouldContain "const typeDefinitionToZodTest_ParentSchema = z.object({child: typeDefinitionToZodTest_Parent_ChildSchema})"
    }

    @Test
    fun `toZodExpressions skal bruke package-prefiks ved navnekollisjon`() {
        val result = TypeDefinitionToZod.toZodExpressions(
            classes = listOf(
                CommonDeltakerlisteResponse::class,
                TiltakskoordinatorDeltakerlisteResponse::class,
            ),
            prettyPrint = false,
        )

        result shouldContain "/** no.nav.amt.deltaker.bff.commonresponse.DeltakerlisteResponse */"
        result shouldContain "const deltakerlisteResponseSchema = z.object({"
        result shouldContain "/** no.nav.amt.deltaker.bff.navtiltakskoordinator.api.response.DeltakerlisteResponse */"
        result shouldContain "const navtiltakskoordinator_api_response_DeltakerlisteResponseSchema = z.object({"
        result shouldNotContain "deltakerlisteResponseSchema_2"
    }
}
