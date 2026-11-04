plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "dev.mwidget.runtime"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.mwidget.runtime"
        minSdk = 31
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
        testInstrumentationRunner = "dev.mwidget.runtime.RuntimeSmoke"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    sourceSets.getByName("androidTest").assets.srcDir(rootProject.file("themes/tape-deck"))
    testOptions.unitTests {
        isIncludeAndroidResources = true
        all { test -> test.systemProperty("mwidget.samples",rootProject.file("dist").absolutePath) }
    }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
