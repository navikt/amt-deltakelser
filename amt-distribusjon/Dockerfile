FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-21
WORKDIR /app
COPY build/install/amt-distribusjon /app
ENV TZ="Europe/Oslo"
EXPOSE 8080
ENTRYPOINT ["/usr/bin/java"]
CMD ["-Xms256m", "-Xmx1024m", "-cp", "lib/*", "no.nav.amt.distribusjon.ApplicationKt"]
