import groovy.json.JsonOutput
import groovy.json.JsonSlurper

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val mapsApiKey = providers.gradleProperty("MAPS_API_KEY")
    .orElse(providers.environmentVariable("MAPS_API_KEY"))
    .orElse("")

android {
    namespace = "com.baboons.vendedorescalle"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.baboons.vendedorescalle"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey.get()
    }

    // prod es la variante que hay que distribuir. qa es la de testing y se instala al lado.
    // AGP no permite un flavor llamado "testing": el nombre no puede empezar con "test".
    flavorDimensions += "env"
    productFlavors {
        create("prod") {
            dimension = "env"
            buildConfigField("String", "BABOONS_API_BASE", "\"https://concesionarias.baboons.com.ar/api/\"")
            buildConfigField("boolean", "IS_TESTING", "false")
        }
        create("qa") {
            dimension = "env"
            applicationIdSuffix = ".testing"
            versionNameSuffix = "-testing"
            buildConfigField("String", "BABOONS_API_BASE", "\"https://testing.baboons.com.ar/api/\"")
            buildConfigField("boolean", "IS_TESTING", "true")
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

}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.12.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.12.1")
    // El lint de release exige Fragment >= 1.3 para Activity Result.
    implementation("androidx.fragment:fragment:1.8.9")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.google.maps.android:maps-compose:7.0.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")

    // API en todas las variantes. El SDK completo (beta, auto-update) solo en debug:
    // es lo que se sube a Firebase App Distribution. No va en release, porque Google Play
    // lo puede rechazar aunque el código no se ejecute. Las KTX ya no se publican.
    // App Distribution sigue en beta y no entra en el BoM; la versión va explícita.
    implementation("com.google.firebase:firebase-appdistribution-api:16.0.0-beta21")
    debugImplementation("com.google.firebase:firebase-appdistribution:16.0.0-beta21")
}

val prodApplicationId = "com.baboons.vendedorescalle"
val testingApplicationId = "$prodApplicationId.testing"
val testingGoogleServices = file("src/qa/google-services.json")
val googleServicesSource = listOf(file("google-services.json"), file("src/prod/google-services.json"))
    .firstOrNull { it.isFile }

if (googleServicesSource != null) {
    ensureTestingGoogleServices(googleServicesSource, testingGoogleServices, testingApplicationId, prodApplicationId)
}
if (googleServicesSource != null || testingGoogleServices.isFile) {
    apply(plugin = "com.google.gms.google-services")
}

tasks.register("assembleTestingDebug") {
    group = "build"
    description = "Alias de assembleQaDebug. AGP no permite un productFlavor llamado testing."
    dependsOn("assembleQaDebug")
}
tasks.register("assembleTestingRelease") {
    group = "build"
    description = "Alias de assembleQaRelease. AGP no permite un productFlavor llamado testing."
    dependsOn("assembleQaRelease")
}
tasks.register("testTestingDebugUnitTest") {
    group = "verification"
    description = "Alias de testQaDebugUnitTest."
    dependsOn("testQaDebugUnitTest")
}

fun clientPackageName(client: Any?): String? {
    val info = (client as? Map<*, *>)?.get("client_info") as? Map<*, *> ?: return null
    val androidInfo = info["android_client_info"] as? Map<*, *> ?: return null
    return androidInfo["package_name"] as? String
}

fun rewritePackageNames(node: Any?, from: String, to: String): Any? = when (node) {
    is Map<*, *> -> linkedMapOf<String, Any?>().apply {
        node.forEach { (key, value) ->
            val name = key.toString()
            put(name, if (name == "package_name" && value == from) to else rewritePackageNames(value, from, to))
        }
    }
    is List<*> -> node.map { rewritePackageNames(it, from, to) }
    else -> node
}

fun Project.ensureTestingGoogleServices(source: File, target: File, testingPackage: String, prodPackage: String) {
    if (target.isFile) return
    val root = JsonSlurper().parse(source)
    if (root !is Map<*, *>) {
        throw org.gradle.api.GradleException("google-services.json invalido: ${source.path}")
    }
    val clients = root["client"] as? List<*> ?: emptyList<Any?>()
    if (clients.any { clientPackageName(it) == testingPackage }) return
    val donor = clients.firstOrNull { clientPackageName(it) == prodPackage } ?: clients.firstOrNull()
        ?: throw org.gradle.api.GradleException("google-services.json no tiene clients: ${source.path}")
    val cloned = rewritePackageNames(donor, prodPackage, testingPackage) as MutableMap<String, Any?>
    val info = cloned["client_info"] as? MutableMap<String, Any?>
    val androidInfo = info?.get("android_client_info") as? MutableMap<String, Any?>
    androidInfo?.put("package_name", testingPackage)
    val testingDoc = linkedMapOf(
        "project_info" to root["project_info"],
        "client" to listOf(cloned),
        "configuration_version" to (root["configuration_version"] ?: "1"),
        "generated_testing_shim" to true
    )
    target.parentFile.mkdirs()
    target.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(testingDoc)) + "\n")
    logger.lifecycle(
        "google-services.json no incluye $testingPackage. Se genero ${target.path} " +
            "solo para compilar. No alcanza para Firebase App Distribution: hay que registrar la app y reemplazar este archivo."
    )
}
