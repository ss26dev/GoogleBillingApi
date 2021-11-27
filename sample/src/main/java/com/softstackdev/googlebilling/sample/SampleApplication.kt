package com.softstackdev.googlebilling.sample

import android.app.Application
import com.softstackdev.googlebilling.BillingDependency
import com.softstackdev.googlebilling.BillingRepository
import com.softstackdev.googlebilling.sample.billing.LocalSkuDetails
import com.softstackdev.googlebilling.sample.billing.SkuProductId

class SampleApplication: Application() {

    override fun onCreate() {
        super.onCreate()

        //init GoogleBilling library
        BillingDependency.initWith(LocalSkuDetails, getBase64PublicKey())
        initSkuDetails()
        BillingRepository.getInstance(this)
    }

    private fun initSkuDetails() {
        SkuProductId.DONATE_1
    }
}