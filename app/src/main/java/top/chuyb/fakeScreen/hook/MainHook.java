package top.chuyb.fakeScreen.hook;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.KeyEvent;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Method;
import java.util.List;

import top.chuyb.fakeScreen.core.Settings;
import top.chuyb.fakeScreen.system.DisplayPowerControl;
import top.chuyb.fakeScreen.system.FakeScreenIpc;
import top.chuyb.fakeScreen.system.ScreenTimeoutController;
import top.chuyb.fakeScreen.system.SystemContextManager;
import top.chuyb.fakeScreen.util.ReflectUtils;
import top.chuyb.fakeScreen.util.XLog;

/**
 * Xposed 模块入口：安装 hook，并决定电源键被按下时如何处理。所有与 hook 无关的逻辑都放在各自的类中：
 * {@link FakeScreenIpc}（IPC / 状态同步）、{@link DisplayPowerControl}（显示电源模式）、
 * {@link ScreenTimeoutController}（屏幕超时）与 {@link ReflectUtils}（反射工具）。
 *
 * @since 1.0
 * @author chuyb-re
 */
public class MainHook extends XposedModule {
    // 标记当前电源键按下时是否成功捕获了交互状态。
    private static volatile boolean sPowerKeyOriginCaptured;
    // 记录按下时设备是否处于非交互状态（通常正在休眠）。
    private static volatile boolean sPowerKeyBeganNonInteractive;

    private final String PREFS_NAME ="FakeScreenSetting";

    /**
     * system_server 正在初始化时调用的入口。
     *
     * @param param Xposed 框架提供的 SystemServerStartingParam，其中包含 ClassLoader。
     */
    @Override
    public void onSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param) {
        super.onSystemServerStarting(param);
        XLog.init(this);
        ClassLoader classLoader = param.getClassLoader();
        try {
            Class<?> pwmClass = classLoader.loadClass("com.android.server.policy.PhoneWindowManager");
            // 1. 动态注册 IPC 接收器
            registerIpcReceiver(pwmClass);
            // 2. 记录按键开始时的交互状态，再 hook 延后的 powerPress
            hookPowerKeyDown(pwmClass);
            hookPowerButton(pwmClass, classLoader);
            // 3. 初始化模块设置并监听后续变更
            initializeSettings();
        } catch (Exception e) {
            XLog.error("MainHook initialization error",e);
        }
    }

    /**
     * 从 Xposed 远程偏好中加载 Settings 的初始值，并保持运行时值同步。
     */
    private void initializeSettings() {
        SharedPreferences remotePreferences = getRemotePreferences(PREFS_NAME);
        Settings.setCompatibilityModeOn(remotePreferences.getBoolean("CompatibilityMode", false));
        Settings.setDebugModeOn(remotePreferences.getBoolean("DebugMode", false));
        XLog.debug("Settings loaded: compatibilityMode=" + Settings.isCompatibilityModeOn()
                + ", debugMode=" + Settings.isDebugModeOn());
        remotePreferences.registerOnSharedPreferenceChangeListener((sharedPreferences, key) -> {
            if ("CompatibilityMode".equals(key)) {
                Settings.setCompatibilityModeOn(sharedPreferences.getBoolean("CompatibilityMode", false));
                XLog.debug("Setting changed: compatibilityMode=" + Settings.isCompatibilityModeOn());
            } else if ("DebugMode".equals(key)) {
                Settings.setDebugModeOn(sharedPreferences.getBoolean("DebugMode", false));
                XLog.log(Log.INFO,"Setting changed: debugMode=" + Settings.isDebugModeOn());
            }
        });
    }

    // =========================================================================
    // 1. IPC 接收器 hook
    // =========================================================================

    /**
     * hook PhoneWindowManager.init，以便在实例（及其系统 Context）就绪后注册 IPC 广播接收器。
     *
     * @param pwmClass PhoneWindowManager 的 Class 引用。
     */
    private void registerIpcReceiver(Class<?> pwmClass) {
        Method initMethod = ReflectUtils.findMethodByName(pwmClass, "init");
        if (initMethod == null) return;
        initMethod.setAccessible(true);
        hook(initMethod).intercept(chain -> {
            Object result = chain.proceed();
            Object pwmInstance = chain.getThisObject();
            if (SystemContextManager.initialize(pwmClass, pwmInstance)) {
                FakeScreenIpc.attach(this);
            } else {
                XLog.log(Log.ERROR, "Failed to extract Context from PhoneWindowManager");
            }
            return result;
        });
    }

    // =========================================================================
    // 2. 电源键 hook 与单击拦截
    // =========================================================================

    /**
     * 在系统尚未执行电源键唤醒处理时保存交互状态，供之后延迟调用的 powerPress 使用。
     */
    private void hookPowerKeyDown(Class<?> pwmClass) {
        Method powerKeyDown = findPowerKeyDownMethod(pwmClass);
        if (powerKeyDown == null) {
            XLog.log(Log.ERROR, "Failed to find interceptPowerKeyDown(KeyEvent, boolean)");
            return;
        }
        powerKeyDown.setAccessible(true);
        hook(powerKeyDown).intercept(chain -> {
            List<Object> args = chain.getArgs();
            if (args.size() > 1 && args.get(1) instanceof Boolean) {
                sPowerKeyBeganNonInteractive = !((Boolean) args.get(1));
                sPowerKeyOriginCaptured = true;
                KeyEvent event = args.get(0) instanceof KeyEvent ? (KeyEvent) args.get(0) : null;
                XLog.debug("Power key down: keyCode=" + (event == null ? "unknown" : event.getKeyCode())
                        + ", downTime=" + (event == null ? "unknown" : event.getDownTime())
                        + ", interactive=" + !sPowerKeyBeganNonInteractive
                        + ", captured=" + sPowerKeyOriginCaptured);
            } else {
                XLog.debug("Power key down: interactive argument unavailable; args=" + args);
            }
            return chain.proceed();
        });
    }

    private static Method findPowerKeyDownMethod(Class<?> pwmClass) {
        for (Method method : pwmClass.getDeclaredMethods()) {
            if (!"interceptPowerKeyDown".equals(method.getName())) continue;
            Class<?>[] paramTypes = method.getParameterTypes();
            if (paramTypes.length >= 2
                    && paramTypes[0] == KeyEvent.class
                    && paramTypes[1] == boolean.class) {
                return method;
            }
        }
        return null;
    }

    /**
     * hook PhoneWindowManager 中的 powerPress 方法，以拦截单击手势。
     *
     * @param pwmClass    PhoneWindowManager 的 Class 引用。
     * @param classLoader 用于加载系统类的 ClassLoader。
     */
    private void hookPowerButton(Class<?> pwmClass, ClassLoader classLoader) {
        Method powerPress = findPowerPressMethod(pwmClass);
        if (powerPress == null) {
            XLog.log(Log.ERROR,"Failed to find suitable powerPress method");
            return;
        }
        powerPress.setAccessible(true);

        hook(powerPress).intercept(chain -> {
            List<Object> args = chain.getArgs();
            long eventTime = args.size() > 0 && args.get(0) instanceof Long ? (Long) args.get(0) : -1L;
            int count = extractClickCount(args);
            Object displayId = args.size() > 2 ? args.get(2) : "unknown";
            boolean enabled = FakeScreenIpc.isEnabled();
            boolean originCaptured = sPowerKeyOriginCaptured;
            // 仅当状态确实捕获且按下时非交互，才交给系统处理唤醒。
            boolean beganNonInteractive = originCaptured && sPowerKeyBeganNonInteractive;
            XLog.debug("powerPress: eventTime=" + eventTime + ", count=" + count
                    + ", displayId=" + displayId + ", fakeScreenEnabled=" + enabled
                    + ", originCaptured=" + originCaptured
                    + ", beganNonInteractive=" + beganNonInteractive);

            if (!enabled) {
                sPowerKeyOriginCaptured = false;
                XLog.debug("powerPress: feature disabled; proceeding with system handling");
                return chain.proceed();
            }

            // 放行多击手势（例如双击启动相机）
            if (count > 1) {
                sPowerKeyOriginCaptured = false;
                DisplayPowerControl.resetTargetMode();
                XLog.debug("powerPress: multi-press; reset target mode and proceed with system handling");
                return chain.proceed();
            }
            //尝试切换屏幕；未切换时交还系统原生处理
            if (count == 1) {
                sPowerKeyOriginCaptured = false;
                if (beganNonInteractive) {
                    XLog.debug("powerPress: key began while non-interactive; proceeding with system wake handling");
                    return chain.proceed();
                }

                if (isKeyguardShowing()) {
                    XLog.debug("powerPress: keyguard is showing; proceeding with system power handling");
                    return chain.proceed();
                }

                try {
                    boolean toggled = DisplayPowerControl.toggle(this, classLoader);
                    XLog.debug("powerPress: display toggle result=" + toggled);
                    if (toggled) {
                        return null;
                    }
                } catch (Exception e) {
                    XLog.error("An exception occurred while switching the screen’s power mode.", e);
                    XLog.debug("powerPress: toggle raised an exception; proceeding with system handling");
                }
                XLog.debug("powerPress: no display toggle occurred; proceeding with system handling");
                return chain.proceed();
            }
            XLog.debug("powerPress: unsupported press count; proceeding with system handling");
            return chain.proceed();
        });
    }

    /**
     * 锁屏显示期间让系统处理电源键，避免模块直接关闭显示器。
     * 状态无法确认时也采用系统原生处理。
     */
    private boolean isKeyguardShowing() {
        try {
            Context context = SystemContextManager.getSystemContext();
            if (context == null) {
                XLog.debug("Keyguard check unavailable: system Context is null");
                return true;
            }

            KeyguardManager keyguardManager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
            if (keyguardManager == null) {
                XLog.debug("Keyguard check unavailable: KeyguardManager is null");
                return true;
            }

            boolean showing = keyguardManager.isKeyguardLocked();
            XLog.debug("Keyguard state: showing=" + showing);
            return showing;
        } catch (Exception e) {
            XLog.error("Failed to check keyguard state; using system power handling.", e);
            return true;
        }
    }

    /**
     * 从方法参数列表中取出点击次数。
     *
     * @param args 调用时捕获的方法参数列表。
     * @return 找到的点击次数，没有则返回 1。
     */
    private int extractClickCount(List<Object> args) {
        for (Object arg : args) {
            if (arg instanceof Integer) {
                return (Integer) arg;
            }
        }
        return 1;
    }

    /**
     * 在 PhoneWindowManager 中查找参数签名匹配的 powerPress 方法。
     *
     * @param pwmClass 目标 PhoneWindowManager 的 Class 引用。
     * @return 匹配的 powerPress Method，未找到时返回 null。
     */
    private static Method findPowerPressMethod(Class<?> pwmClass) {
        for (Method method : pwmClass.getDeclaredMethods()) {
            if ("powerPress".equals(method.getName())) {
                Class<?>[] paramTypes = method.getParameterTypes();
                if (paramTypes.length >= 2 && paramTypes[0] == long.class && paramTypes[1] == int.class) {
                    return method;
                }
            }
        }
        return null;
    }
}
