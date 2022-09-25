package com.softstackdev.googlebilling

object BillingDependency {

    /**
     * BASE_64_ENCODED_PUBLIC_KEY should be YOUR APPLICATION'S PUBLIC KEY
     * (that you got from the Google Play developer console, usually under Services & APIs tab).
     * This is not your developer public key, it's the *app-specific* public key.
     *
     * Just like everything else in this class, this public key should be kept on your server.
     * But if you don't have a server, then you should obfuscate your app so that hackers cannot
     * get it. If you cannot afford a sophisticated obfuscator, instead of just storing the entire
     * literal string here embedded in the program,  construct the key at runtime from pieces or
     * use bit manipulation (for example, XOR with some other string) to hide
     * the actual key.  The key itself is not secret information, but we don't
     * want to make it easy for an attacker to replace the public key with one
     * of their own and then fake messages from the server.
     */
    lateinit var base64EncodedPublicKey: String

    lateinit var localProductDetails: ILocalProductDetails

    /**
     * This method will initialize all the dependency necessary for the app and
     * must be called from [android.app.Application] subclass
     */
    fun initWith(localProductDetails: ILocalProductDetails, base64EncodedPublicKey: String) {
        this.localProductDetails = localProductDetails
        this.base64EncodedPublicKey = base64EncodedPublicKey
    }
}