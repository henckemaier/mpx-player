import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    // Compose Multiplatform 1.7.1 + compose-compiler (== Kotlin 2.0.21). Habilita o Compose
    // no :shared (androidTarget + alvos iOS) e a UI comum (SmokeScreen) consumida pelo :app
    // e pelo host iOS. O jetbrains-compose detecta Kotlin 2.0 e delega o compiler ao kotlin-compose.
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
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
            implementation(libs.ktor.client.core)
            // ViewModel multiplataforma (ViewModel + viewModelScope no commonMain).
            // No Android mapeia p/ androidx.lifecycle.ViewModel (mesma classe do :app).
            implementation(libs.jetbrains.lifecycle.viewmodel)
            // Compose Multiplatform: UI comum (SmokeScreen) + fontes como Res.font.*.
            // Os acessores `compose.*` vêm do plugin jetbrains-compose.
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(libs.compose.components.resources)
        }
        androidMain.dependencies {
            // Engine Ktor do Android (baseado em HttpURLConnection; sem trazer OkHttp).
            implementation(libs.ktor.client.android)
            // Player Android do contrato comum (AndroidAudioPlayer): MediaController/MediaSessionService.
            implementation(libs.androidx.media3.session)
            implementation(libs.kotlinx.coroutines.guava)   // await() do ListenableFuture
            implementation(libs.kotlinx.coroutines.android)  // Dispatchers.Main (fala com o MediaController)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        // Engine Ktor do iOS (Darwin/NSURLSession). O source set `iosMain` é criado pela hierarquia
        // padrão só em host macOS e apenas em afterEvaluate — por isso NÃO usamos getByName aqui
        // (eager, falharia por ordem de avaliação). Configuramos de forma preguiçosa: quando/se o
        // `iosMain` for criado, recebe a dependência. No Windows o bloco nem roda (sem alvos Apple).
        if (HostManager.hostIsMac) {
            configureEach {
                if (name == "iosMain") {
                    dependencies { implementation(libs.ktor.client.darwin) }
                }
            }
        }
    }
}

// Pacote fixo p/ o acessor gerado das Compose Resources (import previsível:
// com.mpx.player.resources.Res). O leitor de resources do CMP no iOS procura em
// <app>.app/compose-resources/composeResources/com.mpx.player.resources/<cat>/<arq>.
compose.resources {
    packageOfResClass = "com.mpx.player.resources"
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
