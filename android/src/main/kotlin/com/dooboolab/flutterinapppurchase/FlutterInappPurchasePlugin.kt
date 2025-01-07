package com.dooboolab.flutterinapppurchase

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import android.content.Context
import io.flutter.plugin.common.MethodChannel
import io.flutter.embedding.engine.plugins.FlutterPlugin.FlutterPluginBinding
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.PluginRegistry.Registrar
import android.content.pm.PackageManager.NameNotFoundException
import android.util.Log
import android.widget.Toast

/** FlutterInappPurchasePlugin  */
class FlutterInappPurchasePlugin : FlutterPlugin, ActivityAware {
    private var androidInappPurchasePlugin: AndroidInappPurchasePlugin? = null
    private var amazonInappPurchasePlugin: AmazonInappPurchasePlugin? = null
    private var samsungInappPurchasePlugin: SamsungInappPurchasePlugin? = null
    private var channel: MethodChannel? = null
    override fun onAttachedToEngine(binding: FlutterPluginBinding) {
        onAttached(binding.applicationContext, binding.binaryMessenger)
    }

    private fun onAttached(context: Context, messenger: BinaryMessenger) {
        isAndroid = isPackageInstalled(context, "com.android.vending")
        isAmazon = isPackageInstalled(context, "com.amazon.venezia")
        isSamsung = isPackageInstalled(context, "com.sec.android.app.samsungapps")
        Log.d("Is Samsung value", isSamsung.toString())

//        if(BuildConfig.DEBUG) {
//            Toast.makeText(context,"Samsung Amazon Android $isSamsung  $isAmazon $isAndroid", Toast.LENGTH_LONG).show()
//        }

//        if(BuildConfig.DEBUG){
//            isAndroid = false;
//            isAmazon = true;
//        }

        // In the case of an amazon device which has been side loaded with the Google Play store,
        // we should use the store the app was installed from.
        if (isAmazon && isAndroid) {
            if (isAppInstalledFrom(context, "amazon")) {
                isAndroid = false

            } else {
                isAmazon = false
            }
        }

        else if(isSamsung && isAndroid) {
            if(isAppInstalledFrom(context, "samsungapps")) {
                isAndroid = false
            } else {
                isSamsung = false
            }
        }

//to test samsung iap
//        if(BuildConfig.DEBUG) {
//            isSamsung = true
//            isAndroid = false
//        }


        channel = MethodChannel(messenger, "flutter_inapp")
        if (isAndroid) {
            androidInappPurchasePlugin = AndroidInappPurchasePlugin()
            androidInappPurchasePlugin!!.setContext(context)
            androidInappPurchasePlugin!!.setChannel(channel)
            channel!!.setMethodCallHandler(androidInappPurchasePlugin)
        } else if (isAmazon) {
            amazonInappPurchasePlugin = AmazonInappPurchasePlugin()
            amazonInappPurchasePlugin!!.setContext(context)
            amazonInappPurchasePlugin!!.setChannel(channel)
            channel!!.setMethodCallHandler(amazonInappPurchasePlugin)
        } else if (isSamsung) {
            samsungInappPurchasePlugin = SamsungInappPurchasePlugin()
            samsungInappPurchasePlugin!!.setContext(context)
            samsungInappPurchasePlugin!!.setChannel(channel)
            channel!!.setMethodCallHandler(samsungInappPurchasePlugin)
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPluginBinding) {
        channel!!.setMethodCallHandler(null)
        channel = null
        if (isAndroid) {
            androidInappPurchasePlugin!!.setChannel(null)
        } else if (isAmazon) {
            amazonInappPurchasePlugin!!.setChannel(null)
        } else if (isSamsung) {
            samsungInappPurchasePlugin!!.setChannel(null)
        }
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        if (isAndroid) {
            androidInappPurchasePlugin!!.setActivity(binding.activity)
        } else if (isAmazon) {
            amazonInappPurchasePlugin!!.setActivity(binding.activity)
        } else if (isSamsung) {
            samsungInappPurchasePlugin!!.setActivity(binding.activity)
        }
    }

    override fun onDetachedFromActivity() {
        if (isAndroid) {
            androidInappPurchasePlugin!!.setActivity(null)
            androidInappPurchasePlugin!!.onDetachedFromActivity()
        } else if (isAmazon) {
            amazonInappPurchasePlugin!!.setActivity(null)
        } else if (isSamsung) {
            samsungInappPurchasePlugin!!.setActivity(null)
        }
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        onAttachedToActivity(binding)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity()
    }

    private fun setAndroidInappPurchasePlugin(androidInappPurchasePlugin: AndroidInappPurchasePlugin) {
        this.androidInappPurchasePlugin = androidInappPurchasePlugin
    }

    private fun setAmazonInappPurchasePlugin(amazonInappPurchasePlugin: AmazonInappPurchasePlugin) {
        this.amazonInappPurchasePlugin = amazonInappPurchasePlugin
    }

    companion object {
        private var isAndroid = false
        private var isAmazon = false
        private var isSamsung = false

        fun getStore(): String {
           return if (!isAndroid && !isAmazon) "none" else if (isAndroid) "play_store" else "amazon"
        }

        fun registerWith(registrar: Registrar) {
            val instance = FlutterInappPurchasePlugin()
            instance.onAttached(registrar.context(), registrar.messenger())
        }

        private fun isPackageInstalled(ctx: Context, packageName: String): Boolean {
            return try {
                ctx.packageManager.getPackageInfo(packageName, 0)
                true
            } catch (e: NameNotFoundException) {
                false
            }
        }

        fun isAppInstalledFrom(ctx: Context, installer: String?): Boolean {
            val installerPackageName = ctx.packageManager.getInstallerPackageName(ctx.packageName)
            return installer != null && installerPackageName != null && installerPackageName.contains(
                installer
            )
        }
    }
}