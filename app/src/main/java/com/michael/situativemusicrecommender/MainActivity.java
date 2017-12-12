package com.michael.situativemusicrecommender;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.loopj.android.http.HttpGet;
import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;
import com.spotify.sdk.android.player.Config;
import com.spotify.sdk.android.player.ConnectionStateCallback;
import com.spotify.sdk.android.player.Error;
import com.spotify.sdk.android.player.Metadata;
import com.spotify.sdk.android.player.Player;
import com.spotify.sdk.android.player.PlayerEvent;
import com.spotify.sdk.android.player.Spotify;
import com.spotify.sdk.android.player.SpotifyPlayer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xml.sax.SAXException;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;

import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.client.ClientProtocolException;
import cz.msebera.android.httpclient.client.HttpClient;
import cz.msebera.android.httpclient.impl.client.DefaultHttpClient;
import cz.msebera.android.httpclient.util.EntityUtils;
import kaaes.spotify.webapi.android.SpotifyApi;
import kaaes.spotify.webapi.android.SpotifyService;
import kaaes.spotify.webapi.android.models.Album;
import kaaes.spotify.webapi.android.models.AlbumSimple;
import kaaes.spotify.webapi.android.models.AlbumsPager;
import kaaes.spotify.webapi.android.models.Artist;
import kaaes.spotify.webapi.android.models.ArtistsPager;
import kaaes.spotify.webapi.android.models.AudioFeaturesTrack;
import kaaes.spotify.webapi.android.models.Pager;
import kaaes.spotify.webapi.android.models.Track;
import kaaes.spotify.webapi.android.models.TrackSimple;
import kaaes.spotify.webapi.android.models.TracksPager;
import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

public class MainActivity extends AppCompatActivity implements
        SpotifyPlayer.NotificationCallback, ConnectionStateCallback {

    private static final int MY_PERMISSIONS_REQUEST_ACCESS_LOCATION = 42;
    private static final String CLIENT_ID = "deaa15968cb84bf09fdd25034b520033";
    private LocationManager locationManager;
    private LocationListener locationListener;
    public static final String TYPE_PLAYLIST = "Playlist";
    private static final String TYPE_TRACK = "Track";
    private static final int REQUEST_CODE = 1337;
    private String provider;
    static String accessToken;
    private Player mPlayer;
    static MainActivity itself;
    private Handler mHandler;

    public static final String REDIRECT_URI = "michael-situativemusicrecommender://callback";
    private DBHelper mydb;
    private String userId;
    private List<Suggestion> queue;
    private List<Suggestion> lastPlayed;

    public int getHours() {
        Calendar c = GregorianCalendar.getInstance();
        return c.getTime().getHours();
    }

    public int getMonth() {
        Calendar c = GregorianCalendar.getInstance();
        return c.getTime().getMonth();
    }

    public int getDay() {
        Calendar c = GregorianCalendar.getInstance();
        return c.getTime().getDay();
    }

    class Suggestion{
        TrackSimple track;
        float suggestionValue;

        Suggestion(TrackSimple track, float suggestionValue){
            this.track = track;
            this.suggestionValue = suggestionValue;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        itself = this;
        constructDB();
        lastPlayed = new ArrayList<>();
        queue = new ArrayList<>();

        prepLayout();

        setLocationListener();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkIfIdTokenStillValid();
    }

    private void checkIfIdTokenStillValid() {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        int initialStorageTime = pref.getInt("AccessToken_StorageTime", 0);
        int currentTime = (int) Calendar.getInstance().getTimeInMillis() / 1000;
        int expirationTime = pref.getInt("AccessToken_ExpirationSeconds", 0);
        if (userId == null || userId.equals("")) {
            userId = pref.getString("UserId", "");
        }
        if (initialStorageTime + expirationTime < currentTime - 1000) {
            if (isNetworkAvailable()) {
                AuthenticationRequest.Builder builder = new AuthenticationRequest.Builder(CLIENT_ID,
                        AuthenticationResponse.Type.TOKEN,
                        REDIRECT_URI);
                builder.setScopes(new String[]{"streaming"});
                AuthenticationRequest request = builder.build();

                //AuthenticationClient.openLoginActivity(this, REQUEST_CODE, request);
                AuthenticationClient.openLoginInBrowser(this, request);
            }
        } else {
            buildPlayer();
        }
    }

    private void buildPlayer() {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        Config playerConfig = new Config(this, pref.getString("AccessToken", ""), CLIENT_ID);
        Spotify.getPlayer(playerConfig, this, new SpotifyPlayer.InitializationObserver() {
            @Override
            public void onInitialized(SpotifyPlayer spotifyPlayer) {
                mPlayer = spotifyPlayer;
                mPlayer.addConnectionStateCallback(MainActivity.this);
                mPlayer.addNotificationCallback(MainActivity.this);
                Log.d("Player", "has been set");
            }

            @Override
            public void onError(Throwable throwable) {
                Log.e("MainActivity", "Could not initialize player: " + throwable.getMessage());
            }
        });
    }

    private void prepLayout() {
        final EditText edittext = (EditText) findViewById(R.id.search_text);
        edittext.setSingleLine(true);
        mHandler = new Handler();
        Runnable UpdateProgressBarRunnable = new Runnable() {
            @Override
            public void run() {
                if (currentSong != null) {
                    try {
                        ProgressBar progressBar = (ProgressBar) findViewById(R.id.progressBar);
                        progressBar.setMax((int) mPlayer.getMetadata().currentTrack.durationMs);
                        progressBar.setProgress((int) mPlayer.getPlaybackState().positionMs);
                    } catch (Exception e){
                        e.printStackTrace();
                    }
                }
                mHandler.postDelayed(this, 500);
            }
        };
        mHandler.postDelayed(UpdateProgressBarRunnable, 500);
        edittext.setOnKeyListener((v, keyCode, event) -> {
            // If the event is a Key-down event on the "enter" button
            if ((event.getAction() == KeyEvent.ACTION_DOWN) &&
                    (keyCode == KeyEvent.KEYCODE_ENTER)) {
                // Perform action on Key press
                Toast.makeText(MainActivity.this, edittext.getText(), Toast.LENGTH_SHORT).show();
                edittext.clearFocus();
                edittext.setCursorVisible(false);
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(edittext.getWindowToken(), 0);
                search(edittext.getText().toString());
                return true;
            }
            return false;
        });
    }

    public int dpToPx(int dp) {
        DisplayMetrics displayMetrics = getContext().getResources().getDisplayMetrics();
        return Math.round(dp * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
    }

    public int pxToDp(int px) {
        DisplayMetrics displayMetrics = getContext().getResources().getDisplayMetrics();
        return Math.round(px / (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
    }

    private void search(String text) {
        SpotifyApi api = new SpotifyApi();
        accessToken = PreferenceManager.getDefaultSharedPreferences(MainActivity.this).getString("AccessToken", "");
        if (!accessToken.equals("")) {
            api.setAccessToken(PreferenceManager.getDefaultSharedPreferences(MainActivity.this).getString("AccessToken", ""));
            SpotifyService spotify = api.getService();
            ScrollView verticalScrollView = (ScrollView) findViewById(R.id.verticalscrollview);
            verticalScrollView.removeAllViews();
            LinearLayout scrollViewWrapper = new LinearLayout(MainActivity.itself);
            scrollViewWrapper.setOrientation(LinearLayout.VERTICAL);
            verticalScrollView.addView(scrollViewWrapper);
            spotify.searchArtists(text, new Callback<ArtistsPager>() {
                @Override
                public void success(ArtistsPager artistsPager, Response response) {
                    HorizontalScrollView horizontalScrollView = new HorizontalScrollView(MainActivity.itself);
                    LinearLayout parent = new LinearLayout(MainActivity.itself);
                    for (Artist a : artistsPager.artists.items) {
                        Log.d("Found Artist", a.name);
                        LinearLayout artistLayout = new LinearLayout(MainActivity.this);
                        artistLayout.setPadding(10, 0, 10, 0);
                        artistLayout.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                        artistLayout.setOrientation(LinearLayout.VERTICAL);

                        ImageView artistImage = new ImageView(MainActivity.this);
                        try {
                            setPictureFromUrl(artistImage, a.images.get(0).url);
                        } catch (Exception e) {
                            artistImage.setImageDrawable(getDrawable(android.R.drawable.ic_menu_agenda));
                        }
                        artistImage.setLayoutParams(new ViewGroup.LayoutParams(dpToPx(80), dpToPx(80)));
                        artistLayout.addView(artistImage);

                        TextView artistName = new TextView(MainActivity.this);
                        artistName.setText(a.name);
                        artistName.setLines(2);
                        artistLayout.addView(artistName);

                        artistLayout.setOnClickListener(v -> {
                            showSongsForArtist(a.id);
                        });

                        parent.addView(artistLayout);
                    }
                    horizontalScrollView.addView(parent);
                    scrollViewWrapper.addView(horizontalScrollView);
                }

                private void setPictureFromUrl(ImageView artistImage, String url) {
                    new Thread(() -> {
                        try {
                            Bitmap x;

                            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
                            connection.connect();

                            InputStream input = connection.getInputStream();

                            x = BitmapFactory.decodeStream(input);
                            Drawable drawable = new BitmapDrawable(x);
                            MainActivity.this.runOnUiThread(() -> artistImage.setImageDrawable(drawable));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }).start();
                }

                @Override
                public void failure(RetrofitError error) {

                }
            });
            spotify.searchAlbums(text, new Callback<AlbumsPager>() {
                @Override
                public void success(AlbumsPager albumsPager, Response response) {
                    HorizontalScrollView horizontalScrollView = new HorizontalScrollView(MainActivity.itself);
                    LinearLayout parent = new LinearLayout(MainActivity.itself);
                    for (AlbumSimple a : albumsPager.albums.items) {
                        Log.d("Found album", a.name);
                        LinearLayout albumLayout = new LinearLayout(MainActivity.this);
                        albumLayout.setPadding(10, 0, 10, 0);
                        albumLayout.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                        albumLayout.setOrientation(LinearLayout.VERTICAL);

                        ImageView albumImage = new ImageView(MainActivity.this);
                        try {
                            setPictureFromUrl(albumImage, a.images.get(0).url);
                        } catch (Exception e) {
                            albumImage.setImageDrawable(getDrawable(android.R.drawable.ic_menu_agenda));
                        }
                        albumImage.setLayoutParams(new ViewGroup.LayoutParams(dpToPx(70), dpToPx(70)));
                        albumLayout.addView(albumImage);

                        TextView albumName = new TextView(MainActivity.this);
                        albumName.setText(a.name);
                        albumName.setLines(2);
                        albumLayout.addView(albumName);

                        albumLayout.setOnClickListener(v -> {
                            showSongsForAlbum(a.id);
                        });

                        parent.addView(albumLayout);
                    }
                    horizontalScrollView.addView(parent);
                    scrollViewWrapper.addView(horizontalScrollView);
                }

                private void setPictureFromUrl(ImageView albumImage, String url) {
                    new Thread(() -> {
                        try {
                            Bitmap x;

                            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
                            connection.connect();

                            InputStream input = connection.getInputStream();

                            x = BitmapFactory.decodeStream(input);
                            Drawable drawable = new BitmapDrawable(x);
                            MainActivity.this.runOnUiThread(() -> albumImage.setImageDrawable(drawable));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }).start();
                }

                @Override
                public void failure(RetrofitError error) {

                }
            });
            spotify.searchTracks(text, new Callback<TracksPager>() {
                @Override
                public void success(TracksPager tracksPager, Response response) {
                    LinearLayout parent = new LinearLayout(MainActivity.itself);
                    parent.setOrientation(LinearLayout.VERTICAL);
                    try {
                        for (Track t : tracksPager.tracks.items) {
                            Log.d("Found track", t.name);
                            LinearLayout trackLayout = new LinearLayout(MainActivity.this);
                            trackLayout.setPadding(20, 5, 20, 5);
                            trackLayout.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                            trackLayout.setOrientation(LinearLayout.HORIZONTAL);

                            TextView trackName = new TextView(MainActivity.this);
                            trackName.setText(t.name);
                            trackLayout.addView(trackName);

                            TextView trackDuration = new TextView(MainActivity.this);
                            int seconds = (int) t.duration_ms % 60000 / 1000;
                            if (seconds < 10) {
                                trackDuration.setText(t.duration_ms / 60000 + ":0" + seconds);
                            } else {
                                trackDuration.setText(t.duration_ms / 60000 + ":" + seconds);
                            }
                            trackDuration.setLayoutParams(new ViewGroup.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                            trackDuration.setGravity(Gravity.END);
                            trackLayout.addView(trackDuration);

                            trackLayout.setOnClickListener(v -> {
                                queue = new ArrayList<>();
                                int index = 0;
                                while (!tracksPager.tracks.items.get(index).name.equals(t.name) && index < tracksPager.tracks.items.size()){
                                    index++;
                                }
                                index++;
                                for (int i = index; i < tracksPager.tracks.items.size(); i++){
                                    queue.add(new Suggestion(tracksPager.tracks.items.get(i), -1f));
                                }
                                lastPlayed.add(new Suggestion(t, -1f));
                                playMusic("spotify:track:" + t.id, TYPE_TRACK);
                            });

                            parent.addView(trackLayout);
                        }
                        scrollViewWrapper.addView(parent);
                    } catch (Exception e) {e.printStackTrace();}
                }

                @Override
                public void failure(RetrofitError error) {

                }
            });
        }
    }

    // TODO: Build search layout programtically

    private void showSongsForArtist(String id) {
        SpotifyApi api = new SpotifyApi();
        api.setAccessToken(getAccessToken());
        SpotifyService spotify = api.getService();
        spotify.getArtist(id, new Callback<Artist>() {
            @Override
            public void success(Artist artist, Response response) {
                spotify.getArtistAlbums(artist.id, new Callback<Pager<Album>>() {
                    @Override
                    public void success(Pager<Album> albumPager, Response response) {
                        clearLayout();
                        LinearLayout artistLayout = new LinearLayout(MainActivity.itself);
                        artistLayout.setOrientation(LinearLayout.VERTICAL);
                        for (AlbumSimple a : albumPager.items) {
                            if (a.album_type.equals("album") || a.album_type.equals("compilation"))
                            Log.d("Found album", a.name);
                            LinearLayout albumLayout = new LinearLayout(MainActivity.this);
                            albumLayout.setPadding(10, 0, 10, 0);
                            albumLayout.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                            albumLayout.setOrientation(LinearLayout.VERTICAL);

                            ImageView albumImage = new ImageView(MainActivity.this);
                            try {
                                setPictureFromUrl(albumImage, a.images.get(0).url);
                            } catch (Exception e) {
                                albumImage.setImageDrawable(getDrawable(android.R.drawable.ic_menu_agenda));
                            }
                            albumImage.setLayoutParams(new ViewGroup.LayoutParams(dpToPx(70), dpToPx(70)));
                            albumLayout.addView(albumImage);

                            TextView albumName = new TextView(MainActivity.this);
                            albumName.setText(a.name);
                            albumName.setLines(2);
                            albumLayout.addView(albumName);

                            albumLayout.setOnClickListener(v -> {
                                showSongsForAlbum(a.id);
                            });
                            artistLayout.addView(albumLayout);
                        }
                        ScrollView verticalScrollView = (ScrollView) findViewById(R.id.verticalscrollview);
                        verticalScrollView.addView(artistLayout);
                    }

                    private void setPictureFromUrl(ImageView albumImage, String url) {
                        new Thread(() -> {
                            try {
                                Bitmap x;

                                HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
                                connection.connect();

                                InputStream input = connection.getInputStream();

                                x = BitmapFactory.decodeStream(input);
                                Drawable drawable = new BitmapDrawable(x);
                                MainActivity.this.runOnUiThread(() -> albumImage.setImageDrawable(drawable));
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }).start();
                    }

                    @Override
                    public void failure(RetrofitError error) {

                    }
                });
            }

            @Override
            public void failure(RetrofitError error) {

            }
        });
    }

    private void showSongsForAlbum(String id) {
        SpotifyApi api = new SpotifyApi();
        api.setAccessToken(getAccessToken());
        SpotifyService spotify = api.getService();
        spotify.getAlbum(id, new Callback<Album>() {
            @Override
            public void success(Album album, Response response) {
                clearLayout();
                LinearLayout albumLayout = new LinearLayout(MainActivity.itself);
                albumLayout.setOrientation(LinearLayout.VERTICAL);
                for (TrackSimple t : album.tracks.items) {
                    Log.d("Found track", t.name);
                    LinearLayout trackLayout = new LinearLayout(MainActivity.this);
                    trackLayout.setPadding(20, 5, 20, 5);
                    trackLayout.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                    trackLayout.setOrientation(LinearLayout.HORIZONTAL);

                    TextView trackName = new TextView(MainActivity.this);
                    trackName.setText(t.name);
                    trackLayout.addView(trackName);

                    TextView trackDuration = new TextView(MainActivity.this);
                    int seconds = (int) t.duration_ms % 60000 / 1000;
                    if (seconds < 10) {
                        trackDuration.setText(t.duration_ms / 60000 + ":0" + seconds);
                    } else {
                        trackDuration.setText(t.duration_ms / 60000 + ":" + seconds);
                    }
                    trackDuration.setLayoutParams(new ViewGroup.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                    trackDuration.setGravity(Gravity.END);
                    trackLayout.addView(trackDuration);

                    trackLayout.setOnClickListener(v -> {
                        queue = new ArrayList<>();
                        int index = 0;
                        while (!album.tracks.items.get(index).name.equals(t.name) && index < album.tracks.items.size()){
                            index++;
                        }
                        index++;
                        for (int i = index; i < album.tracks.items.size(); i++){
                            queue.add(new Suggestion(album.tracks.items.get(i), -1f));
                        }
                        lastPlayed.add(new Suggestion(t, -1f));
                        playMusic("spotify:track:" + t.id, TYPE_TRACK);
                    });

                    albumLayout.addView(trackLayout);
                }
                ScrollView verticalScrollView = (ScrollView) findViewById(R.id.verticalscrollview);
                verticalScrollView.addView(albumLayout);
            }

            @Override
            public void failure(RetrofitError error) {

            }
        });
    }

    private void clearLayout() {
        ScrollView verticalScrollView = (ScrollView) findViewById(R.id.verticalscrollview);
        verticalScrollView.removeAllViews();
    }

    static MainActivity getContext() {
        return itself;
    }

    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.Button:
                // This button logs in the User into Spotify.
                if (isNetworkAvailable()) {
                    AuthenticationRequest.Builder builder = new AuthenticationRequest.Builder(CLIENT_ID,
                            AuthenticationResponse.Type.TOKEN,
                            REDIRECT_URI);
                    builder.setScopes(new String[]{"streaming"});
                    AuthenticationRequest request = builder.build();

                    //AuthenticationClient.openLoginActivity(this, REQUEST_CODE, request);
                    AuthenticationClient.openLoginInBrowser(this, request);
                }
                break;
            case R.id.Button3:
                if (isNetworkAvailable()) {
                    sendRecords();
                }
                break;
            case R.id.Button4:
                if (isNetworkAvailable()){
                    getSuggestion();
                }
                break;
            case R.id.Button5:
                askForIp();
                break;
            case R.id.Button6:
                askForAge(false);
                break;
            case R.id.Button7:
                askForGender(false);
                break;
            case R.id.search_text:
                EditText searchText = (EditText) view;
                searchText.setCursorVisible(true);
                break;
            case R.id.play_button:
                if (mPlayer.getPlaybackState().isPlaying) {
                    mPlayer.pause(new Player.OperationCallback() {
                        @Override
                        public void onSuccess() {
                            ImageView playButton = (ImageView) findViewById(R.id.play_button);
                            playButton.setImageDrawable(getDrawable(R.drawable.ic_action_name));
                        }

                        @Override
                        public void onError(Error error) {

                        }
                    });
                } else {
                    resumePlayMusic();
                }
                break;
            case R.id.skip_button:
                if (queue.size() > 0) {
                    playMusic("spotify:track:" + queue.get(0).track.id, TYPE_TRACK);
                    lastPlayed.add(queue.get(0));
                    queue.remove(0);
                } else {
                    mPlayer.pause(null);
                    mPlayer.seekToPosition(null, 0);
                    finishCurrentSong();
                }
                /*
                if (mPlayer.getPlaybackState().isPlaying) {
                    mPlayer.skipToNext(new Player.OperationCallback() {
                        @Override
                        public void onSuccess() {

                        }

                        @Override
                        public void onError(Error error) {
                        }
                    });
                } else {
                    mPlayer.skipToNext(new Player.OperationCallback() {
                        @Override
                        public void onSuccess() {
                            mPlayer.pause(new Player.OperationCallback() {
                                @Override
                                public void onSuccess() {
                                }

                                @Override
                                public void onError(Error error) {

                                }
                            });
                        }

                        @Override
                        public void onError(Error error) {

                        }
                    });
                }
                */
                break;
            case R.id.go_back_button:
                if (mPlayer.getPlaybackState().isPlaying) {
                    mPlayer.skipToPrevious(new Player.OperationCallback() {
                        @Override
                        public void onSuccess() {

                        }

                        @Override
                        public void onError(Error error) {
                            ImageView playButton = (ImageView) findViewById(R.id.play_button);
                            playButton.setImageDrawable(getDrawable(R.drawable.ic_action_name));
                        }
                    });
                } else {
                    mPlayer.skipToPrevious(new Player.OperationCallback() {
                        @Override
                        public void onSuccess() {
                            mPlayer.pause(new Player.OperationCallback() {
                                @Override
                                public void onSuccess() {
                                    ImageView playButton = (ImageView) findViewById(R.id.play_button);
                                    playButton.setImageDrawable(getDrawable(R.drawable.ic_action_name));
                                }

                                @Override
                                public void onError(Error error) {

                                }
                            });
                        }

                        @Override
                        public void onError(Error error) {
                            ImageView playButton = (ImageView) findViewById(R.id.play_button);
                            playButton.setImageDrawable(getDrawable(R.drawable.ic_action_name));
                        }
                    });
                }
                break;
            default:
                break;
        }
    }

    private void askForIp() {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);

        String REGEX_IPADDRESS = "((\\d+\\.){3}\\d+):(\\d+)";

        alert.setTitle("Please input the server IP");
        alert.setMessage("Please input the server IP in the form of XXX.XXX.XXX.XXX:PPPP");

// Set an EditText view to get user input
        final EditText input = new EditText(this);
        alert.setView(input);

        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                String value = input.getText().toString();
                // Do something with value!
                if(!value.matches(REGEX_IPADDRESS)) {
                    askForIp();
                } else{
                    SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(MainActivity.itself);
                    SharedPreferences.Editor edit = pref.edit();
                    edit.putString("ServerIp", value);
                    edit.apply();
                    makeToast("Server IP successfully set!");
                }
            }
        });

        alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                // Canceled.
            }
        });

        alert.show();

    }

    private void askForGender(boolean misused) {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);

        alert.setTitle("Please input your gender");
        alert.setMessage("Giving us your gender helps us improve our recommendations!");
        if (misused){
            alert.setTitle("Please input your gender");
            alert.setMessage("Please input yor gender as either male or female.");
        }

// Set an EditText view to get user input
        final EditText input = new EditText(this);
        alert.setView(input);

        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                String value = input.getText().toString();
                // Do something with value!
                if (!value.equals("male")&&!value.equals("female")){
                    askForGender(true);
                } else{
                    SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(MainActivity.itself);
                    SharedPreferences.Editor edit = pref.edit();
                    edit.putString("Gender", value);
                    edit.apply();
                }
            }
        });

        alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                // Canceled.
            }
        });

        alert.show();
    }

    private void askForAge(boolean misused) {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);

        alert.setTitle("Please input your age");
        alert.setMessage("Giving us your age helps us improve our recommendations!");
        if (misused) {
            alert.setTitle("Please input your age");
            alert.setMessage("Please input your age as a whole number.");
        }

// Set an EditText view to get user input
        final EditText input = new EditText(this);
        alert.setView(input);

        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                String value = input.getText().toString();
                // Do something with value!
                if (!isInteger(value)){
                    askForAge(true);
                } else{
                    SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(MainActivity.itself);
                    SharedPreferences.Editor edit = pref.edit();
                    edit.putInt("Age", Integer.valueOf(value));
                    edit.apply();
                }
            }
        });

        alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                // Canceled.
            }
        });

        alert.show();
    }

    public static boolean isInteger(String s) {
        return isInteger(s,10);
    }

    public static boolean isInteger(String s, int radix) {
        if(s.isEmpty()) return false;
        for(int i = 0; i < s.length(); i++) {
            if(i == 0 && s.charAt(i) == '-') {
                if(s.length() == 1) return false;
                else continue;
            }
            if(Character.digit(s.charAt(i),radix) < 0) return false;
        }
        return true;
    }

    private void getLocationType() {
        new RetrieveFeedTask().execute();
    }

    private int getLocationId() {
        try {
        Map<String, String> result = OSMWrapperAPI.getLocationType();
        if (result != null) {
            int locationId = 0;
            for (Object o : result.entrySet()) {
                Map.Entry entry = (Map.Entry) o;
                System.out.println("Location Tag ID:" + dictionary.checkForTagId(entry));
                locationId = dictionary.checkForTagId(entry);
                if (locationId != 0){
                    return locationId;
                }
            }
        }
        } catch (IOException | SAXException | ParserConfigurationException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public double getLatitude() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return 0.0;
        }
        try {
            return locationManager.getLastKnownLocation(provider).getLatitude();
        } catch (Exception e) {
            return 0.0;
        }
    }

    public double getLongitude() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return 0.0;
        }

        try {
            return locationManager.getLastKnownLocation(provider).getLongitude();
        } catch (Exception e) {
            return 0.0;
        }
    }

    @Override
    public void onLoggedIn() {
        Toast.makeText(MainActivity.this, "Logged in", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onLoggedOut() {
        Toast.makeText(MainActivity.this, "Logged out", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onLoginFailed(Error error) {
        Toast.makeText(MainActivity.this, "Login failed", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onTemporaryError() {

    }

    @Override
    public void onConnectionMessage(String s) {

    }

    public String getUserId() {
        return userId;
    }

    static class RetrieveFeedTask extends AsyncTask<Object, Object, Map<String, String>> {

        protected Map<String, String> doInBackground(Object... params) {
            Looper.prepare();
            try {
                return OSMWrapperAPI.getLocationType();
            } catch (IOException | SAXException | ParserConfigurationException e) {
                e.printStackTrace();
            }
            return null;
        }

        protected void onPostExecute(Map<String, String> result) {
            if (result != null) {
                for (Object o : result.entrySet()) {
                    Map.Entry entry = (Map.Entry) o;
                    System.out.println("Location Tag ID:" + dictionary.checkForTagId(entry));
                }
                System.out.println("result " + result);
            }
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        // Check if result comes from the correct activity
        if (requestCode == REQUEST_CODE) {
            AuthenticationResponse response = AuthenticationClient.getResponse(resultCode, intent);

            switch (response.getType()) {
                // Response was successful and contains auth token
                case TOKEN:
                    // Handle successful response
                    SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                    SharedPreferences.Editor edit = pref.edit();
                    edit.putString("AccessToken", accessToken);
                    edit.putInt("AccessToken_ExpirationSeconds", response.getExpiresIn());
                    edit.putInt("AccessToken_StorageTime", (int) Calendar.getInstance().getTimeInMillis() / 1000);
                    edit.apply();
                    buildPlayer();
                    break;

                // Auth flow returned an error
                case ERROR:
                    Log.d("Error message", response.getError());
                    Log.d("Error", response.toString());
                    // Handle error response
                    break;

                // Most likely auth flow was cancelled
                default:
                    // Handle other cases
            }
        }
    }

    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Uri uri = intent.getData();
        if (uri != null) {
            AuthenticationResponse response = AuthenticationResponse.fromUri(uri);
            switch (response.getType()) {
                // Response was successful and contains auth token
                case TOKEN:
                    // Handle successful response
                    accessToken = response.getAccessToken();
                    SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                    SharedPreferences.Editor edit = pref.edit();
                    edit.putString("AccessToken", accessToken);
                    edit.putInt("AccessToken_ExpirationSeconds", response.getExpiresIn());
                    edit.putInt("AccessToken_StorageTime", (int) Calendar.getInstance().getTimeInMillis() / 1000);
                    edit.apply();
                    new Thread(() -> {
                        SpotifyApi api = new SpotifyApi();
                        api.setAccessToken(accessToken);
                        SpotifyService spotify = api.getService();
                        userId = spotify.getMe().id;
                        edit.putString("UserId", userId);
                        edit.apply();
                    }).start();
                    buildPlayer();
                    break;

                // Auth flow returned an error
                case ERROR:
                    // Handle error response
                    break;

                // Most likely auth flow was cancelled
                default:
                    // Handle other cases
            }
        }
    }

    public static String getAccessToken() {
        return accessToken;
    }

    public void setLocationListener() {
        locationListener = new MyLocationListener();
        Log.d("locationlistener", locationListener.toString());
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Criteria criteria = new Criteria();
            criteria.setAccuracy(Criteria.ACCURACY_LOW);
            criteria.setPowerRequirement(Criteria.POWER_LOW);
            criteria.setAltitudeRequired(false);
            criteria.setBearingRequired(false);

            locationManager = (LocationManager) this.getSystemService(LOCATION_SERVICE);

            provider = locationManager.getBestProvider(criteria, true);

            // Cant get a hold of provider
            if (provider == null) {
                Log.v("LocationListener", "Provider is null");
                return;
            } else {
                Log.v("LocationListener", "Provider: " + provider);
            }

            locationListener = new MyLocationListener();

            locationManager.requestLocationUpdates(provider, 5000L, 5f, locationListener);

            // connect to the GPS location service
            Location oldLocation = locationManager.getLastKnownLocation(provider);

            if (oldLocation != null) {
                Log.v("LocationListener", "Got Old location");
                String latitude = Double.toString(oldLocation.getLatitude());
                String longitude = Double.toString(oldLocation.getLongitude());
            } else {
                Log.v("LocationListener", "NO Last Location found");
            }
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    MY_PERMISSIONS_REQUEST_ACCESS_LOCATION);
        }
    }

    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_ACCESS_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    setLocationListener();

                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

    private void constructDB() {
        mydb = new DBHelper(this);
        mydb.getWritableDatabase();
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    void playMusic(String s, String type) {
        System.out.println("Queue:");
        for (Suggestion sug: queue){
            System.out.println(sug.track.name + ": " + sug.suggestionValue);
        }
        if (mPlayer.getMetadata().contextUri == null || !mPlayer.getMetadata().contextUri.equals(s)) {
            Intent i = new Intent("com.android.music.musicservicecommand");
            i.putExtra("command", "pause");
            System.out.println("Now playing: " + s);
            sendBroadcast(i);
            switch (type) {
                case TYPE_PLAYLIST:
                    if (mPlayer != null) {
                        mPlayer.playUri(null, s, 0, 0);
                        // PlaylistFragment.itself.setPlaylistGreen(s);
                    }
                    break;
                default:
                    if (mPlayer != null) {
                        mPlayer.playUri(null, s, 0, 0);
                    }
                    break;
            }
        } else if (!mPlayer.getPlaybackState().isPlaying) {
            resumePlayMusic();
        }
    }

    private void resumePlayMusic() {
        if (mPlayer != null) {
            if (mPlayer.getMetadata().currentTrack != null) {
                mPlayer.resume(new Player.OperationCallback() {
                    @Override
                    public void onSuccess() {
                        Log.d("MainActivity", "Resumes playing");
                    }

                    @Override
                    public void onError(Error error) {
                        playMusic("spotify:User:1154572061:playlist:6MPgJeqV7uSo8oIZCdRnGp", TYPE_PLAYLIST);
                    }
                });
            } else {
                playMusic("spotify:User:1154572061:playlist:6MPgJeqV7uSo8oIZCdRnGp", TYPE_PLAYLIST);
            }
        }
    }

    @Override
    public void onPlaybackEvent(PlayerEvent playerEvent) {
        Log.d("MainActivity", "Playback event received: " + playerEvent.name());
        switch (playerEvent) {
            case kSpPlaybackNotifyTrackChanged:
                finishCurrentSong();
                Metadata metadata = mPlayer.getMetadata();
                Log.d("Started Track", metadata.toString());

                startSong(metadata);
                TextView songName = (TextView) findViewById(R.id.song_name);
                songName.setText(metadata.currentTrack.name);
                TextView artistName = (TextView) findViewById(R.id.artist_name);
                artistName.setText(metadata.currentTrack.artistName);
                try {
                    new DownloadImageTask().execute(new URL(metadata.currentTrack.albumCoverWebUrl));
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                }
                break;
            // Handle event type as necessary
            case kSpPlaybackNotifyPause:
                if (currentSong != null) {
                    currentSong.pause();
                    metadata = mPlayer.getMetadata();
                    Log.d("Paused Track", metadata.toString());
                }
                ImageView playButton = (ImageView) findViewById(R.id.play_button);
                playButton.setImageDrawable(getDrawable(R.drawable.ic_action_name));
                break;
            case kSpPlaybackNotifyTrackDelivered:
                if (queue.size()>0){
                    playMusic("spotify:track:" + queue.get(0).track.id, TYPE_TRACK);
                    lastPlayed.add(queue.get(0));
                    queue.remove(0);
                }
                break;
            case kSpPlaybackNotifyPlay:
                currentSong.play();
                playButton = (ImageView) findViewById(R.id.play_button);
                playButton.setImageDrawable(getDrawable(R.drawable.ic_pause_button));
                break;
            default:
                break;
        }
    }

    private class Song {

        String artistName;
        String artistUri;
        long durationMs;
        String name;
        String uri;
        ArrayList<Long> startTimes;
        ArrayList<Long> endTimes;
        boolean isPlaying = false;

        Song(Metadata.Track currentTrack) {
            artistName = currentTrack.artistName;
            artistUri = currentTrack.artistUri;
            durationMs = currentTrack.durationMs;
            name = currentTrack.name;
            uri = currentTrack.uri;
            startTimes = new ArrayList<>();
            endTimes = new ArrayList<>();
            play();
        }

        void play() {
            System.out.println("Play called");
            if (!isPlaying) {
                startTimes.add(GregorianCalendar.getInstance().getTimeInMillis() / 1000);
                isPlaying = true;
            }
        }

        void pause() {
            System.out.println("Pause called");
            if (isPlaying) {
                endTimes.add(GregorianCalendar.getInstance().getTimeInMillis() / 1000);
                isPlaying = false;
            }
        }

        long getPlaytime() {
            System.out.println("StartTimes:" + startTimes.toString());
            System.out.println("EndTimes:" + endTimes.toString());
            long time = 0;
            for (Long l : endTimes) {
                time += l;
            }
            for (Long l : startTimes) {
                time -= l;
            }
            return time;
        }
    }

    Song currentSong;

    private void startSong(Metadata metadata) {
        System.out.println("start called");
        currentSong = new Song(metadata.currentTrack);
    }

    private void finishCurrentSong() {
        System.out.println("finish called");
        try {
            currentSong.pause();
            float fractionListenedTo = ((float) currentSong.getPlaytime()) / ((float) currentSong.durationMs / 1000);
            System.out.println("Finished Track " + currentSong.name + " by " + currentSong.artistName + " after " + fractionListenedTo + " of the Duration");
            System.out.println("Suggestion value for track " + lastPlayed.get(lastPlayed.size()-1).track.name + " was " + lastPlayed.get(lastPlayed.size()-1).suggestionValue);
            stackSong(currentSong, fractionListenedTo, lastPlayed.get(lastPlayed.size()-1).suggestionValue);
            currentSong = null;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void stackSong(Song currentSong, float fractionListenedTo, float suggestionValue) {
        if (fractionListenedTo != 0.0) {
            new Thread(() -> {
                try {
                    SpotifyApi api = new SpotifyApi();
                    accessToken = PreferenceManager.getDefaultSharedPreferences(MainActivity.this).getString("AccessToken", "");
                    checkIfIdTokenStillValid();
                    if (!accessToken.equals("")) {
                        api.setAccessToken(PreferenceManager.getDefaultSharedPreferences(MainActivity.this).getString("AccessToken", ""));
                        SpotifyService spotify = api.getService();
                        String artistId = currentSong.artistUri.split(":")[2];
                        Artist artist = spotify.getArtist(artistId);
                        ArrayList<String> genres = (ArrayList<String>) artist.genres;
                        System.out.println(genres.toString());

                        int tagId = 0;
                        try {
                            Map<String, String> locationIds = OSMWrapperAPI.getLocationType();
                            if (locationIds != null) {
                                for (Object o : locationIds.entrySet()) {
                                    Map.Entry entry = (Map.Entry) o;
                                    tagId = dictionary.checkForTagId(entry);
                                    System.out.println("Location Tag ID:" + tagId);
                                }
                            }
                        } catch (Exception e){
                            tagId = 0;
                        }
                        System.out.println(tagId);

                        long timeInMillis = GregorianCalendar.getInstance().getTimeInMillis();
                        System.out.println(timeInMillis);

                        int age = PreferenceManager.getDefaultSharedPreferences(MainActivity.this).getInt("Age", 20);
                        System.out.println(age);

                        String gender = PreferenceManager.getDefaultSharedPreferences(MainActivity.this).getString("Gender", "female");
                        System.out.println(gender);

                        String trackId = currentSong.uri.split(":")[2];
                        AudioFeaturesTrack audioFeatures = spotify.getTrackAudioFeatures(trackId);
                        float danceability = audioFeatures.danceability;
                        float acousticness = audioFeatures.acousticness;
                        float energy = audioFeatures.energy;
                        float instrumentalness = audioFeatures.instrumentalness;
                        float liveness = audioFeatures.liveness;
                        float loudness = audioFeatures.loudness;
                        float speechiness = audioFeatures.speechiness;
                        float tempo = audioFeatures.tempo;
                        int timeSignature = audioFeatures.time_signature;
                        float valence = audioFeatures.valence;
                        int key = audioFeatures.key;
                        int mode = audioFeatures.mode;
                        int duration = audioFeatures.duration_ms;

                        Date now = GregorianCalendar.getInstance().getTime();
                        String month;
                        if (now.getMonth() < 10) {
                            month = "0" + now.getMonth();
                        } else {
                            month = String.valueOf(now.getMonth());
                        }
                        String hours;
                        if (now.getHours() < 10) {
                            hours = "0" + now.getHours();
                        } else {
                            hours = String.valueOf(now.getHours());
                        }
                        String timeString = month + " " + now.getDay() + " " + hours;
                        TrackRecord trackRecord = new TrackRecord(new UserRecord(getUserId(), age, gender), new ArtistRecord(artist.name, artistId, genres), new SongRecord(currentSong.name, trackId, danceability, energy, key, loudness, mode, speechiness, acousticness, instrumentalness, liveness, valence, tempo, duration, timeSignature), timeString, tagId, fractionListenedTo, suggestionValue);

                        if (mydb != null) {
                            mydb.insertTrackHistory(trackRecord);
                        }
                        // Quick check if there are any unsent records.
                        mydb.getUnsentTrackRecords();
                        sendRecords();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }

    private class DownloadImageTask extends AsyncTask<URL, Void, Bitmap> {
        ImageView bmImage;

        DownloadImageTask() {
            this.bmImage = (ImageView) findViewById(R.id.album_cover);
        }

        @Override
        protected Bitmap doInBackground(URL... params) {
            Bitmap mIcon11 = null;
            try {
                InputStream in = params[0].openStream();
                mIcon11 = BitmapFactory.decodeStream(in);
            } catch (Exception e) {
                Log.e("Error", e.getMessage());
                e.printStackTrace();
            }
            return mIcon11;
        }

        protected void onPostExecute(Bitmap result) {
            bmImage.setImageBitmap(result);
        }
    }

    @Override
    public void onPlaybackError(Error error) {
        Log.d("MainActivity", "Playback error received: " + error.name());
        switch (error) {
            // Handle error type as necessary
            default:
                break;
        }
    }

    static class TrackRecord {
        UserRecord User;
        ArtistRecord Artist;
        SongRecord Track;
        String Time;
        int LocationId;
        float Weight;
        float SuggestionValue;

        TrackRecord(UserRecord user, ArtistRecord artist, SongRecord Track, String time, int locationId, float weight, float suggestionValue) {
            this.User = user;
            this.Artist = artist;
            this.Track = Track;
            this.Time = time;
            this.LocationId = locationId;
            this.Weight = weight;
            this.SuggestionValue = suggestionValue;
        }
    }

    static class UserRecord {
        int Age;
        String UserId;
        String Gender;

        UserRecord(String UserId, int age, String gender) {
            this.UserId = UserId;
            this.Age = age;
            this.Gender = gender;
        }
    }

    static class ArtistRecord {
        String Name;
        String Id;
        List<String> Genres;

        ArtistRecord(String name, String id, List<String> genres) {
            this.Name = name;
            this.Id = id;
            this.Genres = genres;
        }
    }

    static class SongRecord {
        String Name;
        String Id;
        float Danceability;
        float Energy;
        int Key;
        float Loudness;
        int Mode;
        float Speechiness;
        float Acousticness;
        float Instrumentalness;
        float Liveness;
        float Valence;
        float Tempo;
        int Duration;
        int Time_Signature;

        SongRecord(String name, String id, float danceability, float energy, int key, float loudness, int mode, float speechiness, float acousticness, float instrumentalness, float liveness, float valence, float Tempo, int duration, int Time_Signature) {
            this.Acousticness = acousticness;
            this.Danceability = danceability;
            this.Duration = duration;
            this.Energy = energy;
            this.Id = id;
            this.Instrumentalness = instrumentalness;
            this.Key = key;
            this.Liveness = liveness;
            this.Loudness = loudness;
            this.Mode = mode;
            this.Name = name;
            this.Speechiness = speechiness;
            this.Tempo = Tempo;
            this.Time_Signature = Time_Signature;
            this.Valence = valence;
        }
    }

    void sendRecords() {
        new Thread(() -> {
            try {
                System.out.println("Building the http request");
                SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(MainActivity.itself);
                String serverAddress = "http://" + pref.getString("ServerIp", getString(R.string.home_server_url)) + "/send_records";
                URL url = new URL(serverAddress);
                SendTracksObject sendTracksObject = buildRecordsJson();
                JSONArray listOfRecords = sendTracksObject.listOfRecordsJsonArray;
                JSONObject sendToServerObject = new JSONObject();
                sendToServerObject.put("Track_History", listOfRecords);

                //String messageToServer = sendToServerObject.toString().replaceAll("\/", "\\\/");

                System.out.println("Sending: " + sendToServerObject.toString());
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                JsonParser jp = new JsonParser();
                JsonElement je = jp.parse(sendToServerObject.toString());
                String prettyJsonString = gson.toJson(je);
                System.out.println(prettyJsonString);

                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("Accept", "*/*");
                conn.setRequestProperty("Cache-Control", "no-cache");
                conn.setRequestProperty("Accept-Encoding", "gzip, deflate");
                conn.setDoOutput(true);
                conn.setDoInput(true);

                conn.connect();

                String toSendString = Normalizer.normalize(sendToServerObject.toString(), Normalizer.Form.NFD).replaceAll("[^\\p{ASCII}]", "");
                toSendString = toSendString.replace("&", "");

                System.out.println(toSendString);

                DataOutputStream os = new DataOutputStream(conn.getOutputStream());
                os.writeBytes(toSendString);

                os.flush();
                os.close();

                int responseCode = conn.getResponseCode();
                String responseMessage = conn.getResponseMessage();

                System.out.println(responseCode + ": " + responseMessage);

                if (responseCode == 201) {
                    mydb.updateTrackRecords(sendTracksObject.listOfIdsOfUnsentRecords);
                } else {
                    System.out.println(convertStreamToString(conn.getErrorStream()));
                }

                conn.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private String convertStreamToString(InputStream is) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();

        String line = null;
        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return sb.toString();
    }

    class SendTracksObject {
        JSONArray listOfRecordsJsonArray;
        List<Integer> listOfIdsOfUnsentRecords;

        SendTracksObject(JSONArray listOfRecordsJsonArray, List<Integer> listOfIdsOfUnsentRecords) {
            this.listOfRecordsJsonArray = listOfRecordsJsonArray;
            this.listOfIdsOfUnsentRecords = listOfIdsOfUnsentRecords;
        }
    }

    private SendTracksObject buildRecordsJson() {
        System.out.println("Building the json object");
        JSONArray listOfRecordsJsonArray = new JSONArray();
        List<TrackRecord> listOfRecordsArrayList = mydb.getUnsentTrackRecords();
        List<Integer> listOfIdsOfUnsentRecords = mydb.getUnsentTrackRecordsIds();
        try {
            for (TrackRecord t : listOfRecordsArrayList) {
                System.out.println("Found TrackRecord:");
                Gson gson = new Gson();
                JSONObject recordJson = new JSONObject(gson.toJson(t));
                listOfRecordsJsonArray.put(recordJson);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        SendTracksObject sendTracksObject = new SendTracksObject(listOfRecordsJsonArray, listOfIdsOfUnsentRecords);
        return sendTracksObject;
    }

    private void getSuggestion() {
        new Thread(() -> {
            HttpResponse response = null;
            try {
                // Ask for recommendations
                HttpClient client = new DefaultHttpClient();
                HttpGet request = new HttpGet();
                accessToken = PreferenceManager.getDefaultSharedPreferences(MainActivity.this).getString("AccessToken", "");
                SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(MainActivity.itself);
                String serverAddress = "http://" + pref.getString("ServerIp", getString(R.string.home_server_url)) + "/get_recommendations/";
                request.setURI(new URI(serverAddress + getUserId() + "?location_id=" + getLocationId() + "&day=" + getDay() + "&hours=" + getHours() + "&month=" + getMonth() + "&accesstoken=" + accessToken));
                System.out.println(request.getURI());
                response = client.execute(request);
                String responseContent = EntityUtils.toString(response.getEntity());
                System.out.println(responseContent);
                try {
                    // Interpret the result
                    JSONObject jobj = new JSONObject(responseContent);
                    System.out.println(jobj.toString());
                    JSONArray suggestions = jobj.getJSONArray("records");
                    // Add each suggestion to the queue
                    queue = new ArrayList<>();
                    for (int i = 0; i < suggestions.length(); i++) {
                        JSONObject recommendation = suggestions.getJSONObject(i);
                        String id = recommendation.getString("id");
                        float suggestionValue = recommendation.getInt("suggestion_value");
                        System.out.println(id + ": " + suggestionValue);
                        SpotifyApi api = new SpotifyApi();
                        accessToken = PreferenceManager.getDefaultSharedPreferences(MainActivity.this).getString("AccessToken", "");
                        if (!accessToken.equals("")) {
                            api.setAccessToken(PreferenceManager.getDefaultSharedPreferences(MainActivity.this).getString("AccessToken", ""));
                            SpotifyService spotify = api.getService();
                            queue.add(new Suggestion(spotify.getTrack(id), suggestionValue));
                        }
                    }
                    // Shuffling to sprinkle in some stuff here and there
                    Collections.shuffle(queue);
                    // Start the queue
                    if (queue.size() > 0) {
                        playMusic("spotify:track:" + queue.get(0).track.id, TYPE_TRACK);
                        lastPlayed.add(queue.get(0));
                        System.out.println(queue.toString());
                        queue.remove(0);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                    makeToast("The server seems to be talking gibberish...");
                }
            } catch (URISyntaxException | IOException e) {
                e.printStackTrace();
                makeToast("Whoops! The server seems to be down.");
            }
        }).start();
    }

    private void makeToast(String s) {
        this.runOnUiThread(() ->
            Toast.makeText(getApplicationContext(), s,
                    Toast.LENGTH_SHORT).show());
    }
}
