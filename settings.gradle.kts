rootProject.name = "amt-deltakelser"

include(
    "amt-deltaker",
    "amt-deltaker-bff",
    "amt-distribusjon",
    "amt-felles:bff-deltaker-kontrakt",
    "amt-felles:ktor",
    "amt-felles:ktor-test",
    "amt-felles:kafka",
    "amt-lib:testing",
    "amt-lib:utils",
    "amt-lib:models",
    "amt-tiltaksarrangor-bff",
)
