# GoogleBillingApi

## Introduction

Use this api to implement [billing process from google](https://developer.android.com/google/play/billing) to an Android application.

This api is based on [Trivial Drive Kotlin](https://github.com/android/play-billing-samples/tree/master/TrivialDriveKotlin) sample.


## Integration

### I. Using jitpack repository:
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
    implementation 'com.github.ss26dev:GoogleBillingApi:v3.1'
}
```

### II. Importing as external module:
* Add the following lines to target project *settings.gradle*
```
include ':googleBillingApi'
project(':googleBillingApi').projectDir = new File(settingsDir, '../GoogleBillingApi/GoogleBillingApiLib')
```

* Also add the library as a dependency to target project module (module_name/build.gradle):
```
implementation project(':googleBillingApi')
```

## Build requirements

Use JDK 17 and Android SDK 37. The Gradle wrapper uses Gradle 9.8.1 with
Android Gradle Plugin 9.4.1. The library supports Android 6.0 (API 23) and newer.

Dependencies use stable releases: coroutines 1.11.0, AndroidX Core 1.19.1,
AppCompat 1.8.0, Robolectric 4.17, AndroidX Test JUnit 1.3.0, and Espresso 3.7.0.
JUnit remains at 4.13.2 and Billing Client remains pinned as described below.

Only `GoogleBillingApiLib` is included in `settings.gradle`. The legacy `sample`
directory has aligned dependency versions but still requires migration from the
old SKU APIs before it can be enabled and built.

## Checkout compatibility

One-time checkout omits absent offer tokens. Subscription checkout selects the
nonempty offer token for the displayed billing period and applies replacement
parameters only to subscriptions. Missing product details, invalid offers, or
a destroyed Activity reject checkout without granting access.

Billing Client 9.1.0 can call
`ActivityOptions.setPendingIntentBackgroundActivityStartMode` on a device that
reports Android 14 but lacks that framework method (Crashlytics issue
`a6e619dc60f9cd6944e726e6b3ba602b`). The manifest aliases Google's checkout
component to `CompatibleProxyBillingActivity`. It retries only this missing-method
launch without ActivityOptions; Google's result handling, cancellation,
transaction metadata, and saved-state handling remain in place. Supported devices
use the normal SDK path. No SDK bytecode or Android version values are modified.

Billing Client is pinned to 9.1.0. Recheck initialization order, request codes
(100, 101, 110), and intent extras before upgrading it.

Run the real-SDK checkout parameter and framework compatibility tests with:

```sh
bash gradlew :GoogleBillingApiLib:testDebugUnitTest :GoogleBillingApiLib:assembleRelease
```


## Checkout outcome callbacks (local development, not yet published)

The existing three-argument `makePurchase` method is preserved. Its Boolean means
that a request was accepted, not that payment succeeded. Use the new overload for
actual launch and purchase results:

```kotlin
billingViewModel.makePurchase(activity, product, "p1y") { event ->
    when (event.status) {
        CheckoutStatus.PENDING -> recordPending(event.productId)
        CheckoutStatus.COMPLETED -> recordDelivered(event.productId)
        CheckoutStatus.CANCELLED -> recordCancellation(event.productId)
        else -> recordDiagnostic(event.status, event.stage, event.responseCode)
    }
}
```

The sample record functions represent application-owned handlers. `CheckoutEvent`
contains only a process-local request ID, product ID, requested billing period,
status, stage, optional BillingResponseCode and categorical reason. It does not
contain order IDs, purchase tokens, signatures or Google's raw debug message.
Callbacks execute on main; listener failures cannot interrupt billing.

`LAUNCHED` means `launchBillingFlow` returned OK. `VERIFIED` means the received
PURCHASED transaction passed the existing signature verification. `COMPLETED`
follows acknowledgement/consumption and local entitlement delivery. `PROCESSING_FAILED`
means a valid payment needs acknowledgement/consumption recovery, not that payment
was declined. Generic SDK errors remain response codes; the library does not invent
payment-decline reasons. `ALREADY_OWNED` is an explicit Play response. Empty results
and expired tracking are `UNRESOLVED`, not cancellations.

Only a matching purchase callback whose purchase timestamp is not older than the
request can attach a new token. Device/server clock skew can undercount diagnostic
attribution; it never blocks verification or entitlement delivery.
Ordinary restore queries never create checkout events. They can finish an already
tracked pending/failed-delivery token in the same process. Duplicate token callbacks
cannot complete a second request, and repeated consumption callbacks cannot add the
same credits twice. An overlapping active checkout is rejected with
`CHECKOUT_IN_PROGRESS`; pending payments release that slot.

Listeners may survive Activity recreation and Shop closure, so **do not capture an
Activity or View**. The app should retain immutable measurement context and use a
weak reference for optional UI feedback. Attribution is process-local: after process
death, existing purchases are restored without inventing a new checkout. This API is
not a durable transaction ledger or server-side revenue verification.

On subsequent billing activity, unresolved active requests older than five minutes
and retained pending/delivery callbacks older than seven days expire as unknown.
At most 32 retained attempts are kept. These tracking bounds do not cancel payments
or revoke entitlements. SDK connection retries/restore queries may recover delivery;
this change does not introduce a persistent background acknowledgement scheduler.

The app uses the sibling source module during validation. Publish a new library
version before switching it back to a Maven/JitPack dependency; v3.1 does not expose
this overload. No version or release tag has been changed by this implementation.
