plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.sengine"
    compileSdk = 34

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.sengine.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 612
        versionName = "6.1.2 Ultra Edition"
        buildConfigField("String", "ENGINE_VERSION", "\"6.1.2 Ultra Edition [Ultra]\"")
        buildConfigField("int", "ENGINE_API_VERSION", "612")
        buildConfigField("String", "RENDER_PIPELINE", "\"Universal+RayTracing+Nanite+2D_URP+Clustered+FSR2\"")
        buildConfigField("String", "ENGINE_CODENAME", "\"Ultra\"")
        buildConfigField("String", "ENGINE_FEATURES", "\"RayTracing,Nanite,Lumen,Chaos,WorldPartition,ControlRig,MotionMatching,XR,EQS,MLAgents,PCG,2D_URP,Tilemap,2D_Physics,2D_Animation,Sorting,FullControl,Clustered,CSM,FSR2,VolumetricClouds,WaterV2,Burst,UndoRedo,PrefabVariants,GOAP,InterestManagement,ClientPrediction\"")
        ndk { abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a", "x86_64")) }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Signed with the debug key so the release APK is installable out of the box.
            // Replace with your own signing config before publishing.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    packaging {
        resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    // JavaScript scripting runtime (interpreted mode). 1.7.14 breaks on Android (javax.lang.model), keep 1.7.13
    implementation("org.mozilla:rhino:1.7.13")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("failed", "passed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true
    }
}
