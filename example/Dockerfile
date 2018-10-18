FROM openjdk:8 AS builder

WORKDIR /influent-build
COPY ./build.sbt ./build.sbt
COPY ./project ./project

RUN apt-get update \
  && apt-get install apt-transport-https \
  && echo "deb https://dl.bintray.com/sbt/debian /" > /etc/apt/sources.list.d/sbt.list \
  && apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 2EE0EA64E40A89B84B2DF73499E82A75642AC823 \
  && apt-get update \
  && apt-get install sbt \
  && sbt update

COPY ./ ./
RUN sbt "project influentJavaExample" "assembly"

FROM openjdk:8

WORKDIR /influent
COPY --from=builder /influent-build/influent-java-example/target/influent-java-example.jar ./influent-java-example.jar
ENTRYPOINT ["java", "-classpath", "./influent-java-example.jar", "example.Print"]
