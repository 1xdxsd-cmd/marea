plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.marea.music"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.marea.music"
        minSdk = 24
        targetSdk = 35
        // El workflow sube estos números en cada compilación para que Android la instale
        // como actualización encima de la anterior (sin borrar la app ni los datos).
        versionCode = (project.findProperty("MAREA_VERSION_CODE") as String?)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("MAREA_VERSION_NAME") as String?) ?: "0.1.0-mvp"

        // URL HTTPS de tu servidor de acceso (carpeta /server). Define MAREA_ACCESS_URL en
        // gradle.properties, con -PMAREA_ACCESS_URL=... o como variable de entorno.
        val accessUrl = (project.findProperty("MAREA_ACCESS_URL") as String?)
            ?: System.getenv("MAREA_ACCESS_URL")
            ?: "https://marea.example.com"
        buildConfigField("String", "ACCESS_SERVER_URL", "\"${accessUrl.trimEnd('/')}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val ksPath = System.getenv("MAREA_KEYSTORE_PATH")
            if (!ksPath.isNullOrBlank()) {
                storeFile = file(ksPath)
                storePassword = System.getenv("MAREA_KEYSTORE_PASS")
                keyAlias = System.getenv("MAREA_KEY_ALIAS") ?: "marea"
                keyPassword = System.getenv("MAREA_KEY_PASS")
            }
        }
    }

    buildTypes {
        release {
            // Sin R8 por ahora: evita fallos por reflexión (NewPipe/Rhino) mientras no se pruebe a fondo.
            isMinifyEnabled = false
            isShrinkResources = false
            if (!System.getenv("MAREA_KEYSTORE_PATH").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Que una advertencia de lint no detenga la compilación del APK firmado.
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs.nio)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.datasource.okhttp)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.guava)

    implementation(libs.newpipe.extractor)
    implementation(libs.jaudiotagger)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
