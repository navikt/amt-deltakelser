rootProject.name = "amt-deltakelser"

include(
    "amt-deltaker",
    "amt-deltaker-bff",
    "amt-distribusjon",
    "amt-felles",
    "amt-felles:ktor",
    "amt-felles:ktor-test",
    "amt-lib:lib",
    "amt-lib:lib:kafka",
    "amt-lib:lib:testing",
    "amt-lib:lib:utils",
    "amt-lib:lib:models",
)

// Renamer moduler i amt-lib til kortere navn for å unngå lange modulnavn i avhengigheter
findProject(":amt-lib:lib:kafka")?.name = "kafka"
findProject(":amt-lib:lib:testing")?.name = "testing"
findProject(":amt-lib:lib:utils")?.name = "utils"
findProject(":amt-lib:lib:models")?.name = "models"
