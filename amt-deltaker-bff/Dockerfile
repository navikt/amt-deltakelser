FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-21
WORKDIR /app
COPY build/install/amt-deltaker-bff /app

ENV TZ="Europe/Oslo"

EXPOSE 8080
ENTRYPOINT ["/usr/bin/java"]
CMD ["-cp", "lib/*", "no.nav.amt.deltaker.bff.ApplicationKt"]