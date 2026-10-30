package io.github.sql0106.zybclean.compat;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * 兼容层：仿 legacy Xposed 的 {@code XposedHelpers}。现代 API 不再提供这些工具类，
 * 这里用纯反射实现模块用到的少数几个方法。
 */
public final class XposedHelpers {

    private XposedHelpers() {
    }

    // 注意：legacy 的这个方法声明 throws ClassNotFoundException，但模块里多个调用点
    // 没有 try/catch（如 Snap.install），这里改成抛非受检异常，调用方 catch Throwable 照样接得住。
    public static Class<?> findClass(String name, ClassLoader cl) {
        try {
            return Class.forName(name, false, cl);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("class not found: " + name, e);
        }
    }

    /** 反射调用实例方法；找不到精确参数类型时按「参数个数 + 可赋值」宽松匹配。 */
    public static Object callMethod(Object obj, String name, Object... args) {
        if (obj == null) throw new NullPointerException("callMethod on null: " + name);
        Object[] a = args == null ? new Object[0] : args;
        Method m = findMethod(obj.getClass(), name, a);
        try {
            m.setAccessible(true);
            return m.invoke(obj, a);
        } catch (InvocationTargetException e) {
            throw rethrow(e.getCause());
        } catch (Throwable t) {
            throw new RuntimeException("callMethod " + name + " failed: " + t, t);
        }
    }

    public static int getIntField(Object obj, String name) {
        try {
            Field f = findField(obj.getClass(), name);
            f.setAccessible(true);
            return f.getInt(obj);
        } catch (Throwable t) {
            throw new RuntimeException("getIntField " + name + " failed: " + t, t);
        }
    }

    public static Object getObjectField(Object obj, String name) {
        try {
            Field f = findField(obj.getClass(), name);
            f.setAccessible(true);
            return f.get(obj);
        } catch (Throwable t) {
            throw new RuntimeException("getObjectField " + name + " failed: " + t, t);
        }
    }

    public static int getStaticIntField(Class<?> clazz, String name) {
        try {
            Field f = findField(clazz, name);
            f.setAccessible(true);
            return f.getInt(null);
        } catch (Throwable t) {
            throw new RuntimeException("getStaticIntField " + name + " failed: " + t, t);
        }
    }

    private static Method findMethod(Class<?> clazz, String name, Object[] args) {
        Method fallback = null;
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals(name)) continue;
                if (m.getParameterTypes().length != args.length) continue;
                if (assignable(m.getParameterTypes(), args)) return m;
                if (fallback == null) fallback = m;
            }
        }
        if (fallback != null) return fallback;
        throw new RuntimeException("NoSuchMethod: " + clazz.getName() + "#" + name + "/" + args.length);
    }

    private static Field findField(Class<?> clazz, String name) {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // 继续往父类找
            }
        }
        throw new RuntimeException("NoSuchField: " + clazz.getName() + "#" + name);
    }

    private static boolean assignable(Class<?>[] types, Object[] args) {
        for (int i = 0; i < types.length; i++) {
            Class<?> t = types[i];
            Object v = args[i];
            if (v == null) {
                if (t.isPrimitive()) return false;
                continue;
            }
            if (t.isPrimitive()) {
                if (!wrap(t).isInstance(v)) return false;
            } else if (!t.isInstance(v)) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> wrap(Class<?> p) {
        if (p == int.class) return Integer.class;
        if (p == long.class) return Long.class;
        if (p == boolean.class) return Boolean.class;
        if (p == float.class) return Float.class;
        if (p == double.class) return Double.class;
        if (p == short.class) return Short.class;
        if (p == byte.class) return Byte.class;
        if (p == char.class) return Character.class;
        return Void.class;
    }

    /** 把受检异常原样抛出（sneaky throw）。 */
    private static RuntimeException rethrow(Throwable t) {
        if (t instanceof RuntimeException) throw (RuntimeException) t;
        if (t instanceof Error) throw (Error) t;
        throw new RuntimeException(t);
    }
}
