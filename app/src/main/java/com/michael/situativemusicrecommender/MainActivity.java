package com.michael.situativemusicrecommender;

import android.Manifest;
import android.content.Context;
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
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

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

import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;

import kaaes.spotify.webapi.android.SpotifyApi;
import kaaes.spotify.webapi.android.SpotifyService;
import kaaes.spotify.webapi.android.models.AlbumSimple;
import kaaes.spotify.webapi.android.models.AlbumsPager;
import kaaes.spotify.webapi.android.models.Artist;
import kaaes.spotify.webapi.android.models.ArtistsPager;
import kaaes.spotify.webapi.android.models.Track;
import kaaes.spotify.webapi.android.models.TracksPager;
import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

public class MainActivity extends AppCompatActivity implements
        SpotifyPlayer.NotificationCallback, ConnectionStateCallback {

    private static final int MY_PERMISSIONS_REQUEST_ACCESS_LOCATION = 42;
    private static final String CLIENT_ID = "deaa15968cb84bf09fdd25034b520033";
    RecommendationBuilder recommendationBuilder;
    private LocationManager locationManager;
    private LocationListener locationListener;
    public static final String TYPE_PLAYLIST = "Playlist";
    private static final String TYPE_TRACK = "Track";
    private static final int REQUEST_CODE = 1337;
    private String provider;
    static String accessToken;
    private Player mPlayer;
    static MainActivity itself;

    public static final String REDIRECT_URI = "michael-situativemusicrecommender://callback";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        itself = this;
        constructDB();

        checkIfIdTokenStillValid();

        prepLayout();

        recommendationBuilder = new RecommendationBuilder();
        setLocationListener();
    }

    private void checkIfIdTokenStillValid() {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        int initialStorageTime = pref.getInt("AccessToken_StorageTime", 0);
        int currentTime = (int) Calendar.getInstance().getTimeInMillis() / 1000;
        int expirationTime = pref.getInt("AccessToken_ExpirationSeconds", 0);
        if (initialStorageTime + expirationTime < currentTime - 1000) {
            if (isNetworkAvailable()) {
                AuthenticationRequest.Builder builder = new AuthenticationRequest.Builder(CLIENT_ID,
                        AuthenticationResponse.Type.TOKEN,
                        REDIRECT_URI);
                builder.setScopes(new String[]{"user-read-private", "streaming"});
                AuthenticationRequest request = builder.build();

                //AuthenticationClient.openLoginActivity(this, REQUEST_CODE, request);
                AuthenticationClient.openLoginInBrowser(this, request);
            }
        } else {
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
    }

    private void prepLayout() {
        final EditText edittext = (EditText) findViewById(R.id.search_text);
        edittext.setSingleLine(true);
        edittext.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                // If the event is a key-down event on the "enter" button
                if ((event.getAction() == KeyEvent.ACTION_DOWN) &&
                        (keyCode == KeyEvent.KEYCODE_ENTER)) {
                    // Perform action on key press
                    Toast.makeText(MainActivity.this, edittext.getText(), Toast.LENGTH_SHORT).show();
                    edittext.clearFocus();
                    edittext.setCursorVisible(false);
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(edittext.getWindowToken(), 0);
                    search(edittext.getText().toString());
                    return true;
                }
                return false;
            }
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
            spotify.searchArtists(text, new Callback<ArtistsPager>() {
                @Override
                public void success(ArtistsPager artistsPager, Response response) {
                    LinearLayout parent = (LinearLayout) findViewById(R.id.artists_layout);
                    parent.removeAllViews();
                    for (Artist a : artistsPager.artists.items) {
                        Log.d("Found artist", a.name);
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

                        parent.addView(artistLayout);
                    }
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
                    LinearLayout parent = (LinearLayout) findViewById(R.id.albums_layout);
                    parent.removeAllViews();
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

                        parent.addView(albumLayout);
                    }

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
                    LinearLayout parent = (LinearLayout) findViewById(R.id.tracks_layout);
                    parent.removeAllViews();
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

                        trackLayout.setOnClickListener(v -> playMusic("spotify:track:" + t.id, TYPE_TRACK));

                        parent.addView(trackLayout);
                    }

                }

                @Override
                public void failure(RetrofitError error) {

                }
            });
        }
    }

    static MainActivity getContext() {
        return itself;
    }

    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.Button:
                // This button logs in the user into Spotify.
                if (isNetworkAvailable()) {
                    AuthenticationRequest.Builder builder = new AuthenticationRequest.Builder(CLIENT_ID,
                            AuthenticationResponse.Type.TOKEN,
                            REDIRECT_URI);
                    builder.setScopes(new String[]{"user-read-private", "streaming"});
                    AuthenticationRequest request = builder.build();

                    //AuthenticationClient.openLoginActivity(this, REQUEST_CODE, request);
                    AuthenticationClient.openLoginInBrowser(this, request);
                }
                break;
            case R.id.Button3:
                if (isNetworkAvailable()) {
                    printLocation();
                }
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

                        }
                    });
                }
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

    private void printLocation() {
        new RetrieveFeedTask().execute();
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

    static class RetrieveFeedTask extends AsyncTask<Object, Object, Map<String, String>> {

        private Exception exception;

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
                Iterator it = result.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry entry = (Map.Entry) it.next();
                    System.out.println("Location Tag ID:" + dictionary.checkForTagId(entry));
                }
                // TODO: check this.exception
                // TODO: do something with the feed
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
                    accessToken = response.getAccessToken();
                    Config playerConfig = new Config(this, response.getAccessToken(), CLIENT_ID);
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
                    Config playerConfig = new Config(this, response.getAccessToken(), CLIENT_ID);
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

    void printResults(Map<String, RecommendationBuilder.ArtistRecommendation> recommendationsMap) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final ListView listview = (ListView) findViewById(R.id.listview);
                ArrayList<RecommendationBuilder.ArtistRecommendation> recommendations = new ArrayList<>(recommendationsMap.values());
                recommendations.sort(new RecommendationBuilder.ArtistRecommendationComparator());
                ArtistArrayAdapter adapter = new ArtistArrayAdapter(getContext(), recommendations);
                listview.setAdapter(adapter);
                adapter.notifyDataSetChanged();
            }
        });
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

    private class ArtistArrayAdapter extends ArrayAdapter<RecommendationBuilder.ArtistRecommendation> {

        private final Context context;

        public ArtistArrayAdapter(Context context, List<RecommendationBuilder.ArtistRecommendation> objects) {
            super(context, -1, objects);
            this.context = context;
        }


        public String getArtistId(int position) {
            return getItem(position).artistID;

        }

        public String getArtistName(int position) {
            return getItem(position).artistName;
        }

        public String getArtistWeight(int position) {
            return String.valueOf(getItem(position).weight);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            LayoutInflater inflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View rowView = inflater.inflate(R.layout.artist_list_element, parent, false);
            TextView firstLine = (TextView) rowView.findViewById(R.id.firstLine);
            TextView secondLine = (TextView) rowView.findViewById(R.id.secondLine);
            ImageView imageView = (ImageView) rowView.findViewById(R.id.icon);
            firstLine.setText(getArtistName(position));
            secondLine.setText("Weight: " + getArtistWeight(position));

            return rowView;
        }
    }

    private void constructDB() {
        DBHelper mydb = new DBHelper(this);
        Calendar cal = new GregorianCalendar();
        cal.set(2017, 8, 17);
        Date date = cal.getTime();
        mydb.insertTrackHistory("Kalmah", "2YPVtFn6SsYNntkmrdDpGF", "3oNkd3uj2phyrtZCmvtN6c", 1, 1f, date);
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    void playMusic(String s, String type) {
        if (mPlayer.getMetadata().contextUri == null || !mPlayer.getMetadata().contextUri.equals(s)) {
            Intent i = new Intent("com.android.music.musicservicecommand");
            i.putExtra("command", "pause");
            sendBroadcast(i);
            switch (type) {
                case TYPE_PLAYLIST:
                    if (mPlayer != null) {
                        mPlayer.playUri(null, s, 0, 0);
                        ImageView playButton = (ImageView) findViewById(R.id.play_button);
                        playButton.setImageDrawable(getDrawable(R.drawable.ic_pause_button));
                        // PlaylistFragment.itself.setPlaylistGreen(s);
                    }
                    break;
                default:
                    if (mPlayer != null) {
                        mPlayer.playUri(null, s, 0, 0);
                        ImageView playButton = (ImageView) findViewById(R.id.play_button);
                        playButton.setImageDrawable(getDrawable(R.drawable.ic_pause_button));
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
                        ImageView playButton = (ImageView) findViewById(R.id.play_button);
                        playButton.setImageDrawable(getDrawable(R.drawable.ic_pause_button));
                    }

                    @Override
                    public void onError(Error error) {
                        playMusic("spotify:user:1154572061:playlist:6MPgJeqV7uSo8oIZCdRnGp", TYPE_PLAYLIST);
                    }
                });
            } else {
                playMusic("spotify:user:1154572061:playlist:6MPgJeqV7uSo8oIZCdRnGp", TYPE_PLAYLIST);
            }
        }
    }

    @Override
    public void onPlaybackEvent(PlayerEvent playerEvent) {
        Log.d("MainActivity", "Playback event received: " + playerEvent.name());
        switch (playerEvent) {
            case kSpPlaybackNotifyTrackChanged:
                Metadata metadata = mPlayer.getMetadata();
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
            default:
                break;
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
}
