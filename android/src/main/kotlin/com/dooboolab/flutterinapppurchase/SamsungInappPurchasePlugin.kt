package com.dooboolab.flutterinapppurchase

import android.app.Activity
import android.content.Context
import android.util.Log
import com.samsung.android.sdk.iap.lib.constants.HelperDefine
import com.samsung.android.sdk.iap.lib.helper.IapHelper
import com.samsung.android.sdk.iap.lib.listener.OnConsumePurchasedItemsListener
import com.samsung.android.sdk.iap.lib.listener.OnGetOwnedListListener
import com.samsung.android.sdk.iap.lib.listener.OnGetProductsDetailsListener
import com.samsung.android.sdk.iap.lib.listener.OnPaymentListener
import com.samsung.android.sdk.iap.lib.vo.ConsumeVo
import com.samsung.android.sdk.iap.lib.vo.ErrorVo
import com.samsung.android.sdk.iap.lib.vo.OwnedProductVo
import com.samsung.android.sdk.iap.lib.vo.ProductVo
import com.samsung.android.sdk.iap.lib.vo.PurchaseVo
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

class SamsungInappPurchasePlugin : MethodCallHandler {
    private val TAG = "InappPurchasePlugin"
    private var safeResult: MethodResultWrapper? = null
    private var channel: MethodChannel? = null
    private var context: Context? = null
    private var activity: Activity? = null
    private var iapHelper: IapHelper? = null;
    fun setContext(context: Context?) {
        this.context = context
    }

    fun setActivity(activity: Activity?) {
        this.activity = activity
    }

    fun setChannel(channel: MethodChannel?) {
        this.channel = channel
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        if (call.method == "getStore") {
            result.success(FlutterInappPurchasePlugin.getStore())
            return
        }

        safeResult = MethodResultWrapper(result, channel!!)

//        try {
//            PurchasingService.registerListener(context, purchasesUpdatedListener)
//        } catch (e: Exception) {
//            safeResult!!.error(
//                    call.method,
//                    "Call endConnection method if you want to start over.",
//                    e.message
//            )
//        }
        when (call.method) {
            "initConnection" -> {
                iapHelper = IapHelper.getInstance(context)

                if (BuildConfig.DEBUG) {
                    iapHelper?.setOperationMode(HelperDefine.OperationMode.OPERATION_MODE_TEST)
                } else {
//                    iapHelper?.setOperationMode(HelperDefine.OperationMode.OPERATION_MODE_TEST)
                    iapHelper?.setOperationMode(HelperDefine.OperationMode.OPERATION_MODE_PRODUCTION)
                }

                safeResult!!.success("Billing client ready")
            }

            "endConnection" -> {
                safeResult!!.success("Billing client has ended.")
            }

            "isReady" -> {
                safeResult!!.success(true)
            }

            "showInAppMessages" -> {
                safeResult!!.success("in app messages not supported for samsung")
            }

            "consumeAllItems" -> {
                // consumable is a separate type in amazon
                safeResult!!.success("no-ops in samsung")
            }

            "getProducts",
            "getSubscriptions" -> {
                Log.d(TAG, call.method)
                val skus = call.argument<ArrayList<String>>("productIds")!!
                val productSkus: MutableSet<String> = HashSet()
                for (i in skus.indices) {
                    Log.d(TAG, "Adding " + skus[i])
                    productSkus.add(skus[i])
                }
                val commaSeparatedString = skus.joinToString(",")
                iapHelper?.getProductsDetails(commaSeparatedString, getProductDetailsListener)
            }

            "getAvailableItemsByType" -> {
                val type = call.argument<String>("type")
                Log.d(TAG, "gaibt=$type")
                // NOTE: getPurchaseUpdates doesnt return Consumables which are FULFILLED
                if (type == "inapp") {
                    iapHelper?.getOwnedList("item", getOwnedListListener)
                } else if (type == "subs") {
                    // Subscriptions are retrieved during inapp, so we just return empty list
                    iapHelper?.getOwnedList("subscription", getOwnedListListener)
                } else {
                    safeResult!!.notImplemented()
                }
            }

            "getPurchaseHistoryByType" -> {
                // No equivalent
                safeResult!!.success("[]")
            }

            "buyItemByType" -> {
                val type = call.argument<String>("type")
                //val obfuscatedAccountId = call.argument<String>("obfuscatedAccountId")
                //val obfuscatedProfileId = call.argument<String>("obfuscatedProfileId")
                val sku = call.argument<String>("productId")
                val oldSku = call.argument<String>("oldSku")
                //val prorationMode = call.argument<Int>("prorationMode")!!
                Log.d(TAG, "type=$type||sku=$sku||oldsku=$oldSku")
//                val requestId = PurchasingService.purchase(sku)
                iapHelper?.startPayment(sku, sku, paymentListener)
                Log.d(TAG, "resid=")
            }

            "consumeProduct" -> {
                // consumable is a separate type in amazon
                safeResult!!.success("no-ops in amazon")
            }

            "acknowledgePurchase" -> {
//                PurchasingService.notifyFulfillment(call.argument<String>("receipt"), FulfillmentResult.FULFILLED)
                iapHelper?.consumePurchasedItems(call.argument<String>("receipt"), consumePurchaseItemsListener);
                safeResult!!.success("acknowledged purchase")
            }

            else -> {
                safeResult!!.notImplemented()
            }
        }
    }

    private val consumePurchaseItemsListener: OnConsumePurchasedItemsListener = object : OnConsumePurchasedItemsListener {

        override fun onConsumePurchasedItems(error: ErrorVo?, consumedList: ArrayList<ConsumeVo>?) {

            if (error?.errorCode == IapHelper.IAP_ERROR_NONE) {
                safeResult!!.success("item consumed");
            }
        }

    }


    private val paymentListener: OnPaymentListener = object : OnPaymentListener {
        override fun onPayment(error: ErrorVo?, purchase: PurchaseVo?) {
            error?.let { e ->
                if (e.errorCode == IapHelper.IAP_ERROR_NONE) {

                    purchase?.let {
                        try {

                            val dateString = purchase.purchaseDate
                            val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            val date = format.parse(dateString)
                            val timestamp = date?.time

                            val item = getPurchaseData(
                                    purchase.itemId,
                                    purchase.paymentId,
                                    purchase.purchaseId,
                                    timestamp?.toString(),
                                    purchase.passThroughParam
                            )
                            Log.d(TAG, "opr Putting $item")
                            safeResult!!.success(item.toString())
                            Log.d(TAG, "before invoking purchase updated")
                            safeResult!!.invokeMethod("purchase-updated", item.toString())
                            Log.d(TAG, "after invoking purchase updated")
                        } catch (e: JSONException) {
                            safeResult!!.error(TAG, "E_BILLING_RESPONSE_JSON_PARSE_ERROR", e.message)
                        }
                    }

                } else {
                    val json = JSONObject()
                    json.put("message", "Unknown Error")
                    safeResult!!.invokeMethod("purchase-error", json.toString())
                    Log.d(TAG, "iap request failed")
                }
            }
        }
    }
    private val getOwnedListListener: OnGetOwnedListListener = object : OnGetOwnedListListener {
        override fun onGetOwnedProducts(errorVo: ErrorVo?, productList: ArrayList<OwnedProductVo>?) {
            errorVo?.let { error ->
                if (error.errorCode == IapHelper.IAP_ERROR_NONE) {

                    val items = JSONArray()

                    try {
                        productList?.let { products ->
                            for (purchase in products) {

                                val item = JSONObject()

                                val dateString = purchase.purchaseDate
                                val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                                val date = format.parse(dateString)
                                val timestamp = date?.time

                                Log.d(TAG, "timestamp = $timestamp")
                                Log.d(TAG, "date string = $dateString")

                                item.put("productId", purchase.itemId)
                                item.put("transactionId", purchase.paymentId)
                                item.put("transactionDate", timestamp?.toString())
                                item.put("transactionReceipt", purchase.purchaseId)
                                item.put("purchaseToken", purchase.passThroughParam)
                                item.put("signatureAndroid", "")
//                                item.put("purchaseStateAndroid", "")
//                                if (type == BillingClient.ProductType.INAPP) {
//                                    item.put("isAcknowledgedAndroid", purchase.isAcknowledged)
//                                } else if (type == BillingClient.ProductType.SUBS) {
//                                    item.put("autoRenewingAndroid", purchase.isAutoRenewing)
//                                }
                                items.put(item)
                            }
                            safeResult!!.success(items.toString())
                        }
                    } catch (e: Exception) {
                        safeResult!!.error(TAG, "E_BILLING_RESPONSE_JSON_PARSE_ERROR", e.message)
                    }

                } else {
                    safeResult!!.error(TAG, "FAILED", null)
                    Log.d(TAG, "onGetOwnedList: failed, should retry request")
                }
            }
        }
    }

    private val getProductDetailsListener: OnGetProductsDetailsListener = object : OnGetProductsDetailsListener {


        override fun onGetProducts(errorVo: ErrorVo?, productList: ArrayList<ProductVo>?) {
            errorVo?.let { error ->
                if (error.errorCode == IapHelper.IAP_ERROR_NONE) {

                    val items = JSONArray()

                    try {
                        productList?.let { products ->
                            for (product in products) {

                                val item = JSONObject()
                                item.put("productId", product.itemId)
                                item.put("price", product.itemPrice.toString())
                                item.put("currency", null)
                                when (product.type) {
                                    "item" -> item.put(
                                            "type",
                                            "inapp"
                                    )

                                    "subscription" -> item.put("type", "subs")
                                }
                                item.put("localizedPrice", product.itemPriceString)
                                item.put("title", product.itemName)
                                item.put("description", product.itemDesc)
                                item.put("introductoryPrice", "")
                                item.put("subscriptionPeriodAndroid", "")
                                item.put("freeTrialPeriodAndroid", "")
                                item.put("introductoryPriceCyclesAndroid", 0)
                                item.put("introductoryPricePeriodAndroid", "")
                                Log.d(TAG, "opdr Putting $item")
                                items.put(item)
                            }
                            safeResult!!.success(items.toString())
                        }
                    } catch (e: Exception) {
                        safeResult!!.error(TAG, "E_BILLING_RESPONSE_JSON_PARSE_ERROR", e.message)
                    }

                } else {
                    safeResult!!.error(TAG, "FAILED", null)
                    Log.d(TAG, "onProductDataResponse: failed, should retry request")
                }
            }
        }

        // getItemsByType
//        override fun onGetProducts(error: ErrorVo, products: ArrayList<ProductVo>) {
////            Log.d(TAG, "onProductDataResponse: RequestStatus ($status)")
//
//
//
//            when (status) {
//                ProductDataResponse.RequestStatus.SUCCESSFUL -> {
//                    Log.d(
//                            TAG,
//                            "onProductDataResponse: successful.  The item data map in this response includes the valid SKUs"
//                    )
//                    val productData = response.productData
//                    //Log.d(TAG, "productData="+productData.toString());
//                    val unavailableSkus = response.unavailableSkus
//                    Log.d(
//                            TAG,
//                            "onProductDataResponse: " + unavailableSkus.size + " unavailable skus"
//                    )
//                    Log.d(TAG, "unavailableSkus=$unavailableSkus")
//                    val items = JSONArray()
//                    try {
//                        for ((_, product) in productData) {
//                            //val format = NumberFormat.getCurrencyInstance()
//                            val item = JSONObject()
//                            item.put("productId", product.sku)
//                            item.put("price", product.price)
//                            item.put("currency", null)
//                            when (product.productType) {
//                                ProductType.ENTITLED, ProductType.CONSUMABLE -> item.put(
//                                        "type",
//                                        "inapp"
//                                )
//
//                                ProductType.SUBSCRIPTION -> item.put("type", "subs")
//                            }
//                            item.put("localizedPrice", product.price)
//                            item.put("title", product.title)
//                            item.put("description", product.description)
//                            item.put("introductoryPrice", "")
//                            item.put("subscriptionPeriodAndroid", "")
//                            item.put("freeTrialPeriodAndroid", "")
//                            item.put("introductoryPriceCyclesAndroid", 0)
//                            item.put("introductoryPricePeriodAndroid", "")
//                            Log.d(TAG, "opdr Putting $item")
//                            items.put(item)
//                        }
//                        //System.err.println("Sending "+items.toString());
//                        safeResult!!.success(items.toString())
//                    } catch (e: JSONException) {
//                        safeResult!!.error(TAG, "E_BILLING_RESPONSE_JSON_PARSE_ERROR", e.message)
//                    }
//                }
//
//                ProductDataResponse.RequestStatus.FAILED -> {
//                    safeResult!!.error(TAG, "FAILED", null)
//                    Log.d(TAG, "onProductDataResponse: failed, should retry request")
//                    safeResult!!.error(TAG, "NOT_SUPPORTED", null)
//                }
//
//                ProductDataResponse.RequestStatus.NOT_SUPPORTED -> {
//                    Log.d(TAG, "onProductDataResponse: failed, should retry request")
//                    safeResult!!.error(TAG, "NOT_SUPPORTED", null)
//                }
//            }
//        }

        // buyItemByType
//        override fun onPurchaseResponse(response: PurchaseResponse) {
//            Log.d(TAG, "opr=$response")
//            when (val status = response.requestStatus) {
//                PurchaseResponse.RequestStatus.SUCCESSFUL -> {
//                    val receipt = response.receipt
//                    PurchasingService.notifyFulfillment(
//                            receipt.receiptId,
//                            FulfillmentResult.FULFILLED
//                    )
//                    val date = receipt.purchaseDate
//                    val transactionDate = date.time
//                    try {
//                        val item = getPurchaseData(
//                                receipt.sku,
//                                receipt.receiptId,
//                                receipt.receiptId,
//                                transactionDate.toDouble()
//                        )
//                        Log.d(TAG, "opr Putting $item")
//                        safeResult!!.success(item.toString())
//                        safeResult!!.invokeMethod("purchase-updated", item.toString())
//                    } catch (e: JSONException) {
//                        safeResult!!.error(TAG, "E_BILLING_RESPONSE_JSON_PARSE_ERROR", e.message)
//                    }
//                }
//
//                PurchaseResponse.RequestStatus.FAILED -> {
//                    val json = JSONObject()
//                    json.put("message", "Unknown Error")
//                    safeResult!!.invokeMethod("purchase-error", json.toString())
//                    Log.d(TAG, "iap request failed")
//
////                    safeResult!!.error(
////                            TAG,
////                            "buyItemByType",
////                            "billingResponse is not ok: $status"
////                    )
//                }
//
//                else -> {}
//            }
//        }

        // getAvailableItemsByType
//        override fun onPurchaseUpdatesResponse(response: PurchaseUpdatesResponse) {
//            Log.d(TAG, "opudr=$response")
//            when (response.requestStatus) {
//                PurchaseUpdatesResponse.RequestStatus.SUCCESSFUL -> {
//                    val items = JSONArray()
//                    try {
//                        val receipts = response.receipts
//                        for (receipt in receipts) {
//                            val date = receipt.purchaseDate
//                            val transactionDate = date.time
//                            val item = getPurchaseData(
//                                    receipt.sku,
//                                    receipt.receiptId,
//                                    receipt.receiptId,
//                                    transactionDate.toDouble()
//                            )
//                            Log.d(TAG, "opudr Putting $item")
//                            items.put(item)
//                        }
//                        safeResult!!.success(items.toString())
//                    } catch (e: JSONException) {
//                        safeResult!!.error(TAG, "E_BILLING_RESPONSE_JSON_PARSE_ERROR", e.message)
//                    }
//                }
//
//                PurchaseUpdatesResponse.RequestStatus.FAILED -> safeResult!!.error(
//                        TAG,
//                        "FAILED",
//                        null
//                )
//
//                PurchaseUpdatesResponse.RequestStatus.NOT_SUPPORTED -> {
//                    Log.d(TAG, "onPurchaseUpdatesResponse: failed, should retry request")
//                    safeResult!!.error(TAG, "NOT_SUPPORTED", null)
//                }
//            }
//        }
    }


    @Throws(JSONException::class)
    fun getPurchaseData(
            productId: String?, transactionId: String?, transactionReceipt: String?,
            transactionDate: String?, purchaseToken: String?
    ): JSONObject {
        val item = JSONObject()
        item.put("productId", productId)
        item.put("transactionId", transactionId)
        item.put("transactionReceipt", transactionReceipt)
        item.put("transactionDate", transactionDate)
        item.put("dataAndroid", null)
        item.put("signatureAndroid", null)
        item.put("purchaseToken", null)
        return item
    }
}