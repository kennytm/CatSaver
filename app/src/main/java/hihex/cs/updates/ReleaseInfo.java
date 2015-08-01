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
 *
 */

package hihex.cs.updates;

import android.util.JsonReader;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Created by kennytm on 15-08-01.
 */
public final class ReleaseInfo {
    // The 'X' pattern for ISO-8601 time zone does not exist on Android...
    private static final SimpleDateFormat ISO8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);

    static {
        ISO8601.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    public String url;
    public Date date;
    public long size;
    public String version = "0.0.0";
    public String info;
    public String eTag;

    private void initializeAssets(final JsonReader reader) throws IOException {
        long mySize = 0;
        boolean isValid = false;
        String myUrl = null;

        reader.beginArray();
        while (reader.hasNext()) {
            reader.beginObject();
            while (reader.hasNext()) {
                switch (reader.nextName()) {
                    default:
                        reader.skipValue();
                        break;
                    case "size":
                        mySize = reader.nextLong();
                        break;
                    case "state":
                        isValid = "uploaded".equals(reader.nextString());
                        break;
                    case "browser_download_url":
                        myUrl = reader.nextString();
                        break;
                }
            }
            reader.endObject();
        }
        reader.endArray();

        if (isValid) {
            size = mySize;
            url = myUrl;
        } else {
            throw new IOException("APK not yet uploaded, please wait a few minutes");
        }
    }

    public final void initialize(final JsonReader reader) throws IOException, ParseException {
        reader.beginObject();
        while (reader.hasNext()) {
            switch (reader.nextName()) {
                default:
                    reader.skipValue();
                    break;
                case "tag_name":
                    version = reader.nextString().substring(1);
                    break;
                case "published_at":
                    date = ISO8601.parse(reader.nextString());
                    break;
                case "assets":
                    initializeAssets(reader);
                    break;
                case "body":
                    info = reader.nextString();
                    break;
            }
        }
        reader.endObject();
    }
}
