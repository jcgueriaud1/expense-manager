FROM ghcr.io/jqlang/jq:latest AS jq-stage

FROM eclipse-temurin:25-jdk AS build
COPY --from=jq-stage /jq /usr/bin/jq
# Test that jq works after copying
RUN jq --version

ENV HOME=/app
RUN mkdir -p $HOME
WORKDIR $HOME
COPY . $HOME

# If you have a Vaadin Pro key, pass it as a secret with id "proKey":
#
#   $ docker build --secret id=proKey,src=$HOME/.vaadin/proKey .
#
# If you have a Vaadin Offline key, pass it as a secret with id "offlineKey":
#
#   $ docker build --secret id=offlineKey,src=$HOME/.vaadin/offlineKey .

RUN --mount=type=cache,target=/root/.m2 \
    --mount=type=secret,id=proKey \
    --mount=type=secret,id=offlineKey \
    sh -c 'PRO_KEY=$(jq -r ".proKey // empty" /run/secrets/proKey 2>/dev/null || echo "") && \
    OFFLINE_KEY=$(cat /run/secrets/offlineKey 2>/dev/null || echo "") && \
    ./mvnw clean package -DskipTests -Dvaadin.proKey=${PRO_KEY} -Dvaadin.offlineKey=${OFFLINE_KEY}'

FROM eclipse-temurin:25-jre-alpine
COPY --from=build /app/target/*.jar app.jar
# MaxRAMPercentage lets the heap use most of the container's memory limit;
# without it Temurin caps the heap at ~25% of the limit, which OOM-kills this
# Spring Boot + Vaadin app during startup under Shepherd's runtime memory quota.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70.0", "-jar", "/app.jar", "--spring.profiles.active=vherd"]
