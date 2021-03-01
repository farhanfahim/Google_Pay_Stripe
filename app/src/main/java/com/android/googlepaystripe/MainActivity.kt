package com.android.googlepaystripe

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.StripeFactory
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wallet.*
import com.stripe.android.ApiResultCallback
import com.stripe.android.GooglePayConfig
import com.stripe.android.PaymentConfiguration
import com.stripe.android.Stripe
import com.stripe.android.model.PaymentMethod
import com.stripe.android.model.PaymentMethodCreateParams
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject


class MainActivity : AppCompatActivity() {

    var btn:ImageButton? = null
    private val stripe: Stripe by lazy {
        StripeFactory(this).create()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btn = findViewById(R.id.btnGooglePay)
        PaymentConfiguration.init(this, "Your publishable key")

        btn!!.setOnClickListener {
            payWithGoogle()
        }

        isReadyToPay()

    }

    private val paymentsClient: PaymentsClient by lazy {
        Wallet.getPaymentsClient(
                this,
                Wallet.WalletOptions.Builder()
                        .setEnvironment(WalletConstants.ENVIRONMENT_TEST)
                        .build()
        )
    }

    @Throws(JSONException::class)
    private fun createIsReadyToPayRequest(): IsReadyToPayRequest {
        val allowedAuthMethods = JSONArray()
        allowedAuthMethods.put("PAN_ONLY")
        allowedAuthMethods.put("CRYPTOGRAM_3DS")
        val allowedCardNetworks = JSONArray()
        allowedCardNetworks.put("AMEX")
        allowedCardNetworks.put("DISCOVER")
        allowedCardNetworks.put("MASTERCARD")
        allowedCardNetworks.put("VISA")
        val cardParameters = JSONObject()
        cardParameters.put("allowedAuthMethods", allowedAuthMethods)
        cardParameters.put("allowedCardNetworks", allowedCardNetworks)
        val cardPaymentMethod = JSONObject()
        cardPaymentMethod.put("type", "CARD")
        cardPaymentMethod.put("parameters", cardParameters)
        val allowedPaymentMethods = JSONArray()
        allowedPaymentMethods.put(cardPaymentMethod)
        val isReadyToPayRequestJson = JSONObject()
        isReadyToPayRequestJson.put("apiVersion", 2)
        isReadyToPayRequestJson.put("apiVersionMinor", 0)
        isReadyToPayRequestJson.put("allowedPaymentMethods", allowedPaymentMethods)
        return IsReadyToPayRequest.fromJson(isReadyToPayRequestJson.toString())
    }

    private fun isReadyToPay() {
        val request: IsReadyToPayRequest = createIsReadyToPayRequest()
        paymentsClient.isReadyToPay(request)
                .addOnCompleteListener { task ->
                    try {
                        if (task.isSuccessful) {
                            btn!!.visibility = View.VISIBLE
                        } else {
                            btn!!.visibility = View.GONE
                        }
                    } catch (exception: ApiException) {
                    }
                }
    }

    private fun createPaymentDataRequest(): PaymentDataRequest {
        val cardPaymentMethod = JSONObject()
                .put("type", "CARD")
                .put(
                        "parameters",
                        JSONObject()
                                .put("allowedAuthMethods", JSONArray()
                                        .put("PAN_ONLY")
                                        .put("CRYPTOGRAM_3DS"))
                                .put("allowedCardNetworks",
                                        JSONArray()
                                                .put("AMEX")
                                                .put("DISCOVER")
                                                .put("MASTERCARD")
                                                .put("VISA"))

                                // require billing address
                                .put("billingAddressRequired", true)
                                .put(
                                        "billingAddressParameters",
                                        JSONObject()
                                                // require full billing address
                                                .put("format", "MIN")

                                                // require phone number
                                                .put("phoneNumberRequired", true)
                                )
                )
                .put(
                        "tokenizationSpecification",
                        GooglePayConfig(this).tokenizationSpecification
                )

        // create PaymentDataRequest
        val paymentDataRequest = JSONObject()
                .put("apiVersion", 2)
                .put("apiVersionMinor", 0)
                .put("allowedPaymentMethods",
                        JSONArray().put(cardPaymentMethod))
                .put("transactionInfo", JSONObject()
                        .put("totalPrice", "10.00")
                        .put("totalPriceStatus", "FINAL")
                        .put("currencyCode", "USD")
                )
                .put("merchantInfo", JSONObject()
                        .put("merchantName", "Example Merchant"))

                // require email address
                .put("emailRequired", true)
                .toString()

        return PaymentDataRequest.fromJson(paymentDataRequest)
    }

    private fun payWithGoogle() {
        AutoResolveHelper.resolveTask(
                paymentsClient.loadPaymentData(createPaymentDataRequest()),
                this@MainActivity,
                LOAD_PAYMENT_DATA_REQUEST_CODE
        )
    }

    companion object {
        private const val LOAD_PAYMENT_DATA_REQUEST_CODE = 53
    }

    override fun onActivityResult(
            requestCode: Int,
            resultCode: Int,
            data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            LOAD_PAYMENT_DATA_REQUEST_CODE -> {
                when (resultCode) {
                    Activity.RESULT_OK -> {
                        if (data != null) {
                            onGooglePayResult(data)
                        }
                    }
                    Activity.RESULT_CANCELED -> {
                        // Cancelled
                    }
                    AutoResolveHelper.RESULT_ERROR -> {
                        // Log the status for debugging
                        // Generally there is no need to show an error to
                        // the user as the Google Payment API will do that
                        val status = AutoResolveHelper.getStatusFromIntent(data)
                    }
                    else -> {
                        // Do nothing.
                    }
                }
            }
            else -> {
                // Handle any other startActivityForResult calls you may have made.
            }
        }
    }

    private fun onGooglePayResult(data: Intent) {
        val paymentData = PaymentData.getFromIntent(data) ?: return
        val paymentMethodCreateParams =
                PaymentMethodCreateParams.createFromGooglePay(
                        JSONObject(paymentData.toJson())
                )


        // now use the `paymentMethodCreateParams` object to create a PaymentMethod
        stripe.createPaymentMethod(
                paymentMethodCreateParams,
                callback = object : ApiResultCallback<PaymentMethod> {
                    override fun onSuccess(result: PaymentMethod) {
                       // showSnackbar("Created PaymentMethod ${result.id}")
                        Log.e("StripeExample", "Created PaymentMethod ${result.id}")
                        Log.e("StripeExample", "Created PaymentMethod $data")
                    }

                    override fun onError(e: Exception) {
                        Log.e("StripeExample", "Exception while creating PaymentMethod", e)
                      //  showSnackbar("Exception while creating PaymentMethod")
                    }
                }
        )


    }

}