# The SDK explicitly launches the original component, which aliases this subclass.
-keep class com.softstackdev.googlebilling.CompatibleProxyBillingActivity { *; }

# The missing-PendingIntent guard recognizes the pinned SDK onCreate boundary.
# Keep it from being inlined into our subclass in minified app builds.
-keepclassmembers class com.android.billingclient.api.ProxyBillingActivity {
    protected void onCreate(android.os.Bundle);
}
