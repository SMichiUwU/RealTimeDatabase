package com.example.googlemaps1;

import static com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Looper;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.slider.Slider;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final int REQ_LOCATION = 2001;
    private static final int REQ_RESOLUTION = 3001;

    private GoogleMap mapa;
    private Marker marker;         // marcador rojo
    private Circle circulo;        // círculo de radio
    private EditText txtLatitud, txtLongitud;
    private Slider sliderRadio;

    // ubicación
    private FusedLocationProviderClient fused;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;

    // Firebase -> /Coordenadas/{latitud,longitud}
    private final DatabaseReference coordsRef = FirebaseDatabase.getInstance()
            .getReference()
            .child("Coordenadas");

    // estado
    private double lastLat = -1.013000, lastLng = -79.469000;
    private float radio = 5f; // en "unidades" del slider

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txtLatitud  = findViewById(R.id.txt_Latitud);
        txtLongitud = findViewById(R.id.txt_Longitud);
        sliderRadio = findViewById(R.id.sliderRadio);

        sliderRadio.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override public void onStartTrackingTouch(@NonNull Slider slider) { }
            @Override public void onStopTrackingTouch(@NonNull Slider slider) {
                radio = slider.getValue();
              //  dibujarCirculo();
            }
        });

        // Mapa
        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        // Ubicación (API clásica)
        fused = LocationServices.getFusedLocationProviderClient(this);
        locationRequest = LocationRequest.create()
                .setPriority(PRIORITY_HIGH_ACCURACY)
                .setInterval(5000)
                .setFastestInterval(3000);

        // Callback de ubicación: UI + marcador local + escritura Firebase
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                if (result.getLastLocation() == null) return;

                double lat = result.getLastLocation().getLatitude();
                double lng = result.getLastLocation().getLongitude();
                lastLat = lat; lastLng = lng;

                // UI
                txtLatitud.setText(String.format("%.6f", lat));
                txtLongitud.setText(String.format("%.6f", lng));

                // marcador (fallback local)
                LatLng pos = new LatLng(lat, lng);
                if (marker == null) {
                    marker = mapa.addMarker(new MarkerOptions().position(pos).title("Mi ubicación"));
                    mapa.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 16f));
                } else {
                    marker.setPosition(pos);
                    mapa.animateCamera(CameraUpdateFactory.newLatLng(pos));
                }

                // círculo
               // dibujarCirculo();

                // escritura en Firebase
                coordsRef.child("latitud").setValue(lat);
                coordsRef.child("longitud").setValue(lng);
            }
        };

        // Listener RTDB: mueve el marcador cuando cambie en Firebase
        coordsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snap) {
                Double lat = snap.child("latitud").getValue(Double.class);
                Double lng = snap.child("longitud").getValue(Double.class);
                if (lat == null || lng == null || mapa == null) return;

                lastLat = lat; lastLng = lng;
                LatLng pos = new LatLng(lat, lng);
                if (marker == null) {
                    marker = mapa.addMarker(new MarkerOptions().position(pos).title("Ubicación (RTDB)"));
                    mapa.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 16f));
                } else {
                    marker.setPosition(pos);
                    mapa.animateCamera(CameraUpdateFactory.newLatLng(pos));
                }
              //  dibujarCirculo();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mapa = googleMap;
        mapa.setMapType(GoogleMap.MAP_TYPE_HYBRID);  // como tu ejemplo
        mapa.getUiSettings().setZoomControlsEnabled(true);

        // fallback visual
        LatLng quevedo = new LatLng(-1.013000, -79.469000);
        mapa.moveCamera(CameraUpdateFactory.newLatLngZoom(quevedo, 14f));

        if (hasLocationPermission()) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                mapa.setMyLocationEnabled(true);
            }
        }
        ensureLocationUpdates();
    }

    @Override
    protected void onStart() {
        super.onStart();
        ensureLocationUpdates();
    }

    @Override
    protected void onStop() {
        super.onStop();
        fused.removeLocationUpdates(locationCallback);
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void ensureLocationUpdates() {
        if (!hasLocationPermission()) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQ_LOCATION
            );
            return;
        }
        if (mapa != null &&
                (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
            mapa.setMyLocationEnabled(true);
        }
        startLocationWithSettingsCheck();
    }

    private void startLocationWithSettingsCheck() {
        LocationSettingsRequest.Builder builder =
                new LocationSettingsRequest.Builder().addLocationRequest(locationRequest).setAlwaysShow(true);

        SettingsClient client = LocationServices.getSettingsClient(this);
        Task<LocationSettingsResponse> task = client.checkLocationSettings(builder.build());

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        task.addOnSuccessListener(locationSettingsResponse ->
                fused.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        );

        task.addOnFailureListener(e -> {
            if (e instanceof ResolvableApiException) {
                try { ((ResolvableApiException) e).startResolutionForResult(MainActivity.this, REQ_RESOLUTION); }
                catch (Exception ignored) {}
            }
        });
    }

   /* private void dibujarCirculo() {
        if (mapa == null) return;
        if (circulo != null) {
            circulo.remove();
            circulo = null;
        }
        // radio del slider en metros (x100 como tu práctica)
        float metros = radio * 100f;
        circulo = mapa.addCircle(new CircleOptions()
                .center(new LatLng(lastLat, lastLng))
                .radius(metros)
                .strokeColor(Color.RED)
                .fillColor(Color.argb(50, 150, 50, 50)));
    }*/

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] perms,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, perms, grantResults);
        if (requestCode == REQ_LOCATION && hasLocationPermission()) {
            ensureLocationUpdates();
        }
    }
}