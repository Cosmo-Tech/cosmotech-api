import org.springframework.boot.gradle.tasks.bundling.BootBuildImage
import org.springframework.boot.gradle.tasks.bundling.BootJar

tasks.getByName<BootJar>("bootJar") { enabled = false }

tasks.getByName<BootBuildImage>("bootBuildImage") { enabled = false }
