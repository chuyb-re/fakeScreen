package top.chuyb.fakeScreen;

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import io.github.libxposed.api.XposedModule;

import java.lang.reflect.Method;

/**
 * 通过硬件层 SurfaceControl 接口控制显示屏电源模式。本类不安装任何 hook，
 * hook 层只调用 {@link #toggle(XposedModule, ClassLoader)} 与 {@link #resetTargetMode()}。
 *
 * @since 1.0
 * @author chuyb-re
 */
final class DisplayPowerControl {

    private static final String TAG = "FakeScreen";

    private static final int POWER_MODE_OFF = 0;    // 熄屏
    private static final int POWER_MODE_NORMAL = 2; // 亮屏
    private static volatile int sTargetPowerMode = POWER_MODE_OFF;

    private DisplayPowerControl() {
        // 工具类，禁止实例化。
    }

    /**
     * 复位待切换的电源模式，使下一次切换执行熄屏。
     */
    static void resetTargetMode() {
        sTargetPowerMode = POWER_MODE_OFF;
    }

    /**
     * 通过硬件层 SurfaceControl 接口切换屏幕电源模式。
     *
     * @param module      用于框架日志的模块实例。
     * @param classLoader 用于解析 SurfaceControl 与显示 token 的 ClassLoader。
     */
    static void toggle(XposedModule module, ClassLoader classLoader) {
        IBinder displayBinder = getUniversalDisplayBinder(classLoader);
        if (displayBinder == null) {
            module.log(Log.ERROR, TAG, "DisplayBinder is null, skipping display power mode toggle");
            return;
        }

        try {
            Class<?> surfaceControlClass = classLoader.loadClass("android.view.SurfaceControl");
            @SuppressLint("SoonBlockedPrivateApi")
            Method setDisplayPowerMode = surfaceControlClass.getDeclaredMethod(
                    "setDisplayPowerMode", IBinder.class, int.class);
            setDisplayPowerMode.setAccessible(true);

            // 直接调用 SurfaceControl 切换显示电源模式
            setDisplayPowerMode.invoke(null, displayBinder, sTargetPowerMode);

            // 翻转下一次的目标电源模式
            sTargetPowerMode = (sTargetPowerMode == POWER_MODE_OFF) ? POWER_MODE_NORMAL : POWER_MODE_OFF;
        } catch (Throwable t) {
            module.log(Log.ERROR, TAG, "Failed to invoke setDisplayPowerMode", t);
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

        if (sdkInt >= 34) { // Android 14+
            try {
                Class<?> displayControl = classLoader.loadClass("com.android.server.display.DisplayControl");
                Method getPhysicalDisplayIds = displayControl.getDeclaredMethod("getPhysicalDisplayIds");
                getPhysicalDisplayIds.setAccessible(true);
                long[] ids = (long[]) getPhysicalDisplayIds.invoke(null);
                if (ids != null && ids.length > 0) {
                    Method getPhysicalDisplayToken = displayControl.getDeclaredMethod("getPhysicalDisplayToken", long.class);
                    getPhysicalDisplayToken.setAccessible(true);
                    return (IBinder) getPhysicalDisplayToken.invoke(null, ids[0]);
                }
            } catch (Throwable ignored) {}
        }

        if (sdkInt >= 29) { // Android 10 ~ 13
            try {
                Class<?> surfaceControl = classLoader.loadClass("android.view.SurfaceControl");
                @SuppressLint("BlockedPrivateApi")
                Method getInternalDisplayToken = surfaceControl.getDeclaredMethod("getInternalDisplayToken");
                getInternalDisplayToken.setAccessible(true);
                IBinder token = (IBinder) getInternalDisplayToken.invoke(null);
                if (token != null) return token;
            } catch (Throwable ignored) {}
        }

        try { // Android 9 (API 28)
            Class<?> surfaceControl = classLoader.loadClass("android.view.SurfaceControl");
            Method getBuiltInDisplay = surfaceControl.getDeclaredMethod("getBuiltInDisplay", int.class);
            getBuiltInDisplay.setAccessible(true);
            return (IBinder) getBuiltInDisplay.invoke(null, 0);
        } catch (Throwable ignored) {}

        return null;
    }
}
