package io.github.sql0106.zybclean.compat;

import java.lang.reflect.Member;

/**
 * 兼容层：仿 legacy Xposed 的 {@code XC_MethodHook}，内部由 {@link XposedBridge} 转发到
 * libxposed 现代 API（102）的 {@code Hooker / Chain}。
 *
 * <p>存在的意义：13 个 killer 里的 hook 写法（beforeHookedMethod / afterHookedMethod /
 * param.args / param.setResult / param.thisObject）一行都不用改，只换 import。</p>
 */
public abstract class XC_MethodHook {

    /** 方法调用前回调。 */
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
    }

    /** 方法调用后回调。 */
    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
    }

    /** 供 {@link XposedBridge} 调用（跨类，不能是 protected）。 */
    public final void invokeBefore(MethodHookParam param) throws Throwable {
        beforeHookedMethod(param);
    }

    /** 供 {@link XposedBridge} 调用。 */
    public final void invokeAfter(MethodHookParam param) throws Throwable {
        afterHookedMethod(param);
    }

    /** 兼容 legacy 的 MethodHookParam（legacy 里它是 XC_MethodHook 的静态内部类）。 */
    public static final class MethodHookParam {
        /** 被 hook 方法所属的实例，静态方法为 null。 */
        public Object thisObject;
        /** 参数数组，可直接改元素，改动会传给原方法。 */
        public Object[] args = new Object[0];
        /** 被 hook 的方法/构造器。 */
        public Member method;

        private Object result;
        private boolean replaced;
        private Throwable throwable;

        public Object getResult() {
            return result;
        }

        /** 调用它表示「不再执行原方法，直接返回这个值」（after 阶段则是改写返回值）。 */
        public void setResult(Object r) {
            result = r;
            replaced = true;
        }

        public boolean hasResult() {
            return replaced;
        }

        public Throwable getThrowable() {
            return throwable;
        }

        public boolean hasThrowable() {
            return throwable != null;
        }

        public void setThrowable(Throwable t) {
            throwable = t;
        }
    }
}
