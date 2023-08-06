package com.bacar.trackat_android;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.util.Log;
import android.view.WindowManager;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.bottomsheet.BottomSheetBehavior;


public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap googleMap;
    private Handler handler;

    private ListView petList;
    private TextView locationsTextView;
    private String locationsString = "";

    private Polyline historicalPath;

    private CardView mainMenu;

    private Menu currentMenu;

    private Marker locationMarker;
    private LatLng currentLocation;

    private Map<Date,LatLng> locations;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);



        mainMenu = findViewById(R.id.sheet);

        //  Set sliding menu
        BottomSheetBehavior<View> bottomSheetBehavior = BottomSheetBehavior.from(mainMenu);
        bottomSheetBehavior.setPeekHeight(180);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);

        mainMenu.setBackgroundResource(R.drawable.rounded);

        currentMenu = Menu.CURRENT_LOCATION;

        locations = new HashMap<>();

//        locations.put(new Date(2023, 07, 24), new LatLng(45.55463869883398, -73.55484081280571));
//        locations.put(new Date(2023, 07, 25), new LatLng(45.56474555250344, -73.55466037601063));
//        locations.put(new Date(2023, 07, 26), new LatLng(45.56909094068752, -73.56628050563006));


        //  List of pet trackers
        petList = findViewById(R.id.pet_list);

        String[] devices = {"Cat 1"};
        ArrayAdapter<String> deviceAdapter = new ArrayAdapter<>(this, androidx.appcompat.R.layout.support_simple_spinner_dropdown_item, devices);
        petList.setAdapter(deviceAdapter);


        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map_fragment);
        mapFragment.getMapAsync(this);

        ImageButton geofencesButton = findViewById(R.id.geofences_button);
        geofencesButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Handle the click on the Geofences ImageButton
                Intent intent = new Intent(MainActivity.this, GeofencingActivity.class);
                startActivity(intent);
            }
        });

        handler = new Handler(Looper.getMainLooper());
        handler.post(periodicRequest);



    }
    public void onMapReady(GoogleMap googleMap) {

        this.googleMap = googleMap;

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Request the missing permissions
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
        } else {
            // Permission already granted, proceed with displaying the current location
            googleMap.setMyLocationEnabled(true);
        }

//        LatLng latLng = new LatLng(45.56762289016064, -73.55580278337429);
//        MarkerOptions markerOptions = new MarkerOptions()
//                .position(latLng)
//                .title("Cat 1")
//                .snippet("Last seen 1 minute ago");
//        this.googleMap.addMarker(markerOptions);


        displayPath(true);

    }

    private Runnable periodicRequest = new Runnable() {
        @Override
        public void run() {
            getLocations();
            updateLocationMarker();
            if(currentLocation != null){
                handler.postDelayed(this, 30000);
            }else{
                handler.postDelayed(this, 1000);
            }

        }
    };


    private void updateLocationMarker(){
        if(currentLocation != null){
            if(locationMarker == null){
                locationMarker = this.googleMap.addMarker(new MarkerOptions()
                        .position(currentLocation)
                        .title("Cat"));

                this.googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation,15));
            }else{
                locationMarker.setPosition(currentLocation);
            }
        }

    }


    private void displayPath(boolean display){
        List<Map.Entry<Date, LatLng>> entriesList = new ArrayList<>(locations.entrySet());
        List<LatLng> path = new ArrayList<>();

        Collections.sort(entriesList, new Comparator<Map.Entry<Date, LatLng>>() {
            @Override
            public int compare(Map.Entry<Date, LatLng> entry1, Map.Entry<Date, LatLng> entry2) {
                return entry2.getKey().compareTo(entry1.getKey()); // Reverse order for most recent first
            }
        });

        for (Map.Entry<Date, LatLng> entry : entriesList) {
            Date date = entry.getKey();
            LatLng latLng = entry.getValue();

            path.add(latLng);
        }

        PolylineOptions line = new PolylineOptions();

        for (int i = 0; i < path.size();i++){
            line.add(path.get(i));
        }

        line.width(10);
        line.color(Color.BLACK);

        this.googleMap.addPolyline(line);


    }



    private void test(){
        MarkerOptions m = new MarkerOptions();
        m.position(new LatLng(45.564428039100235, -73.55155886115642));
        m.title("Failes");
        googleMap.addMarker(m);
    }

    /**
     * Get from the server all the locations
     */
    private void getLocations(){
        OkHttpClient client = new OkHttpClient();


        String loc_url = "http://82.180.161.23/locations";

        Request request = new Request.Builder()
                .url(loc_url)
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
//                locationsTextView.setText("Failed to load Locations");
                Log.i("Failse", "HTTP fail");
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if(response.isSuccessful()){
                    String locationResponse = response.body().string();
                    locationsString = "";
                    try {
                        JSONObject jsonLocations = new JSONObject(locationResponse);
                        for (int i = jsonLocations.length(); i > 0; i--){
                            JSONObject location = jsonLocations.getJSONObject(String.valueOf(i));
                            String dateStr = location.getString("date");
                            SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);
                            Date date = sdf.parse(dateStr);
                            double latitude = location.getDouble("latitude");
                            double longitude = location.getDouble("longitude");

                            locations.put(date, new LatLng(latitude,longitude));
                            Log.i("Position", "Lat:"+latitude+", Long:"+longitude);

                            if (i == jsonLocations.length()){
                                currentLocation = new LatLng(latitude,longitude);
//
//                                runOnUiThread(()->{
//                                    locationsString = "Current Location\n\n" + latitude + ", " + longitude +"\n\nPast locations:\n";
//                                    MarkerOptions m = new MarkerOptions();
//                                    m.position(new LatLng(latitude, longitude));
//                                    googleMap.addMarker(m);
//                                });
//
                            }

                        }

                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                    catch (ParseException e) {
                        throw new RuntimeException(e);
                    }

                }
            }
        });
    }
}