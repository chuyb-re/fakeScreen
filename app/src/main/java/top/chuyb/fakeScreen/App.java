package top.chuyb.fakeScreen;

import android.app.Application;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public class App extends Application implements XposedServiceHelper.OnServiceListener {

    private static final String TAG = "App";


    @Nullable
    private static volatile XposedService sService = null;

    public App(){
        super();
    }

    public static SharedPreferences getSharedPreferences(){
        return sService.getRemotePreferences("DuressWipe");
    }

    @Nullable
    public static XposedService getService() {
        return sService;
    }

    // ---------- Application 生命周期 ----------
    @Override
    public void onCreate() {
        super.onCreate();
        XposedServiceHelper.registerListener(this);
        Log.d(TAG, "XposedServiceHelper registered");
    }

    // ---------- 实现 OnServiceListener ----------
    @Override
    public void onServiceBind(@NonNull XposedService service) {
        sService = service;
        Log.d(TAG, "XposedService connected");
    }

    @Override
    public void onServiceDied(@NonNull XposedService service) {
        sService = null;
        Log.w(TAG, "XposedService disconnected");
    }
}