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

        // Override vulnerable versions selected by the Spring Boot BOM.
        val logbackVersion = versionCatalog.findVersion("logback-version").get().requiredVersion
        dependency("ch.qos.logback:logback-classic:$logbackVersion")
        dependency("ch.qos.logback:logback-core:$logbackVersion")
        dependency("org.json:json:${versionCatalog.findVersion("org-json-version").get().requiredVersion}")
    }
}
