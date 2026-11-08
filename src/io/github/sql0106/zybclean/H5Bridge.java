package io.github.sql0106.zybclean;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 注入到 H5 页面里的原生桥，对象名 {@code zybcleanHost}。
 *
 * <p>页面里的「复制内容 / 下载图片」按钮调用这里：</p>
 * <ul>
 *   <li>{@link #copyText(String)} —— 剪贴板写入（WebView 里 navigator.clipboard 常被限制）；</li>
 *   <li>{@link #saveImage(String, String)} —— dataURL(base64) 落盘并插入相册；</li>
 *   <li>{@link #toast(String)} —— 主线程 toast 反馈。</li>
 * </ul>
 *
 * <p>JavaBridge 线程没有 Looper，Toast 必须 post 到主线程。</p>
 */
public final class H5Bridge {

    private static final String ALBUM = "zybclean";
    private static final int MAX_BYTES = 40 * 1024 * 1024;

    private final Context mContext;
    private final android.webkit.WebView mWebView;
    private final Handler mMain = new Handler(Looper.getMainLooper());

    public H5Bridge(Context context, android.webkit.WebView webView) {
        mContext = context.getApplicationContext();
        mWebView = webView;
    }

    /**
     * 整页长截图：页面里的「存长图」按钮调这里。Android 主线程才允许 draw WebView，
     * 而 JavaBridge 线程没有 Looper，所以必须 post 回主线程再走 {@link PageShot}。
     */
    @JavascriptInterface
    public void shot() {
        shotPart("full");
    }

    /**
     * 分段截图。
     *
     * @param part {@code "full"} 整页 / {@code "q"} 只存题目 / {@code "a"} 只存解答+解析
     */
    @JavascriptInterface
    public void shotPart(final String part) {
        final android.webkit.WebView wv = mWebView;
        if (wv == null) {
            toast("长图不可用");
            return;
        }
        mMain.post(new Runnable() {
            @Override
            public void run() {
                try {
                    PageShot.capture(mContext, wv, part);
                } catch (Throwable t) {
                    toast("长图失败：" + t);
                }
            }
        });
    }

    /** 供 {@link PageShot} 落盘（复用相册写入的三级回落逻辑）。 */
    static String saveBytes(Context context, String name, String mime, byte[] data) {
        if (context == null || data == null || data.length == 0) return "";
        return new H5Bridge(context, null).store(sanitize(name, mime), mime, data);
    }

    @JavascriptInterface
    public void copyText(final String text) {
        if (text == null || text.length() == 0) return;
        try {
            ClipboardManager cm = (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("zybclean", text));
                toast("已复制 " + text.length() + " 字");
            }
        } catch (Throwable t) {
            toast("复制失败：" + t);
        }
    }

    @JavascriptInterface
    public String saveImage(String name, String dataUrl) {
        try {
            if (dataUrl == null || !dataUrl.startsWith("data:")) return "";
            int comma = dataUrl.indexOf(',');
            if (comma < 0) return "";
            String meta = dataUrl.substring(5, comma);
            String mime = meta.split(";")[0];
            if (mime.length() == 0) mime = "image/jpeg";
            byte[] data = Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT);
            if (data.length == 0 || data.length > MAX_BYTES) return "";
            String safe = sanitize(name, mime);
            return store(safe, mime, data);
        } catch (Throwable t) {
            return "";
        }
    }

    /**
     * 按 URL 下载图片（原生侧带 WebView 的 Cookie，绕开页面里的跨域限制）。
     *
     * @return 落盘路径 / content uri，失败返回空串
     */
    @JavascriptInterface
    public String saveUrl(String name, String url) {
        if (url == null || url.length() == 0) return "";
        if (url.startsWith("data:")) return saveImage(name, url);
        HttpURLConnection conn = null;
        InputStream in = null;
        try {
            if (url.startsWith("//")) url = "https:" + url;
            if (!url.startsWith("http://") && !url.startsWith("https://")) return "";
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(25000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36");
            conn.setRequestProperty("Accept", "image/*,*/*;q=0.8");
            try {
                String cookie = CookieManager.getInstance().getCookie(url);
                if (cookie != null && cookie.length() > 0) conn.setRequestProperty("Cookie", cookie);
            } catch (Throwable ignored) {
            }
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) return "";
            String type = conn.getContentType();
            String mime = type == null ? "" : type.split(";")[0].trim();
            if (mime.length() == 0) mime = "image/jpeg";
            in = conn.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream(64 * 1024);
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
                if (bos.size() > MAX_BYTES) return "";
            }
            byte[] data = bos.toByteArray();
            if (data.length == 0) return "";
            return store(sanitize(name, mime), mime, data);
        } catch (Throwable t) {
            return "";
        } finally {
            if (in != null) try { in.close(); } catch (Throwable ignored) { }
            if (conn != null) conn.disconnect();
        }
    }

    @JavascriptInterface
    public void toast(String msg) {        if (msg == null) return;
        final String m = msg.length() > 120 ? msg.substring(0, 120) : msg;
        mMain.post(new Runnable() {
            @Override
            public void run() {
                try {
                    Toast.makeText(mContext, m, Toast.LENGTH_SHORT).show();
                } catch (Throwable ignored) {
                }
            }
        });
    }

    private String store(String name, String mime, byte[] data) {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                cv.put(MediaStore.Images.Media.MIME_TYPE, mime);
                cv.put(MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + File.separator + ALBUM);
                cv.put(MediaStore.Images.Media.IS_PENDING, 1);
                Uri uri = mContext.getContentResolver()
                        .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
                if (uri == null) return legacy(name, data);
                OutputStream os = null;
                try {
                    os = mContext.getContentResolver().openOutputStream(uri);
                    if (os == null) return legacy(name, data);
                    os.write(data);
                    os.flush();
                } finally {
                    if (os != null) try { os.close(); } catch (Throwable ignored) { }
                }
                ContentValues done = new ContentValues();
                done.put(MediaStore.Images.Media.IS_PENDING, 0);
                mContext.getContentResolver().update(uri, done, null, null);
                return uri.toString();
            } catch (Throwable t) {
                return legacy(name, data);
            }
        }
        return legacy(name, data);
    }

    /** API 29 以下（或 MediaStore 失败）：写公共 Pictures，再退回 App 私有目录。 */
    private String legacy(String name, byte[] data) {
        File dir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), ALBUM);
        String path = write(new File(dir, name), data);
        if (path != null) {
            try {
                MediaScannerConnection.scanFile(mContext, new String[]{path}, null, null);
            } catch (Throwable ignored) {
            }
            return path;
        }
        File priv = mContext.getExternalFilesDir(ALBUM);
        if (priv == null) return "";
        path = write(new File(priv, name), data);
        return path == null ? "" : path;
    }

    private static String write(File f, byte[] data) {
        FileOutputStream out = null;
        try {
            File parent = f.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) return null;
            out = new FileOutputStream(f, false);
            out.write(data);
            out.flush();
            return f.getAbsolutePath();
        } catch (Throwable t) {
            return null;
        } finally {
            if (out != null) try { out.close(); } catch (Throwable ignored) { }
        }
    }

    /** 只留安全字符，补上扩展名。 */
    private static String sanitize(String name, String mime) {
        String base = name == null ? "" : name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (base.length() == 0) base = "zybclean_" + System.currentTimeMillis();
        String lower = base.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                || lower.endsWith(".webp") || lower.endsWith(".gif") || lower.endsWith(".bmp")) {
            return base;
        }
        if ("image/png".equals(mime)) return base + ".png";
        if ("image/webp".equals(mime)) return base + ".webp";
        if ("image/gif".equals(mime)) return base + ".gif";
        return base + ".jpg";
    }
}
