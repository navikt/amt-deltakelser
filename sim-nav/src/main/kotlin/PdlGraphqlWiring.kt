import graphql.GraphQL
import graphql.schema.GraphQLSchema
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import graphql.scalars.ExtendedScalars

private const val PDL_SCHEMA_RESOURCE = "/pdl.graphqls"

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
        .scalar(ExtendedScalars.Date)
        .scalar(ExtendedScalars.DateTime)
        .scalar(ExtendedScalars.GraphQLLong)
        .type("Query") { typeWiring ->
            typeWiring
                .dataFetcher("hentPerson") { null }
                .dataFetcher("hentIdenter") { null }
        }
        .build()

    return SchemaGenerator().makeExecutableSchema(typeDefinitionRegistry, runtimeWiring)
}

private fun loadPdlTypeDefinitions() =
    object {}.javaClass.getResourceAsStream(PDL_SCHEMA_RESOURCE)?.use { stream ->
        SchemaParser().parse(stream.reader())
    } ?: error("Missing schema resource: $PDL_SCHEMA_RESOURCE")




