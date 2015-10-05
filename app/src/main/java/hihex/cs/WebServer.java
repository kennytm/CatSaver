/*
 * CatSaver
 * Copyright (C) 2015 HiHex Ltd.
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package hihex.cs;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;

import com.google.common.base.Functions;
import com.google.common.base.Optional;
import com.google.common.eventbus.Subscribe;
import com.google.common.io.ByteStreams;
import com.google.gson.JsonSyntaxException;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import fi.iki.elonen.NanoHTTPD;

/**
 * The HTTP server that allows testers to download the crash logs.
 */
final class WebServer extends NanoHTTPD {
    public static final int PORT = 47689; // Perhaps we should let the OS choose the port?

    private final Config mConfig;

    private final Drawable mCatSaverIcon;
    private final Paint mFaviconFillPaint;
    private final Paint mFaviconOutlinePaint;
    private byte[] mFaviconPng = {};

    public WebServer(final Config config, final Drawable catSaverIcon) {
        super(PORT);

        mConfig = config;
        mCatSaverIcon = catSaverIcon;
        Events.bus.register(this);

        {
            final Paint paint = new Paint();
            paint.setTextAlign(Paint.Align.RIGHT);
            paint.setColor(Color.BLACK);
            paint.setTextSize(12);
            paint.setTypeface(Typeface.SANS_SERIF);
            paint.setAntiAlias(true);
            paint.setTextScaleX(1.17f);
            paint.setStyle(Paint.Style.FILL);
            mFaviconFillPaint = paint;
        }
        {
            final Paint paint = new Paint(mFaviconFillPaint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(5);
            paint.setColor(Color.WHITE);
            mFaviconOutlinePaint = paint;
        }
    }

    private static String getMimeType(final String path) {
        final int lastDot = path.lastIndexOf('.');
        if (lastDot >= 0) {
            final String extension = path.substring(lastDot + 1);
            switch (extension) {
                case "png":
                    return "image/png";
                case "ttf":
                    return "application/font-sfnt";
                case "css":
                    return "text/css";
                default:
                    break;
            }
        }
        return "application/octet-stream";
    }

    @Override
    public Response serve(final IHTTPSession session) {
        final Uri uri = Uri.parse(session.getUri());

        final String path = uri.getPath();
        switch (path) {
            case "/":
                return serveIndex();
            case "/favicon.ico":
                return serveFavicon();
            case "/settings":
                return serveSettings();
            case "/filters":
                return serveFilters();
            case "/update-settings": {
                try {
                    session.parseBody(new HashMap<String, String>());
                    return updateSettings(session.getParms());
                } catch (final IOException | ResponseException e) {
                    return serve404();
                }
            }
            case "/update-filter-settings": {
                try {
                    session.parseBody(new HashMap<String, String>());
                    return updateFilterSettings(session.getParms());
                } catch (final IOException | ResponseException e) {
                    return serve404();
                }
            }
            case "/bulk":
                try {
                    session.parseBody(new HashMap<String, String>());
                    // We can't really use the map because there will be multiple "file-selector"s. But we cannot pass
                    // a HashMultimap above. So we need to parse the query string ourselves. The query string won't be
                    // available without calling parseBody() though, so the statement above still exists.

                    final StringTokenizer tokenizer = new StringTokenizer(session.getQueryParameterString(), "&");
                    final ArrayList<String> files = new ArrayList<>();
                    String action = "";
                    // We parse the string ourselves instead of using Uri.parse(), because the latter does not properly
                    // translate '+' back to spaces.

                    while (tokenizer.hasMoreTokens()) {
                        final String token = tokenizer.nextToken();
                        final int sep = token.indexOf('=');
                        final String key;
                        final String value;
                        if (sep >= 0) {
                            key = URLDecoder.decode(token.substring(0, sep), "UTF-8");
                            value = URLDecoder.decode(token.substring(sep + 1), "UTF-8");
                        } else {
                            key = URLDecoder.decode(token, "UTF-8");
                            value = "";
                        }
                        switch (key) {
                            case "file-selector":
                                files.add(value);
                                break;
                            case "action":
                                action = value;
                                break;
                            default:
                                break;
                        }
                    }

                    switch (action) {
                        case "delete":
                            return bulkDelete(files);
                        case "download":
                            return bulkDownload(files);
                        default:
                            break;
                    }
                } catch (final IOException | ResponseException e) {
                    CsLog.e("Bulk action failed", e);
                }
                return serve404();

            default:
                if (path.length() <= 1) {
                    return serve404();
                }
                break;
        }

        final int secondSlash = path.indexOf('/', 1);
        if (secondSlash < 0) {
            return serve404();
        }

        final String action = path.substring(1, secondSlash);
        final String filename = path.substring(secondSlash + 1);
        switch (action) {
            case "static":
                if ("favicon.png".equals(filename)) {
                    return serveFavicon();
                } else {
                    return serveStatic(filename);
                }
            case "read":
                return serveLog(filename);
            case "download":
                return serveDownload(filename);
            case "apk":
                return serveApk(filename);
            case "stop":
                return stopPid(filename);
            case "start":
                return startPid(filename);
            case "delete":
                return deleteLog(filename);
            default:
                return serve404();
        }
    }

    private Response serveIndex() {
        mConfig.refreshPids();
        final String content = mConfig.renderer.renderIndex(mConfig.logFiles, mConfig.pidDatabase);
        return new Response(content);
    }

    private Response serveStatic(final String filename) {
        final AssetManager assets = mConfig.context.getAssets();
        try {
            final InputStream stream = assets.open("static/" + filename);
            final String mimeType = getMimeType(filename);
            return new Response(Response.Status.OK, mimeType, stream);
        } catch (final IOException e) {
            return serve404();
        }
    }

    private Response serveLog(final String filename) {
        mConfig.flushWriter(filename);
        try {
            final InputStream stream = mConfig.logFiles.open(filename);
            final Response resp = new Response(Response.Status.OK, MIME_HTML, stream);
            resp.addHeader("Content-Encoding", "gzip");
            return resp;
        } catch (final IOException e) {
            return serve404();
        }
    }

    private Response serveDownload(final String filename) {
        try {
            final InputStream stream = mConfig.logFiles.open(filename);
            return serveFileDownload(filename, "application/x-gzip", stream);
        } catch (final IOException e) {
            return serve404();
        }
    }

    private Response serveRedirect(final String message, final String destination) {
        final Response resp = new Response(Response.Status.REDIRECT, MIME_PLAINTEXT, message);
        resp.addHeader("Refresh", "0; url=" + destination);
        return resp;
    }

    private Response stopPid(final String pidString) {
        final int pid = Integer.parseInt(pidString);
        mConfig.stopRecording(pid, Functions.<Writer>identity());
        return serveRedirect("Recording stopped: " + pidString, "/");
    }

    private Response startPid(final String pidString) {
        final int pid = Integer.parseInt(pidString);
        try {
            mConfig.startRecording(pid, Optional.<String>absent(), new Date());
        } catch (final IOException e) {
            CsLog.e("Cannot start recording", e);
        }
        return serveRedirect("Recording started: " + pidString, "/");
    }

    private Response deleteLog(final String fileName) {
        mConfig.logFiles.delete(fileName);
        return serveRedirect("File deleted: " + fileName, "/");
    }

    private Response serveApk(final String packageName) {
        final PackageManager packageManager = mConfig.context.getPackageManager();
        try {
            final ApplicationInfo info = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA);
            final String apkPath = info.sourceDir;
            final FileInputStream stream = new FileInputStream(apkPath);
            return serveFileDownload(packageName + ".apk", "application/vnd.android.package-archive", stream);
        } catch (final PackageManager.NameNotFoundException e) {
            return serve404();
        } catch (final FileNotFoundException e) {
            return new Response(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Cannot download");
        }
    }

    private Response serveSettings() {
        final Preferences preferences = mConfig.preferences;
        final String content = mConfig.renderer.renderSettings(preferences);
        return new Response(content);
    }

    private Response updateSettings(final Map<String, String> parameters) {
        final String filterString = parameters.get("filter");
        if (filterString == null) {
            return serveInvalidSettingError("settings", "Missing filter", null);
        }
        try {
            final Pattern filter = Pattern.compile(filterString);
            final long filesize;
            final long duration;
            final long splitSize;
            final boolean shouldShowIndictor = "on".equals(parameters.get("show-indicator"));
            final boolean shouldRunOnBoot = "on".equals(parameters.get("run-on-boot"));
            if ("on".equals(parameters.get("purge-by-filesize"))) {
                filesize = Math.max(1, Long.parseLong(parameters.get("filesize")) * 1048576);
            } else {
                filesize = -1;
            }
            if ("on".equals(parameters.get("purge-by-date"))) {
                duration = Math.max(1, Long.parseLong(parameters.get("date")) * 86400000);
            } else {
                duration = -1;
            }
            if ("on".equals(parameters.get("split-size-enabled"))) {
                splitSize = Math.max(1, Long.parseLong(parameters.get("split-size")) * 1024);
            } else {
                splitSize = -1;
            }
            mConfig.preferences.updateSettings(filter, filesize, duration, shouldShowIndictor, shouldRunOnBoot, splitSize);
            return serveRedirect("Settings updated", "/");
        } catch (final PatternSyntaxException e) {
            return serveInvalidSettingError("settings", "Invalid filter syntax", e);
        } catch (final NumberFormatException e) {
            return serveInvalidSettingError("settings", "Invalid number", e);
        }
    }

    private Response updateFilterSettings(final Map<String, String> parameters) {
        final String filterString = parameters.get("filter");
        if (filterString == null) {
            return serveInvalidSettingError("filters", "Missing filter", null);
        }

        final boolean hasDefaultLogFilter = "on".equals(parameters.get("log-filter-use-default"));
        final String logFilter = hasDefaultLogFilter ? null : parameters.get("log-filters");

        try {
            final Pattern filter = Pattern.compile(filterString);
            mConfig.preferences.updateFilters(filter, logFilter);
            return serveRedirect("Filters updated", "/");
        } catch (final PatternSyntaxException e) {
            return serveInvalidSettingError("filters", "Invalid filter regular expression", e);
        } catch (final IllegalStateException e) {
            return serveInvalidSettingError("filters", "Invalid TOML syntax", e);
        } catch (final JsonSyntaxException e) {
            return serveInvalidSettingError("filters", "Invalid TOML type", e);
        }
    }

    private Response serveFilters() {
        final Preferences preferences = mConfig.preferences;
        final String content = mConfig.renderer.renderFilterSettings(preferences);
        return new Response(content);
    }

    private Response bulkDownload(final List<String> files) throws IOException {
        final File zipFile = File.createTempFile("CatSaverLogs", "zip");
        try {
            final ZipOutputStream stream = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)));
            stream.putNextEntry(new ZipEntry("CatSaverLogs/"));

            for (final String fileName : files) {
                final InputStream source = mConfig.logFiles.open(fileName);
                final InputStream input;
                final ZipEntry entry;
                if (fileName.endsWith(".gz")) {
                    entry = new ZipEntry("CatSaverLogs/" + fileName.substring(0, fileName.length() - 3));
                    input = new GZIPInputStream(source);
                } else {
                    entry = new ZipEntry("CatSaverLogs/" + fileName);
                    input = source;
                }
                stream.putNextEntry(entry);
                try {
                    ByteStreams.copy(input, stream);
                } catch (final EOFException e) {
                    // Early EOF, ignore?
                }
                input.close();
                stream.closeEntry();
            }
            stream.close();

            final FileInputStream result = new FileInputStream(zipFile);
            return serveFileDownload("CatSaverLogs.zip", "application/zip", result);
        } finally {
            zipFile.delete();
        }
    }

    private Response bulkDelete(final List<String> files) {
        for (final String fileName : files) {
            mConfig.logFiles.delete(fileName);
        }
        return serveRedirect("Deleted " + files.size() + " files", "/");
    }

    private Response serve404() {
        return new Response(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found");
    }

    private Response serveInvalidSettingError(final String source, final String title, final Throwable e) {
        final String content = mConfig.renderer.renderSettingsError(source, title, e);
        return new Response(Response.Status.BAD_REQUEST, MIME_HTML, content);
    }

    private Response serveFileDownload(final String fileName, final String mimeType, final InputStream stream) {
        final Response resp = new Response(Response.Status.OK, mimeType, stream);
        resp.addHeader("Content-Disposition", "attachment; filename=" + fileName);
        return resp;
    }

    private Response serveFavicon() {
        final ByteArrayInputStream stream = new ByteArrayInputStream(mFaviconPng);
        return new Response(Response.Status.OK, "image/png", stream);
    }

    @Subscribe
    public void refreshFavicon(final Events.UpdateIpAddress ipValue) {
        final Bitmap bitmap = Bitmap.createBitmap(36, 36, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(bitmap);
        mCatSaverIcon.setBounds(0, 0, 36, 36);
        mCatSaverIcon.draw(canvas);

        final String ipSegment = IpAddresses.extractLastPart(ipValue.ipAddress);
        canvas.drawText(ipSegment, 36, 34, mFaviconOutlinePaint);
        canvas.drawText(ipSegment, 36, 34, mFaviconFillPaint);

        final ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        mFaviconPng = stream.toByteArray();
    }
}
