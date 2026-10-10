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
    listOf("lwjgl", "lwjgl-glfw", "lwjgl-opengl").forEach {
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
