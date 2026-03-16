# amt-lib

Dette er en modul hvor vi i Team Komet kan dele kode mellom backend-applikasjonene våre.

## Installasjon

Se [siste release](https://github.com/navikt/amt-deltakelser/releases) eller 
[packages](https://github.com/orgs/navikt/packages?repo_name=amt-deltakelser) for nyeste versjon.

**Gradle**
```kotlin
val amtLibVersion = "1.2026.02.28_12.19-0d67545b99df"

dependencies {
    implementation("no.nav.amt.deltakelser.lib:utils:$amtLibVersion")
    testImplementation("no.nav.amt.deltakelser.lib:testing:$amtLibVersion")
}
```

**Maven**
```xml
<dependency>
    <groupId>no.nav.amt.deltakelser.lib</groupId>
    <artifactId>utils</artifactId>
    <version>1.2026.02.28_12.19-0d67545b99df</version>
</dependency>

<dependency>
    <groupId>no.nav.amt.deltakelser.lib</groupId>
    <artifactId>testing</artifactId>
    <version>1.2026.02.28_12.19-0d67545b99df</version>
</dependency>
```
For at Gradle eller Maven skal finne pakkene, må følgende repository legges til:

**Gradle**
```kotlin
repositories {
    maven { setUrl("https://github-package-registry-mirror.gc.nav.no/cached/maven-release") }
}
```

**Maven**
```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://github-package-registry-mirror.gc.nav.no/cached/maven-release</url>
    </repository>
</repositories>
```

Det anbefales å legge GitHub Package Registry til slutt, slik at andre repositories blir søkt først for avhengigheter.

## Utvikling

### Testing
For å verifisere at biblioteket virker som forventet i andre applikasjoner lokalt, kan du publisere 
til `mavenLocal()` ved å kjøre:
```bash
./gradlew publishToMavenLocal
```

I applikasjonen må `mavenLocal()` inkluderes i `repositories`, og versjonen av amt-lib må oppdateres.
Hvis versjon ikke spesifiseres i `amt-lib.conventions.gradle.kts`, blir default-versjonen `unspecified`.

**Gradle**
```kotlin
repositories {
    mavenLocal()
}
```