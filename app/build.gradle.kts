plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.youtubegesturecontrol"
    // androidx.core 1.19는 컴파일 SDK 37 이상을 요구합니다.
    // 37.2는 이 개발 환경의 SDK Manager에 있는 안정 플랫폼입니다.
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "com.example.youtubegesturecontrol"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            // 네이티브 라이브러리를 축소 규칙 없이 빼내지 않도록 난독화는 켜지 않습니다.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 폰에 깔린 디버그 앱 위에 그대로 올리려고 같은 키로 서명합니다.
            // Play 스토어에 올릴 때는 이 키가 아니라 따로 보관한 업로드 키가 필요합니다.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
    }

    bundle {
        language {
            // 앱 안에서 언어를 바꾸므로, 번들로 만들 때 언어별 분할을 끄면 네 언어가 함께 들어갑니다.
            enableSplit = false
        }
    }

    androidResources {
        // sherpa-onnx가 assets의 모델을 직접 읽으므로 onnx는 압축하지 않습니다.
        noCompress += "onnx"
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
            )
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-service:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    val cameraVersion = "1.6.2"
    implementation("androidx.camera:camera-core:$cameraVersion")
    implementation("androidx.camera:camera-camera2:$cameraVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraVersion")

    implementation("com.google.mediapipe:tasks-vision:1.1.1")
    implementation("com.github.k2-fsa.sherpa-onnx:sherpa-onnx:v1.13.5")

    testImplementation("junit:junit:4.13.2")
}
