package com.michael.situativemusicrecommender;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

/**
 * Created by Michael on 08.09.2017.
 */

public class DBHelper extends SQLiteOpenHelper {

    public static final String DATABASE_NAME = "ArtistSimilarities.db";
    public static final String HISTORY_TABLE_NAME = "history";
    public static final String HISTORY_COLUMN_ID = "id";
    public static final String HISTORY_COLUMN_ARTISTNAME = "artistname";
    public static final String HISTORY_COLUMN_ARTISTSPOTIFYID = "artistspotifyid";
    public static final String HISTORY_COLUMN_TRACKSPOTIFYID = "trackspotifyid";
    public static final String HISTORY_COLUMN_SITUATIONID = "situationid";
    public static final String HISTORY_COLUMN_WEIGHT = "weight";
    public static final String HISTORY_COLUMN_TIMESTAMP = "timestamp";
    public static final String CREATE_HISTORY_TABLE = "create table " + HISTORY_TABLE_NAME + " (" + HISTORY_COLUMN_ID + "integer primary key, " +
            HISTORY_COLUMN_ARTISTNAME + " text, " + HISTORY_COLUMN_ARTISTSPOTIFYID + " text, " +
            HISTORY_COLUMN_TRACKSPOTIFYID + " text, " + HISTORY_COLUMN_SITUATIONID + " integer, " +
            HISTORY_COLUMN_WEIGHT + " real, " + HISTORY_COLUMN_TIMESTAMP + " text)";
    private HashMap hp;

    public DBHelper(Context context) {
        super(context, DATABASE_NAME , null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // TODO Auto-generated method stub
        db.execSQL(CREATE_HISTORY_TABLE
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // TODO Auto-generated method stub
        db.execSQL("DROP TABLE IF EXISTS " + HISTORY_TABLE_NAME);
        onCreate(db);
    }

    public boolean insertTrackHistory (String artistName, String artistSpotifyID, String trackSpotifyID, int situationID, float weight, Date dateTimestamp) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put(HISTORY_COLUMN_ARTISTNAME, artistName);
        contentValues.put(HISTORY_COLUMN_ARTISTSPOTIFYID, artistSpotifyID);
        contentValues.put(HISTORY_COLUMN_TRACKSPOTIFYID, trackSpotifyID);
        contentValues.put(HISTORY_COLUMN_SITUATIONID, situationID);
        contentValues.put(HISTORY_COLUMN_WEIGHT, weight);
        long timestamp = dictionary.dateToMilliseconds(dateTimestamp);
        contentValues.put(HISTORY_COLUMN_TIMESTAMP, timestamp);
        db.insert(HISTORY_TABLE_NAME, null, contentValues);
        return true;
    }

    public Cursor getHistoryData(String field, String where) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor res =  db.rawQuery( "select " + field + " from " + HISTORY_TABLE_NAME + " where " + where + "", null );
        return res;
    }

    public int numberOfRows(){
        SQLiteDatabase db = this.getReadableDatabase();
        int numRows = (int) DatabaseUtils.queryNumEntries(db, HISTORY_TABLE_NAME);
        return numRows;
    }

    public boolean updateContact (Integer id, String name, String phone, String email, String street,String place) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put("name", name);
        contentValues.put("phone", phone);
        contentValues.put("email", email);
        contentValues.put("street", street);
        contentValues.put("place", place);
        db.update("contacts", contentValues, "id = ? ", new String[] { Integer.toString(id) } );
        return true;
    }

    public ArrayList<String> getAllTracksFromHistory() {
        ArrayList<String> array_list = new ArrayList<String>();

        //hp = new HashMap();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor res =  db.rawQuery( "select * from " + HISTORY_TABLE_NAME, null );
        res.moveToFirst();

        while(res.isAfterLast() == false){
            array_list.add(res.getString(res.getColumnIndex(HISTORY_COLUMN_TRACKSPOTIFYID)));
            res.moveToNext();
        }
        return array_list;
    }

    public ArrayList<RecommendationBuilder.TrackRecord> getTrackRecordsForSituation(int i) {
        Cursor mCursor = getHistoryData(DBHelper.HISTORY_COLUMN_ARTISTSPOTIFYID + ", " + DBHelper.HISTORY_COLUMN_WEIGHT + ", " + DBHelper.HISTORY_COLUMN_TIMESTAMP, DBHelper.HISTORY_COLUMN_SITUATIONID + " = " + i);
        ArrayList<RecommendationBuilder.TrackRecord> trackRecords = new ArrayList<>();
        while (mCursor.moveToNext()){
            Date date = new Date(mCursor.getLong(2));
            trackRecords.add(new RecommendationBuilder.TrackRecord(mCursor.getString(0), mCursor.getFloat(1), date));
        }
        System.out.println(trackRecords.toString());
        return trackRecords;
    }
}
