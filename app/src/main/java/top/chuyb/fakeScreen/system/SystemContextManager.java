package top.chuyb.fakeScreen.system;

import android.content.Context;

import top.chuyb.fakeScreen.util.ReflectUtils;
import top.chuyb.fakeScreen.util.XLog;

/**
 * 持有 system_server 中可供模块各功能复用的系统 Context。
 *
 * <p>由 PhoneWindowManager 初始化完成后捕获一次，之后系统侧功能通过
 * {@link #getSystemContext()} 或 {@link #requireSystemContext()} 访问。</p>
 */
public final class SystemContextManager {

    private static volatile Context sSystemContext;

    private SystemContextManager() {
        // 工具类，禁止实例化。
    }

    /**
     * 从 PhoneWindowManager 实例捕获系统 Context。
     *
     * @param pwmClass PhoneWindowManager 的 Class 引用。
     * @param pwmInstance 已初始化的 PhoneWindowManager 实例。
     * @return 捕获到 Context 时返回 true。
     */
    public static boolean initialize(Class<?> pwmClass, Object pwmInstance) {
        Context context = ReflectUtils.getSystemContext(pwmClass, pwmInstance);
        if (context == null) {
            XLog.debug("System Context capture failed from PhoneWindowManager");
            return false;
        }
        sSystemContext = context;
        XLog.debug("System Context captured: " + context.getClass().getName());
        return true;
    }

    /**
     * 获取已捕获的系统 Context。
     *
     * @return Context；初始化尚未完成时返回 null。
     */
    public static Context getSystemContext() {
        return sSystemContext;
    }

    /**
     * 获取已捕获的系统 Context，未初始化时抛出异常。
     */
    public static Context requireSystemContext() {
        Context context = sSystemContext;
        if (context == null) {
            throw new IllegalStateException("System context is not available yet");
        }
        return context;
    }
}
