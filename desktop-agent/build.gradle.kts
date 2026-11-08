import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Modulo ISOLADO Kotlin/JVM do conector desktop-sync (NAO Android). Irmao do :app no
// multi-projeto: o :app NAO depende dele (nenhum `project(":desktop-agent")` no :app).
// Deps NOVAS vivem SO aqui (Ktor/jaudiotagger) — nunca no :app. Repositorios vem do
// settings.gradle (dependencyResolutionManagement, FAIL_ON_PROJECT_REPOS) -> sem bloco
// `repositories {}` local de proposito.
plugins {
    // kotlin("jvm") sem versao: o KGP 2.0.21 ja esta no classpath do build (o root traz
    // o kotlin-android via alias apply false — mesmo artefato kotlin-gradle-plugin).
    kotlin("jvm")
    // O plugin de serialization NAO esta no classpath ainda -> declara a versao (= Kotlin).
    kotlin("plugin.serialization") version "2.0.21"
    application
}

val ktorVersion = "2.3.12"            // Ktor 2.x estavel, compat. Kotlin 2.0.x
val serializationVersion = "1.7.3"    // kotlinx-serialization-json
val jaudiotaggerVersion = "3.0.1"     // net.jthink jaudiotagger (Maven Central)

dependencies {
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-cio:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-partial-content:$ktorVersion")
    implementation("io.ktor:ktor-server-auto-head-response:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
    implementation("net.jthink:jaudiotagger:$jaudiotaggerVersion")
    implementation("com.google.zxing:core:3.5.3")   // QR-gen do pareamento (JVM puro)
    implementation("org.jmdns:jmdns:3.5.9")          // anúncio mDNS _mpx._tcp (descoberta)

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
}

application {
    mainClass.set("com.mpx.desktop.MainKt")
}

// Bytecode Java 17 (igual ao alvo do :app), SEM forcar download de toolchain: compila com o
// JVM que roda o Gradle (>=17) emitindo classes 17. Evita exigir um JDK 17 instalado.
kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// ----------------------------------------------------------------------------------------
// jpackage (app-image, JRE trimado via jlink) — NAO entra no grafo de `build`/`test`.
// Alvo: pasta executavel standalone no Windows (usuario nao instala Java). Requer um JDK
// 17+ com `jpackage` no PATH. Rode:  ./gradlew :desktop-agent:jpackageImage
// Detalhes/alternativa manual em desktop-agent/README.md.
// ----------------------------------------------------------------------------------------
tasks.register<Exec>("jpackageImage") {
    group = "distribution"
    description = "Gera um app-image standalone (jpackage + JRE trimado) em build/jpackage."
    dependsOn("installDist") // monta build/install/<app>/lib com todos os jars
    val installDir = layout.buildDirectory.dir("install/${project.name}")
    val outDir = layout.buildDirectory.dir("jpackage")
    doFirst {
        outDir.get().asFile.deleteRecursively()
        outDir.get().asFile.mkdirs()
    }
    commandLine(
        "jpackage",
        "--type", "app-image",
        "--name", "MPXDesktopAgent",
        "--input", installDir.get().dir("lib").asFile.absolutePath,
        "--main-jar", "${project.name}.jar",
        "--main-class", "com.mpx.desktop.MainKt",
        "--dest", outDir.get().asFile.absolutePath,
        // JRE trimado: jpackage roda o jlink internamente a partir dos modulos detectados.
        "--jlink-options", "--strip-debug --no-header-files --no-man-pages --compress=2",
    )
}
