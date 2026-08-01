import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJLinkTask
import org.gradle.api.provider.Property

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":shared-core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
    implementation("org.xerial:sqlite-jdbc:3.46.0.0")
    implementation("org.json:json:20240303")
    implementation("net.java.dev.jna:jna-platform:5.14.0")
    implementation("org.apache.pdfbox:pdfbox:3.0.2")
    implementation("org.apache.poi:poi-ooxml:5.2.5")
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}

compose.desktop {
    application {
        mainClass = "com.mozhou.novelcraft.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            // JDBC, PDFBox and POI reach parts of the JDK through runtime loading.
            // Shipping all standard modules prevents jlink's static analysis from
            // omitting a required module such as java.sql.
            includeAllModules = true
            packageName = "NovelEdit"
            packageVersion = "1.0.3"
            description = "本地优先的长篇小说创作工作室"
            vendor = "NovelEdit"
            windows {
                iconFile.set(project.file("src/main/resources/branding/noveledit-icon.ico"))
                menuGroup = "NovelEdit"
                shortcut = true
                perUserInstall = true
                upgradeUuid = "8c3f70e1-cad5-43f2-b807-7cbb0bdf7d77"
            }
        }
    }
}

// Compose 1.6 strips native commands by default. On the bundled Temurin 17
// toolchain that produces a runtime the Windows launcher cannot bootstrap.
tasks.withType<AbstractJLinkTask>().configureEach {
    @Suppress("UNCHECKED_CAST")
    val stripNativeCommands = javaClass
        .getMethod("getStripNativeCommands\$compose")
        .invoke(this) as Property<Boolean>
    stripNativeCommands.set(false)
}
