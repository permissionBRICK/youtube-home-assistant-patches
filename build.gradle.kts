plugins { kotlin("jvm") version "2.3.10" }
repositories { mavenCentral() }
dependencies { compileOnly(files("tools/revanced-cli.jar")) }
kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll("-Xcontext-parameters", "-Xexplicit-backing-fields")
    }
}
sourceSets.main { kotlin.srcDir("patches/src/main/kotlin") }
tasks.jar {
    archiveFileName.set("youtube-home-assistant.rvp")
    from("extensions/build/extension.rve") { into("extensions") }
    manifest {
        attributes("Name" to "YouTube Home Assistant", "Description" to "Send the current YouTube video to a Home Assistant webhook", "Version" to project.version, "Author" to "permissionBRICK", "License" to "GPL-3.0-only", "Source" to "https://github.com/permissionBRICK/youtube-home-assistant-patches")
    }
}
tasks.withType<JavaCompile>().configureEach { options.release.set(17) }
