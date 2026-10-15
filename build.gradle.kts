plugins {
    application
    java
    id("com.diffplug.spotless") version "8.10.4"
}

repositories { mavenCentral() }

val lwjglVersion = "3.4.3"
val jomlVersion = "1.10.8"
val os = System.getProperty("os.name").lowercase()
val arch = System.getProperty("os.arch").lowercase()
val natives = when {
    os.contains("win") -> if (arch.contains("aarch64") || arch.contains("arm64")) "natives-windows-arm64" else "natives-windows"
    os.contains("mac") -> if (arch.contains("aarch64") || arch.contains("arm64")) "natives-macos-arm64" else "natives-macos"
    else -> if (arch.contains("aarch64") || arch.contains("arm64")) "natives-linux-arm64" else "natives-linux"
}

dependencies {
    implementation(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))
    listOf("lwjgl", "lwjgl-glfw", "lwjgl-opengl", "lwjgl-stb").forEach {
        implementation("org.lwjgl:$it")
        runtimeOnly("org.lwjgl:$it::$natives")
    }
    implementation("org.joml:joml:$jomlVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java { toolchain { languageVersion.set(JavaLanguageVersion.of(25)) } }
application {
    mainClass.set("com.norbertfila.Main")
    if (os.contains("mac")) applicationDefaultJvmArgs = listOf("-XstartOnFirstThread")
}
tasks.test { useJUnitPlatform() }

spotless {
    java {
        palantirJavaFormat("2.102.0")
    }
}

tasks.register<JavaExec>("benchmarkGame") {
    group = "verification"
    description = "Runs the scripted production-game benchmark (-Pscenes, -Prd, -Plabel)."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.norbertfila.Main")
    jvmArgs("-Xmx4g")
    if (os.contains("mac")) jvmArgs("-XstartOnFirstThread")
    systemProperty("voxel.benchmark", findProperty("scenes") ?: "all")
    systemProperty("voxel.benchmark.rd", findProperty("rd") ?: "8")
    systemProperty("voxel.benchmark.label", findProperty("label") ?: "unlabeled")
    systemProperty("voxel.benchmark.gpuTimers", findProperty("gpuTimers") ?: "true")
    findProperty("seconds")?.let { systemProperty("voxel.benchmark.seconds", it) }
    findProperty("directDraw")?.let { systemProperty("voxel.render.directDraw", it) }
}
