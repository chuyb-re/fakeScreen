package top.chuyb.fakeScreen.util;

import android.util.Log;
import io.github.libxposed.api.XposedModule;
import top.chuyb.fakeScreen.core.Settings;

/**
 * 日志工具
 * @author chuyb-re
 * @since 2.1
 */
public class XLog {
    private static XposedModule module;
    private static final String TAG="FakeScreen";

    private XLog(){

    }

    public static void log(int level,String message){
        module.log(level,TAG,message);
    }

    public static void error(String message,Exception e){
        module.log(Log.ERROR,TAG,message,e);
    }

    public static void debug(String message){
        if(Settings.isDebugModeOn()){
            module.log(Log.DEBUG,TAG,message);
        }
    }
    public static void init(XposedModule module){
        XLog.module=module;

    }

}
