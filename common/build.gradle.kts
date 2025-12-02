// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
// no-import

plugins { id("org.jetbrains.kotlinx.kover") }

val hashidsVersion = "1.0.3"
val testContainersRedisVersion = "1.6.4"
val testContainersPostgreSQLVersion = "1.21.3"
val testContainersLocalStackVersion = "1.21.3"
val tikaVersion = "3.2.3"

val jUnitBomVersion = "6.0.1"

dependencies {
  implementation("org.apache.httpcomponents.client5:httpclient5")
  implementation("org.hashids:hashids:${hashidsVersion}")
  implementation(
      "com.redis.testcontainers:testcontainers-redis-junit:${testContainersRedisVersion}"
  ) {
    constraints { implementation("com.redis:lettucemod:4.5.0") }
  }
  implementation("org.testcontainers:postgresql:${testContainersPostgreSQLVersion}")
  implementation("org.testcontainers:localstack:${testContainersLocalStackVersion}")
  implementation("org.apache.tika:tika-core:${tikaVersion}")
  implementation("org.springframework.boot:spring-boot-starter-test")

  testImplementation(kotlin("test"))
  testImplementation(platform("org.junit:junit-bom:${jUnitBomVersion}"))
}
