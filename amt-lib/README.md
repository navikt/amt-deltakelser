# amt-lib

Dette er et repository hvor vi i Komet kan dele kode i mellom backend appene våre.

## Installasjon
Se [siste release](https://github.com/navikt/amt-lib/releases) eller [packages](https://github.com/orgs/navikt/packages?repo_name=amt-lib) for nyeste versjon.

**Gradle**
```kotlin
val amtLibVersion = "1.2024.06.03_12.31-800644a77a1b"

dependencies {
    implementation("no.nav.amt.lib:kafka:$amtLibVersion")
    implementation("no.nav.amt.lib:utils:$amtLibVersion")
    testImplementation("no.nav.amt.lib:testing:$amtLibVersion")
}
```

**Maven**
```xml
<dependency>
  <groupId>no.nav.amt.lib</groupId>
  <artifactId>utils</artifactId>
  <version>1.2024.06.03_12.31-800644a77a1b</version>
</dependency>

<dependency>
    <groupId>no.nav.amt.lib</groupId>
    <artifactId>kafka</artifactId>
    <version>1.2024.06.03_12.31-800644a77a1b</version>
</dependency>

<dependency>
    <groupId>no.nav.amt.lib</groupId>
    <artifactId>testing</artifactId>
    <version>1.2024.06.03_12.31-800644a77a1b</version>
</dependency>
```
For at Gradle eller Maven skal finne pakkene må man legge til følgende repository:

**Gradle**
```kotlin
repositories {
    ...
    maven { setUrl("https://github-package-registry-mirror.gc.nav.no/cached/maven-release") }
}
```

**Maven**
```xml
<repositories>
    ...
    <repository>
        <id>github</id>
        <url>https://github-package-registry-mirror.gc.nav.no/cached/maven-release</url>
    </repository>
</repositories>
```

Det er anbefalt å legge til GitHub Package Registry til slutt for å først søke igjennom andre repositories for avhengigheter.

## Utvikling
### Testing
For å verifisere at biblioteket virker som forventet i andre apper lokalt kan man publisere til `mavenLocal()` ved å kjøre:
```sh
./gradlew publishToMavenLocal
```

I appen må man inkludere `mavenLocal()` i `repositories` samt endre versjonen av amt-lib. Hvis man ikke spesifiserer en versjon i `amt-lib.conventions.gradle.kts` blir default versjon `unspecified`:

**Gradle**
```kotlin
repositories {
    mavenLocal()
    ...
}
```