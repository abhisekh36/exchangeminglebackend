plugins {
	kotlin("jvm") version "2.2.21"
	kotlin("plugin.spring") version "2.2.21"
	id("org.springframework.boot") version "3.4.1"
	id("io.spring.dependency-management") version "1.1.7"
	kotlin("plugin.jpa") version "2.2.21"
}

group = "com.exchangemingle"
version = "0.0.1-SNAPSHOT"
description = "Backend API for ExchangeMingle - Skill Exchange Platform"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

configurations {
	compileOnly {
		extendsFrom(configurations.annotationProcessor.get())
	}
}

repositories {
	mavenCentral()
}

dependencies {

	// ✅ LiveKit Server SDK (token generation)
	implementation("io.livekit:livekit-server:0.6.1")

	// OAuth2 for Google Login
	implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
	implementation("com.google.api-client:google-api-client:2.2.0")
	implementation("com.google.auth:google-auth-library-oauth2-http:1.19.0")




	// Spring Boot Starters
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-web")

	// Kotlin
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

	// Database
	runtimeOnly("org.postgresql:postgresql")

	// DevTools
	developmentOnly("org.springframework.boot:spring-boot-devtools")

	// Lombok (optional, can remove if not needed)
	compileOnly("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok")

	// JWT (Modern version 0.12.3)
	implementation("io.jsonwebtoken:jjwt-api:0.12.3")
	runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.3")
	runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.3")

	// Firebase Admin SDK
	implementation("com.google.firebase:firebase-admin:9.2.0")

	// Email Support
	implementation("org.springframework.boot:spring-boot-starter-mail")

	// Kotlin Coroutines
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.7.3")

	// Testing
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.security:spring-security-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")




	// ✅ Cloudinary (INSTEAD OF AWS S3)
	implementation("com.cloudinary:cloudinary-http44:1.36.0")

	// ✅ Redis
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	implementation("io.lettuce:lettuce-core:6.2.6.RELEASE")

	// ✅ Caffeine Cache
	implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
	implementation("org.springframework.boot:spring-boot-starter-cache")

	// ✅ Bucket4j Rate Limiting
	implementation("com.bucket4j:bucket4j-core:8.5.0")
	implementation("com.bucket4j:bucket4j-redis:8.5.0")

	// ✅ Spring Boot Admin
	implementation("de.codecentric:spring-boot-admin-starter-server:3.2.1")
	implementation("de.codecentric:spring-boot-admin-starter-client:3.2.1")

	// ✅ Springdoc OpenAPI
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0")

	// ✅ WebClient for Brevo API
	implementation("org.springframework.boot:spring-boot-starter-webflux")
}


kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
	}
}

allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
	useJUnitPlatform()
}