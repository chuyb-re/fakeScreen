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


    private static Context sSystemContext = null;
    private static volatile boolean sIsFakeScreenEnabled = false;

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
        registerToggleReceiver(module);
    }

    /**
     * 把 Settings.Global 复位为关闭状态，并注册广播接收器，使运行时开关状态、Settings.Global
     * 与界面三者保持同步。
     *
     * @param module 用于框架日志的模块实例。
     */
    private static void registerToggleReceiver(XposedModule module) {
        try {
            if (sSystemContext != null) {
                // 开机时把 Settings.Global 复位为关闭状态
                Settings.Global.putInt(sSystemContext.getContentResolver(), FakeScreenState.KEY_FAKE_SCREEN_ENABLED, 0);

                // 开机后功能处于关闭状态，因此这里还原上一次会话遗留的超时覆盖，
                // 避免屏幕一直保持不自动熄灭。
                applyScreenTimeout(module, false);

                IntentFilter filter = new IntentFilter(FakeScreenState.ACTION_TOGGLE_FAKE_SCREEN);
                BroadcastReceiver receiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        boolean enabled = intent.getBooleanExtra(FakeScreenState.EXTRA_ENABLED, false);
                        sIsFakeScreenEnabled = enabled;

                        try {
                            Settings.Global.putInt(
                                    context.getContentResolver(),
                                    FakeScreenState.KEY_FAKE_SCREEN_ENABLED,
                                    enabled ? 1 : 0
                            );
                        } catch (Exception e) {
                            XLog.error("Failed to write Settings.Global in system_server", e);
                        }
                        applyScreenTimeout(module, enabled);
                    }
                };

                ContextCompat.registerReceiver(
                        sSystemContext,
                        receiver,
                        filter,
                        ContextCompat.RECEIVER_EXPORTED
                );
                XLog.log(Log.INFO,"Register BroadcastReceiver successful");
            } else {
                XLog.log(Log.ERROR,"Failed to extract Context from PhoneWindowManager");
            }
        } catch (Exception e) {
            XLog.error("Failed to register BroadcastReceiver", e);
        }
    }

    /**
     * 应用功能对应的屏幕超时副作用：功能开启时让屏幕永不自动熄灭，
     * 功能关闭时还原用户原本设置的超时。
     *
     * @param module  用于框架日志的模块实例。
     * @param enabled 当前开关状态。
     */
    private static void applyScreenTimeout(XposedModule module, boolean enabled) {
        try {
            if (enabled) {
                ScreenTimeoutController.saveOldTimeOut();
            } else {
                ScreenTimeoutController.recoveryTimeOut();
            }
        } catch (Exception e) {
            XLog.error("Failed to apply screen off timeout", e);
        }
    }
}
