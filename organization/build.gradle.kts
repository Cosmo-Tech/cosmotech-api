import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
   id("org.openapi.generator")
}

tasks.getByName<BootJar>("bootJar") {
   enabled = false
}
