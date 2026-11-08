package io.github.sql0106.zybclean;

/**
 * 编译期开关。{@link #PROBES} 控制是否把调试探针（Snap / WebEyes / ViewDump / Probe）编进包。
 *
 * <p>正式包为 {@code false}：{@code javac} 会把 {@code if (false)} 分支整段消除，
 * dex 里不存在探针类和触发文件路径。debug 包由 {@code DEBUG=1 ./build.sh} 生成，
 * 打包脚本会用 {@code sed} 把本常量改成 {@code true} 后再编译（不改动本文件）。</p>
 */
final class BuildFlags {

    /** true = 带探针的 debug 包。 */
    static final boolean PROBES = false;

    private BuildFlags() {
    }
}
