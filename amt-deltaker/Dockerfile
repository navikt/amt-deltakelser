FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25
WORKDIR /app
COPY build/install/amt-deltaker /app

ENV TZ="Europe/Oslo"
ENV JAVA_TOOL_OPTIONS="--enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow"

EXPOSE 8080
ENTRYPOINT ["/usr/bin/java"]
CMD ["-cp", "lib/*", "no.nav.amt.deltaker.ApplicationKt"]
