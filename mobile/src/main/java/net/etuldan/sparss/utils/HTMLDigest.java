package net.etuldan.sparss.utils;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.os.Environment;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.widget.Toast;

import net.etuldan.sparss.Constants;
import net.etuldan.sparss.MainApplication;
import net.etuldan.sparss.provider.FeedData;
import net.etuldan.sparss.provider.FeedDataContentProvider;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;

import static net.etuldan.sparss.MainApplication.getContext;

/**
 * @author Oliver G
 */

public class HTMLDigest {

    // TODO: Add timestamp to filename
    public static final String STARRED_DIGEST = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/spaRSS_starred_digest.html";

    private static final String[] FAVEXPORT_PROJECTION = new String[]{FeedData.EntryColumns.FEED_ID,
            FeedData.EntryColumns.TITLE, FeedData.EntryColumns.DATE, FeedData.EntryColumns.LINK,
            FeedData.EntryColumns.AUTHOR, FeedData.EntryColumns.ABSTRACT,
            FeedData.EntryColumns.MOBILIZED_HTML};

    private static final String FAVEXPORT_START = "<!DOCTYPE HTML><html>\n\t<head>\n\t\t<meta http-equiv='Content-Type' content='text/html; charset=UTF-8' />"
                                                  + "<meta id='Viewport' name='viewport' content='initial-scale=1, maximum-scale=1, minimum-scale=1, user-scalable=no'>"
                                                  + "\n\t\t<title>spaRSS favorites export</title>\n\t</head>\n\t<body>\n";
    private static final String FAVEXPORT_ENTRY_START = "\t\t<article>\n\t\t\t<header>\n\t\t\t\t<h1>";
    private static final String FAVEXPORT_ENTRY_AFTER_TITLE = "</h1>\n\t\t\t</header>\n\t\t\t<p><i>";
    private static final String FAVEXPORT_ENTRY_AFTER_META = "</i>\n";
    private static final String FAVEXPORT_ENTRY_LINK_START = "&lt;";
    private static final String FAVEXPORT_ENTRY_LINK_END = "&gt;<br />";
    private static final String FAVEXPORT_ENTRY_CLOSING ="</p>\n\t\t</article>\n";
    private static final String FAVEXPORT_CLOSING = "</body>\n</html>\n";

    // private static boolean mIncludeImages = false;

    public static void exportStarred(String filename) throws IOException {

        // Build HTML File
        // HEADER
        StringBuilder builder = new StringBuilder(FAVEXPORT_START);

        // TODO: sort order: by Groups > by Feed > by Date
        Cursor cursor = getContext().getContentResolver()
            .query(FeedData.EntryColumns.FAVORITES_CONTENT_URI, FAVEXPORT_PROJECTION, null, null, FeedData.EntryColumns.DATE);

        // no favorites at all? Display toast and leave!
        if (cursor.getCount() == 0) {
            Context context = getContext();
            CharSequence text = "Favorites empty, nothing to export.";
            int duration = Toast.LENGTH_SHORT;

            Toast toast = Toast.makeText(context, text, duration);
            toast.show();
            return;
        }

        // Loop through all starred entries
        // TODO:
        // - Sort by Group and Feed Order Within Groups
        // - Print Headlines with Group and Feed Names
        while (cursor.moveToNext()) {
            builder.append(FAVEXPORT_ENTRY_START);
            builder.append(cursor.isNull(1) ? "" : TextUtils.htmlEncode(cursor.getString(1))); // title
            builder.append(FAVEXPORT_ENTRY_AFTER_TITLE);

            Date date = new Date(cursor.getLong(2));
            Context context = getContext();
            StringBuilder dateStringBuilder = new StringBuilder(DateFormat.getDateFormat(context).format(date)).append(' ').append(
                    DateFormat.getTimeFormat(context).format(date));

            builder.append(dateStringBuilder);

            if (!cursor.isNull(4)) {
                builder.append(", ");
                builder.append(cursor.getString(4)); // author if exists
            }

            String url = cursor.getString(3);
            builder.append("<br />").append(FAVEXPORT_ENTRY_LINK_START);
            builder.append("<a href='").append(url).append("'>");
            builder.append(url).append("</a>").append(FAVEXPORT_ENTRY_LINK_END);
            builder.append(FAVEXPORT_ENTRY_AFTER_META);
            builder.append(cursor.isNull(6) ? cursor.getString(5) : cursor.getString(6)); // fulltext unavailable? use abstract!
            builder.append(FAVEXPORT_ENTRY_CLOSING);
        }

        // CLOSING
        builder.append(FAVEXPORT_CLOSING);

        // Write File
        BufferedWriter writer = new BufferedWriter(new FileWriter(filename));

        writer.write(builder.toString());
        writer.close();
    }
}
