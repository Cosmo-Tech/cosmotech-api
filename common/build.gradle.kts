// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
import org.springframework.boot.gradle.tasks.bundling.BootBuildImage
import org.springframework.boot.gradle.tasks.bundling.BootJar

dependencies {
  implementation("io.swagger.parser.v3:swagger-parser-v3:2.0.28")
  implementation("org.hashids:hashids:1.0.3")
}

tasks.getByName<BootJar>("bootJar") { enabled = false }

tasks.getByName<BootBuildImage>("bootBuildImage") { enabled = false }
