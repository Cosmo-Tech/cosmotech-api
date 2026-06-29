// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
// no-import

plugins { id("org.jetbrains.kotlinx.kover") }

val hashidsVersion = "1.0.3"
val testContainersRedisVersion = "2.2.4"
val testContainersPostgreSQLVersion = "2.0.5"
val tikaVersion = "3.3.1"
val springPlatformBomVersion = "4.0.7"
val jUnitBomVersion = "6.1.0"
val testContainersJupiterVersion = "1.21.4"

dependencies {
  implementation("org.apache.httpcomponents.client5:httpclient5")
  implementation(
      "org.springframework.boot:spring-boot-starter-restclient:$springPlatformBomVersion"
  )
  implementation("org.hashids:hashids:${hashidsVersion}")
  implementation("com.redis:testcontainers-redis:${testContainersRedisVersion}")
  implementation("org.testcontainers:testcontainers-postgresql:${testContainersPostgreSQLVersion}")
  implementation("org.apache.tika:tika-core:${tikaVersion}")
  implementation("org.springframework.boot:spring-boot-starter-test")
  implementation("org.testcontainers:junit-jupiter:$testContainersJupiterVersion")

  testImplementation(kotlin("test"))
  testImplementation(platform("org.junit:junit-bom:${jUnitBomVersion}"))
}
