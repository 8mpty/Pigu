package moe.shizuku.manager;

import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import java.lang.annotation.Retention;
import java.util.Locale;
import moe.shizuku.manager.service.WatchdogService;
import moe.shizuku.manager.receiver.BootCompleteReceiver;
import moe.shizuku.manager.utils.Token;
import moe.shizuku.manager.utils.EmptySharedPreferencesImpl;
import moe.shizuku.manager.utils.EnvironmentUtils;
import static java.lang.annotation.RetentionPolicy.SOURCE;

public class ShizukuSettings {

    public static final String NAME = "settings";
    private static final String SECRETS_NAME = "secrets";
    public static class Keys {
        public static final String KEY_AUTH_TOKEN = "auth_token";
        public static final String KEY_START_ON_BOOT = "start_on_boot";
        public static final String KEY_WATCHDOG = "watchdog";
        public static final String KEY_TCP_MODE = "tcp_mode";
        public static final String KEY_TCP_PORT = "tcp_port";
        public static final String KEY_KEEP_WIRELESS_DEBUGGING = "keep_wireless_debugging";
        public static final String KEY_AUTO_DISABLE_USB_DEBUGGING = "auto_disable_usb_debugging";
        public static final String KEY_LANGUAGE = "language";
        public static final String KEY_LIGHT_THEME = "light_theme";
        public static final String KEY_NIGHT_MODE = "night_mode";
        public static final String KEY_BLACK_NIGHT_THEME = "black_night_theme";
        public static final String KEY_USE_SYSTEM_COLOR = "use_system_color";
        public static final String KEY_LEGACY_PAIRING = "legacy_pairing";
        public static final String KEY_CATEGORY_ADVANCED = "category_advanced";
        public static final String KEY_DEVELOPER_OPTIONS = "developer_options";
    }

    private static SharedPreferences sPreferences;
    private static SharedPreferences sSecrets;

    public static SharedPreferences getPreferences() {
        return sPreferences;
    }

    @NonNull
    private static Context getSettingsStorageContext(@NonNull Context context) {
        Context storageContext;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            storageContext = context.createDeviceProtectedStorageContext();
        } else {
            storageContext = context;
        }

        storageContext = new ContextWrapper(storageContext) {
            @Override
            public SharedPreferences getSharedPreferences(String name, int mode) {
                try {
                    return super.getSharedPreferences(name, mode);
                } catch (IllegalStateException e) {
                    // SharedPreferences in credential encrypted storage are not available until after user is unlocked
                    return new EmptySharedPreferencesImpl();
                }
            }
        };

        return storageContext;
    }

    public static void initialize(Context context) {
        Context storageContext = getSettingsStorageContext(context);
        if (sPreferences == null) {
            sPreferences = storageContext.getSharedPreferences(NAME, Context.MODE_PRIVATE);
        }
        if (sSecrets == null) {
            sSecrets = storageContext.getSharedPreferences(SECRETS_NAME, Context.MODE_PRIVATE);
            migrateAuthToken();
        }
    }

    @IntDef({
        LaunchMethod.UNKNOWN,
        LaunchMethod.ROOT,
        LaunchMethod.ADB,
        LaunchMethod.WIRELESS_ADB,
    })
    @Retention(SOURCE)
    public @interface LaunchMethod {
        int UNKNOWN = -1;
        int ROOT = 0;
        int ADB = 1;
        int WIRELESS_ADB = 2;
    }

    @LaunchMethod
    public static int getLastLaunchMode() {
        return getPreferences().getInt("mode", LaunchMethod.UNKNOWN);
    }

    public static void setLastLaunchMode(@LaunchMethod int method) {
        getPreferences().edit().putInt("mode", method).apply();
    }

    public static boolean getAutoDisableUsbDebugging() {
        return getPreferences().getBoolean(Keys.KEY_AUTO_DISABLE_USB_DEBUGGING, false);
    }
    
    /**
     * The token lives in a separate preferences file so it is excluded from both the legacy
     * full-backup descriptor and the Android 12+ data-extraction rules. Those rules include only
     * settings.xml.
     */
    private static void migrateAuthToken() {
        String legacyToken = sPreferences.getString(Keys.KEY_AUTH_TOKEN, null);
        if (legacyToken == null) return;
        if (legacyToken.isEmpty()) {
            sPreferences.edit().remove(Keys.KEY_AUTH_TOKEN).commit();
            return;
        }

        String existingToken = sSecrets.getString(Keys.KEY_AUTH_TOKEN, null);
        if (existingToken == null || existingToken.isEmpty()) {
            // Persist before removing the legacy copy so a crash in between cannot lose the token.
            if (!sSecrets.edit().putString(Keys.KEY_AUTH_TOKEN, legacyToken).commit()) {
                // Keep the legacy copy; the next launch will retry the migration.
                return;
            }
        }
        // Remove the legacy copy synchronously so it cannot be picked up by a backup that starts
        // immediately after this migration.
        sPreferences.edit().remove(Keys.KEY_AUTH_TOKEN).commit();
    }

    public static synchronized String getAuthToken() {
        String authToken = sSecrets.getString(Keys.KEY_AUTH_TOKEN, null);
        if (authToken == null || authToken.isEmpty()) {
            authToken = generateAuthTokenLocked();
        }
        return authToken;
    }

    public static synchronized String generateAuthToken() {
        return generateAuthTokenLocked();
    }

    private static String generateAuthTokenLocked() {
        String token = Token.generateToken();
        // The token is shown to the user and copied into automation intents. Persist it before
        // returning so a process death cannot leave the UI and the stored token out of sync.
        if (!sSecrets.edit().putString(Keys.KEY_AUTH_TOKEN, token).commit()) {
            Log.w("ShizukuSettings", "Failed to persist the automation auth token");
        }
        return token;
    }

    public static boolean getStartOnBoot(Context context) {
        ComponentName bootCompleteReceiver = new ComponentName(context.getPackageName(), BootCompleteReceiver.class.getName());
        int state = context.getPackageManager().getComponentEnabledSetting(bootCompleteReceiver);
        return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    }

    public static void setStartOnBoot(Context context, boolean enable) {
        ComponentName bootCompleteReceiver = new ComponentName(context.getPackageName(), BootCompleteReceiver.class.getName());
        context.getPackageManager().setComponentEnabledSetting(
            bootCompleteReceiver,
            enable ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        );
        getPreferences().edit().putBoolean(Keys.KEY_START_ON_BOOT, enable).apply();
    }
    
    public static boolean getWatchdog() {
        return getPreferences().getBoolean(Keys.KEY_WATCHDOG, false);
    }

    public static boolean isWatchdogRunning() {
        return WatchdogService.isRunning();
    }

    public static void setWatchdog(Context context, boolean enable) {
        // Persist the user's choice first. Starting/stopping a service is asynchronous, and the
        // preference listener must not briefly show the opposite state.
        setWatchdogPreference(enable);
        if (enable) {
            WatchdogService.start(context);
        } else {
            WatchdogService.stop(context);
        }
    }

    /**
     * Writes the watchdog preference without starting or stopping the service.
     *
     * The service itself uses this only for an explicit stop, so a system kill or process death
     * no longer silently turns the user's watchdog setting off.
     */
    public static void setWatchdogPreference(boolean enable) {
        getPreferences().edit().putBoolean(Keys.KEY_WATCHDOG, enable).apply();
    }

    public static boolean getTcpMode() {
        return getPreferences().getBoolean(Keys.KEY_TCP_MODE, true);
    }

    public static void setTcpMode(boolean enable) {
        getPreferences().edit().putBoolean(Keys.KEY_TCP_MODE, enable).apply();
    }

    public static boolean getKeepWirelessDebugging() {
        return getPreferences().getBoolean(Keys.KEY_KEEP_WIRELESS_DEBUGGING, false);
    }

    public static void setKeepWirelessDebugging(boolean enable) {
        getPreferences().edit().putBoolean(Keys.KEY_KEEP_WIRELESS_DEBUGGING, enable).apply();
    }

    public static int getTcpPort() {
        try {
            return Integer.parseInt(getPreferences().getString(Keys.KEY_TCP_PORT, "5555"));
        } catch (NumberFormatException e) {
            return 5555;
        }
    }

    public static void setTcpPort(@Nullable Integer port) {
        if (port != null) {
            getPreferences().edit().putString(Keys.KEY_TCP_PORT, Integer.toString(port)).apply();
        } else {
            getPreferences().edit().remove(Keys.KEY_TCP_PORT).apply();
        }
        
    }

    public static boolean getLegacyPairing() {
        return getPreferences().getBoolean(Keys.KEY_LEGACY_PAIRING, false);
    }

    @AppCompatDelegate.NightMode
    public static int getNightMode() {
        int defValue = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        if (EnvironmentUtils.isWatch()) {
            defValue = AppCompatDelegate.MODE_NIGHT_YES;
        }
        return getPreferences().getInt(Keys.KEY_NIGHT_MODE, defValue);
    }

    public static Locale getLocale() {
        String tag = getPreferences().getString(Keys.KEY_LANGUAGE, null);
        // Only English and follow-system are supported; treat any other
        // stored tag as follow-system.
        if ("en".equals(tag)) {
            return Locale.forLanguageTag("en");
        }
        return Locale.getDefault();
    }

}
