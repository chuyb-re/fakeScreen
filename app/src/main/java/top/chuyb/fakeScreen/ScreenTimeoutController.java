package top.chuyb.fakeScreen;

import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;

/**
 * 屏幕自动熄灭超时处理，与 hook 无关：功能开启时把屏幕设为永不自动熄灭，
 * 功能关闭后再还原用户设置的超时。
 *
 * @since 1.0
 * @author chuyb-re
 */
final class ScreenTimeoutController {

    /** 保存被覆盖前的超时所使用的 Settings.System 键。 */
    private static final String KEY_OLD_SCREEN_OFF_TIMEOUT = "old_screen_off_timeout";

    /** 读不到当前超时时使用的兜底值。 */
    private static final int DEFAULT_SCREEN_OFF_TIMEOUT_MS = 15000;

    /** 未保存过超时时，{@link #KEY_OLD_SCREEN_OFF_TIMEOUT} 中存放的标记值。 */
    private static final int NO_SAVED_TIMEOUT = -1;

    /** 屏幕永不自动熄灭。 */
    private static final int SCREEN_OFF_TIMEOUT_NEVER = Integer.MAX_VALUE;

    private ScreenTimeoutController() {
        // 工具类，禁止实例化。
    }

    /**
     * 保存当前的屏幕超时，并把屏幕设为永不自动熄灭。
     *
     * <p>已经保存过超时时不覆盖，当前值本身就是“永不”时也不保存，
     * 因此重复收到开启事件也不会破坏用户原本的配置。</p>
     */
    static void saveOldTimeOut() {
        ContentResolver cr = requireSystemContext().getContentResolver();
        int saved = Settings.System.getInt(cr, KEY_OLD_SCREEN_OFF_TIMEOUT, NO_SAVED_TIMEOUT);
        int current = Settings.System.getInt(
                cr, Settings.System.SCREEN_OFF_TIMEOUT, DEFAULT_SCREEN_OFF_TIMEOUT_MS);

        if (saved == NO_SAVED_TIMEOUT && current != SCREEN_OFF_TIMEOUT_NEVER) {
            Settings.System.putInt(cr, KEY_OLD_SCREEN_OFF_TIMEOUT, current);
        }

        Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, SCREEN_OFF_TIMEOUT_NEVER);
    }

    /**
     * 还原 {@link #saveOldTimeOut()} 之前保存的屏幕超时。
     *
     * <p>没有保存过任何值时不做任何操作，因此用户自己设置的“永不”不会被改动。</p>
     */
    static void recoveryTimeOut() {
        ContentResolver cr = requireSystemContext().getContentResolver();
        int saved = Settings.System.getInt(cr, KEY_OLD_SCREEN_OFF_TIMEOUT, NO_SAVED_TIMEOUT);
        if (saved == NO_SAVED_TIMEOUT) return;

        Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, saved);
        // 清除记录，避免之后的还原带回过期的超时值。
        Settings.System.putInt(cr, KEY_OLD_SCREEN_OFF_TIMEOUT, NO_SAVED_TIMEOUT);
    }

    /**
     * @return 在 system_server 中捕获的系统 Context。
     * @throws IllegalStateException 尚未捕获到 Context 时抛出。
     */
    private static Context requireSystemContext() {
        Context context = FakeScreenIpc.getSystemContext();
        if (context == null) {
            throw new IllegalStateException("System context is not available yet");
        }
        return context;
    }
}
