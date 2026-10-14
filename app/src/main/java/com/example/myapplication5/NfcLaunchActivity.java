package com.example.myapplication5;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

/**
 * Invisible entry point for the vehicle NFC tag.
 *
 * <p>The NFC intent filters live here instead of on {@link MainActivity} on
 * purpose: a tag that stays in the field is dispatched repeatedly, and routing
 * those repeats to MainActivity brought it to the front, which dismissed the
 * screen-capture consent dialog the user was about to tap.
 *
 * <p>This activity has no UI and finishes immediately. If a session is already
 * running or is being started, it does nothing at all - so nothing is brought to
 * the foreground while the consent dialog or the navigator is on screen.
 */
public class NfcLaunchActivity extends Activity {

    private static final String TAG = "NfcLaunchActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (MainActivity.isSessionBusy()) {
            Log.d(TAG, "Сессия уже идёт/запускается - ничего не делаем");
            finish();
            return;
        }

        if (!isOurTag(getIntent())) {
            Log.d(TAG, "Не наша метка - выходим");
            finish();
            return;
        }

        Log.d(TAG, "Запускаем MainActivity с автозапуском");
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_AUTO_START, true);
        startActivity(intent);
        finish();
    }

    /** True for our NDEF URI tag or for the transport card (by UID). */
    private boolean isOurTag(Intent intent) {
        if (intent == null) {
            return false;
        }
        String action = intent.getAction();

        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            Uri data = intent.getData();
            return data != null && MainActivity.NFC_TRIGGER_URI.equals(data.toString());
        }

        if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)) {
            Tag tag = tagFromIntent(intent);
            return tag != null
                    && MainActivity.TARGET_NFC_UID.equalsIgnoreCase(bytesToHex(tag.getId()));
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    private static Tag tagFromIntent(Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag.class);
        }
        return intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
    }

    private static String bytesToHex(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}
