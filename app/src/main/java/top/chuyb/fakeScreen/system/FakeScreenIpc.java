package top.chuyb.fakeScreen.system;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.provider.Settings;
import android.util.Log;

import androidx.core.content.ContextCompat;

import top.chuyb.fakeScreen.core.FakeScreenState;
import top.chuyb.fakeScreen.util.XLog;

import io.github.libxposed.api.XposedModule;

/**
 * 界面应用与 system_server 之间的 IPC 与状态同步：注册开关广播接收器并向 hook 层暴露当前开关状态。
 * 系统 Context 由 {@link SystemContextManager} 统一管理。本类不安装任何 hook。
 *
 * @since 1.0
 * @author chuyb-re
 */
public final class FakeScreenIpc {


    private static volatile boolean sIsFakeScreenEnabled = false;

    private FakeScreenIpc() {
        // 工具类，禁止实例化。
    }

    /**
     * 当前开关状态，由开关广播保持同步。
     *
     * @return 已开启时返回 true。
     */
    public static boolean isEnabled() {
        return sIsFakeScreenEnabled;
    }

    /**
     * 注册开关广播接收器。调用前需由 {@link SystemContextManager} 捕获系统 Context。
     *
     * @param module      用于框架日志的模块实例。
     */
    public static void attach(XposedModule module) {
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
            Context systemContext = SystemContextManager.getSystemContext();
            if (systemContext != null) {
                // 开机时把 Settings.Global 复位为关闭状态
                Settings.Global.putInt(systemContext.getContentResolver(), FakeScreenState.KEY_FAKE_SCREEN_ENABLED, 0);
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
                    }
                };

                ContextCompat.registerReceiver(
                        systemContext,
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

}
