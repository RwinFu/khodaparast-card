package com.persiarobot.cardnfc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.cardemulation.CardEmulation;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private NfcAdapter adapter;
    private CardEmulation cardEmulation;
    private boolean supportsHce;
    private boolean active;
    private boolean preferred;
    private boolean listenOnly;
    private TextView status;
    private TextView detail;
    private Button toggle;
    private Button settings;

    private final BroadcastReceiver nfcStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (adapter != null && !adapter.isEnabled() && active) stopSharing();
            render();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        status = findViewById(R.id.status);
        detail = findViewById(R.id.detail);
        toggle = findViewById(R.id.toggle_nfc);
        settings = findViewById(R.id.nfc_settings);
        getSharedPreferences(CardConfig.PREFERENCES, MODE_PRIVATE).edit()
                .putBoolean(CardConfig.ACTIVE, false).apply();

        adapter = NfcAdapter.getDefaultAdapter(this);
        supportsHce = adapter != null && getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION);
        if (supportsHce) {
            try {
                cardEmulation = CardEmulation.getInstance(adapter);
            } catch (RuntimeException ignored) {
                supportsHce = false;
            }
        }

        toggle.setOnClickListener(view -> {
            if (active) stopSharing();
            else startSharing();
            render();
        });
        settings.setOnClickListener(view -> openNfcSettings());
        findViewById(R.id.open_card).setOnClickListener(view -> openCard());
        findViewById(R.id.show_qr).setOnClickListener(view -> showQr());

        IntentFilter filter = new IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(nfcStateReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(nfcStateReceiver, filter);
        }
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    @Override
    protected void onPause() {
        stopSharing();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(nfcStateReceiver);
        stopSharing();
        super.onDestroy();
    }

    private void startSharing() {
        if (!supportsHce || adapter == null || !adapter.isEnabled()) return;
        try {
            preferred = cardEmulation.setPreferredService(this,
                    new ComponentName(this, NfcCardService.class));
            if (!preferred) {
                showActivationError();
                return;
            }
            if (Build.VERSION.SDK_INT >= 35) {
                try {
                    adapter.setDiscoveryTechnology(this, NfcAdapter.FLAG_READER_DISABLE,
                            NfcAdapter.FLAG_LISTEN_KEEP);
                    listenOnly = true;
                } catch (RuntimeException ignored) {
                    // Older/vendor implementations can still use ordinary HCE routing.
                }
            }
            getSharedPreferences(CardConfig.PREFERENCES, MODE_PRIVATE).edit()
                    .putBoolean(CardConfig.ACTIVE, true).apply();
            active = true;
        } catch (RuntimeException error) {
            stopSharing();
            showActivationError();
        }
    }

    private void stopSharing() {
        getSharedPreferences(CardConfig.PREFERENCES, MODE_PRIVATE).edit()
                .putBoolean(CardConfig.ACTIVE, false).apply();
        if (preferred && cardEmulation != null) {
            try {
                cardEmulation.unsetPreferredService(this);
            } catch (RuntimeException ignored) { }
        }
        if (listenOnly && adapter != null && Build.VERSION.SDK_INT >= 35) {
            try {
                adapter.resetDiscoveryTechnology(this);
            } catch (RuntimeException ignored) { }
        }
        preferred = false;
        listenOnly = false;
        active = false;
    }

    private void render() {
        if (status == null) return;
        settings.setVisibility(View.GONE);
        toggle.setText(active ? R.string.stop_nfc : R.string.start_nfc);
        toggle.setEnabled(true);
        toggle.setAlpha(1f);
        if (!supportsHce || adapter == null) {
            status.setText(R.string.status_unsupported);
            detail.setText(R.string.detail_unsupported);
            toggle.setEnabled(false);
            toggle.setAlpha(0.55f);
        } else if (!adapter.isEnabled()) {
            status.setText(R.string.status_off);
            detail.setText(R.string.detail_off);
            toggle.setEnabled(false);
            toggle.setAlpha(0.55f);
            settings.setVisibility(View.VISIBLE);
        } else if (active) {
            status.setText(R.string.status_active);
            detail.setText(R.string.detail_active);
        } else {
            status.setText(R.string.status_ready);
            detail.setText(R.string.detail_ready);
        }
    }

    private void showActivationError() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.status_error)
                .setMessage(R.string.detail_error)
                .setPositiveButton(R.string.close, null)
                .show();
    }

    private void openNfcSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_NFC_SETTINGS));
        } catch (ActivityNotFoundException ignored) {
            try {
                startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
            } catch (ActivityNotFoundException ignoredAgain) {
                Toast.makeText(this, R.string.detail_off, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void openCard() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(CardConfig.URL)));
        } catch (ActivityNotFoundException ignored) {
            Toast.makeText(this, R.string.no_browser, Toast.LENGTH_LONG).show();
        }
    }

    private void showQr() {
        ImageView image = new ImageView(this);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        image.setPadding(padding, padding, padding, padding);
        image.setBackgroundColor(Color.WHITE);
        image.setAdjustViewBounds(true);
        image.setImageResource(R.drawable.card_qr);
        image.setContentDescription(getString(R.string.qr_title));
        new AlertDialog.Builder(this)
                .setTitle(R.string.qr_title)
                .setView(image)
                .setPositiveButton(R.string.close, null)
                .show();
    }
}
