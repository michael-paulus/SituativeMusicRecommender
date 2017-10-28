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

    private static final String DATABASE_NAME = "ArtistSimilarities.db";
    private static final String HISTORY_TABLE_NAME = "history";
    private static final String HISTORY_COLUMN_ID = "id";
    private static final String HISTORY_COLUMN_USERAGE = "userage";
    private static final String HISTORY_COLUMN_USERGENDER = "usergender";
    private static final String HISTORY_COLUMN_ARTISTNAME = "artistname";
    private static final String HISTORY_COLUMN_ARTISTSPOTIFYID = "artistspotifyid";
    private static final String HISTORY_COLUMN_ARTIST_GENRES = "artistgenres";
    private static final String HISTORY_COLUMN_TRACKSPOTIFYID = "trackspotifyid";
    private static final String HISTORY_COLUMN_TRACKNAME = "trackname";
    private static final String HISTORY_COLUMN_TRACKDANCEABILITY = "trackdanceability";
    private static final String HISTORY_COLUMN_TRACKENERGY = "trackenergy";
    private static final String HISTORY_COLUMN_TRACKKEY = "trackkey";
    private static final String HISTORY_COLUMN_TRACKLOUDNESS = "trackloudness";
    private static final String HISTORY_COLUMN_TRACKMODE = "trackmode";
    private static final String HISTORY_COLUMN_TRACKSPEECHINESS = "trackspeechiness";
    private static final String HISTORY_COLUMN_TRACKACOUSTICNESS = "trackacousticness";
    private static final String HISTORY_COLUMN_TRACKINSTRUMENTALNESS = "trackinstrumentalness";
    private static final String HISTORY_COLUMN_TRACKLIVENESS = "trackliveness";
    private static final String HISTORY_COLUMN_TRACKVALENCE = "trackvalence";
    private static final String HISTORY_COLUMN_TRACKTEMPO = "tracktempo";
    private static final String HISTORY_COLUMN_TRACKDURATION = "trackduration";
    private static final String HISTORY_COLUMN_TRACKTIMESIGNATURE = "tracktimesignature";
    private static final String HISTORY_COLUMN_LOCATIONID = "locationid";
    private static final String HISTORY_COLUMN_WEIGHT = "weight";
    private static final String HISTORY_COLUMN_TIMESTAMP = "timestamp";
    private static final String CREATE_HISTORY_TABLE = "create table " + HISTORY_TABLE_NAME + " (" +
            HISTORY_COLUMN_ID + "integer primary key, " + HISTORY_COLUMN_ARTISTNAME + " text, " +
            HISTORY_COLUMN_ARTISTSPOTIFYID + " text, " + HISTORY_COLUMN_TRACKSPOTIFYID + " text, " +
            HISTORY_COLUMN_LOCATIONID + " integer, " + HISTORY_COLUMN_WEIGHT + " real, " +
            HISTORY_COLUMN_TIMESTAMP + " text, " + HISTORY_COLUMN_USERAGE + " integer, " +
            HISTORY_COLUMN_USERGENDER + " text, " + HISTORY_COLUMN_ARTIST_GENRES + " text, " +
            HISTORY_COLUMN_TRACKNAME + " text, " + HISTORY_COLUMN_TRACKDANCEABILITY + " real, " +
            HISTORY_COLUMN_TRACKENERGY + " real, " + HISTORY_COLUMN_TRACKKEY + " real, " +
            HISTORY_COLUMN_TRACKLOUDNESS + " real, " + HISTORY_COLUMN_TRACKMODE + " integer, " +
            HISTORY_COLUMN_TRACKSPEECHINESS + " real, " + HISTORY_COLUMN_TRACKACOUSTICNESS + " real, " +
            HISTORY_COLUMN_TRACKINSTRUMENTALNESS + " real, " + HISTORY_COLUMN_TRACKLIVENESS + " real, " +
            HISTORY_COLUMN_TRACKVALENCE + " real, " + HISTORY_COLUMN_TRACKTEMPO + " real, " +
            HISTORY_COLUMN_TRACKDURATION + " integer, " + HISTORY_COLUMN_TRACKTIMESIGNATURE + " integer)";
    private HashMap hp;

    DBHelper(Context context) {
        super(context, DATABASE_NAME , null, 4);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // TODO Auto-generated method stub
        db.execSQL(CREATE_HISTORY_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // TODO Auto-generated method stub
        System.out.println("New version of database created: " + newVersion + ", former Version: " + oldVersion);
        db.execSQL("DROP TABLE IF EXISTS " + HISTORY_TABLE_NAME);
        onCreate(db);
    }

    void insertTrackHistory(MainActivity.TrackRecord trackRecord) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put(HISTORY_COLUMN_ARTISTNAME, trackRecord.artist.name);
        contentValues.put(HISTORY_COLUMN_ARTISTSPOTIFYID, trackRecord.artist.id);
        contentValues.put(HISTORY_COLUMN_TRACKSPOTIFYID, trackRecord.song.id);
        contentValues.put(HISTORY_COLUMN_LOCATIONID, trackRecord.locationId);
        contentValues.put(HISTORY_COLUMN_WEIGHT, trackRecord.weight);
        contentValues.put(HISTORY_COLUMN_TIMESTAMP, trackRecord.time);
        contentValues.put(HISTORY_COLUMN_USERAGE, trackRecord.user.age);
        contentValues.put(HISTORY_COLUMN_USERGENDER, trackRecord.user.gender);
        contentValues.put(HISTORY_COLUMN_ARTIST_GENRES, trackRecord.artist.genres.toString());
        contentValues.put(HISTORY_COLUMN_TRACKNAME, trackRecord.song.name);
        contentValues.put(HISTORY_COLUMN_TRACKDANCEABILITY, trackRecord.song.danceability);
        contentValues.put(HISTORY_COLUMN_TRACKENERGY, trackRecord.song.energy);
        contentValues.put(HISTORY_COLUMN_TRACKKEY, trackRecord.song.key);
        contentValues.put(HISTORY_COLUMN_TRACKLOUDNESS, trackRecord.song.loudness);
        contentValues.put(HISTORY_COLUMN_TRACKMODE, trackRecord.song.mode);
        contentValues.put(HISTORY_COLUMN_TRACKSPEECHINESS, trackRecord.song.speechiness);
        contentValues.put(HISTORY_COLUMN_TRACKACOUSTICNESS, trackRecord.song.acousticness);
        contentValues.put(HISTORY_COLUMN_TRACKINSTRUMENTALNESS, trackRecord.song.instrumentalness);
        contentValues.put(HISTORY_COLUMN_TRACKLIVENESS, trackRecord.song.liveness);
        contentValues.put(HISTORY_COLUMN_TRACKVALENCE, trackRecord.song.valence);
        contentValues.put(HISTORY_COLUMN_TRACKTEMPO, trackRecord.song.tempo);
        contentValues.put(HISTORY_COLUMN_TRACKDURATION, trackRecord.song.duration);
        contentValues.put(HISTORY_COLUMN_TRACKTIMESIGNATURE, trackRecord.song.timeSignature);
        db.insert(HISTORY_TABLE_NAME, null, contentValues);
    }

    private Cursor getHistoryData(String field, String where) {
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

        while(!res.isAfterLast()){
            array_list.add(res.getString(res.getColumnIndex(HISTORY_COLUMN_TRACKSPOTIFYID)));
            res.moveToNext();
        }
        return array_list;
    }

    ArrayList<MainActivity.TrackRecord> getTrackRecordsForSituation(int i) {
        Cursor mCursor = getHistoryData("*", HISTORY_COLUMN_TRACKDURATION + " > 1");
        ArrayList<MainActivity.TrackRecord> trackRecords = new ArrayList<>();
        while (mCursor.moveToNext()){
            System.out.println("Record found: " + mCursor.toString());
        }
        System.out.println(trackRecords.toString());
        return trackRecords;
    }
}
