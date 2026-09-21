package no.nav.tiltaksarrangor

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.resilience.annotation.EnableResilientMethods

@SpringBootApplication
@EnableResilientMethods
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}
