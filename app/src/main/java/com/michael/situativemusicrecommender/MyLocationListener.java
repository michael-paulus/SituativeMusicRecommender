package com.michael.situativemusicrecommender;

import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import static android.content.ContentValues.TAG;

/**
 * Created by Michael on 25.09.2017.
 */

class MyLocationListener implements LocationListener {

    double longitude;
    double latitude;

    @Override
    public void onLocationChanged(Location loc) {
        Log.d("Location", "changed");
        longitude = loc.getLongitude();
        latitude = loc.getLatitude();
    }

    @Override
    public void onProviderDisabled(String provider) {}

    @Override
    public void onProviderEnabled(String provider) {}

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}
}
