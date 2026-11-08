package io.github.sql0106.zybclean.compat;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;

/**
 * 兼容层：仿 legacy Xposed 的 {@code XposedHelpers}。现代 API 不再提供这些工具类，
 * 这里用纯反射实现模块用到的少数几个方法。
 *
 * <p>性能要点：ART 上 {@code getDeclaredMethods()} 每次调用都会复制一份 {@code Method[]}
 * （View 约 700 个方法），全树遍历时按 View 数量放大几十倍。所以这里缓存解析结果：
 * <b>读路径完全无锁</b>（只有未命中时才进 {@code synchronized} 块），命中后直接 invoke。</p>
 */
public final class XposedHelpers {

    private XposedHelpers() {
    }

    private static final Object[] NO_ARGS = new Object[0];

    /**
     * 方法缓存，key = 类名#方法名/参数运行时类型,...（同参数个数但类型不同的重载也能区分）。
     * 无锁读：并发写期间最坏读到 null/旧值，只是多走一次同步块重新解析，结果依然正确。
     */
    private static final HashMap<String, Method> METHOD_CACHE = new HashMap<String, Method>();

    /** 字段缓存（信息流按条读 itemType / d 是热路径）。 */
    private static final HashMap<String, Field> FIELD_CACHE = new HashMap<String, Field>();

    /** 同名类在不同 ClassLoader 下是不同类，所以 findClass 的缓存要带上 loader 身份。 */
    private static final class ClassEntry {
        final ClassLoader loader;
        final Class<?> type;

        ClassEntry(ClassLoader loader, Class<?> type) {
            this.loader = loader;
            this.type = type;
        }
    }

    private static final HashMap<String, ClassEntry> CLASS_CACHE = new HashMap<String, ClassEntry>();

    // 注意：legacy 的这个方法声明 throws ClassNotFoundException，但模块里多个调用点
    // 没有 try/catch（如 Snap.install），这里改成抛非受检异常，调用方 catch Throwable 照样接得住。
    public static Class<?> findClass(String name, ClassLoader cl) {
        ClassEntry cached = CLASS_CACHE.get(name);          // 无锁读
        if (cached != null && cached.loader == cl) return cached.type;
        try {
            Class<?> c = Class.forName(name, false, cl);
            synchronized (CLASS_CACHE) {
                CLASS_CACHE.put(name, new ClassEntry(cl, c));
            }
            return c;
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("class not found: " + name, e);
        }
    }

    /** 反射调用实例方法；找不到精确参数类型时按「参数个数 + 可赋值」宽松匹配。 */
    public static Object callMethod(Object obj, String name, Object... args) {
        if (obj == null) throw new NullPointerException("callMethod on null: " + name);
        Object[] a = args == null ? NO_ARGS : args;
        Class<?> cls = obj.getClass();
        String key = methodKey(cls, name, a);
        Method m = METHOD_CACHE.get(key);                   // 无锁读
        if (m == null || !m.getDeclaringClass().isAssignableFrom(cls)) {
            m = findCachedMethod(key, cls, name, a);        // 未命中才进同步块
        }
        try {
            return m.invoke(obj, a);
        } catch (InvocationTargetException e) {
            throw rethrow(e.getCause());
        } catch (Throwable t) {
            throw new RuntimeException("callMethod " + name + " failed: " + t, t);
        }
    }

    public static int getIntField(Object obj, String name) {
        try {
            Field f = findCachedField(fieldKey(obj.getClass(), name), obj.getClass(), name);
            return f.getInt(obj);
        } catch (Throwable t) {
            throw new RuntimeException("getIntField " + name + " failed: " + t, t);
        }
    }

    public static Object getObjectField(Object obj, String name) {
        try {
            Field f = findCachedField(fieldKey(obj.getClass(), name), obj.getClass(), name);
            return f.get(obj);
        } catch (Throwable t) {
            throw new RuntimeException("getObjectField " + name + " failed: " + t, t);
        }
    }

    public static int getStaticIntField(Class<?> clazz, String name) {
        try {
            Field f = findCachedField(fieldKey(clazz, name), clazz, name);
            return f.getInt(null);
        } catch (Throwable t) {
            throw new RuntimeException("getStaticIntField " + name + " failed: " + t, t);
        }
    }

    /** 未命中时的慢路径：解析一次并缓存（{@code setAccessible} 也只做一次）。 */
    private static Method findCachedMethod(String key, Class<?> cls, String name, Object[] args) {
        synchronized (METHOD_CACHE) {
            Method m = METHOD_CACHE.get(key);
            if (m != null && m.getDeclaringClass().isAssignableFrom(cls)) return m;
            m = findMethod(cls, name, args);                // 找不到时照旧抛 RuntimeException
            try {
                m.setAccessible(true);
            } catch (Throwable ignored) {
                // 设不上也缓存：invoke 失败会抛同样的 RuntimeException，语义与未缓存时一致
            }
            METHOD_CACHE.put(key, m);
            return m;
        }
    }

    private static String methodKey(Class<?> cls, String name, Object[] args) {
        StringBuilder sb = new StringBuilder(48 + args.length * 16);
        sb.append(cls.getName()).append('#').append(name).append('/');
        for (int i = 0; i < args.length; i++) {
            Object o = args[i];
            sb.append(o == null ? "?" : o.getClass().getName()).append(',');
        }
        return sb.toString();
    }

    private static String fieldKey(Class<?> cls, String name) {
        return cls.getName() + '#' + name;
    }

    private static Field findCachedField(String key, Class<?> cls, String name) {
        Field f = FIELD_CACHE.get(key);                     // 无锁读
        if (f != null && f.getDeclaringClass().isAssignableFrom(cls)) return f;
        synchronized (FIELD_CACHE) {
            Field cached = FIELD_CACHE.get(key);
            if (cached != null && cached.getDeclaringClass().isAssignableFrom(cls)) return cached;
            Field found = findField(cls, name);             // 找不到时照旧抛 RuntimeException
            try {
                found.setAccessible(true);
            } catch (Throwable ignored) {
            }
            FIELD_CACHE.put(key, found);
            return found;
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
