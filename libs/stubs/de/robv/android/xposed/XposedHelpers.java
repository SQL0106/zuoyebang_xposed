package de.robv.android.xposed;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

public final class XposedHelpers {
    private XposedHelpers() {}

    public static Class<?> findClass(String className, ClassLoader classLoader) { return null; }
    public static Class<?> findClassIfExists(String className, ClassLoader classLoader) { return null; }

    public static Field findField(Class<?> clazz, String fieldName) { return null; }
    public static Object getObjectField(Object obj, String fieldName) { return null; }
    public static Object getStaticObjectField(Class<?> clazz, String fieldName) { return null; }
    public static void setObjectField(Object obj, String fieldName, Object value) {}
    public static void setStaticObjectField(Class<?> clazz, String fieldName, Object value) {}
    public static int getIntField(Object obj, String fieldName) { return 0; }
    public static int getStaticIntField(Class<?> clazz, String fieldName) { return 0; }
    public static boolean getBooleanField(Object obj, String fieldName) { return false; }
    public static void setBooleanField(Object obj, String fieldName, boolean value) {}
    public static void setIntField(Object obj, String fieldName, int value) {}

    public static Method findMethodExact(Class<?> clazz, String methodName, Object... parameterTypesAndCallback) { return null; }
    public static Method findMethodExact(String className, ClassLoader classLoader, String methodName, Object... parameterTypesAndCallback) { return null; }
    public static Set<XC_MethodHook.Unhook> findAndHookMethod(Class<?> clazz, String methodName, Object... parameterTypesAndCallback) { return null; }
    public static Set<XC_MethodHook.Unhook> findAndHookMethod(String className, ClassLoader classLoader, String methodName, Object... parameterTypesAndCallback) { return null; }

    public static Constructor<?> findConstructorExact(Class<?> clazz, Object... parameterTypesAndCallback) { return null; }

    public static Object callMethod(Object obj, String methodName, Object... args) { return null; }
    public static Object callStaticMethod(Class<?> clazz, String methodName, Object... args) { return null; }
    public static Object getObjectField(Object obj, Class<?> clazz, String fieldName) { return null; }
    public static Object newInstance(Class<?> clazz, Object... args) { return null; }
}
