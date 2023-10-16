# default pedestal template build
# FROM openjdk:8-alpine
# MAINTAINER Your Name <you@example.com>
# ADD target/master-djinn-0.0.1-SNAPSHOT-standalone.jar /master-djinn/app.jar
# EXPOSE 8080
# CMD ["java", "-jar", "/master-djinn/app.jar"]



# FROM openjdk:18-alpine
# lein has CMD ["lein", "repl"], override it
FROM clojure:openjdk-18-lein-2.9.6
MAINTAINER Kiba Gateaux perfectloyalty2+masterdjinn@proton.me

ARG ACTIVITYDB_URI
ARG ACTIVITYDB_USER
ARG ACTIVITYDB_PW
ARG IDENTITYDB_URI
ARG IDENTITYDB_USER
ARG IDENTITYDB_PW
ARG SPOTIFY_CLIENT_ID
ARG SPOTIFY_CLIENT_SECRET
# Note that Environ automatically lowercases keys, and replaces the characters "_" and "." with "-". The environment variable DATABASE_URL and the system property database.url are therefore both converted to the same keyword :database-url.

ENV ACTIVITYDB_URI=${ACTIVITYDB_URI}
ENV ACTIVITYDB_USER=${ACTIVITYDB_USER}
ENV ACTIVITYDB_PW=${ACTIVITYDB_PW}
ENV IDENTITYDB_URI=${IDENTITYDB_URI}
ENV IDENTITYDB_USER=${IDENTITYDB_USER}
ENV IDENTITYDB_PW=${IDENTITYDB_PW}
ENV SPOTIFY_CLIENT_ID=${SPOTIFY_CLIENT_ID}
ENV SPOTIFY_CLIENT_SECRET=${SPOTIFY_CLIENT_SECRET}

# assume env vars will remain constant over time but app version will update consistenyl
# put JAR_FILE last to keep cached layers
ARG JAR_FILE

RUN lein uberjar
## TODO make target path dynmic or catch all for different version numbers
# ADD target/master-djinn-0.0.1-SNAPSHOT-standalone.jar /master-djinn/app.jar
ADD ${ARG JAR_FILE} /master-djinn/app.jar
CMD ["java", "-jar", " /master-djinn/app.jar"]
