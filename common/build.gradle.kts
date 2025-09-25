// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
// no-import

plugins { id("org.jetbrains.kotlinx.kover") }

// Dependencies version

// Required versions
val jacksonAnnotationsVersion = "2.20"
val jacksonDatabindVersion = "2.20.0"
val jacksonKotlinVersion = "2.20.0"
val springWebVersion = "6.2.10"
val springBootVersion = "3.5.3"
val bouncyCastleJdk18Version = "1.81"

// Implementation
val swaggerParserVersion = "2.1.33"
val hashidsVersion = "1.0.3"
val springOauthAutoConfigureVersion = "2.6.8"
val springSecurityJwtVersion = "1.1.1.RELEASE"
val springDocVersion = "2.8.12"
val springOauthVersion = "6.5.3"
val servletApiVersion = "6.1.0"
val tikaVersion = "3.2.2"
val redisOMVersion = "1.0.0"
val kotlinCoroutinesCoreVersion = "1.10.2"

// Checks
val detektVersion = "1.23.8"

// Tests
val jUnitBomVersion = "5.13.4"
val mockkVersion = "1.14.5"
val awaitilityKVersion = "4.3.0"
val testContainersRedisVersion = "1.6.4"
val testContainersPostgreSQLVersion = "1.21.3"
val testContainersLocalStackVersion = "1.21.3"

dependencies {
  implementation("org.apache.httpcomponents.client5:httpclient5")
  implementation("org.hashids:hashids:${hashidsVersion}")
  implementation(
      "com.redis.testcontainers:testcontainers-redis-junit:${testContainersRedisVersion}") {
        constraints { implementation("com.redis:lettucemod:4.4.0") }
      }
  implementation("org.testcontainers:postgresql:${testContainersPostgreSQLVersion}")
  implementation("org.testcontainers:localstack:${testContainersLocalStackVersion}")
  implementation("org.apache.tika:tika-core:${tikaVersion}")
  implementation("org.springframework.boot:spring-boot-starter-test")

  testImplementation(kotlin("test"))
  testImplementation(platform("org.junit:junit-bom:${jUnitBomVersion}"))
  /*
  // https://youtrack.jetbrains.com/issue/KT-71057/POM-file-unusable-after-upgrading-to-2.0.20-from-2.0.10
  implementation(platform("org.jetbrains.kotlin:kotlin-bom:2.0.21"))
  implementation(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))

  detekt("io.gitlab.arturbosch.detekt:detekt-cli:$detektVersion")
  detekt("io.gitlab.arturbosch.detekt:detekt-formatting:$detektVersion")
  detektPlugins("io.gitlab.arturbosch.detekt:detekt-rules-libraries:$detektVersion")

  // Align versions of all Kotlin components
  implementation(platform("org.jetbrains.kotlin:kotlin-bom"))

  // Use the Kotlin JDK 8 standard library.
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")


  implementation("io.swagger.parser.v3:swagger-parser-v3:${swaggerParserVersion}")

  implementation(
      "org.springframework.security.oauth.boot:spring-security-oauth2-autoconfigure:${springOauthAutoConfigureVersion}") {
        constraints {
          implementation(
              "com.fasterxml.jackson.core:jackson-annotations:$jacksonAnnotationsVersion")
          implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonDatabindVersion")
          implementation("org.springframework:spring-web:$springWebVersion")
          implementation("org.springframework.boot:spring-boot-autoconfigure:$springBootVersion")
          implementation(
              "org.springframework.security:spring-security-jwt:${springSecurityJwtVersion}") {
                exclude(group = "org.bouncycastle", module = "bcpkix-jdk15on")
                constraints {
                  implementation("org.bouncycastle:bcpkix-jdk18on:${bouncyCastleJdk18Version}")
                }
              }
        }
      }
  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.security:spring-security-oauth2-jose:${springOauthVersion}") {
    constraints { implementation("com.nimbusds:nimbus-jose-jwt:10.4.2") }
  }
  implementation(
      "org.springframework.security:spring-security-oauth2-resource-server:${springOauthVersion}")

  implementation("org.springframework.boot:spring-boot-starter-web") {
    exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
  }

  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:${springDocVersion}")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonKotlinVersion")

  implementation("jakarta.servlet:jakarta.servlet-api:${servletApiVersion}")
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("io.micrometer:micrometer-registry-prometheus")
  implementation("org.springframework.boot:spring-boot-starter-aop")

  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinCoroutinesCoreVersion")
  implementation("com.redis.om:redis-om-spring:${redisOMVersion}")


  testImplementation("org.junit.jupiter:junit-jupiter")
  testImplementation("io.mockk:mockk:${mockkVersion}")
  testImplementation("org.awaitility:awaitility-kotlin:${awaitilityKVersion}")

  // Use the Kotlin test library.
  testImplementation("org.jetbrains.kotlin:kotlin-test")

  // Use the Kotlin JUnit integration.
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")

  annotationProcessor("org.springframework.boot:spring-boot-configuration-processor") */
}
