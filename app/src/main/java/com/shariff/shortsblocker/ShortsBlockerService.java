package com.shariff.shortsblocker;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.List;

/**
 * Watches the YouTube app's screen layout (not the video) and presses Back
 * whenever the Shorts player is open.
 */
public class ShortsBlockerService extends AccessibilityService {

    private static final String YT = "com.google.android.youtube";
    private static final long CHECK_DELAY_MS = 120;   // let the screen settle before looking
    private static final long COOLDOWN_MS = 600;      // never press Back twice in a row too fast
    private static final long VERIFY_MS = 900;        // re-check after a block

    private final Handler handler = new Handler(Looper.getMainLooper());
    private String[] shortsIds = new String[0];
    private String tabLabel = "Shorts";
    private String barHint = "pivot_bar";

    private boolean checkPending = false;
    private long lastActionAt = 0;
    private int actionsThisEpisode = 0;

    private final Runnable check = () -> {
        checkPending = false;
        runCheck();
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        String[] raw = getResources().getStringArray(R.array.shorts_view_ids);
        shortsIds = new String[raw.length];
        for (int i = 0; i < raw.length; i++) {
            shortsIds[i] = YT + ":id/" + raw[i];
        }
        tabLabel = getString(R.string.shorts_tab_label);
        barHint = getString(R.string.bottom_bar_id_hint);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        if (!YT.contentEquals(event.getPackageName())) return;
        if (!Prefs.isBlockingActive(this)) return;
        if (!checkPending) {
            checkPending = true;
            handler.postDelayed(check, CHECK_DELAY_MS);
        }
    }

    private void runCheck() {
        if (!Prefs.isBlockingActive(this)) return;

        AccessibilityNodeInfo root;
        try {
            root = getRootInActiveWindow();
        } catch (Exception e) {
            return;
        }
        if (root == null || root.getPackageName() == null) return;
        if (!YT.contentEquals(root.getPackageName())) {
            actionsThisEpisode = 0;
            return;
        }

        if (!isShortsOpen(root)) {
            actionsThisEpisode = 0;
            return;
        }

        long now = SystemClock.uptimeMillis();
        if (now - lastActionAt < COOLDOWN_MS) {
            handler.postDelayed(check, COOLDOWN_MS);
            return;
        }
        lastActionAt = now;

        if (actionsThisEpisode == 0) {
            Prefs.recordBlock(this);
            if (Prefs.showToast(this)) {
                Toast.makeText(getApplicationContext(), R.string.blocked_toast, Toast.LENGTH_SHORT).show();
            }
        }

        if (actionsThisEpisode >= 2) {
            // Back didn't get out (e.g. opened from a shared link) - leave YouTube instead.
            performGlobalAction(GLOBAL_ACTION_HOME);
            actionsThisEpisode = 0;
        } else {
            performGlobalAction(GLOBAL_ACTION_BACK);
            actionsThisEpisode++;
        }

        handler.postDelayed(check, VERIFY_MS);
    }

    private boolean isShortsOpen(AccessibilityNodeInfo root) {
        // Signal 1: a view that only exists in the Shorts player.
        for (String id : shortsIds) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
            if (nodes == null) continue;
            for (AccessibilityNodeInfo n : nodes) {
                if (n != null && n.isVisibleToUser()) return true;
            }
        }

        // Signal 2: the "Shorts" tab is selected in the bottom bar.
        List<AccessibilityNodeInfo> tabs = root.findAccessibilityNodeInfosByText(tabLabel);
        if (tabs != null) {
            for (AccessibilityNodeInfo t : tabs) {
                if (t != null && t.isSelected() && t.isVisibleToUser() && isInBottomBar(t)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isInBottomBar(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo cur = node;
        for (int depth = 0; depth < 6 && cur != null; depth++) {
            String id = cur.getViewIdResourceName();
            if (id != null && id.contains(barHint)) return true;
            cur = cur.getParent();
        }
        return false;
    }

    @Override
    public void onInterrupt() {
        handler.removeCallbacks(check);
        checkPending = false;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
