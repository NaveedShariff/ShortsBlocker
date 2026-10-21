package com.shariff.shortsblocker;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.List;

/**
 * Watches the screen layout (not the video) of YouTube and Instagram only, and presses Back
 * whenever the YouTube Shorts player or the Instagram Reels player is open.
 */
public class ShortsBlockerService extends AccessibilityService {

    static final String YT = "com.google.android.youtube";
    static final String IG = "com.instagram.android";

    private static final long CHECK_DELAY_MS = 120;   // let the screen settle before looking
    private static final long COOLDOWN_MS = 600;      // never press Back twice in a row too fast
    private static final long VERIFY_MS = 900;        // re-check after a block

    private final Handler handler = new Handler(Looper.getMainLooper());
    private String[] shortsIds = new String[0];
    private String tabLabel = "Shorts";
    private String barHint = "pivot_bar";
    private String[] reelsIds = new String[0];
    private String reelsTabId = IG + ":id/clips_tab";

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
        shortsIds = withPackage(YT, getResources().getStringArray(R.array.shorts_view_ids));
        tabLabel = getString(R.string.shorts_tab_label);
        barHint = getString(R.string.bottom_bar_id_hint);
        reelsIds = withPackage(IG, getResources().getStringArray(R.array.instagram_reels_view_ids));
        reelsTabId = IG + ":id/" + getString(R.string.instagram_reels_tab_id);
    }

    private static String[] withPackage(String pkg, String[] raw) {
        String[] out = new String[raw.length];
        for (int i = 0; i < raw.length; i++) {
            out[i] = pkg + ":id/" + raw[i];
        }
        return out;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        if (!Prefs.isBlockingActiveFor(this, event.getPackageName().toString())) return;
        if (!checkPending) {
            checkPending = true;
            handler.postDelayed(check, CHECK_DELAY_MS);
        }
    }

    private void runCheck() {
        AccessibilityNodeInfo root;
        try {
            root = getRootInActiveWindow();
        } catch (Exception e) {
            return;
        }
        if (root == null || root.getPackageName() == null) return;
        String pkg = root.getPackageName().toString();
        if (!Prefs.isBlockingActiveFor(this, pkg)) {
            actionsThisEpisode = 0;
            return;
        }

        boolean blocked = YT.equals(pkg) ? isShortsOpen(root) : isReelsOpen(root);
        if (!blocked) {
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
            // Back didn't get out (e.g. opened from a shared link) - leave the app instead.
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

    private boolean isReelsOpen(AccessibilityNodeInfo root) {
        Rect screen = new Rect();
        root.getBoundsInScreen(screen);

        // Signal 1: the Reels player is on screen and fills most of it (feed previews are smaller).
        for (String id : reelsIds) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
            if (nodes == null) continue;
            for (AccessibilityNodeInfo n : nodes) {
                if (n != null && n.isVisibleToUser() && fillsScreen(n, screen)) return true;
            }
        }

        // Signal 2: the Reels tab is selected in the bottom bar.
        List<AccessibilityNodeInfo> tabs = root.findAccessibilityNodeInfosByViewId(reelsTabId);
        if (tabs != null) {
            for (AccessibilityNodeInfo t : tabs) {
                if (t != null && t.isSelected() && t.isVisibleToUser()) return true;
            }
        }
        return false;
    }

    private static boolean fillsScreen(AccessibilityNodeInfo node, Rect screen) {
        if (screen.isEmpty()) return true;
        Rect b = new Rect();
        node.getBoundsInScreen(b);
        return b.height() >= screen.height() * 0.6f && b.width() >= screen.width() * 0.8f;
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
