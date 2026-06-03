plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.intellij.platform") version "2.16.0"
}

group = "games.ace"
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // Target Rider 2024.3. Bump this string to test against newer Rider;
        // useInstaller downloads the SDK automatically.
        rider("2024.3")
        // No Git4Idea / VCS dependency: Overseer detects pushes by watching the .git
        // filesystem directly, independent of Rider's Git client.
    }
}

intellijPlatform {
    // Required for Rider: the .NET-backed platform can't run the JVM bytecode instrumenter.
    instrumentCode = false

    pluginConfiguration {
        ideaVersion {
            sinceBuild = "243"
            untilBuild = provider { null } // no upper bound — run on any Rider 2024.3+
        }
    }
}

kotlin {
    jvmToolchain(21) // Rider 2024.3 runs on JBR 21
}
