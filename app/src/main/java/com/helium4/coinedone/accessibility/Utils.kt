package com.helium4.coinedone.accessibility

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Browser
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlin.collections.HashMap
import kotlin.collections.List
import kotlin.collections.set
import androidx.core.content.ContextCompat.startActivity
import com.helium4.coinedone.AcessActivity


/**
 * Checking accessibility conditions on events and
 * Triggering accessibility actions like "back button"
 */
class Utils {

companion object{
    var myRestrictedAddress: String?=null
    var redirectTo: String?=null
}

    data class Builder(
        var myRestrictedAddress: String? = null,
        var redirectTo: String? = null
    ) {

        fun setMyRestrictedAddress(myRestrictedAddress: String) =
            apply { this.myRestrictedAddress = filterInputAddress(myRestrictedAddress) }

        private fun filterInputAddress(edtRestrictedAddress: String): String {
            return if (edtRestrictedAddress
                    .startsWith("www.")
            ) edtRestrictedAddress.split("www.")
                .toTypedArray()[1] else edtRestrictedAddress
        }

        fun setRedirectTo(redirectTo: String) = apply { this.redirectTo = redirectTo }
        fun build() {
            Utils.myRestrictedAddress = myRestrictedAddress
            Utils.redirectTo = redirectTo


        }

    }

    private val previousUrlDetections: HashMap<String, Long> = HashMap()
    var packageName: String = ""
    private var foregroundAppName: String? = null
    private var browserConfig: BrowserConfig? = null

    fun filterBrowserURL(
        event: AccessibilityEvent,
        AccessibilityService: Service,
        getSupportedBrowsers: List<BrowserConfig>
    ) {

        try {
            //get accessibility node info
            val parentNodeInfo = event.source ?: return

            if (event.packageName != null) {
                packageName = event.packageName.toString()
            }
            //get foreground app name
            val packageManager: PackageManager = AccessibilityService.packageManager
            try {
                val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
                foregroundAppName = packageManager.getApplicationLabel(applicationInfo) as String
            } catch (e: PackageManager.NameNotFoundException) {
                e.printStackTrace()
            }
            //get all the child views from the nodeInfo
            getChild(parentNodeInfo)

            //fetch urls from different browsers
            browserConfig = null
            for (supportedConfig in getSupportedBrowsers) {
                if (supportedConfig.packageName == packageName) {
                    browserConfig = supportedConfig
                }
            }
            //this is not supported browser, so exit
            if (browserConfig == null) {
                return
            }

            val capturedUrl =
                captureUrl(parentNodeInfo, browserConfig)
            parentNodeInfo.recycle()


            //we can't find a url. Browser either was updated or opened page without url text field
            if (capturedUrl == null) {
                return
            }
            Log.e("TAG", "event: "+event )
            Log.e("TAG", "capturedUrl: "+capturedUrl )
            Log.e("TAG", "eventt: "+event.contentChangeTypes )

            val eventTime = event.eventTime
            val detectionId = "$packageName, and url $capturedUrl"
            val lastRecordedTime: Long? =
                if (previousUrlDetections.containsKey(detectionId)) previousUrlDetections[detectionId] else 0
            //some kind of redirect throttling

            if (eventTime - lastRecordedTime!! > 500) {
                previousUrlDetections[detectionId] = eventTime
                if(event.contentChangeTypes ==1)
                analyzeCapturedUrl(
                    AccessibilityService,
                    capturedUrl,
                    browserConfig?.packageName ?: ""
                )
            }else{
                analyzeCapturedUrl(
                    AccessibilityService,
                    capturedUrl,
                    browserConfig?.packageName ?: ""
                )
            }
        } catch (e: Exception) {
            //ignored
        }
    }

    private fun getChild(info: AccessibilityNodeInfo) {
        val i = info.childCount
        for (p in 0 until i) {
            val n = info.getChild(p)
            if (n != null) {
                n.viewIdResourceName
                if (n.text != null) {
                    n.text.toString()
                }
                getChild(n)
            }
        }
    }


    private fun captureUrl(info: AccessibilityNodeInfo, config: BrowserConfig?): String? {
        if (config == null) return null
        val nodes = info.findAccessibilityNodeInfosByViewId(config.addressBarId)
        if (nodes == null || nodes.size <= 0) {
            return null
        }
        val addressBarNodeInfo = nodes[0]
        var url: String? = null
        if (addressBarNodeInfo.text != null) {
            url = addressBarNodeInfo.text.toString()
        }
        addressBarNodeInfo.recycle()
        return url
    }

    private fun analyzeCapturedUrl(
        serviceMy: Service,
        capturedUrl: String,
        browserPackage: String
    ) {
        Log.e("TAG", "myRestrictedAddress: "+myRestrictedAddress )
        Log.e("TAG", "redirectTo: "+redirectTo )

        if (capturedUrl.lowercase().startsWith(myRestrictedAddress ?: "")
            && myRestrictedAddress ?: "" != ""
        ) {redirectTo
            val replaced = redirectTo
            performRedirect(serviceMy, replaced ?: "", browserPackage)
        }
    }

    // we just reopen the browser app with our redirect url using service context
    private fun performRedirect(
        serviceMy: Service,
        redirectUrl: String,
        browserPackage: String
    ) {
        var url = redirectUrl
        if (!redirectUrl.startsWith("https://")) {
            url = "https://$redirectUrl"
        }
        try {
            if (url == "")
                return;


            val myService: Utils

            myService = this


//            Intent intent = new Intent("android.intent.category.LAUNCHER");
//            intent.setClassName("com.your.package", "com.your.package.MainActivity");
//            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//            startActivity(intent)

            val intent2  =  Intent("android.intent.category.LAUNCHER")
            intent2.setClassName("com.helium4.coinedone","com.helium4.coinedone.AcessActivity")
            intent2.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.setPackage(browserPackage)
            intent.putExtra(Browser.EXTRA_APPLICATION_ID, browserPackage)

            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

            serviceMy.startActivity(intent2)
            Log.e("TAG", "Activityyyy: Started" )

            //serviceMy.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // the expected browser is not installed
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            serviceMy.startActivity(i)
        }
    }


}
