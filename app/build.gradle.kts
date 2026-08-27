import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Secrets de build (apenas URL + anon/publishable key do Supabase).
// NUNCA colocar service_role key, senhas administrativas ou outros segredos aqui.
// Fontes aceitas (em ordem): local.properties (gitignored) -> variável de ambiente.
val bridgeSecrets = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun bridgeSecret(name: String): String = sequenceOf(
    bridgeSecrets.getProperty(name),
    System.getenv(name)
).map { it?.trim()?.takeIf { value -> value.isNotEmpty() } }.firstOrNull { it != null } ?: ""

fun signingProp(name: String): String? = sequenceOf(
    bridgeSecrets.getProperty(name),
    System.getenv(name)
).map { it?.trim()?.takeIf { v -> v.isNotEmpty() } }.firstOrNull { it != null }

val domnexKeystorePath = signingProp("DOMNEX_KEYSTORE_PATH")
val domnexKeystorePassword = signingProp("DOMNEX_KEYSTORE_PASSWORD")
val domnexKeyAlias = signingProp("DOMNEX_KEY_ALIAS")
val domnexKeyPassword = signingProp("DOMNEX_KEY_PASSWORD")

val signingReady = listOfNotNull(
    domnexKeystorePath, domnexKeystorePassword, domnexKeyAlias, domnexKeyPassword
).size == 4

if (!signingReady) {
    logger.warn(
        "⚠ DOMNEX BRIDGE: signingConfigs.release não configurado.\n" +
            "Adicione as 4 propriedades em local.properties (gitignored) ou variáveis de ambiente:\n" +
            "  DOMNEX_KEYSTORE_PATH\n" +
            "  DOMNEX_KEYSTORE_PASSWORD\n" +
            "  DOMNEX_KEY_ALIAS\n" +
            "  DOMNEX_KEY_PASSWORD\n" +
            "O build release vai falhar até que todas estejam presentes."
    )
}

android {
    namespace = "com.domnex.cfi.bridge"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.domnex.cfi.bridge"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Somente a anon/publishable key vai para o APK — é pública por design.
        // O acesso administrativo real acontece em backend (Edge Function) validando role.
        buildConfigField("String", "SUPABASE_URL", "\"${bridgeSecret("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${bridgeSecret("SUPABASE_ANON_KEY")}\"")
    }

    if (signingReady) {
        signingConfigs {
            create("release") {
                storeFile = file(domnexKeystorePath!!)
                storePassword = domnexKeystorePassword
                keyAlias = domnexKeyAlias
                keyPassword = domnexKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signingReady) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                // Sem assinatura configurada: forçar falha clara no build.
                signingConfig = null
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            // Logs de diagnóstico usam android.util.Log; nos testes JVM vira no-op.
            isReturnDefaultValues = true
        }
    }
}

tasks.configureEach {
    if (name.startsWith("assemble") && name.contains("Release", ignoreCase = true) && !signingReady) {
        doFirst {
            throw GradleException(
                "DOMNEX BRIDGE: Build release bloqueado — signingConfigs.release não configurado.\n\n" +
                    "Adicione as seguintes propriedades em local.properties (ou variáveis de ambiente):\n" +
                    "  DOMNEX_KEYSTORE_PATH=/caminho/para/domnex-bridge.jks\n" +
                    "  DOMNEX_KEYSTORE_PASSWORD=<senha>\n" +
                    "  DOMNEX_KEY_ALIAS=<alias>\n" +
                    "  DOMNEX_KEY_PASSWORD=<senha>\n\n" +
                    "Arquivo local.properties é gitignored e nunca versionado."
            )
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
