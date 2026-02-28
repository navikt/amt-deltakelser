import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

data class VersionCatalogWrapper(
    private val libs: VersionCatalog,
) {
    fun getLibrary(libName: String): MinimalExternalModuleDependency =
        libs
            .findLibrary(libName)
            .orElseThrow { IllegalArgumentException("Library '$libName' not found in version catalog") }
            .get()

    fun getBundle(bundleName: String): List<MinimalExternalModuleDependency> =
        libs
            .findBundle(bundleName)
            .orElseThrow { IllegalArgumentException("Bundle '$bundleName' not found in version catalog") }
            .get()

    fun getVersion(versionName: String): String =
        libs
            .findVersion(versionName)
            .orElseThrow { IllegalArgumentException("Version '$versionName' not found in version catalog") }
            .toString()

    companion object {
        private const val LIBS_KEY = "libs"

        fun fromProject(project: Project): VersionCatalogWrapper =
            VersionCatalogWrapper(
                project.extensions
                    .getByType<VersionCatalogsExtension>()
                    .named(LIBS_KEY),
            )
    }
}
