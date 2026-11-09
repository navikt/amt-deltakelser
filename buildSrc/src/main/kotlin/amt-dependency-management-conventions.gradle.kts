import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("io.spring.dependency-management")
}

val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencyManagement {
    dependencies {
        dependency("com.google.guava:guava:${versionCatalog.findVersion("guava-version").get().requiredVersion}")
        dependency("at.yawk.lz4:lz4-java:${versionCatalog.findVersion("lz4-java-version").get().requiredVersion}")
        dependency("org.xerial.snappy:snappy-java:${versionCatalog.findVersion("snappy-java-version").get().requiredVersion}")
    }
}
