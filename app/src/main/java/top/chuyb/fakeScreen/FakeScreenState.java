package top.chuyb.fakeScreen;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;

/**
 * 假熄屏功能的业务层。
 *
 * <p>本类持有持久化的开关状态（保存在 {@link Settings.Global} 中）以及用于通知 system_server 的
 * IPC 广播协议。它不持有任何 UI 引用，由 {@link MainActivity}（表现层）与
 * {@link MainHook}（system_server 侧）共用。</p>
 *
 * @since 1.0
 * @author chuyb-re
 */
public final class FakeScreenState {


    /** 保存在 Settings.Global 中的功能开关状态键。 */
    public static final String KEY_FAKE_SCREEN_ENABLED = "FakeScreen_enable";

    /** 界面切换开关时发送的广播 Action。 */
    public static final String ACTION_TOGGLE_FAKE_SCREEN = "top.chuyb.fakeScreen.ACTION_TOGGLE";

    /** 广播中携带目标开关状态的 extra 键。 */
    public static final String EXTRA_ENABLED = "enabled";

    private FakeScreenState() {
        // 工具类，禁止实例化。
    }

    /**
     * 从 Settings.Global 读取当前开关状态。
     *
     * @param context 任意可用的 Context。
     * @return 已开启返回 true，否则返回 false。
     */
    public static boolean isEnabled(Context context) {
        try {
            int currentState = Settings.Global.getInt(
                    context.getContentResolver(), KEY_FAKE_SCREEN_ENABLED, 0);
            return currentState == 1;
        } catch (Exception e) {
            XLog.error("Failed to read Settings.Global state", e);
            return false;
        }
    }

    /**
     * 广播状态变更请求，由 system_server 写入 Settings.Global。
     *
     * @param context 任意可用的 Context。
     * @param enabled 目标开关状态。
     */
    public static void setEnabled(Context context, boolean enabled) {
        Intent intent = new Intent(ACTION_TOGGLE_FAKE_SCREEN);
        intent.putExtra(EXTRA_ENABLED, enabled);
        context.sendBroadcast(intent);
    }
}
