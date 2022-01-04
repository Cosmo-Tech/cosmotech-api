// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.

dependencies {
  api(projects.cosmotechApiCommonParent.cosmotechApiCommonId)
  api(projects.cosmotechApiCommonParent.cosmotechApiCommonEvents)
  implementation("io.swagger.parser.v3:swagger-parser-v3:2.0.29")

  implementation(
      "org.springframework.security.oauth.boot:spring-security-oauth2-autoconfigure:2.6.2")
  implementation("org.springframework.security:spring-security-jwt:1.1.1.RELEASE")
}
