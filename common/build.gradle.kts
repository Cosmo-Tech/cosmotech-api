import org.springframework.boot.gradle.tasks.bundling.BootBuildImage
import org.springframework.boot.gradle.tasks.bundling.BootJar

dependencies { implementation("io.swagger.parser.v3:swagger-parser-v3:2.0.25") }

tasks.getByName<BootJar>("bootJar") { enabled = false }

tasks.getByName<BootBuildImage>("bootBuildImage") { enabled = false }
