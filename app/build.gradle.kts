plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}
val tallyAiEnabled = providers.gradleProperty("tallyAi").map {
    require(it == "true" || it == "false") { "tallyAi must be true or false" }
    it.toBoolean()
}.getOrElse(true)
// Never fall back to a debug key for release distribution.
val releaseSigningValues = listOf("TALLY_KEYSTORE_FILE", "TALLY_KEYSTORE_PASSWORD", "TALLY_KEY_ALIAS", "TALLY_KEY_PASSWORD")
    .associateWith { providers.environmentVariable(it).orNull }
val releaseSigningReady = releaseSigningValues.values.all { !it.isNullOrBlank() }

val validateReleaseSigning = tasks.register("validateReleaseSigning") {
    doLast {
        check(releaseSigningReady) {
            "Release signing is required. Set TALLY_KEYSTORE_FILE, TALLY_KEYSTORE_PASSWORD, TALLY_KEY_ALIAS and TALLY_KEY_PASSWORD. Use the existing app signing identity for updates."
        }
        check(file(releaseSigningValues.getValue("TALLY_KEYSTORE_FILE")!!).isFile) { "Release keystore file does not exist" }
    }
}

android {
    namespace = "com.smartexpense"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.smartexpense"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "0.2.3"
        buildConfigField("boolean", "LOCAL_AI_ENABLED", tallyAiEnabled.toString())
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    sourceSets.getByName("androidTest").assets.srcDir("schemas")
    signingConfigs {
        if (releaseSigningReady) create("distribution") {
            storeFile = file(releaseSigningValues.getValue("TALLY_KEYSTORE_FILE")!!)
            storePassword = releaseSigningValues.getValue("TALLY_KEYSTORE_PASSWORD")
            keyAlias = releaseSigningValues.getValue("TALLY_KEY_ALIAS")
            keyPassword = releaseSigningValues.getValue("TALLY_KEY_PASSWORD")
            enableV1Signing = true
            enableV2Signing = true
        }
    }
    buildTypes {
        getByName("release") {
            isDebuggable = false
            if (releaseSigningReady) signingConfig = signingConfigs.getByName("distribution")
        }
    }
    if (tallyAiEnabled) sourceSets.getByName("main").assets.srcDir("src/localAi/assets")
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    testOptions { unitTests.isReturnDefaultValues = true }
}
tasks.configureEach {
    if (name == "preReleaseBuild" || name == "validateSigningRelease" || name == "packageRelease" || name == "signReleaseBundle") {
        dependsOn(validateReleaseSigning)
    }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation(platform("androidx.compose:compose-bom:2025.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.12.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.navigation:navigation-compose:2.9.6")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    implementation("androidx.datastore:datastore-preferences:1.2.0")
    implementation("com.google.dagger:hilt-android:2.57.1")
    ksp("com.google.dagger:hilt-compiler:2.57.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.room:room-testing:2.8.4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
