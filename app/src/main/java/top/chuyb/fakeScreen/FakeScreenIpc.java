package top.chuyb.fakeScreen;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.provider.Settings;
import android.util.Log;

import androidx.core.content.ContextCompat;

import io.github.libxposed.api.XposedModule;

/**
 * 界面应用与 system_server 之间的 IPC 与状态同步：从 PhoneWindowManager 获取系统 Context、
 * 注册开关广播接收器、向 hook 层暴露当前开关状态，并在每次切换时应用功能副作用（屏幕超时）。
 * 本类不安装任何 hook。
 *
 * @since 1.0
 * @author chuyb-re
 */
final class FakeScreenIpc {

    private static final String TAG = "FakeScreen";
    private static final String KEY_OLD_SCREEN_OFF_TIMEOUT = "old_screen_off_timeout";
    private static final int NO_SAVED_TIMEOUT = -1;

    private static Context sSystemContext = null;
    private static volatile boolean sIsFakeScreenEnabled = false;
    private static volatile int sAutoScreenOffSeconds = 0;

    private FakeScreenIpc() {
        // 工具类，禁止实例化。
    }

    /**
     * 当前开关状态，由开关广播保持同步。
     *
     * @return 已开启时返回 true。
     */
    static boolean isEnabled() {
        return sIsFakeScreenEnabled;
    }

    /**
     * 从 PhoneWindowManager 捕获的系统 Context。
     *
     * @return 捕获到的 Context；{@link #attach} 尚未执行时返回 null。
     */

    static int getAutoScreenOffSeconds() {
        return sAutoScreenOffSeconds;
    }

    static Context getSystemContext() {
        return sSystemContext;
    }

    /**
     * 从 PhoneWindowManager 实例获取系统 Context，并在该实例存在时注册开关广播接收器。
     *
     * @param module      用于框架日志的模块实例。
     * @param pwmClass    PhoneWindowManager 的 Class 引用。
     * @param pwmInstance PhoneWindowManager 实例对象。
     */
    static void attach(XposedModule module, Class<?> pwmClass, Object pwmInstance) {
        sSystemContext = ReflectUtils.getSystemContext(pwmClass, pwmInstance);
        if (pwmInstance == null) return;
        registerReceivers(module);
    }

    /**
     * 把 Settings.Global 复位为关闭状态，并注册广播接收器，使运行时开关状态、Settings.Global
     * 与界面三者保持同步。
     *
     * @param module 用于框架日志的模块实例。
     */
    private static void registerReceivers(XposedModule module) {
        try {
            if (sSystemContext != null) {
                // 清理旧版本遗留的保存值，避免曾经开启模块时系统超时一直处于“永不”状态。
                restoreLegacyScreenTimeout(module);

                Settings.Global.putInt(sSystemContext.getContentResolver(), FakeScreenState.KEY_FAKE_SCREEN_ENABLED, 0);

                try {
                    sAutoScreenOffSeconds = Settings.Global.getInt(
                            sSystemContext.getContentResolver(),
                            FakeScreenState.KEY_AUTO_SCREEN_OFF_SECONDS,
                            0);
                } catch (Throwable ignored) {
                    sAutoScreenOffSeconds = 0;
                }
                AutoScreenOffScheduler.setSeconds(sAutoScreenOffSeconds);
                applySideEffects(false);

                IntentFilter filter = new IntentFilter();
                filter.addAction(FakeScreenState.ACTION_TOGGLE_FAKE_SCREEN);
                filter.addAction(FakeScreenState.ACTION_AUTO_SCREEN_OFF_CHANGED);

                BroadcastReceiver receiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        String action = intent.getAction();
                        if (FakeScreenState.ACTION_TOGGLE_FAKE_SCREEN.equals(action)) {
                            boolean enabled = intent.getBooleanExtra(FakeScreenState.EXTRA_ENABLED, false);
                            sIsFakeScreenEnabled = enabled;

                            try {
                                Settings.Global.putInt(
                                        context.getContentResolver(),
                                        FakeScreenState.KEY_FAKE_SCREEN_ENABLED,
                                        enabled ? 1 : 0
                                );
                            } catch (Throwable t) {
                                module.log(Log.ERROR, TAG, "Failed to write Settings.Global in system_server", t);
                            }

                            applySideEffects(enabled);
                        } else if (FakeScreenState.ACTION_AUTO_SCREEN_OFF_CHANGED.equals(action)) {
                            int seconds = intent.getIntExtra(FakeScreenState.EXTRA_AUTO_SCREEN_OFF_SECONDS, 0);
                            if (seconds < 0) {
                                seconds = 0;
                            }
                            sAutoScreenOffSeconds = seconds;

                            try {
                                Settings.Global.putInt(
                                        context.getContentResolver(),
                                        FakeScreenState.KEY_AUTO_SCREEN_OFF_SECONDS,
                                        seconds
                                );
                            } catch (Throwable t) {
                                module.log(Log.ERROR, TAG, "Failed to write auto screen off seconds", t);
                            }

                            AutoScreenOffScheduler.setSeconds(seconds);
                        }
                    }
                };

                ContextCompat.registerReceiver(
                        sSystemContext,
                        receiver,
                        filter,
                        ContextCompat.RECEIVER_EXPORTED
                );
            } else {
                module.log(Log.ERROR, TAG, "Failed to extract Context from PhoneWindowManager");
            }
        } catch (Throwable t) {
            module.log(Log.ERROR, TAG, "Failed to register BroadcastReceiver", t);
        }
    }

    private static void restoreLegacyScreenTimeout(XposedModule module) {
        try {
            if (sSystemContext == null) return;
            android.content.ContentResolver resolver = sSystemContext.getContentResolver();
            int saved = Settings.System.getInt(resolver, KEY_OLD_SCREEN_OFF_TIMEOUT, NO_SAVED_TIMEOUT);
            if (saved == NO_SAVED_TIMEOUT) return;

            Settings.System.putInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT, saved);
            Settings.System.putInt(resolver, KEY_OLD_SCREEN_OFF_TIMEOUT, NO_SAVED_TIMEOUT);
            module.log(Log.INFO, TAG, "Restored screen timeout saved by an older version");
        } catch (Throwable t) {
            module.log(Log.ERROR, TAG, "Failed to restore legacy screen timeout", t);
        }
    }

    private static void applySideEffects(boolean enabled) {
        if (enabled) {
            ScreenTimeoutController.saveOldTimeOut();
        } else {
            ScreenTimeoutController.recoveryTimeOut();
        }
        AutoScreenOffScheduler.setEnabled(enabled);
    }
}
