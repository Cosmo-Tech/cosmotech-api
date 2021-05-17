import org.springframework.boot.gradle.tasks.bundling.BootBuildImage
import org.springframework.boot.gradle.tasks.bundling.BootJar

dependencies {
  implementation("io.swagger.parser.v3:swagger-parser-v3:2.0.25")
  implementation("org.hashids:hashids:1.0.3")
  implementation("io.argoproj.workflow:argo-client-java:v3.0.1")
  implementation("com.squareup.retrofit2:retrofit:2.9.0")
  implementation("com.squareup.retrofit2:converter-scalars:2.9.0")
  implementation("com.squareup.okhttp3:okhttp:4.9.1")
}

tasks.getByName<BootJar>("bootJar") { enabled = false }

tasks.getByName<BootBuildImage>("bootBuildImage") { enabled = false }
