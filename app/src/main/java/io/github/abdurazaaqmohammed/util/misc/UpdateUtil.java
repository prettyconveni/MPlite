package io.github.abdurazaaqmohammed.util.misc;

import static android.content.Context.DOWNLOAD_SERVICE;

import android.app.DownloadManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.DisplayMetrics;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textview.MaterialTextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.codehasan.colorpicker.extensions.Extensions;
import io.noties.markwon.Markwon;

public class UpdateUtil {

    public static final String PREF_LAST_VER_CHECKED = "lastVerChecked";
    public static final String PREF_DOWNLOAD_ID = "downloadId";
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private static final String GITHUB_REPO = "prettyconveni/MPlite";
    private static final String GITHUB_RELEASES_API = "https://api.github.com/repos/" + GITHUB_REPO + "/releases";
    private static final String GITHUB_RELEASE_TAG_URL = "https://github.com/" + GITHUB_REPO + "/releases/tag/";
    private static final int PROBE_TIMEOUT_MS = 3500;

    /** Direct GitHub + mirror prefixes for release downloads and API fallback. */
    private static final String[] DOWNLOAD_MIRRORS = new String[] {
            "",
            "https://gh-proxy.com/",
            "https://ghfast.top/",
            "https://mirror.ghproxy.com/",
            "https://ghproxy.net/"
    };

    /** Tag that names an app version: "1.1.2" or "v1.1.2". */
    private static final Pattern VERSION_TAG =
            Pattern.compile("^v?(\\d+(?:\\.\\d+)*)$");

    /** The app's own APK asset, e.g. MPlite-v1.1.2.apk. */
    private static final String APP_APK_PREFIX = "mplite-";

    /** An MPlite release: newest first, tag, APK asset and changelog. */
    private static final class AppRelease {
        String tagName;
        String version;
        String apkName;
        String apkUrl;
        String changelog;
    }

    private static final class RouteResult {
        final String url;
        final String label;
        final long latencyMs;

        RouteResult(String url, String label, long latencyMs) {
            this.url = url;
            this.label = label;
            this.latencyMs = latencyMs;
        }
    }

    public static void checkForUpdates(boolean toast, AppCompatActivity context) {
        Resources rss = context.getResources();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String lastVerChecked = prefs.getString(PREF_LAST_VER_CHECKED, null);
        new Thread(() -> {
            try {
                String json = readReleasesJson();
                String currentVer;
                try {
                    currentVer = (context).getPackageManager().getPackageInfo((context).getPackageName(), 0).versionName;
                } catch (Exception e) {
                    currentVer = null;
                }
                AppRelease release = latestAppRelease(json);
                if (release == null) {
                    if (toast) Extensions.showMessage(context, R.string.no_update_found);
                    return;
                }
                if (!isNewerVersion(currentVer, release.version)) {
                    if (toast) Extensions.showMessage(context, R.string.no_update_found);
                    return;
                }
                if (!toast && !TextUtils.isEmpty(lastVerChecked) && lastVerChecked.equals(release.version)) {
                    return;
                }
                String filename = release.apkName;
                String link = release.apkUrl;
                Markwon markwon = Markwon.create(context);

                DisplayMetrics dm = rss.getDisplayMetrics();
                MaterialTextView tv = new MaterialTextView(context);
                tv.setMaxHeight(dm.heightPixels / 2);
                markwon.setMarkdown(tv, release.changelog);
                int p = (int) (16 * dm.density + 0.5f);
                tv.setPadding(p, p, p, p);

                MAIN_HANDLER.post(() -> {
                    AlertDialog alertDialog = new MaterialAlertDialogBuilder(context)
                            .setTitle(rss.getString(R.string.new_ver, release.version)).setView(tv)
                            .setPositiveButton(rss.getString(R.string.download), (dialog, which) -> {
                                Extensions.showMessage(context, "正在测速选择最佳下载线路...");
                                new Thread(() -> {
                                    RouteResult best = selectFastestDownloadRoute(link);
                                    MAIN_HANDLER.post(() -> {
                                        if (best.latencyMs >= 0) {
                                            Extensions.showMessage(context, "已选择线路: " + best.label + " (" + best.latencyMs + "ms)");
                                        } else {
                                            Extensions.showMessage(context, "测速超时，使用默认 GitHub 线路下载");
                                        }
                                        DownloadManager.Request request = new DownloadManager.Request(
                                                Uri.parse(best.url))
                                                .setTitle(filename).setDescription(filename)
                                                .setMimeType("application/vnd.android.package-archive")
                                                .setDestinationInExternalPublicDir(
                                                        Environment.DIRECTORY_DOWNLOADS, filename)
                                                .setNotificationVisibility(
                                                        DownloadManager.Request.VISIBILITY_VISIBLE
                                                                | DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                                        long downloadId = ((DownloadManager) context.getSystemService(DOWNLOAD_SERVICE)).enqueue(request);
                                        prefs.edit().putLong(PREF_DOWNLOAD_ID, downloadId).apply();
                                    });
                                }).start();
                            })
                                    .setNegativeButton("Go to GitHub Release", (dialog, which) -> context
                                            .startActivity(new Intent(Intent.ACTION_VIEW).setData(Uri.parse(
                                                    GITHUB_RELEASE_TAG_URL + release.tagName))))
                                    .setNeutralButton(rss.getString(android.R.string.cancel), null).create();
                    alertDialog.setOnDismissListener(dialog -> prefs.edit()
                            .putString(PREF_LAST_VER_CHECKED, release.version).apply());
                    alertDialog.show();
                    alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnLongClickListener(v -> {
                        Extensions.showMessage(context, link);
                        return false;
                    });
                });
            } catch (Exception e) {
                if (toast) Extensions.showMessage(context, R.string.failed_to_check_for_update);
            }
        }).start();
    }

    /**
     * Newest release that is actually the app: a version tag plus an
     * MP-Manager.*.apk asset. The repo also hosts distribution releases
     * (tool-packs, native-tools) that share this API but are not app
     * updates, so they must not be offered as one.
     */
    private static AppRelease latestAppRelease(String json) throws Exception {
        JSONArray releases = new JSONArray(json);
        for (int i = 0; i < releases.length(); i++) {
            JSONObject release = releases.optJSONObject(i);
            if (release == null) continue;
            String tag = release.optString("tag_name", "").trim();
            Matcher tagMatcher = VERSION_TAG.matcher(tag);
            if (!tagMatcher.find()) continue;
            JSONArray assets = release.optJSONArray("assets");
            if (assets == null) continue;
            for (int j = 0; j < assets.length(); j++) {
                JSONObject asset = assets.optJSONObject(j);
                if (asset == null) continue;
                String name = asset.optString("name", "");
                String url = asset.optString("browser_download_url", "");
                if (!name.toLowerCase().startsWith(APP_APK_PREFIX)
                        || !name.toLowerCase().endsWith(".apk")
                        || url.isEmpty()) continue;
                AppRelease out = new AppRelease();
                out.tagName = tag;
                out.version = tagMatcher.group(1);
                out.apkName = name;
                out.apkUrl = url;
                out.changelog = release.optString("body", "");
                return out;
            }
        }
        return null;
    }

    private static boolean isNewerVersion(String currentVer, String latestVer) {
        if (TextUtils.isEmpty(latestVer)) return false;
        String curr = TextUtils.isEmpty(currentVer) ? "1.0.0" : currentVer.replaceFirst("^v", "");
        String[] currParts = curr.split("\\.");
        String[] latestParts = latestVer.split("\\.");
        int maxLen = Math.max(currParts.length, latestParts.length);
        for (int i = 0; i < maxLen; i++) {
            int c = i < currParts.length ? parseIntSafe(currParts[i]) : 0;
            int l = i < latestParts.length ? parseIntSafe(latestParts[i]) : 0;
            if (l > c) return true;
            if (l < c) return false;
        }
        return false;
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    private static String readReleasesJson() throws IOException {
        IOException lastError = null;
        for (String prefix : DOWNLOAD_MIRRORS) {
            try {
                return read(getHttpURLConnection(prefix + GITHUB_RELEASES_API));
            } catch (IOException e) {
                lastError = e;
            }
        }
        throw lastError != null ? lastError : new IOException("Failed to fetch releases");
    }

    @NonNull
    private static RouteResult selectFastestDownloadRoute(String rawUrl) {
        ExecutorService pool = Executors.newFixedThreadPool(DOWNLOAD_MIRRORS.length);
        List<Callable<RouteResult>> tasks = new ArrayList<>(DOWNLOAD_MIRRORS.length);
        for (String prefix : DOWNLOAD_MIRRORS) {
            final String candidateUrl = prefix + rawUrl;
            final String label = prefix.isEmpty() ? "GitHub 直连" : Uri.parse(prefix).getHost();
            tasks.add(() -> probeRoute(candidateUrl, label));
        }

        RouteResult best = null;
        try {
            List<Future<RouteResult>> futures = pool.invokeAll(tasks, PROBE_TIMEOUT_MS + 500L, TimeUnit.MILLISECONDS);
            for (Future<RouteResult> future : futures) {
                if (!future.isDone() || future.isCancelled()) continue;
                try {
                    RouteResult res = future.get();
                    if (res != null && res.latencyMs >= 0 && (best == null || res.latencyMs < best.latencyMs)) {
                        best = res;
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } finally {
            pool.shutdownNow();
        }
        return best != null ? best : new RouteResult(rawUrl, "GitHub 直连", -1);
    }

    private static RouteResult probeRoute(String urlStr, String label) {
        long start = SystemClock.elapsedRealtime();
        HttpURLConnection conn = null;
        try {
            conn = openProbeConnection(urlStr, "HEAD");
            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_BAD_METHOD || code == 405) {
                conn.disconnect();
                conn = openProbeConnection(urlStr, "GET");
                conn.setRequestProperty("Range", "bytes=0-0");
                code = conn.getResponseCode();
            }
            if (code >= 200 && code < 400) {
                return new RouteResult(urlStr, label, SystemClock.elapsedRealtime() - start);
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    @NonNull
    private static HttpURLConnection openProbeConnection(String urlStr, String method) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(PROBE_TIMEOUT_MS);
        conn.setReadTimeout(PROBE_TIMEOUT_MS);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 Edg/128.0.0.0");
        return conn;
    }

    private static String read(HttpURLConnection conn) throws IOException {
        try (InputStream inputStream = conn.getInputStream();
             InputStreamReader in = new InputStreamReader(inputStream);
             BufferedReader reader = new BufferedReader(in)) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    @NonNull
    private static HttpURLConnection getHttpURLConnection(String apiUrl) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 Edg/128.0.0.0");
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        return conn;
    }
}