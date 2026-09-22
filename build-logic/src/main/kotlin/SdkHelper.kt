import org.gradle.api.Project

/**
 * Retrieves the Android SDK location from the local.properties file.
 * This is used by the build-logic convention plugins.
 */
fun Project.androidSdkDir(): String? =
    rootProject.file("local.properties")
        .takeIf { it.exists() }
        ?.readLines()
        ?.firstOrNull { it.startsWith("sdk.dir") }
        ?.substringAfter('=')
        ?.trim()
