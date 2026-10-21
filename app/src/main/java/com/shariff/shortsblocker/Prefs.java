package com.shariff.shortsblocker;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** On-device settings and block counts. Only numbers and times are stored, never what you watch. */
final class Prefs {
    private static final String FILE = "shorts_blocker";

    private Prefs() {}

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static boolean isEnabled(Context c) { return p(c).getBoolean("enabled", true); }

    static void setEnabled(Context c, boolean on) { p(c).edit().putBoolean("enabled", on).apply(); }

    static long getPauseUntil(Context c) { return p(c).getLong("pause_until", 0L); }

    static void setPauseUntil(Context c, long t) { p(c).edit().putLong("pause_until", t).apply(); }

    static boolean isPaused(Context c) { return getPauseUntil(c) > System.currentTimeMillis(); }

    static boolean isBlockingActive(Context c) { return isEnabled(c) && !isPaused(c); }

    static boolean blockYouTube(Context c) { return p(c).getBoolean("block_youtube", true); }

    static void setBlockYouTube(Context c, boolean on) { p(c).edit().putBoolean("block_youtube", on).apply(); }

    static boolean blockInstagram(Context c) { return p(c).getBoolean("block_instagram", true); }

    static void setBlockInstagram(Context c, boolean on) { p(c).edit().putBoolean("block_instagram", on).apply(); }

    /** True when the blocker is on and the given app (YouTube or Instagram) is switched on. */
    static boolean isBlockingActiveFor(Context c, String pkg) {
        if (!isBlockingActive(c)) return false;
        if (ShortsBlockerService.YT.equals(pkg)) return blockYouTube(c);
        if (ShortsBlockerService.IG.equals(pkg)) return blockInstagram(c);
        return false;
    }

    static boolean showToast(Context c) { return p(c).getBoolean("show_toast", true); }

    static void setShowToast(Context c, boolean on) { p(c).edit().putBoolean("show_toast", on).apply(); }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    static int getBlocksToday(Context c) {
        SharedPreferences s = p(c);
        return today().equals(s.getString("day", "")) ? s.getInt("count", 0) : 0;
    }

    static void recordBlock(Context c) {
        int n = getBlocksToday(c) + 1;
        p(c).edit().putString("day", today()).putInt("count", n).apply();
    }
}
