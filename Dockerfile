FROM maven:3-jdk-8-alpine as compilation
WORKDIR /app
ENV MAVEN_OPTS="-XX:+TieredCompilation -XX:TieredStopAtLevel=1"
COPY pom.xml .
RUN mvn -T 1C install && rm -rf target
COPY src src
RUN mvn -T 1C -o package -DskipTests

FROM openjdk:8-jre-alpine
WORKDIR /app
COPY --from=compilation /app/target/debezium.jar .
ENTRYPOINT java -cp /app/debezium.jar io.inblocks.precedence.Debezium

ENV APPLICATION_ID ""
ENV BOOTSTRAP_SERVERS ""
ENV INPUT_TOPIC_PATTERN ""
ENV PRECEDENCE_API ""
