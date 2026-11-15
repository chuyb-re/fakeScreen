plugins {
    alias(libs.plugins.agp.app)
}

android {
    namespace = "top.chuyb.fakeScreen"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        //noinspection ExpiredTargetSdkVersion
        targetSdk = 31
        minSdk = 26
        versionCode = 21
        versionName = "2.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles("proguard-rules.pro")
            signingConfig = signingConfigs["debug"]
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources {
            merges += "META-INF/xposed/*"
            excludes += "**"
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(libs.core)
    implementation(libs.appcompat)
    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)
    implementation("com.google.android.material:material:1.12.0")
}
