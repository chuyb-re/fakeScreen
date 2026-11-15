package top.chuyb.fakeScreen.app;

import android.app.Application;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;
import java.util.concurrent.CopyOnWriteArraySet;

public class App extends Application implements XposedServiceHelper.OnServiceListener {

    private static final String TAG = "App";

    @Nullable
    private static volatile XposedService sService = null;
    private static final CopyOnWriteArraySet<ServiceStateListener> sServiceStateListeners =
            new CopyOnWriteArraySet<>();

    /**
     * 当前 Xposed service 的服务状态监听器。
     */
    public interface ServiceStateListener {
        void onServiceStateChanged(@Nullable XposedService service);
    }

    /**
     * 获取当前已连接的 Xposed service；尚未连接时返回 null。
     */
    @Nullable
    public static XposedService getService() {
        return sService;
    }

    /**
     * 注册服务状态监听器，可选择立即收到当前服务状态。
     */
    public static void addServiceStateListener(
            @NonNull ServiceStateListener listener,
            boolean notifyImmediately) {
        sServiceStateListeners.add(listener);
        if (notifyImmediately) {
            dispatchServiceState(listener, sService);
        }
    }

    /**
     * 移除服务状态监听器，通常在 Activity 的 onStop 中调用。
     */
    public static void removeServiceStateListener(@NonNull ServiceStateListener listener) {
        sServiceStateListeners.remove(listener);
    }

    private static void dispatchServiceState(
            @NonNull ServiceStateListener listener,
            @Nullable XposedService service) {
        if (sServiceStateListeners.contains(listener)) {
            listener.onServiceStateChanged(service);
        }
    }

    private void notifyServiceStateChanged(@Nullable XposedService service) {
        for (ServiceStateListener listener : sServiceStateListeners) {
            dispatchServiceState(listener, service);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        XposedServiceHelper.registerListener(this);
        Log.d(TAG, "XposedServiceHelper registered");
    }

    @Override
    public void onServiceBind(@NonNull XposedService service) {
        sService = service;
        notifyServiceStateChanged(service);
        Log.d(TAG, "XposedService connected");
    }

    @Override
    public void onServiceDied(@NonNull XposedService service) {
        sService = null;
        notifyServiceStateChanged(null);
        Log.w(TAG, "XposedService disconnected");
    }
}
