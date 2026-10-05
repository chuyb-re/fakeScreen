package top.chuyb.fakeScreen;

import android.content.Context;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * hook 层与 IPC 层共用的反射工具，本类不做任何 hook。
 *
 * @since 1.0
 * @author chuyb-re
 */
final class ReflectUtils {

    private ReflectUtils() {
        // 工具类，禁止实例化。
    }

    /**
     * 按方法名查找已声明的方法，不匹配参数列表。
     *
     * @param clazz 要检查的类。
     * @param name  方法名。
     * @return 匹配的 Method 对象，未找到时返回 null。
     */
    static Method findMethodByName(Class<?> clazz, String name) {
        for (Method m : clazz.getDeclaredMethods()) {
            if (name.equals(m.getName())) {
                return m;
            }
        }
        return null;
    }

    /**
     * 通过字段反射或方法调用，安全地从 PhoneWindowManager 实例中取出 Context。
     *
     * @param pwmClass    PhoneWindowManager 的 Class 引用。
     * @param pwmInstance PhoneWindowManager 实例对象。
     * @return 取到的系统 Context，取不到时返回 null。
     */
    static Context getSystemContext(Class<?> pwmClass, Object pwmInstance) {
        Class<?> currentClass = pwmClass;
        while (currentClass != null && currentClass != Object.class) {
            try {
                Field field = currentClass.getDeclaredField("mContext");
                field.setAccessible(true);
                Object ctx = field.get(pwmInstance);
                if (ctx instanceof Context) {
                    return (Context) ctx;
                }
            } catch (Throwable ignored) {}
            currentClass = currentClass.getSuperclass();
        }

        try {
            Method getContextMethod = pwmClass.getDeclaredMethod("getContext");
            getContextMethod.setAccessible(true);
            Object ctx = getContextMethod.invoke(pwmInstance);
            if (ctx instanceof Context) {
                return (Context) ctx;
            }
        } catch (Throwable ignored) {}

        return null;
    }
}
