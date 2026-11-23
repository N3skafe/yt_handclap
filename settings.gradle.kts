pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
            // 다른 의존성이 JitPack에서 풀리지 않도록 sherpa-onnx 그룹만 허용합니다.
            // JitPack 의존성을 새로 추가하면 여기에 그룹을 더해야 합니다.
            content { includeGroupByRegex("com\\.github\\.k2-fsa.*") }
        }
    }
}

rootProject.name = "SenaCue"
include(":app")
