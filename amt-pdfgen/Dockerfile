# Dockerfile
FROM ghcr.io/navikt/pdfgen:2.0.125-PRERELEASE-3

ENV TZ="Europe/Oslo"
ENV JAVA_TOOL_OPTIONS="--enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow"

COPY templates /app/templates
COPY fonts /app/fonts
COPY resources /app/resources
