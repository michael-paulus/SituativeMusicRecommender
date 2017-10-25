package com.michael.situativemusicrecommender;

import android.database.Cursor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import kaaes.spotify.webapi.android.SpotifyApi;
import kaaes.spotify.webapi.android.SpotifyService;
import kaaes.spotify.webapi.android.models.Artist;
import kaaes.spotify.webapi.android.models.Artists;

/**
 * Created by Michael on 22.09.2017.
 */

public class RecommendationBuilder {

    ArrayList<TrackRecord> trackRecords;
    SpotifyApi api;

    private void addArtistToRecommendation(Map<String, ArtistRecommendation> recommendations, TrackRecord t, String artistID, Boolean isOriginal) {
        float weight;
        if (isOriginal) {
            weight = t.weight * getHalfLifeValue(t.timestamp);
        } else {
            weight = t.weight * getHalfLifeValue(t.timestamp);
        }
        if (recommendations.containsKey(artistID)){
            ArtistRecommendation r = recommendations.get(artistID);
            r.addWeight(weight);
        } else {
            api = new SpotifyApi();
            api.setAccessToken(MainActivity.getAccessToken());
            SpotifyService spotify = api.getService();
            recommendations.put(artistID, new ArtistRecommendation(artistID, spotify.getArtist(artistID).name, weight, 1));
        }
    }

    private float getHalfLifeValue(Date timestamp) {
        Calendar cal = new GregorianCalendar();
        Date today = cal.getTime();
        long days = getDateDiff(timestamp, today, TimeUnit.DAYS);
        return (float) Math.pow(0.97715997, days);
    }

    private long getDateDiff(Date date1, Date date2, TimeUnit timeUnit) {
        long diffInMillies = date2.getTime() - date1.getTime();
        return timeUnit.convert(diffInMillies, TimeUnit.MILLISECONDS);
    }

    private ArrayList<String> findSimilarArtists(String artistID) {
        api = new SpotifyApi();
        api.setAccessToken(MainActivity.getAccessToken());
        SpotifyService spotify = api.getService();
        Artists artists = spotify.getRelatedArtists(artistID);
        ArrayList<String> artistIDs = new ArrayList<>();
        for (int i = 0; i<5; i++){
            Artist a = artists.artists.get(i);
            artistIDs.add(a.id);
        }
        return artistIDs;
    }

    public void build() {
        new Thread(() -> {
            DBHelper mydb = new DBHelper(MainActivity.getContext());
            trackRecords = mydb.getTrackRecordsForSituation(1);
            final Map<String, ArtistRecommendation> recommendations = new HashMap<>();
            for (TrackRecord t: trackRecords){
                addArtistToRecommendation(recommendations, t, t.artistID, true);
                ArrayList<String> similarArtistIDs = findSimilarArtists(t.artistID);
                for (String s: similarArtistIDs){
                    addArtistToRecommendation(recommendations, t, s, false);
                }
            }
            System.out.println(Arrays.toString(recommendations.entrySet().toArray()));
            MainActivity.getContext().printResults(recommendations);
        }).start();
    }

    static class TrackRecord {
        String artistID;
        float weight;
        Date timestamp;

        TrackRecord(String artistID, float weight, Date timestamp) {
            super();
            this.artistID=artistID;
            this.weight=weight;
            this.timestamp=timestamp;
        }

        @Override
        public String toString() {
            return "(Artist ID: " + artistID + ", weight: " + weight + ", Time: " + timestamp.toString() +")";
        }
    }

    class ArtistRecommendation {
        String artistID;
        String artistName;
        float weightSum;
        int count;
        float weight;

        ArtistRecommendation(String artistID, String artistName, float weightSum, int count){
            super();
            this.artistID=artistID;
            this.artistName=artistName;
            this.weightSum=weightSum;
            this.count=count;
            this.weight = weightSum / count;
        }

        void addWeight(float Weight){
            count++;
            weightSum+=Weight;
            weight = weightSum / count;
        }

        @Override
        public String toString() {
            return "(Sum: " + weightSum + ", count: " + count + ", weight: " + weight + ")";
        }
    }


    static class ArtistRecommendationComparator implements Comparator<ArtistRecommendation> {
        public int compare(ArtistRecommendation artist1, ArtistRecommendation artist2) {
            return (int) (100*artist2.weight - 100*artist1.weight);
        }
    }
}
