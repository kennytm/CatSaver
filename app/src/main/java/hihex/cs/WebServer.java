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
import android.net.Uri;

import com.google.common.base.Functions;
import com.google.common.base.Optional;
import com.google.common.collect.TreeMultimap;
import com.x5.template.Chunk;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import fi.iki.elonen.NanoHTTPD;

/**
 * The HTTP server that allows testers to download the crash logs.
 */
final class WebServer extends NanoHTTPD {
    public static final int PORT = 47689; // Perhaps we should let the OS choose the port?
    public static final String DEFAULT_FILTER_PREFS_KEY = "filter";

    private final Config mConfig;

    public WebServer(final Config config) {
        super(PORT);

        mConfig = config;
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
                return serveStatic("favicon.png");
            case "/settings":
                return serveSettings();
            case "/update-settings": {
                try {
                    session.parseBody(new HashMap<String, String>());
                    return updateSettings(session.getParms());
                } catch (final IOException | ResponseException e) {
                    return serve404();
                }
            }
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
                return serveStatic(filename);
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
        final List<HashMap<String, String>> processes = mConfig.runningProcesses();

        final Chunk chunk = mConfig.theme.makeChunk("index");

        final TreeMultimap<Long, File> files = mConfig.listLogFiles();

        final ArrayList<HashMap<String, String>> encodedFiles = new ArrayList<>(files.size());
        long totalSize = 0;
        for (final File file : files.values()) {
            final HashMap<String, String> content = new HashMap<>(5);
            final long fileSize = file.length();
            final String name = file.getName();
            final int pid = mConfig.findPid(name);

            content.put("file_name", name);
            content.put("last_modified", String.valueOf(file.lastModified()));
            content.put("file_size", String.valueOf(fileSize));
            if (pid != -1) {
                content.put("pid", String.valueOf(pid));
                content.put("process_name", mConfig.getProcessName(pid));
            }

            encodedFiles.add(content);
            totalSize += fileSize;
        }

        chunk.set("files", encodedFiles);
        chunk.set("total_size", String.valueOf(totalSize));
        chunk.set("processes", processes);
        return new Response(chunk.toString());
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
        final File file = new File(mConfig.logFolder, filename);
        try {
            final FileInputStream stream = new FileInputStream(file);
            final Response resp = new Response(Response.Status.OK, MIME_HTML, stream);
            resp.addHeader("Content-Encoding", "gzip");
            return resp;
        } catch (final IOException e) {
            return serve404();
        }
    }

    private Response serveDownload(final String filename) {
        final File file = new File(mConfig.logFolder, filename);
        try {
            final FileInputStream stream = new FileInputStream(file);
            final Response resp = new Response(Response.Status.OK, "application/x-gzip", stream);
            resp.addHeader("Content-Disposition", "attachment; filename=" + filename);
            return resp;
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
        final File file = new File(mConfig.logFolder, fileName);
        file.delete();
        return serveRedirect("File deleted: " + fileName, "/");
    }

    private Response serveApk(final String packageName) {
        final PackageManager packageManager = mConfig.context.getPackageManager();
        try {
            final ApplicationInfo info = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA);
            final String apkPath = info.sourceDir;
            final FileInputStream stream = new FileInputStream(apkPath);
            return new Response(Response.Status.OK, "application/vnd.android.package-archive", stream);
        } catch (final PackageManager.NameNotFoundException e) {
            return serve404();
        } catch (final FileNotFoundException e) {
            return new Response(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Cannot download");
        }
    }

    private Response serveSettings() {
        final Chunk chunk = mConfig.theme.makeChunk("settings");
        chunk.put("filter", mConfig.getFilter().pattern());
        chunk.put("filesize", String.valueOf(mConfig.getPurgeFilesize()));
        chunk.put("date", String.valueOf(mConfig.getPurgeDuration()));
        return new Response(chunk.toString());
    }

    private Response updateSettings(final Map<String, String> parameters) {
        final String filterString = parameters.get("filter");
        if (filterString == null) {
            return serveInvalidSettingError("Missing filter", null);
        }
        try {
            final Pattern filter = Pattern.compile(filterString);
            final long filesize;
            final long duration;
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
            mConfig.updateSettings(filter, filesize, duration);
            return serveRedirect("Settings updated", "/");
        } catch (final PatternSyntaxException e) {
            return serveInvalidSettingError("Invalid filter syntax", e);
        } catch (final NumberFormatException e) {
            return serveInvalidSettingError("Invalid number", e);
        }
    }

    private Response serve404() {
        return new Response(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found");
    }

    private Response serveInvalidSettingError(final String title, final Throwable e) {
        final Chunk chunk = mConfig.theme.makeChunk("settings_error");
        chunk.put("title", title);
        if (e != null) {
            chunk.put("message", e.getLocalizedMessage());
        }
        return new Response(Response.Status.BAD_REQUEST, MIME_HTML, chunk.toString());
    }
}
