package com.example.scheaman.rapidtracker;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.GenericTypeIndicator;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private static final int PERMISSIONS_REQUEST = 1;

    // Firebase DB
    private String dbPath;

    // UI related
    private TextView mDistanceView;
    private GoogleMap mMap;

    private FirebaseAuth mAuth;
    private LatLng current = null;
    private LatLng previous = null;
    private double distanceTravelled;
    private boolean oldLocation = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();
        dbPath = mAuth.getUid() + "/";

        setContentView(R.layout.activity_maps);
        mDistanceView = findViewById(R.id.distance_content);
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference(dbPath);

        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if(snapshot.child("distanceTravelled").exists())
                    distanceTravelled = snapshot.child("distanceTravelled").getValue(Double.class);
                else
                    distanceTravelled = 0;

            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
            }
        });

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // Check GPS is enabled
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Toast.makeText(this, "Please enable location services", Toast.LENGTH_SHORT).show();
            finish();
        }

        // Check location permission is granted - if it is, start
        // the service, otherwise request the permission
        int permission = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST);
        } else {
            requestLocationUpdates();
        }

        Button mDistanceResetButton = findViewById(R.id.clear_button);
        mDistanceResetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                resetDistance();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    requestLocationUpdates();
                } else {
                    Toast.makeText(this, "Please allow permission for location services", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }
        }
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setScrollGesturesEnabled(false);
        mMap.getUiSettings().setZoomGesturesEnabled(false);
        final String path = dbPath + "location";

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference(path);

        ref.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.getValue() != null) {
                    // This method is called once with the initial value and again
                    // whenever data at this location is updated.
                    GenericTypeIndicator<HashMap<String,Object>> t = new GenericTypeIndicator<HashMap<String,Object>>() {};
                    HashMap<String,Object> location = dataSnapshot.getValue(t);
                    Log.d("Retrieved", "Value is: " + location);

                    // Add a marker and move the camera
                    Double lat = (Double)location.get("latitude");
                    Double lng = (Double)location.get("longitude");
                    previous = current;
                    current = new LatLng(lat, lng);
                    if (previous != null) calculateDistance();

                    mMap.clear();
                    mMap.addMarker(new MarkerOptions().position(current).title("ME"));
                    mMap.moveCamera(CameraUpdateFactory.newLatLng(current));
                    mMap.animateCamera(CameraUpdateFactory.zoomTo(19.0f));
                }
                oldLocation = true;
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                // Ignore
            }
        });
    }

    private void requestLocationUpdates() {
        final String path = dbPath + "location";
        int permission = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION);
        if (permission == PackageManager.PERMISSION_GRANTED) {
            // Request location updates and when an update is
            // received, store the location in Firebase
            LocationRequest request = new LocationRequest();
            request.setInterval(2000);
            request.setFastestInterval(1000);
            request.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
            FusedLocationProviderClient client = LocationServices.getFusedLocationProviderClient(this);
            client.requestLocationUpdates(request, new LocationCallback() {
                @Override
                public void onLocationResult(LocationResult locationResult) {
                    DatabaseReference ref = FirebaseDatabase.getInstance().getReference(path);
                    Location location = locationResult.getLastLocation();
                    if (location != null) {
                        if (oldLocation) { // Workaround as old location get replaced before calculateDistance() could be run
                            Log.d("Location", "location update " + location);
                            ref.setValue(location);
                        }
                        else mDistanceView.setText("Loading from last time ...");
                    }
                }
            }, null);
        }
    }

    /**
     * Calculate Distance Travelled
     */
    private void calculateDistance() {
        final int R = 6371;

        double prevLat = previous.latitude;
        double prevLong = previous.longitude;
        double currLat = current.latitude;
        double currLong = current.longitude;

        double latDistance = Math.toRadians(currLat - prevLat);
        double longDistance = Math.toRadians(currLong - prevLong);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(prevLat)) * Math.cos(Math.toRadians(currLat))
                * Math.sin(longDistance / 2) * Math.sin(longDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        double distance = R * c * 1000;

        distance = Math.sqrt(Math.pow(distance, 2) + Math.pow(0.0, 2));

        distanceTravelled += distance;
        updateDistance();
    }

    /**
     * Update Distance Travelled
     */
    private void updateDistance() {
        final String path = dbPath + "distanceTravelled";
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference(path);
        ref.setValue(distanceTravelled);
        String displayText = String.format("Distance travelled: %d", Math.round(distanceTravelled)) + "m";
        mDistanceView.setText(displayText);
    }

    /**
     * Renew Distance Travelled
     */
    private void resetDistance() {
        distanceTravelled = 0;
        updateDistance();
    }
}