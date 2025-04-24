android {
    namespace = "com.example.voicetotexttest"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.voicetotexttest"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SPEECH_TO_TEXT_API_KEY", project.properties["SPEECH_TO_TEXT_API_KEY"] as? String ?: "")
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }

    packagingOptions {
        exclude("META-INF/INDEX.LIST")
        pickFirst("META-INF/DEPENDENCIES") // Prova a prendere la prima versione trovata
    }
}

dependencies {
    implementation("androidx.concurrent:concurrent-futures-ktx:1.2.0")
    implementation("com.google.api.grpc:proto-google-common-protos:2.24.0") // O una versione recente
    implementation("com.google.api.grpc:grpc-google-common-protos:2.24.0") // Assicurati che la versione corrisponda
    implementation(libs.google.cloud.speech)
    implementation("com.google.api.grpc:grpc-google-cloud-speech-v1:2.2.2") {
        exclude(group = "io.grpc", module = "grpc-context")
    }
    implementation(libs.grpc.okhttp)
    implementation("io.grpc:grpc-protobuf:1.57.2")
    implementation("io.grpc:grpc-stub:1.57.2")
    implementation("io.grpc:grpc-api:1.57.2") // Aggiungi anche grpc-api
    implementation("io.grpc:grpc-context:1.57.2") // Forza esplicitamente la versione di grpc-context
    implementation ("com.google.android.material:material:1.11.0") // Usa la versione più recente disponibile
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation(libs.androidx.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.kotlin.coroutines.android)
    implementation(libs.lifecycle.runtime.ktx)
    implementation("androidx.cardview:cardview:1.0.0")

}
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

