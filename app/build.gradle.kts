import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.cyclonedx)
}

// `-PapiUrl=https://...` points the build at another backend. The value is compiled into the binary, so nothing secret
// may go there. The default is the public deployment of the same API (reached through the web app's proxy).
val apiUrl = providers.gradleProperty("apiUrl").orElse("https://react-state-flax.vercel.app/api")

android {
    namespace = "com.ronybrand.estados"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ronybrand.estados"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "API_URL", "\"${apiUrl.get()}\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    implementation(libs.lifecycle.viewmodel.compose)
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
