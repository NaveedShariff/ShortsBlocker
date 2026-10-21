package com.shariff.shortsblocker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.function.Consumer;

public class MainActivity extends Activity {

    private static final int FRICTION_SECONDS = 30;
    private static final int MINUTES_PER_BLOCK = 5; // rough estimate used for "time saved"

    private TextView statusTitle, statusText, blocksCount, timeSaved;
    private View setupCard;
    private Switch toastSwitch;
    private Button mainButton;
    private boolean settingSwitch = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusTitle = findViewById(R.id.status_title);
        statusText = findViewById(R.id.status_text);
        blocksCount = findViewById(R.id.blocks_count);
        timeSaved = findViewById(R.id.time_saved);
        setupCard = findViewById(R.id.setup_card);
        toastSwitch = findViewById(R.id.toast_switch);
        mainButton = findViewById(R.id.main_button);

        findViewById(R.id.enable_button).setOnClickListener(v -> showDisclosure());
        findViewById(R.id.restricted_button).setOnClickListener(v -> openAppInfo());
        findViewById(R.id.battery_button).setOnClickListener(v -> openBatterySettings());
        findViewById(R.id.test_button).setOnClickListener(v -> openYouTube());

        toastSwitch.setChecked(Prefs.showToast(this));
        toastSwitch.setOnCheckedChangeListener((CompoundButton b, boolean on) -> Prefs.setShowToast(this, on));

        wireBlockSwitch(findViewById(R.id.youtube_switch), Prefs.blockYouTube(this),
                on -> Prefs.setBlockYouTube(this, on));
        wireBlockSwitch(findViewById(R.id.instagram_switch), Prefs.blockInstagram(this),
                on -> Prefs.setBlockInstagram(this, on));

        mainButton.setOnClickListener(v -> {
            if (Prefs.isBlockingActive(this)) {
                showFrictionDialog();
            } else {
                Prefs.setEnabled(this, true);
                Prefs.setPauseUntil(this, 0);
                refresh();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        boolean serviceOn = isServiceEnabled();
        setupCard.setVisibility(serviceOn ? View.GONE : View.VISIBLE);

        int blocks = Prefs.getBlocksToday(this);
        blocksCount.setText(String.valueOf(blocks));
        timeSaved.setText(getString(R.string.time_saved_value, blocks * MINUTES_PER_BLOCK));

        if (!serviceOn) {
            statusTitle.setText(R.string.status_setup_title);
            statusText.setText(R.string.status_setup_text);
            mainButton.setVisibility(View.GONE);
        } else if (Prefs.isPaused(this)) {
            long mins = Math.max(1, (Prefs.getPauseUntil(this) - System.currentTimeMillis()) / 60000 + 1);
            statusTitle.setText(R.string.status_paused_title);
            statusText.setText(getString(R.string.status_paused_text, mins));
            mainButton.setVisibility(View.VISIBLE);
            mainButton.setText(R.string.resume_now);
        } else if (!Prefs.isEnabled(this)) {
            statusTitle.setText(R.string.status_off_title);
            statusText.setText(R.string.status_off_text);
            mainButton.setVisibility(View.VISIBLE);
            mainButton.setText(R.string.turn_on);
        } else {
            statusTitle.setText(R.string.status_on_title);
            statusText.setText(R.string.status_on_text);
            mainButton.setVisibility(View.VISIBLE);
            mainButton.setText(R.string.pause_or_off);
        }
    }

    private boolean isServiceEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(enabled)) return false;
        ComponentName me = new ComponentName(this, ShortsBlockerService.class);
        TextUtils.SimpleStringSplitter split = new TextUtils.SimpleStringSplitter(':');
        split.setString(enabled);
        while (split.hasNext()) {
            ComponentName c = ComponentName.unflattenFromString(split.next());
            if (me.equals(c)) return true;
        }
        return false;
    }

    /** Shown before sending the user to Accessibility settings (required by Google Play). */
    private void showDisclosure() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.disclosure_title)
                .setMessage(R.string.disclosure_text)
                .setPositiveButton(R.string.i_agree, (d, w) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                        Toast.makeText(this, R.string.find_us_toast, Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Toast.makeText(this, R.string.open_settings_failed, Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void openAppInfo() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception ignored) { }
    }

    private void openBatterySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        } catch (Exception e) {
            openAppInfo();
        }
    }

    private void openYouTube() {
        Intent i = getPackageManager().getLaunchIntentForPackage("com.google.android.youtube");
        if (i == null) {
            i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/shorts"));
        }
        try {
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, R.string.no_youtube, Toast.LENGTH_SHORT).show();
        }
    }

    /** 30-second wait before the blocker can be paused or turned off. */
    private void showFrictionDialog() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.friction_title)
                .setMessage(getString(R.string.friction_wait, FRICTION_SECONDS))
                .setPositiveButton(R.string.pause_15, (d, w) -> pauseFor(15))
                .setNeutralButton(R.string.pause_60, (d, w) -> pauseFor(60))
                .setNegativeButton(R.string.turn_off, (d, w) -> {
                    Prefs.setEnabled(this, false);
                    Prefs.setPauseUntil(this, 0);
                    refresh();
                })
                .create();

        dialog.setOnShowListener(di -> startCountdown(dialog,
                dialog.getButton(AlertDialog.BUTTON_POSITIVE),
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL),
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE)));
        dialog.show();
    }

    /** Keeps the buttons disabled for the 30-second wait, counting down in the message. */
    private void startCountdown(AlertDialog dialog, Button... buttons) {
        for (Button b : buttons) b.setEnabled(false);
        final CountDownTimer timer = new CountDownTimer(FRICTION_SECONDS * 1000L, 1000L) {
            @Override
            public void onTick(long left) {
                dialog.setMessage(getString(R.string.friction_wait, (int) (left / 1000) + 1));
            }

            @Override
            public void onFinish() {
                dialog.setMessage(getString(R.string.friction_done));
                for (Button b : buttons) b.setEnabled(true);
            }
        }.start();
        dialog.setOnDismissListener(x -> timer.cancel());
    }

    /** A per-app block switch. Turning it ON is instant; turning it OFF needs the same 30-second wait. */
    private void wireBlockSwitch(Switch s, boolean checked, Consumer<Boolean> save) {
        s.setChecked(checked);
        s.setOnCheckedChangeListener((CompoundButton b, boolean on) -> {
            if (settingSwitch) return;
            if (on || !Prefs.isBlockingActive(this)) {
                save.accept(on);
                return;
            }
            setQuietly(s, true);
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.friction_title)
                    .setMessage(getString(R.string.friction_wait, FRICTION_SECONDS))
                    .setPositiveButton(R.string.turn_off, (d, w) -> {
                        save.accept(false);
                        setQuietly(s, false);
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .create();
            dialog.setOnShowListener(di -> startCountdown(dialog, dialog.getButton(AlertDialog.BUTTON_POSITIVE)));
            dialog.show();
        });
    }

    private void setQuietly(Switch s, boolean on) {
        settingSwitch = true;
        s.setChecked(on);
        settingSwitch = false;
    }

    private void pauseFor(int minutes) {
        Prefs.setPauseUntil(this, System.currentTimeMillis() + minutes * 60_000L);
        refresh();
    }
}
