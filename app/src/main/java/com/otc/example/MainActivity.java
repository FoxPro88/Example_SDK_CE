package com.otc.example;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.Scroller;
import android.widget.TextView;
import android.widget.Toast;

import com.androidnetworking.AndroidNetworking;
import com.androidnetworking.common.Priority;
import com.androidnetworking.error.ANError;
import com.androidnetworking.interceptors.HttpLoggingInterceptor;
import com.androidnetworking.interfaces.ParsedRequestListener;
import com.androidnetworking.interfaces.StringRequestListener;
import com.otc.sdk.commerce.OtcApplication;
import com.otc.sdk.commerce.data.model.request.SessionTokenRequest;
import com.otc.sdk.commerce.data.model.request.authorization.AuthorizationRequest;
import com.otc.sdk.commerce.data.model.request.authorization.Device;
import com.otc.sdk.commerce.data.model.request.authorization.Header;
import com.otc.sdk.commerce.data.model.request.authorization.Merchant;
import com.otc.sdk.commerce.data.model.request.authorization.Order;
import com.otc.sdk.commerce.data.model.response.SessionTokenResponse;
import com.otc.sdk.commerce.presentation.custom.PaymentViewCustom;
import com.otc.sdk.commerce.util.Utils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.UUID;

import okhttp3.Authenticator;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Route;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private static String ENDPOINT = "apiqa.ecore.mobi";
    private static String TENANT = "lpg";
    private static String MERCHANT = "40116061";

    private String mAmount = "";
    private String mPurchaseNumber = "";


    EditText etAmount;
    EditText etPurchaseNumber;
    Button btnPay;
    RelativeLayout viewLoading;
    OtcApplication OTC_SDK;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        init();
    }

    private void init() {

        viewLoading = findViewById(R.id.loading_view);

        etAmount = findViewById(R.id.et_amount);
        etPurchaseNumber = findViewById(R.id.et_purchase_number);
        btnPay = findViewById(R.id.btn_pay);

        AndroidNetworking.initialize(this);
        btnPay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                payment();
            }
        });

    }

    private void payment() {

        mAmount = etAmount.getText().toString();
        if (mAmount == "") {
            etAmount.setError("Ingresar un monto a pagar.");
            return;
        }

        mPurchaseNumber = etPurchaseNumber.getText().toString();
        if (mPurchaseNumber == "") {
            etPurchaseNumber.setError("Ingresar un número de pedido.");
            return;
        }

        ApiSecurity();

    }

    private void ApiSecurity() {

        viewLoading.setVisibility(View.VISIBLE);

        final String username = "pgaldamez@quiputech.com";
        final String password = "Alemania#2006";

        OkHttpClient client = new OkHttpClient.Builder().authenticator(new Authenticator() {
            @Override
            public Request authenticate(Route route, okhttp3.Response response) throws IOException {
                String credential = Credentials.basic(username, password);
                return response.request().newBuilder().header("Authorization", credential).build();
            }
        }).build();

        String path = "https://apiqa.ecore.mobi/api.security/v2/lpg/security/accessToken";
        String[] values = {ENDPOINT, TENANT, MERCHANT};

        AndroidNetworking.enableLogging(HttpLoggingInterceptor.Level.BODY);
        AndroidNetworking.get(MessageFormat.format(path, values))
                .setOkHttpClient(client)
                .setTag(this)
                .setPriority(Priority.HIGH)
                .build()
                .getAsString(new StringRequestListener() {
                    @Override
                    public void onResponse(String response) {

                        viewLoading.setVisibility(View.GONE);

                        ApiRegisterOrder(response);
                    }

                    @Override
                    public void onError(ANError error) {

                        viewLoading.setVisibility(View.GONE);

                        Log.i(TAG, "onError: " + error.getErrorCode());
                        Log.i(TAG, "onError: " + error.getErrorDetail());
                        Log.i(TAG, "onError: " + error.getErrorBody());
                        Log.e(TAG, "onError: ", error);

                        Toast.makeText(MainActivity.this, "Error : Revisar el log....", Toast.LENGTH_SHORT).show();

                    }
                });

    }

    private void ApiRegisterOrder(final String accessToken) {

        viewLoading.setVisibility(View.VISIBLE);



        SessionTokenRequest request = new SessionTokenRequest();
        request.setChannel(OtcApplication.getChannel().name());
        request.setCurrency(OtcApplication.getCurrency().name());
        request.setAmount(Utils.parseAmount(mAmount));

        String path = "https://{0}/api.ecommerce/v3/{1}/token/session/{2}";
        String[] values = {ENDPOINT, TENANT, MERCHANT};

        AndroidNetworking.post(MessageFormat.format(path, values))
                .setTag(this)
                .addHeaders("Authorization", accessToken)
                .addHeaders("Content-Type", "application/json")
                .addApplicationJsonBody(request)
                .setPriority(Priority.HIGH)
                .build()
                .getAsObject(SessionTokenResponse.class, new ParsedRequestListener<SessionTokenResponse>() {
                    @Override
                    public void onResponse(SessionTokenResponse response) {
                        //-------------------------------- SDK OTC -------------------------------------------------
                        PaymentViewCustom sdkCustom = new PaymentViewCustom();
                        sdkCustom.setInputLogo(R.drawable.logo);
                        sdkCustom.setButtonPayColor(R.color.amber_600);

                        OtcApplication.setApplicationContext(MainActivity.this);
                        //default
                        //OtcApplication.setCurrency(Currency.USD)
                        //OtcApplication.setLanguage(Language.SPANISH)
                        //OtcApplication.setChannel(Channel.MOBILE)
                        OtcApplication.setEndpoint(ENDPOINT);
                        OtcApplication.setTenantId(TENANT);
                        OtcApplication.setMerchantId(MERCHANT);
                        OtcApplication.setSecurityToken(accessToken);
                        //custom
                        OtcApplication.setCustom(sdkCustom);

                        OTC_SDK = OtcApplication.getInstance();
                        //------------------------------------------------------------------------------------------
                        String sessionToken = response.getSessionKey();

                        OTC_SDK.openPaymentActivity(MainActivity.this, mAmount, mPurchaseNumber, sessionToken);

                    }

                    @Override
                    public void onError(ANError anError) {

                        viewLoading.setVisibility(View.GONE);

                        Log.i(TAG, "onError: " + anError.getErrorCode());
                        Log.i(TAG, "onError: " + anError.getErrorDetail());
                        Log.i(TAG, "onError: " + anError.getErrorBody());
                        Log.e(TAG, "onError: ", anError);

                        Toast.makeText(MainActivity.this, "Error : Revisar el log....", Toast.LENGTH_SHORT).show();

                    }
                });

    }

    private void ApiAuthorization(String data) {

        try {
            JSONObject json_contact = new JSONObject(data);
            String transactionToken = json_contact.getString("tokenId");
            String expiration = json_contact.getString("expiration");

            Log.i(TAG, "Authorization: " + transactionToken + " - " + expiration);

            AuthorizationRequest authorizationRequest = new AuthorizationRequest();

            Header header = new Header();
            header.setExternalId(UUID.randomUUID().toString());
            authorizationRequest.setHeader(header);

            Merchant merchant = new Merchant();
            merchant.setMerchantId(OtcApplication.getMerchantId());
            authorizationRequest.setMerchant(merchant);

            Device device = new Device();
            device.setTerminalId("1");
            device.setCaptureType("manual");
            device.setUnattended(false);
            authorizationRequest.setDevice(device);

            Order order = new Order();
            order.setChannel(OtcApplication.getChannel().name());
            order.setAmount(Utils.parseAmount(etAmount.getText().toString()));
            order.setCurrency(OtcApplication.getCurrency().name());
            order.setCountable(true);
            order.setPurchaseNumber(etPurchaseNumber.getText().toString());
            order.setTransactionToken(transactionToken);
            authorizationRequest.setOrder(order);

            String endpoint = "apiqa.ecore.mobi";
            String tenant = "lpg";

            String path = "https://{0}/api.authorization/v3/{1}/authorize";
            String[] values = {endpoint, tenant};


            viewLoading.setVisibility(View.VISIBLE);

            AndroidNetworking.post(MessageFormat.format(path, values))
                    .setTag(this)
                    .addHeaders("Authorization", OtcApplication.getSecurityToken())
                    .addHeaders("Content-Type", "application/json")
                    .addApplicationJsonBody(authorizationRequest)
                    .setPriority(Priority.HIGH)
                    .build()
                    .getAsString(new StringRequestListener() {
                        @Override
                        public void onResponse(String response) {

                            viewLoading.setVisibility(View.GONE);

                            Log.i(TAG, "onResponse: " + response);

                            dialogResult(MainActivity.this, response);
                        }

                        @Override
                        public void onError(ANError anError) {

                            viewLoading.setVisibility(View.GONE);

                            Log.i(TAG, "onError: " + anError.getErrorDetail());

                            dialogResult(MainActivity.this, anError.getErrorDetail());

                        }
                    });


        } catch (JSONException e) {
            Log.e(TAG, "Authorization: ", e);
        }

    }

    private void dialogResult(Activity context, String msg){
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("OTC-DEMO")
                .setMessage(msg)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog1, int which) {
                        dialog1.dismiss();
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_info)
                .show();
        TextView textView = dialog.findViewById(android.R.id.message);
        textView.setScroller(new Scroller(context));
        textView.setVerticalScrollBarEnabled(true);
        textView.setMovementMethod(new ScrollingMovementMethod());
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        String resultString = "";

        if (requestCode == OtcApplication.REQUEST_PAYMENT) {

            if (data != null) {
                if (resultCode == Activity.RESULT_OK) {

                    resultString = data.getExtras().getString(OtcApplication.KEY_SUCCESS);

                    //show result
                    //Util.dialogResult(this, returnString)

                    ApiAuthorization(resultString);

                } else {

                    resultString = data.getExtras().getString(OtcApplication.KEY_ERROR);
                    //show result
                    Toast.makeText(this, resultString, Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Cancelado....", Toast.LENGTH_SHORT).show();
            }

        }

    }


}