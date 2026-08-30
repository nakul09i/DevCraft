import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// google-services.json is per-project Firebase config and is not committed.
// Apply the plugin only when it is present, so a fresh clone still builds a
// working offline APK instead of failing at configuration time.
// Mappls credentials come from local.properties (gitignored) or the environment.
// Never hardcoded, never committed. Absent key => mapping reports "not configured"
// and the rest of the app is unaffected.
val mapplsApiKey: String = run {
    val props = Properties()
    rootProject.file("local.properties").takeIf { it.exists() }
        ?.inputStream()?.use { props.load(it) }
    props.getProperty("MAPPLS_API_KEY")
        ?: System.getenv("MAPPLS_API_KEY")
        ?: ""
}

val hasFirebaseConfig = project.file("google-services.json").exists()
if (hasFirebaseConfig) {
    apply(plugin = "com.google.gms.google-services")
} else {
    logger.lifecycle("DevCraft: google-services.json absent - building without Firebase sync. See FIREBASE_SETUP.md")
}

android {
    namespace = "com.devcraft"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.devcraft"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("String", "MAPPLS_API_KEY", "\"$mapplsApiKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // Room
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Firebase sync transport. Compiled in unconditionally; at runtime the app
    // checks whether Firebase actually initialized and stays fully offline if not.
    implementation(platform("com.google.firebase:firebase-bom:32.7.4"))
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")

    // HTTP for Mappls REST APIs. OkHttp only - Retrofit would add a layer for
    // the three endpoints we call. Gson (already present) handles the responses;
    // org.json is deliberately avoided because it is stubbed in JVM unit tests.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    // Lets us verify Mappls response parsing and HTTP error classification
    // without credentials or a live service.
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
