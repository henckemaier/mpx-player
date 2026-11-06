import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// SPIKE CMP — Fase 1 (toolchain). Prova que Compose Multiplatform 1.7.1 compila no
// Android e LINKA o alvo iosSimulatorArm64 com Kotlin 2.0.21 + AGP 8.7.3, sem subir o
// Kotlin. Módulo descartável, separado do app real (applicationId próprio).
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    // Só iosSimulatorArm64 (decisão do Maestro): é o que o CI macOS linka. SEM guarda
    // HostManager — no CI o alvo precisa existir p/ linkar; no Windows a task iOS só
    // não é chamada (o :app:assembleDebug local não a toca).
    iosSimulatorArm64 {
        binaries.framework {
            baseName = "ComposeAppSpike"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.components.resources)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
    }
}

// Pacote fixo p/ o acessor gerado (import previsível: com.mpx.player.spike.resources.Res).
compose.resources {
    packageOfResClass = "com.mpx.player.spike.resources"
}

android {
    namespace = "com.mpx.player.spike"
    compileSdk = 35
    defaultConfig {
        // applicationId SEPARADO do app real: instala lado-a-lado no device, sem colidir.
        applicationId = "com.mpx.player.spike"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
