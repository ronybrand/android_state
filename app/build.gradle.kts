import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kover)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.cyclonedx)
}

// `-PapiUrl=https://...` points the build at another backend. The value is compiled into the binary, so nothing secret
// may go there. The default is the public deployment of the same API (reached through the web app's proxy).
val apiUrl = providers.gradleProperty("apiUrl").orElse("https://react-state-flax.vercel.app/api")

// The upload key, from `key.properties` at the repository root (git-ignored; CI writes it from secrets). Without it a
// release build is unsigned, and the app bundle for Google Play refuses to build (see below).
val keyProperties = Properties().apply {
    rootProject.file("key.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}
val hasUploadKey = keyProperties.containsKey("storeFile")

android {
    namespace = "com.ronybrand.estados"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ronybrand.estados"
        minSdk = 26
        targetSdk = 37
        // `-PversionCode=N` (the release workflow): Google Play rejects an upload whose number is not higher than the last.
        versionCode = providers.gradleProperty("versionCode").map(String::toInt).orElse(1).get()
        versionName = "0.1.0"
        buildConfigField("String", "API_URL", "\"${apiUrl.get()}\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasUploadKey) {
            create("upload") {
                storeFile = rootProject.file(keyProperties.getProperty("storeFile"))
                storePassword = keyProperties.getProperty("storePassword")
                keyAlias = keyProperties.getProperty("keyAlias")
                keyPassword = keyProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasUploadKey) signingConfig = signingConfigs.getByName("upload")
        }
        // What the end-to-end flows install: the release build (minified, so R8 is exercised) signed with the debug key
        // and allowed to talk plain http to the mock API on localhost. It is never published.
        create("e2e") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // Robolectric reaches into JDK internals that newer JDKs (the dev machine runs 25) keep closed.
                it.jvmArgs(
                    "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                    "--add-opens=java.base/java.lang=ALL-UNNAMED",
                    "--add-opens=java.base/java.util=ALL-UNNAMED",
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                    "--add-opens=java.base/java.net=ALL-UNNAMED",
                    "--add-opens=java.base/java.nio=ALL-UNNAMED",
                    "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
                )
            }
        }
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        checkDependencies = true
        // The release build is minified, so lint should judge what ships.
        checkReleaseBuilds = true
    }
}

// Google Play rejects a bundle signed with the debug key (or unsigned), so say why instead of producing one.
gradle.taskGraph.whenReady {
    if (hasTask(":app:bundleRelease") && !hasUploadKey) {
        throw GradleException(
            "Refusing to build a release app bundle: there is no key.properties with the upload key (see the README, 'Release signing').",
        )
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        // A warning that is not fixed is a warning nobody reads.
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.okhttp)
    implementation(libs.serialization.json)
    implementation(libs.coroutines.android)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.compose.ui.test.junit4)
}

ktlint {
    version.set("1.8.0")
}

// ktlint's own tool classpath carries a logback line that no longer gets fixes (see kotlin-order-api); it is build
// tooling only, so keeping it on the patched line costs nothing at run time.
configurations.named("ktlint") {
    resolutionStrategy.eachDependency {
        if (requested.group == "ch.qos.logback") useVersion("1.5.38")
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt.yml"))
    source.setFrom("src/main/kotlin", "src/test/kotlin")
}

kover {
    reports {
        filters.excludes {
            classes(
                // Generated.
                "*.BuildConfig",
                "*ComposableSingletons*",
                // Process wiring: it only connects the pieces, and the end-to-end flows start the real thing.
                "com.ronybrand.estados.EstadosApp",
                "com.ronybrand.estados.MainActivity",
                "com.ronybrand.estados.AppContainer",
                // Needs the AndroidKeyStore provider, which exists only on a device (see the class comment).
                "com.ronybrand.estados.core.KeystoreKeyValueStore",
            )
        }
        verify {
            rule {
                minBound(90)
            }
        }
    }
}

// The bill of materials of what ships: the release runtime classpath only (no test or build tooling), as JSON. CI scans
// it for known vulnerabilities and keeps it as an artifact.
tasks.cyclonedxDirectBom {
    includeConfigs.set(listOf("releaseRuntimeClasspath"))
    projectType.set(org.cyclonedx.model.Component.Type.APPLICATION)
    jsonOutput.set(layout.buildDirectory.file("reports/sbom/bom.json"))
}
