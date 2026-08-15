# Stage 1: Build
FROM gradle:8.5-jdk21 AS build
WORKDIR /app
COPY gradlew gradlew
COPY gradle/ gradle/
COPY build.gradle.kts settings.gradle.kts ./
RUN ./gradlew dependencies --no-daemon || true
COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080

# ── Memory tuning ────────────────────────────────────────────────────────
# The previous flags only capped the JVM HEAP (-Xmx350m). That's not the same
# as capping the container's total memory: Render kills the container based
# on its whole resident memory (RSS), which also includes Metaspace (class
# metadata - unbounded by default), thread stacks (default ~1MB EACH, and
# Tomcat defaults to up to 200 worker threads = up to 200MB on its own),
# direct/off-heap buffers (used by the Lettuce Redis client - also unbounded
# by default), and JIT code cache. With only -Xmx set, all of those were free
# to grow without limit, so the process could - and did - exceed the
# container's real memory ceiling even though the heap itself was "fine".
# No java.lang.OutOfMemoryError ever showed up in the app's own logs (which
# is exactly what you'd expect if the heap were the problem) - Render killed
# the whole process from outside, which matches this precisely.
#
# Fix: bound EVERY region, not just the heap, and size Tomcat/Hikari to
# match a small container instead of Spring Boot's desktop-sized defaults.
# -XX:MaxRAMPercentage (instead of a fixed -Xmx) makes the heap cap scale
# automatically with whatever instance size Render gives this service in the
# future, while still leaving headroom for the non-heap regions below.
ENTRYPOINT ["java", \
    "-XX:MaxRAMPercentage=50.0", \
    "-XX:InitialRAMPercentage=25.0", \
    "-XX:MaxMetaspaceSize=224m", \
    "-XX:CompressedClassSpaceSize=48m", \
    "-XX:ReservedCodeCacheSize=48m", \
    "-XX:MaxDirectMemorySize=48m", \
    "-Xss512k", \
    "-XX:+UseSerialGC", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-XX:+HeapDumpOnOutOfMemoryError", \
    "-XX:HeapDumpPath=/tmp/heapdump.hprof", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]