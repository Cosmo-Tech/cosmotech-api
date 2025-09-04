// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
// no-import

import org.gradle.api.tasks.bundling.Jar
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins { id("org.jetbrains.kotlinx.kover") }

val jar: Jar by tasks
val bootJar: BootJar by tasks

bootJar.enabled = false

jar.enabled = true
