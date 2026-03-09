rootProject.name = "amt-deltakelser"

include(
    "amt-deltaker",
    "amt-deltaker-bff",
    "amt-distribusjon",
    "amt-felles:ktor",
    "amt-felles:ktor-test",
    "amt-lib:kafka",
    "amt-lib:testing",
    "amt-lib:utils",
    "amt-lib:models",
)
