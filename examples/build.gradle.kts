// SPDX-FileCopyrightText: 2025
//
// SPDX-License-Identifier: Apache-2.0

kotlin {
    jvm()
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":a2a4k-models"))
                implementation(project(":a2a4k-client"))
                implementation(project(":a2a4k-server-ktor"))
                implementation(project(":a2a4k-server-arc"))
                implementation(libs.ktor.client.core)
                implementation(libs.bundles.kotlinx)
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation("dev.langchain4j:langchain4j-open-ai:1.0.0-beta3")
                implementation("org.eclipse.lmos:arc-agents:0.139.0")
                implementation("org.eclipse.lmos:arc-azure-client:0.139.0")
                implementation(libs.slf4j.jdk14)
            }
        }
    }
}

tasks.register<JavaExec>("runAgentMesh") {
    group = "application"
    mainClass.set("io.github.a2a_4k.examples.AgentMeshKt")
    val jvmCompilations = kotlin.targets.getByName("jvm").compilations.getByName("main")
    classpath(jvmCompilations.output.allOutputs)
    classpath(jvmCompilations.runtimeDependencyFiles)

    // Add LangChain4j env variables if needed
    environment("OPENAI_API_KEY", System.getenv("OPENAI_API_KEY") ?: "demo")
}

repositories {
    mavenLocal()
    mavenCentral()
    maven(url = "https://oss.sonatype.org/content/repositories/snapshots/")
}

// Task to run the Agent Mesh Example
tasks.register<JavaExec>("runAgentMesh") {
    group = "application"
    description = "Runs the Agent Mesh Example"
    mainClass.set("io.github.a2a_4k.AgentMeshExampleKt")

    val jvmTarget = kotlin.targets.getByName("jvm")
    val mainCompilation = jvmTarget.compilations.getByName("main")

    classpath = mainCompilation.output.allOutputs + mainCompilation.runtimeDependencyFiles!!
}
