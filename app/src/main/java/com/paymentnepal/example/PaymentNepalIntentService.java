package com.paymentnepal.example;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.util.Log;

import java.math.BigDecimal;

import com.paymentnepal.PaymentNepalService;
import com.paymentnepal.PaymentNepalFatalError;
import com.paymentnepal.PaymentNepalTemporaryError;
import com.paymentnepal.Card3ds;
import com.paymentnepal.CardTokenRequest;
import com.paymentnepal.CardTokenResponse;
import com.paymentnepal.InitPaymentRequest;
import com.paymentnepal.InitPaymentResponse;
import com.paymentnepal.TransactionDetails;


public class PaymentNepalIntentService extends IntentService {
    ResultReceiver resultReceiver;

    public static final int STATUS_INIT_FINISHED = 1000;
    public static final int STATUS_CHECK_FINISHED = 1001;
    public static final int STATUS_INIT_ERROR = 1002;
    public static final int STATUS_CHECK_ERROR = 1003;
    public static final int STATUS_CREATE_TOKEN_ERROR = 1004;
    public static final int STATUS_TOKEN_CREATED = 1005;
    public static final int STATUS_TOKEN_CREATE_FAILED = 1006;

    public static final String DATA_TRANSACTION_ID = "com.paymentnepal.example.data.transaction_id";
    public static final String DATA_SESSION_KEY = "com.paymentnepal.example.data.session_Key";
    public static final String DATA_STATUS = "com.paymentnepal.example.data.status";

    public static final String DATA_TOKEN = "com.paymentnepal.example.data.token";
    public static final String DATA_TOKEN_ERROR_CARD_NUMBER = "com.paymentnepal.example.data.token.error.card";
    public static final String DATA_TOKEN_ERROR_HOLDER = "com.paymentnepal.example.data.token.error.card_holder";
    public static final String DATA_TOKEN_ERROR_MONTH = "com.paymentnepal.example.data.token.error.exp_month";
    public static final String DATA_TOKEN_ERROR_YEAR = "com.paymentnepal.example.data.token.error.exp_year";
    public static final String DATA_TOKEN_ERROR_CVC = "com.paymentnepal.example.data.token.error.cvc";

    private static final String ACTION_CREATE_TOKEN = "com.paymentnepal.example.action.create_token";
    private static final String ACTION_START = "com.paymentnepal.example.action.start_payment";
    private static final String ACTION_CHECK = "com.paymentnepal.example.action.check_status";

    private static final String EXTRA_PAYMENT_TYPE = "com.paymentnepal.example.extra.payment_type";
    private static final String EXTRA_RECEIVER = "com.paymentnepal.example.extra.receiver";
    private static final String EXTRA_TRANSACTION_ID = "com.paymentnepal.example.extra.transaction_id";
    private static final String EXTRA_NAME = "com.paymentnepal.example.extra.name";
    private static final String EXTRA_AMOUNT = "com.paymentnepal.example.extra.amount";
    private static final String EXTRA_PHONE = "com.paymentnepal.example.extra.phone";
    private static final String EXTRA_SESSION_KEY = "com.paymentnepal.example.extra.session_Key";
    private static final String EXTRA_TOKEN = "com.paymentnepal.example.extra.token";

    private static final String EXTRA_CARD_NUMBER = "com.paymentnepal.example.extra.card_number";
    private static final String EXTRA_CARD_HOLDER = "com.paymentnepal.example.extra.card_holder";
    private static final String EXTRA_CARD_MONTH = "com.paymentnepal.example.extra.card_month";
    private static final String EXTRA_CARD_YEAR = "com.paymentnepal.example.extra.card_year";
    private static final String EXTRA_CARD_CVC = "com.paymentnepal.example.extra.card_cvc";

    private PaymentNepalService paymentNepal = null;


    public static void createToken(Context context, AlbaResultReceiver resultReceiver, String cardNumber, String cardHolder, String month, String year, String cvc) {
        Intent intent = new Intent(context, PaymentNepalIntentService.class);
        intent.setAction(ACTION_CREATE_TOKEN);
        intent.putExtra(EXTRA_CARD_NUMBER, cardNumber);
        intent.putExtra(EXTRA_CARD_HOLDER, cardHolder);
        intent.putExtra(EXTRA_CARD_MONTH, month);
        intent.putExtra(EXTRA_CARD_YEAR, year);
        intent.putExtra(EXTRA_CARD_CVC, cvc);
        intent.putExtra(EXTRA_RECEIVER, resultReceiver);
        context.startService(intent);
    }

    public static void startPayment(Context context, AlbaResultReceiver resultReceiver, String paymentType, String name, String amount, String phone, String token) {
        Intent intent = new Intent(context, PaymentNepalIntentService.class);
        intent.setAction(ACTION_START);
        intent.putExtra(EXTRA_PAYMENT_TYPE, paymentType);
        intent.putExtra(EXTRA_NAME, name);
        intent.putExtra(EXTRA_AMOUNT, amount);
        intent.putExtra(EXTRA_PHONE, phone);
        intent.putExtra(EXTRA_TOKEN, token);
        intent.putExtra(EXTRA_RECEIVER, resultReceiver);
        context.startService(intent);
    }

    public static void startCheckStatus(Context context, AlbaResultReceiver resultReceiver, int TransactionId, String sessionKey) {
        Intent intent = new Intent(context, PaymentNepalIntentService.class);
        intent.setAction(ACTION_CHECK);
        intent.putExtra(EXTRA_TRANSACTION_ID, TransactionId);
        intent.putExtra(EXTRA_SESSION_KEY, sessionKey);
        intent.putExtra(EXTRA_RECEIVER, resultReceiver);
        context.startService(intent);
    }

    public PaymentNepalIntentService() {
        super("AlbaIntentService");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        resultReceiver = intent.getParcelableExtra(EXTRA_RECEIVER);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null && resultReceiver != null) {
            final String action = intent.getAction();
            if (paymentNepal == null) {
                paymentNepal = new PaymentNepalService(getResources().getString(R.string.paymentnepal_key));
            }

            if (ACTION_START.equals(action)) {
                final String paymentType = intent.getStringExtra(EXTRA_PAYMENT_TYPE);
                final String name = intent.getStringExtra(EXTRA_NAME);
                final String amount = intent.getStringExtra(EXTRA_AMOUNT);
                final String phone = intent.getStringExtra(EXTRA_PHONE);
                final String token = intent.getStringExtra(EXTRA_TOKEN);

                handleStartPayment(paymentType, name, new BigDecimal(amount), phone, token);
            } else if (ACTION_CHECK.equals(action)) {
                final int transactionId = intent.getIntExtra(EXTRA_TRANSACTION_ID, 0);
                final String sessionKey = intent.getStringExtra(EXTRA_SESSION_KEY);

                handleCheckStatus(transactionId, sessionKey);
            } else if (ACTION_CREATE_TOKEN.equals(action)) {
                final String cardNumber = intent.getStringExtra(EXTRA_CARD_NUMBER);
                final String cardHolder = intent.getStringExtra(EXTRA_CARD_HOLDER);
                final int month = Utils.safeParseInt(intent.getStringExtra(EXTRA_CARD_MONTH), 0);
                final int year = Utils.safeParseInt(intent.getStringExtra(EXTRA_CARD_YEAR), 0);
                final String cvc = intent.getStringExtra(EXTRA_CARD_CVC);

                handleCreateToken(cardNumber, cardHolder, month, year, cvc);
            }
        }
    }

    private void handleCreateToken(String cardNumber, String cardHolder, int month, int year, String cvc) {
        Log.d("AlbaIntentService", "Create token");

        try {
            int serviceId = getResources().getInteger(R.integer.paymentnepal_service_id);
            CardTokenRequest request = new CardTokenRequest(serviceId, cardNumber, month, year, cvc, cardHolder);
            CardTokenResponse response = paymentNepal.createCardToken(
                    request,
                    getResources().getString(R.string.payment_type_card).equals("spg_test")
            );

            Bundle resultData = new Bundle();
            if (response.hasErrors()) {
                resultData.putString(DATA_TOKEN_ERROR_CARD_NUMBER, Utils.implode(". ", response.getCardErrors()));
                resultData.putString(DATA_TOKEN_ERROR_HOLDER, Utils.implode(". ", response.getCardHolderErrors()));
                resultData.putString(DATA_TOKEN_ERROR_MONTH, Utils.implode(". ", response.getExpMonthErrors()));
                resultData.putString(DATA_TOKEN_ERROR_YEAR, Utils.implode(". ", response.getExpYearErrors()));
                resultData.putString(DATA_TOKEN_ERROR_CVC, Utils.implode(". ", response.getCvcErrors()));

                resultReceiver.send(STATUS_TOKEN_CREATE_FAILED, resultData);
            } else {
                resultData.putString(DATA_TOKEN, response.getToken());
                resultReceiver.send(STATUS_TOKEN_CREATED, resultData);
            }

        } catch (PaymentNepalTemporaryError | PaymentNepalFatalError paymentNepalError) {
            paymentNepalError.printStackTrace();
            resultReceiver.send(STATUS_CREATE_TOKEN_ERROR, new Bundle());
        }

    }

    private void handleStartPayment(String paymentType, String name, BigDecimal amount, String phone, String token) {
        Log.d("PaymentNepalIntentService", "Start payment: " + paymentType + ", " + name + ", " + amount + ", " + phone + ", " + token);
        try {
            InitPaymentRequest request = InitPaymentRequest.builder()
                    .paymentType(paymentType)
                    .cost(amount)
                    .name(name)
                    .phone(phone)
                    .cardToken(token)
                    .currency("NPR")
                    // .setTest(InitTestType.OK)
                    .build();
            InitPaymentResponse response = paymentNepal.initPayment(request);
            Log.d("PaymentNepalIntentService", "Transaction created: " + response.getTransactionId());

            Bundle resultData = new Bundle();
            resultData.putInt(DATA_TRANSACTION_ID, response.getTransactionId());
            resultData.putString(DATA_SESSION_KEY, response.getSessionKey());

            Card3ds card3ds = response.getCard3ds();
            if (card3ds != null) {
                resultData.putString("3DS_ACSUrl", card3ds.getAcsUrl());
                resultData.putString("3DS_MD", card3ds.getMd());
                resultData.putString("3DS_PaReq", card3ds.getPaReq());
            }

            resultReceiver.send(STATUS_INIT_FINISHED, resultData);

        } catch (PaymentNepalTemporaryError | PaymentNepalFatalError paymentNepalError) {
            paymentNepalError.printStackTrace();
            Bundle resultData = new Bundle();
            resultData.putString("ERROR_MESSAGE", paymentNepalError.getMessage());
            resultReceiver.send(STATUS_INIT_ERROR, resultData);

        }
    }

    private void handleCheckStatus(int transactionId, String sessionKey) {
        Bundle resultData = new Bundle();

        try {
            TransactionDetails details = paymentNepal.transactionDetails(sessionKey);
            resultData.putInt(DATA_TRANSACTION_ID, transactionId);
            resultData.putString(DATA_STATUS, details.getStatus().toString());
            resultReceiver.send(STATUS_CHECK_FINISHED, resultData);

            Log.d("PaymentNepalIntentService", "Transaction status check finished");
        } catch (PaymentNepalTemporaryError paymentNepalTemporaryError) {
            // игнорирование временной ошибки
            Log.d("PaymentNepalIntentService", "Check status error: " + paymentNepalTemporaryError.getMessage());
        } catch (PaymentNepalFatalError paymentNepalFatalError) {
            paymentNepalFatalError.printStackTrace();
            resultData.putInt(DATA_TRANSACTION_ID, transactionId);
            resultReceiver.send(STATUS_CHECK_ERROR, resultData);
        }
    }
}
