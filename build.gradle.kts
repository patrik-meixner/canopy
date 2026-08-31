plugins {
    id("java")
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intellijPlatform)
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        val type = providers.gradleProperty("platformType")
        val version = providers.gradleProperty("platformVersion")
        create(type, version)

        bundledPlugin("org.jetbrains.plugins.terminal")

        pluginVerifier()
    }

    implementation(libs.commonmark)
    implementation(libs.commonmark.tables)

    testImplementation(kotlin("test"))
}

intellijPlatform {
    pluginConfiguration {
        id = providers.gradleProperty("pluginGroup")
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")

        val changelogEnv = providers.environmentVariable("CHANGELOG")
        if (changelogEnv.isPresent && changelogEnv.get().isNotBlank()) {
            changeNotes = changelogEnv
        }

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }
        }
    }

    // Marketplace accepts unsigned uploads, but a signed one is verifiably ours; CI sets these.
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }

    pluginVerification {
        // Pinned to what the plugin is built against: `recommended()` reaches for an IDE build that
        // has no downloadable artifact for this architecture and fails before verifying anything.
        ides {
            ide(providers.gradleProperty("platformType"), providers.gradleProperty("platformVersion"))
        }
    }

    buildSearchableOptions = false
}

tasks {
    wrapper {
        gradleVersion = "8.10.2"
    }
}
