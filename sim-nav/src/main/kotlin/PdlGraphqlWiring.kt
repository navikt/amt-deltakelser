import graphql.GraphQL
import graphql.GraphQLContext
import graphql.execution.CoercedVariables
import graphql.language.IntValue
import graphql.language.StringValue
import graphql.language.Value
import graphql.schema.Coercing
import graphql.schema.CoercingParseLiteralException
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLSchema
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeRuntimeWiring
import java.util.Locale

private const val PDL_SCHEMA_RESOURCE = "/pdl.graphqls"

private val dateScalar: GraphQLScalarType = passthroughStringScalar("Date")
private val dateTimeScalar: GraphQLScalarType = passthroughStringScalar("DateTime")
private val longScalar: GraphQLScalarType = GraphQLScalarType.newScalar()
    .name("Long")
    .description("64-bit signed integer")
    .coercing(
        object : Coercing<Long, Long> {
            override fun serialize(dataFetcherResult: Any, graphQLContext: GraphQLContext, locale: Locale): Long =
                coerceLong(dataFetcherResult) ?: throw CoercingSerializeException("Expected Long-compatible value")

            override fun parseValue(input: Any, graphQLContext: GraphQLContext, locale: Locale): Long =
                coerceLong(input) ?: throw CoercingParseValueException("Expected Long-compatible value")

            override fun parseLiteral(
                input: Value<*>,
                variables: CoercedVariables,
                graphQLContext: GraphQLContext,
                locale: Locale,
            ): Long =
                when (input) {
                    is IntValue -> input.value.longValueExact()
                    is StringValue -> input.value.toLongOrNull()
                    else -> null
                } ?: throw CoercingParseLiteralException("Expected IntValue or numeric StringValue")
        },
    )
    .build()

/**
 * Minimal schema-first GraphQL-java setup for PDL.
 *
 * Current data fetchers are placeholders and will be replaced when the new PdlFake implementation lands.
 */
fun createPdlGraphql(): GraphQL {
    val executableSchema = createPdlExecutableSchema()
    return GraphQL.newGraphQL(executableSchema).build()
}

fun createPdlExecutableSchema(): GraphQLSchema {
    val typeDefinitionRegistry = loadPdlTypeDefinitions()

    val runtimeWiring = RuntimeWiring.newRuntimeWiring()
        .scalar(dateScalar)
        .scalar(dateTimeScalar)
        .scalar(longScalar)
        .type(
            TypeRuntimeWiring.newTypeWiring("Query")
                .dataFetcher("hentPerson") { null }
                .dataFetcher("hentIdenter") { null },
        )
        .build()

    return SchemaGenerator().makeExecutableSchema(typeDefinitionRegistry, runtimeWiring)
}

private fun loadPdlTypeDefinitions() =
    object {}.javaClass.getResourceAsStream(PDL_SCHEMA_RESOURCE)?.use { stream ->
        SchemaParser().parse(stream.reader())
    } ?: error("Missing schema resource: $PDL_SCHEMA_RESOURCE")

private fun passthroughStringScalar(name: String): GraphQLScalarType = GraphQLScalarType.newScalar()
    .name(name)
    .coercing(
        object : Coercing<String, String> {
            override fun serialize(dataFetcherResult: Any, graphQLContext: GraphQLContext, locale: Locale): String =
                (dataFetcherResult as? String) ?: throw CoercingSerializeException("Expected String value")

            override fun parseValue(input: Any, graphQLContext: GraphQLContext, locale: Locale): String =
                (input as? String) ?: throw CoercingParseValueException("Expected String value")

            override fun parseLiteral(
                input: Value<*>,
                variables: CoercedVariables,
                graphQLContext: GraphQLContext,
                locale: Locale,
            ): String =
                (input as? StringValue)?.value ?: throw CoercingParseLiteralException("Expected StringValue")
        },
    )
    .build()

private fun coerceLong(value: Any): Long? = when (value) {
    is Long -> value
    is Int -> value.toLong()
    is Number -> value.toLong()
    is String -> value.toLongOrNull()
    else -> null
}



