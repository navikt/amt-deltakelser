rootProject.name = "amt-deltakelser"

include(
    "amt-aktivitetskort-publisher",
    "amt-deltaker",
    "amt-deltaker-bff",
    "amt-distribusjon",
    "amt-felles:intern-api-kontrakter",
    "amt-felles:ktor",
    "amt-felles:ktor-test",
    "amt-lib:spring-boot",
    "amt-felles:archunit-test",
    "amt-felles:typegenerering",
    "amt-felles:kafka",
    "amt-felles:visningsnavn",
    "amt-lib:testing",
    "amt-lib:utils",
    "amt-lib:models",
    "amt-pdfgen",
    "amt-tiltaksarrangor-bff",
)
