plugins { id("com.android.application") }

android {
    namespace = "com.huimeng.bookshop"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.huimeng.bookshop"
        minSdk = 26
        targetSdk = 35
        versionCode = 26
        versionName = "1.0.36"
        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
        buildConfigField("String", "API_BASE_URL", "\"${providers.gradleProperty("apiBaseUrl").getOrElse("http://10.0.2.2:3000/api/v1/")}\"")
    }
    buildFeatures { viewBinding = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}

dependencies {
    androidTestImplementation(files("${android.sdkDirectory}/platforms/android-35/optional/android.test.runner.jar", "${android.sdkDirectory}/platforms/android-35/optional/android.test.base.jar"))
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity:1.10.1")
    implementation("androidx.fragment:fragment:1.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.8.7")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.android.volley:volley:1.2.1")
    implementation("com.github.bumptech.glide:glide:4.16.0")
}
