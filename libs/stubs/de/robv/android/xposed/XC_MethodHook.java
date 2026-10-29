package de.robv.android.xposed;

import java.lang.reflect.Member;

public abstract class XC_MethodHook {
    public XC_MethodHook() {}
    public XC_MethodHook(int priority) {}

    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {}
    protected void afterHookedMethod(MethodHookParam param) throws Throwable {}

    public static final class MethodHookParam {
        public Member method;
        public Object thisObject;
        public Object[] args;
        private Object result;
        private Throwable throwable;
        public Object getResult() { return result; }
        public void setResult(Object r) { result = r; }
        public void setResult(Object r, boolean fromReturn) { result = r; }
        public Throwable getThrowable() { return throwable; }
        public boolean hasThrowable() { return throwable != null; }
        public void setThrowable(Throwable t) { throwable = t; }
        public Object getObjectExtra(String key) { return null; }
        public void setObjectExtra(String key, Object o) {}
    }

    public static final class Unhook {
        private final Object hook;
        Unhook(Object h) { hook = h; }
        public void unhook() {}
    }
}
