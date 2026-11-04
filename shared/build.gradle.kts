import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

kotlin {
    // ---- Android (consumido pelo :app) ----
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // ---- iOS ----
    // Kotlin/Native só gera binários Apple em macOS + Xcode. Registramos os alvos
    // iOS APENAS quando o host é macOS; assim, no Windows, tasks agregadas
    // (`build`, `check`, `:shared:allTests`, "Build All" da IDE) não tentam
    // compilar/linkar/testar Apple e quebrar. Num Mac, os alvos aparecem e o
    // XCFramework `Shared` é montado para o iosApp.
    if (HostManager.hostIsMac) {
        val xcf = org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFrameworkConfig(project, "Shared")
        listOf(
            iosX64(),
            iosArm64(),
            iosSimulatorArm64(),
        ).forEach { target ->
            target.binaries.framework {
                baseName = "Shared"
                isStatic = true
                xcf.add(this)
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "com.mpx.player.shared"
    // Mantido alinhado ao :app (compileSdk 35 / minSdk 29). TODO: extrair para o
    // version catalog quando houver mais módulos para evitar divergência.
    compileSdk = 35
    defaultConfig {
        minSdk = 29
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
