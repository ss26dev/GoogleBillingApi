# GoogleBillingApi

## Introduction

Use this api to implement [billing process from google](https://developer.android.com/google/play/billing) to an Android application.

This api is based on [Trivial Drive Kotlin](https://github.com/android/play-billing-samples/tree/master/TrivialDriveKotlin) sample.


## Integration

* Add the JitPack repository to your root build.gradle
```
allprojects {
    repositories {
        ...
        maven { url "https://jitpack.io" }
    }
}
```

* Add the latest version dependency to your module build.gradle
```
dependencies {
    implementation 'com.github.ss26dev:GoogleBillingApi:v1.0.0'
}
```
