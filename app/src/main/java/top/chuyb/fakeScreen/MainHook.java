package top.chuyb.fakeScreen;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Xposed 模块入口：安装 hook，并决定电源键被按下时如何处理。所有与 hook 无关的逻辑都放在各自的类中：
 * {@link FakeScreenIpc}（IPC / 状态同步）、{@link DisplayPowerControl}（显示电源模式）、
 * {@link ScreenTimeoutController}（屏幕超时）与 {@link ReflectUtils}（反射工具）。
 *
 * @since 1.0
 * @author chuyb-re
 */
public class MainHook extends XposedModule {
    private static final String TAG = "FakeScreen";

    private static final Handler mHandler = new Handler(Looper.getMainLooper());
    private static Runnable mPendingPowerTask = null;
    private static final long MULTI_PRESS_TIMEOUT_MS = 220; // 多击防抖超时

    private final String PREFS_NAME ="FakeScreenSetting";
    /**
     * system_server 正在初始化时调用的入口。
     *
     * @param param Xposed 框架提供的 SystemServerStartingParam，其中包含 ClassLoader。
     */
    @Override
    public void onSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param) {
        super.onSystemServerStarting(param);
        ClassLoader classLoader = param.getClassLoader();
        try {
            Class<?> pwmClass = classLoader.loadClass("com.android.server.policy.PhoneWindowManager");
            // 1. 动态注册 IPC 接收器
            AutoScreenOffScheduler.init(this, classLoader);
            registerIpcReceiver(pwmClass);
            // 2. hook 电源键处理
            hookPowerButton(pwmClass, classLoader);
            //3. 初始化兼容模式设置
            hookUserActivity(classLoader);
            SharedPreferences remotePreferences = getRemotePreferences(PREFS_NAME);
            DisplayPowerControl.useCompatibilityMode = remotePreferences.getBoolean("CompatibilityMode", false);
            //4. 注册监听器
            remotePreferences.registerOnSharedPreferenceChangeListener((sharedPreferences, key) -> {
                if (key.equals("CompatibilityMode")) {
                    DisplayPowerControl.useCompatibilityMode = sharedPreferences.getBoolean("CompatibilityMode", false);
                }
            });
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "MainHook initialization error: " + Log.getStackTraceString(t));
        }
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
            FakeScreenIpc.attach(this, pwmClass, pwmInstance);
            return result;
        });
    }

    // =========================================================================
    // 2. 电源键 hook 与单击拦截
    // =========================================================================

    /**
     * hook PhoneWindowManager 中的 powerPress 方法，以拦截单击手势。
     *
     * @param pwmClass    PhoneWindowManager 的 Class 引用。
     * @param classLoader 用于加载系统类的 ClassLoader。
     */

    /**
     * PowerManagerService 是触摸、按键等用户活动的统一入口。
     * 每次用户活动都重置自动假息屏倒计时，避免操作过程中屏幕被定时熄灭。
     */
    private void hookUserActivity(ClassLoader classLoader) {
        try {
            Class<?> powerManagerService = classLoader.loadClass("com.android.server.power.PowerManagerService");
            int hooked = 0;
            for (Method method : powerManagerService.getDeclaredMethods()) {
                if (!"userActivityInternal".equals(method.getName())) {
                    continue;
                }
                method.setAccessible(true);
                hook(method).intercept(chain -> {
                    // Reset before PowerManagerService processes the event, so a callback
                    // racing with the end of the timeout sees the latest activity first.
                    AutoScreenOffScheduler.onUserActivity();
                    return chain.proceed();
                });
                hooked++;
            }
            if (hooked == 0) {
                log(Log.WARN, TAG, "PowerManagerService.userActivityInternal not found; auto screen-off timer cannot track user activity on this Android version");
            } else {
                log(Log.INFO, TAG, "Hooked " + hooked + " PowerManagerService user activity method(s)");
            }
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Failed to hook PowerManagerService user activity", t);
        }
    }


    private void hookPowerButton(Class<?> pwmClass, ClassLoader classLoader) {
        Method powerPress = findPowerPressMethod(pwmClass);
        if (powerPress == null) {
            log(Log.ERROR, TAG, "Failed to find suitable powerPress method");
            return;
        }
        powerPress.setAccessible(true);

        hook(powerPress).intercept(chain -> {
            if (!FakeScreenIpc.isEnabled()) {
                return chain.proceed();
            }

            int count = extractClickCount(chain.getArgs());

            // 放行多击手势（例如双击启动相机）
            if (count > 1) {
                cancelPendingPowerTask();
                DisplayPowerControl.resetTargetMode();
                return chain.proceed();
            }
            // 拦截单击电源键
            if (count == 1) {
                cancelPendingPowerTask();
                mPendingPowerTask = () -> {
                    try {
                        DisplayPowerControl.toggle(this, classLoader);
                        AutoScreenOffScheduler.onDisplayToggled(DisplayPowerControl.isExpectingOn());
                    } catch (Exception e) {
                        log(Log.ERROR, TAG, "An exception occurred while switching the screen's power mode.", e);
                        return;
                    }
                    mPendingPowerTask = null;
                };

                mHandler.postDelayed(mPendingPowerTask, MULTI_PRESS_TIMEOUT_MS);
                return null; // 拦截系统原生的锁屏与休眠行为
            }

            return chain.proceed();
        });
    }

    /**
     * 取消 Handler 队列中待执行的电源键延时任务。
     */
    private void cancelPendingPowerTask() {
        if (mPendingPowerTask != null) {
            mHandler.removeCallbacks(mPendingPowerTask);
            mPendingPowerTask = null;
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
