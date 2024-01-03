FROM clojure:lein AS builder
MAINTAINER Kiba Gateaux the_anonymous_hash+masterdjinn@proton.me

COPY . /usr/src/app
WORKDIR /usr/src/app

RUN apt-get -y update; apt-get -y install curl;
RUN curl -o honeycomb.jar -L https://github.com/honeycombio/honeycomb-opentelemetry-java/releases/latest/download/honeycomb-opentelemetry-javaagent.jar

RUN lein uberjar

FROM clojure:lein
# if prod deployment
EXPOSE 80
# if dev deployment
EXPOSE 8888

# RUN mv "$(lein uberjar | sed -n 's/^Created \(.*standalone\.jar\)/\1/p')" app.jar
ARG JAR_FILE="/usr/src/app/target/master-djinn-0.0.1-SNAPSHOT-standalone.jar"
WORKDIR /usr/src/app
COPY --from=builder $JAR_FILE ./app.jar
COPY --from=builder /usr/src/app/honeycomb.jar ./honeycomb.jar

# https://stackoverflow.com/questions/57885828/netty-cannot-access-class-jdk-internal-misc-unsafe
CMD ["java", "-javaagent:honeycomb.jar", "--add-opens", "java.base/jdk.internal.misc=ALL-UNNAMED", "--illegal-access=permit", "-Dio.netty.tryReflectionSetAccessible=true", "-jar", "./app.jar"]
