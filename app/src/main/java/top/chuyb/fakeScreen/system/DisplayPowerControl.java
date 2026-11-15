package top.chuyb.fakeScreen.system;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import io.github.libxposed.api.XposedModule;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import top.chuyb.fakeScreen.core.Settings;
import top.chuyb.fakeScreen.util.XLog;

/**
 * 通过硬件层 SurfaceControl 接口控制显示屏电源模式。本类不安装任何 hook，
 * hook 层只调用 {@link #toggle(XposedModule, ClassLoader)} 与 {@link #resetTargetMode()}。
 *
 * @author chuyb-re
 * @since 1.0
 */
public final class DisplayPowerControl {
    private static final int POWER_MODE_OFF = 0;    // 熄屏
    private static final int POWER_MODE_NORMAL = 2; // 亮屏
    private static final AtomicInteger sTargetPowerMode = new AtomicInteger(POWER_MODE_OFF);


    private DisplayPowerControl() {
        // 工具类，禁止实例化。
    }

    /**
     * 复位待切换的电源模式，使下一次切换执行熄屏。
     */
    public static void resetTargetMode() {
        sTargetPowerMode.set(POWER_MODE_OFF);
    }

    /**
     * 通过硬件层 SurfaceControl 接口切换屏幕电源模式。
     *
     * @param module      用于框架日志的模块实例。
     * @param classLoader 用于解析 SurfaceControl 与显示 token 的 ClassLoader。
     * @return 屏幕状态切换结果，当使用该方法切换屏幕状态后，返回true
     */
    public static boolean toggle(XposedModule module, ClassLoader classLoader) throws IOException, ClassNotFoundException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        XLog.debug("toggle requested: targetMode=" + powerModeName(sTargetPowerMode.get())
                + ", compatibilityMode=" + Settings.isCompatibilityModeOn());
        IBinder displayBinder = getUniversalDisplayBinder(classLoader);
        if (displayBinder == null) {
            XLog.log(Log.ERROR,"DisplayBinder is null, skipping display power mode toggle");
            XLog.debug("toggle result=false: display binder unavailable");
            return false;
        }
        if (sTargetPowerMode.get() == POWER_MODE_OFF) {
            Context context = SystemContextManager.getSystemContext();
            if (context != null) {
                PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                if (powerManager == null) {
                    XLog.debug("toggle guard: PowerManager unavailable; interactive state not checked");
                } else {
                    boolean interactive = powerManager.isInteractive();
                    XLog.debug("toggle guard: target=OFF, systemInteractive=" + interactive);
                    if (!interactive) {
                        XLog.debug("toggle result=false: device is not interactive");
                        return false;
                    }
                }
            } else {
                XLog.debug("toggle guard: system Context unavailable; interactive state not checked");
            }
        }
        boolean result;
        if (Settings.isCompatibilityModeOn()) {
            result = doToggleInCompatibilityMode(classLoader, displayBinder);
        } else {
            result = doToggle(classLoader, displayBinder);
        }
        if (result) {
            sTargetPowerMode.set(sTargetPowerMode.get() == POWER_MODE_NORMAL ? POWER_MODE_OFF : POWER_MODE_NORMAL);
        }
        XLog.debug("toggle complete: result=" + result
                + ", nextTargetMode=" + powerModeName(sTargetPowerMode.get()));
        return result;
    }

    private static String powerModeName(int powerMode) {
        if (powerMode == POWER_MODE_OFF) return "OFF";
        if (powerMode == POWER_MODE_NORMAL) return "NORMAL";
        return "UNKNOWN(" + powerMode + ")";
    }

    /**
     * 使用普通模式切换电源模式
     */
    private static boolean doToggle(ClassLoader classLoader, IBinder displayBinder) throws ClassNotFoundException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
       XLog.debug("direct mode: applying targetMode=" + powerModeName(sTargetPowerMode.get()));
        setDisplayPowerMode(sTargetPowerMode.get(), classLoader, displayBinder);
        return true;
    }
    /**
     * 使用兼容模式切换电源模式，防止由于其他因素修改导致变量翻转失效
     */
    private static boolean doToggleInCompatibilityMode(ClassLoader classLoader, IBinder displayBinder) throws ClassNotFoundException, InvocationTargetException, NoSuchMethodException, IllegalAccessException, IOException {
        int targetPowerMode = -1;
        //通过硬件层获取当前屏幕状态
        Process process;

        try {
            process = Runtime.getRuntime().exec("dumpsys SurfaceFlinger");
        } catch (IOException e) {
            XLog.debug("compatibility mode: dumpsys failed to start (" + e.getMessage()
                    + "); falling back to targetMode=" + powerModeName(sTargetPowerMode.get()));
            return doToggle(classLoader, displayBinder);
        }
        //读取屏幕信息并切换状态
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.contains("powerMode=On")) {
                    targetPowerMode = POWER_MODE_OFF;
                    XLog.debug("compatibility mode: SurfaceFlinger reports powerMode=On; targetMode=OFF");
                    break;
                }

                if (line.contains("powerMode=Off")) {
                    targetPowerMode = POWER_MODE_NORMAL;
                    XLog.debug("compatibility mode: SurfaceFlinger reports powerMode=Off; targetMode=NORMAL");
                    break;
                }
            }
        }
        process.destroy();
        //获取失败进行降级
        if (targetPowerMode == -1) {
            XLog.debug("compatibility mode: display power state not found; falling back to targetMode=" + powerModeName(sTargetPowerMode.get()));
            return doToggle(classLoader, displayBinder);
        } else {
            setDisplayPowerMode(targetPowerMode, classLoader, displayBinder);
            return true;
        }

    }

    /**
     * 切换屏幕电源模式
     *
     * @param targetPowerMode 目标电源模式，参考{@link POWER_MODE_OFF}与{@link POWER_MODE_NORMAL}
     * @param classLoader     使用的类加载器，需能够访问私有api
     * @param displayBinder   目标屏幕binder
     */
    private static void setDisplayPowerMode(int targetPowerMode, ClassLoader classLoader, IBinder displayBinder) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        XLog.debug("applying SurfaceControl power mode=" + powerModeName(targetPowerMode));
        if(targetPowerMode==POWER_MODE_OFF){
            ScreenTimeoutController.saveOldTimeOut();
        }
        Class<?> surfaceControlClass = classLoader.loadClass("android.view.SurfaceControl");
        @SuppressLint("SoonBlockedPrivateApi")
        Method setDisplayPowerMode = surfaceControlClass.getDeclaredMethod(
                "setDisplayPowerMode", IBinder.class, int.class);
        setDisplayPowerMode.setAccessible(true);
        // 直接调用 SurfaceControl 切换显示电源模式
        setDisplayPowerMode.invoke(null, displayBinder, targetPowerMode);
        XLog.debug("SurfaceControl power mode call completed: mode=" + powerModeName(targetPowerMode));
        if(targetPowerMode==POWER_MODE_NORMAL){
            PowerManager powerManager= (PowerManager) SystemContextManager.requireSystemContext().getSystemService(Context.POWER_SERVICE);
            Method method = classLoader.loadClass("android.os.PowerManager").getMethod("userActivity", long.class, int.class, int.class);
            method.invoke(powerManager,SystemClock.uptimeMillis(),0,0);
            ScreenTimeoutController.recoveryTimeOut();
            XLog.debug("display restored: userActivity signaled and screen timeout recovered");
        }
    }

    /**
     * 获取各 Android 版本（Android 9 ~ 14+）下的物理显示屏 IBinder token。
     *
     * @param classLoader 用于解析系统类的 ClassLoader。
     * @return 物理显示屏 IBinder token，解析失败时返回 null。
     */
    private static IBinder getUniversalDisplayBinder(ClassLoader classLoader) {
        int sdkInt = Build.VERSION.SDK_INT;
        XLog.debug("resolving display binder: sdk=" + sdkInt);

        if (sdkInt >= 34) { // Android 14+
            try {
                @SuppressLint("PrivateApi") Class<?> displayControl = classLoader.loadClass("com.android.server.display.DisplayControl");
                Method getPhysicalDisplayIds = displayControl.getDeclaredMethod("getPhysicalDisplayIds");
                getPhysicalDisplayIds.setAccessible(true);
                long[] ids = (long[]) getPhysicalDisplayIds.invoke(null);
                if (ids != null && ids.length > 0) {
                    XLog.debug("physical display ids found=" + ids.length + ", using id=" + ids[0]);
                    Method getPhysicalDisplayToken = displayControl.getDeclaredMethod("getPhysicalDisplayToken", long.class);
                    getPhysicalDisplayToken.setAccessible(true);
                    IBinder token = (IBinder) getPhysicalDisplayToken.invoke(null, ids[0]);
                    XLog.debug("physical display token available=" + (token != null));
                    return token;
                }
                XLog.debug("no physical display ids returned; trying SurfaceControl fallback");
            } catch (Throwable e) {
                XLog.debug("DisplayControl token lookup failed: " + e.getClass().getSimpleName()
                        + ": " + e.getMessage());
            }
        }

        if (sdkInt >= 29) { // Android 10 ~ 13
            try {
                Class<?> surfaceControl = classLoader.loadClass("android.view.SurfaceControl");
                @SuppressLint("BlockedPrivateApi")
                Method getInternalDisplayToken = surfaceControl.getDeclaredMethod("getInternalDisplayToken");
                getInternalDisplayToken.setAccessible(true);
                IBinder token = (IBinder) getInternalDisplayToken.invoke(null);
                if (token != null) {
                    XLog.debug("internal display token resolved through SurfaceControl");
                    return token;
                }
                XLog.debug("SurfaceControl internal display token is null; trying API 28 fallback");
            } catch (Throwable e) {
                XLog.debug("SurfaceControl internal token lookup failed: " + e.getClass().getSimpleName()
                        + ": " + e.getMessage());
            }
        }

        try { // Android 9 (API 28)
            Class<?> surfaceControl = classLoader.loadClass("android.view.SurfaceControl");
            Method getBuiltInDisplay = surfaceControl.getDeclaredMethod("getBuiltInDisplay", int.class);
            getBuiltInDisplay.setAccessible(true);
            IBinder token = (IBinder) getBuiltInDisplay.invoke(null, 0);
            XLog.debug("built-in display token available=" + (token != null));
            return token;
        } catch (Throwable e) {
            XLog.debug("built-in display token lookup failed: " + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
        }

        XLog.debug("display binder resolution failed on all supported paths");
        return null;
    }
}
