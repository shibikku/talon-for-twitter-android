package com.klinker.android.twitter.data.sq_lite;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.Settings.System;
import android.util.Log;

import com.klinker.android.twitter.ui.MainActivity;
import com.klinker.android.twitter.utils.HtmlUtils;

import java.util.ArrayList;
import java.util.List;

import twitter4j.Status;

public class HomeContentProvider extends ContentProvider {
    static final String TAG = "HomeTimeline";

    public static final String AUTHORITY = "com.klinker.android.twitter.provider";
    static final String BASE_PATH = "tweet_id";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/" + BASE_PATH);

    private Context context;
    private HomeSQLiteHelper helper;

    @Override
    public boolean onCreate() {
        Log.d(TAG, "onCreate");
        context = getContext();
        helper = new HomeSQLiteHelper(context);

        return (helper == null) ? false : true;
    }

    @Override
    public String getType(Uri uri) {
        String ret = getContext().getContentResolver().getType(System.CONTENT_URI);
        Log.d(TAG, "getType returning: " + ret);
        return ret;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        Log.d(TAG, "insert uri: " + uri.toString());

        Uri result = null;

        SQLiteDatabase db = helper.getWritableDatabase();
        long rowID;
        try {
            rowID = db.insert(HomeSQLiteHelper.TABLE_HOME, null, values);
        } catch (IllegalStateException e) {
            // shouldn't happen here, but might i guess
            db = HomeDataSource.getInstance(context).getDatabase();
            rowID = db.insert(HomeSQLiteHelper.TABLE_HOME, null, values);
        }

        if (rowID > 0) {
            // Return a URI to the newly created row on success
            result = ContentUris.withAppendedId(Uri.parse(AUTHORITY + "/status"), rowID);

            // Notify the Context's ContentResolver of the change
            getContext().getContentResolver().notifyChange(result, null);
        }

        db.close();

        return Uri.parse(BASE_PATH + "/" + rowID);
    }

    @Override
    public int bulkInsert(Uri uri, ContentValues[] allValues) {
        return insertMultiple(allValues);
    }

    private int insertMultiple(ContentValues[] allValues) {
        int rowsAdded = 0;
        long rowId;
        ContentValues values;

        SQLiteDatabase db = HomeDataSource.getInstance(context).getDatabase();

        try {
            db.beginTransaction();

            for (ContentValues initialValues : allValues) {
                values = initialValues == null ? new ContentValues() : new ContentValues(initialValues);
                try {
                    rowId = db.insert(HomeSQLiteHelper.TABLE_HOME, null, values);
                } catch (IllegalStateException e) {
                    db = HomeDataSource.getInstance(context).getDatabase();
                    rowId = db.insert(HomeSQLiteHelper.TABLE_HOME, null, values);
                }
                if (rowId > 0)
                    rowsAdded++;
            }

            db.setTransactionSuccessful();
        } catch (IllegalStateException e) {
            // caught setting up the transaction I guess, shouldn't ever happen now.
            e.printStackTrace();
            return rowsAdded;
        } finally {
            try {
                db.endTransaction();
            } catch (Exception e) {
                // shouldn't happen unless it gets caught above from an illegal state
            }
        }

        return rowsAdded;
    }

    // arg[0] is the account
    // arg[1] is the position
    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        Log.d(TAG, "update uri: " + uri.toString());
        SQLiteDatabase db = helper.getWritableDatabase();

        HomeDataSource dataSource = HomeDataSource.getInstance(context);
        Cursor cursor = dataSource.getUnreadCursor(Integer.parseInt(selectionArgs[0]));

        if (cursor.moveToPosition(Integer.parseInt(selectionArgs[1]))) {
            long tweetId = cursor.getLong(cursor.getColumnIndex(HomeSQLiteHelper.COLUMN_TWEET_ID));

            ContentValues cv = new ContentValues();
            cv.put(HomeSQLiteHelper.COLUMN_UNREAD, 0);

            db.update(HomeSQLiteHelper.TABLE_HOME, cv, HomeSQLiteHelper.COLUMN_TWEET_ID + " = ?", new String[] {tweetId + ""});
        }

        db.close();

        context.getContentResolver().notifyChange(uri, null);

        return 1;
    }

    @Override
    public int delete(Uri uri, String id, String[] selectionArgs) {
        Log.d(TAG, "delete uri: " + uri.toString());
        SQLiteDatabase db = helper.getWritableDatabase();
        int count;

        String segment = uri.getLastPathSegment();
        count = db.delete(HomeSQLiteHelper.TABLE_HOME, HomeSQLiteHelper.COLUMN_TWEET_ID
                + " = " + id, null);


        if (count > 0) {
            // Notify the Context's ContentResolver of the change
            getContext().getContentResolver().notifyChange(uri, null);
        }

        db.close();
        return count;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        Log.d(TAG, "query with uri: " + uri.toString());

        SQLiteDatabase db = helper.getWritableDatabase();

        // A convenience class to help build the query
        HomeDataSource data = HomeDataSource.getInstance(context);
        Cursor c = data.getCursor(Integer.parseInt(selectionArgs[0]));//qb.query(db,
                //projection, HomeSQLiteHelper.COLUMN_ACCOUNT + " = " + selectionArgs[0], null, null, null, HomeSQLiteHelper.COLUMN_TWEET_ID + " ASC");
        c.setNotificationUri(context.getContentResolver(), uri);

        return c;
    }

    public static void insertTweet(Status status, int currentAccount, Context context) {
        ContentValues values = new ContentValues();
        String originalName = "";
        long id = status.getId();
        long time = status.getCreatedAt().getTime();

        if(status.isRetweet()) {
            originalName = status.getUser().getScreenName();
            status = status.getRetweetedStatus();
        }

        String[] html = HtmlUtils.getHtmlStatus(status);
        String text = html[0];
        String media = html[1];
        String url = html[2];
        String hashtags = html[3];
        String users = html[4];

        values.put(HomeSQLiteHelper.COLUMN_ACCOUNT, currentAccount);
        values.put(HomeSQLiteHelper.COLUMN_TEXT, text);
        values.put(HomeSQLiteHelper.COLUMN_TWEET_ID, id);
        values.put(HomeSQLiteHelper.COLUMN_NAME, status.getUser().getName());
        values.put(HomeSQLiteHelper.COLUMN_PRO_PIC, status.getUser().getBiggerProfileImageURL());
        values.put(HomeSQLiteHelper.COLUMN_SCREEN_NAME, status.getUser().getScreenName());
        values.put(HomeSQLiteHelper.COLUMN_TIME, time);
        values.put(HomeSQLiteHelper.COLUMN_RETWEETER, originalName);
        values.put(HomeSQLiteHelper.COLUMN_UNREAD, 1);
        values.put(HomeSQLiteHelper.COLUMN_PIC_URL, media);
        values.put(HomeSQLiteHelper.COLUMN_URL, url);
        values.put(HomeSQLiteHelper.COLUMN_USERS, users);
        values.put(HomeSQLiteHelper.COLUMN_HASHTAGS, hashtags);

        context.getContentResolver().insert(HomeContentProvider.CONTENT_URI, values);
    }

    public static void insertTweets(List<Status> statuses, int currentAccount, Context context) {

        ContentValues[] valueses = new ContentValues[statuses.size()];

        for (int i = 0; i < statuses.size(); i++) {
            Status status = statuses.get(i);
            ContentValues values = new ContentValues();
            String originalName = "";
            long id = status.getId();
            long time = status.getCreatedAt().getTime();

            if(status.isRetweet()) {
                originalName = status.getUser().getScreenName();
                status = status.getRetweetedStatus();
            }

            String[] html = HtmlUtils.getHtmlStatus(status);
            String text = html[0];
            String media = html[1];
            String url = html[2];
            String hashtags = html[3];
            String users = html[4];

            values.put(HomeSQLiteHelper.COLUMN_ACCOUNT, currentAccount);
            values.put(HomeSQLiteHelper.COLUMN_TEXT, text);
            values.put(HomeSQLiteHelper.COLUMN_TWEET_ID, id);
            values.put(HomeSQLiteHelper.COLUMN_NAME, status.getUser().getName());
            values.put(HomeSQLiteHelper.COLUMN_PRO_PIC, status.getUser().getBiggerProfileImageURL());
            values.put(HomeSQLiteHelper.COLUMN_SCREEN_NAME, status.getUser().getScreenName());
            values.put(HomeSQLiteHelper.COLUMN_TIME, time);
            values.put(HomeSQLiteHelper.COLUMN_RETWEETER, originalName);
            values.put(HomeSQLiteHelper.COLUMN_UNREAD, 1);
            values.put(HomeSQLiteHelper.COLUMN_PIC_URL, media);
            values.put(HomeSQLiteHelper.COLUMN_URL, url);
            values.put(HomeSQLiteHelper.COLUMN_USERS, users);
            values.put(HomeSQLiteHelper.COLUMN_HASHTAGS, hashtags);

            valueses[i] = values;
        }

        context.getContentResolver().bulkInsert(HomeContentProvider.CONTENT_URI, valueses);
    }

}
