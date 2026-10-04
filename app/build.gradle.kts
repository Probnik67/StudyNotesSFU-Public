plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "ru.studynotes.sfu"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.studynotes.sfu"
        minSdk = 26
        targetSdk = 35
        versionCode = 85
        versionName = "0.8.58"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")

}
val studyNotesStoreFile = System.getenv("STUDYNOTES_STORE_FILE")
val studyNotesStorePassword = System.getenv("STUDYNOTES_STORE_PASSWORD")
val studyNotesKeyAlias = System.getenv("STUDYNOTES_KEY_ALIAS")
val studyNotesKeyPassword = System.getenv("STUDYNOTES_KEY_PASSWORD")

val studyNotesReleaseSigningAvailable =
    !studyNotesStoreFile.isNullOrBlank() &&
    !studyNotesStorePassword.isNullOrBlank() &&
    !studyNotesKeyAlias.isNullOrBlank() &&
    !studyNotesKeyPassword.isNullOrBlank()

android {
    if (studyNotesReleaseSigningAvailable) {
        signingConfigs {
            create("cloudRelease") {
                storeFile = file(studyNotesStoreFile!!)
                storePassword = studyNotesStorePassword
                keyAlias = studyNotesKeyAlias
                keyPassword = studyNotesKeyPassword
            }
        }

        buildTypes {
            getByName("release") {
                signingConfig = signingConfigs.getByName("cloudRelease")
            }
        }
    }
}
