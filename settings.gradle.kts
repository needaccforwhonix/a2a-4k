pluginManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        gradlePluginPortal()
    }
}
// SPDX-FileCopyrightText: 2025
//
// SPDX-License-Identifier: Apache-2.0

rootProject.name = "a2a4k"

include("a2a4k-models")
include("a2a4k-server")
include("a2a4k-server-arc")
include("a2a4k-server-ktor")
include("a2a4k-client")
include("a2a4k-storage-redis")
include("examples")
