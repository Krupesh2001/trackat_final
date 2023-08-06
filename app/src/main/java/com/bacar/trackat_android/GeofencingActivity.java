package com.bacar.trackat_android;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Toast;

import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GeofencingActivity extends FragmentActivity implements OnMapReadyCallback {

    // Views
    private MapView mapView;
    private GoogleMap googleMap;
    private ListView geofenceListView;

    // Geofence data
    private List<LatLng> geofencePoints = new ArrayList<>();
    private Polygon geofencePolygon;
    private Marker currentMarker;

    // Geofence models
    private List<GeofenceModel> geofences = new ArrayList<>();
    private ArrayAdapter<String> geofenceAdapter;

    // Geofencing client
    private GeofencingClient geofencingClient;

    // Geofence radius in meters
    private static final float GEOFENCE_RADIUS_IN_METERS = 100;

    // Flag for creating geofence
    private boolean creatingGeofence = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_geofencing);

        // Initialize views
        mapView = new MapView(this);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);
        FrameLayout mapContainer = findViewById(R.id.map_container);
        mapContainer.addView(mapView);
        geofenceListView = findViewById(R.id.geofence_listview);

        // Back button
        ImageButton backButton = findViewById(R.id.geolocation_button);
        backButton.setOnClickListener(v -> finish());

        // Create Geofence button
        ImageButton createGeofenceButton = findViewById(R.id.create_button);
        createGeofenceButton.setOnClickListener(v -> {
            if (geofencePoints.size() >= 3) {
                saveGeofenceToDatabase();
                creatingGeofence = true;
            } else {
                Toast.makeText(this, "Please add at least three markers to create a geofence!", Toast.LENGTH_SHORT).show();
            }
        });

        // Initialize geofence adapter and set it to the list view
        geofenceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new ArrayList<>());
        geofenceListView.setAdapter(geofenceAdapter);

        // Set a listener for the geofence list view
        geofenceListView.setOnItemClickListener((adapterView, view, position, id) -> {
            GeofenceModel selectedGeofence = geofences.get(position);
            showGeofenceOnMap(selectedGeofence);
        });

        // Initialize GeofencingClient
        geofencingClient = LocationServices.getGeofencingClient(this);

        // Load geofences from the server
        loadGeofencesFromServer();
    }

    @Override
    public void onMapReady(GoogleMap map) {
        googleMap = map;
        if (googleMap == null) {
            Log.d("GeofencingActivity", "Google Map is null");
        } else {
            Log.d("GeofencingActivity", "Google Map is ready");
        }

        // Set a listener to handle map clicks for adding markers
        googleMap.setOnMapClickListener(latLng -> {
            Log.d("GeofencingActivity", "Map clicked at: " + latLng.toString());
            if (creatingGeofence) {
                geofencePoints.add(latLng);
                drawGeofencePolygon();
                if (currentMarker != null) {
                    currentMarker.remove();
                }
                currentMarker = googleMap.addMarker(new MarkerOptions().position(latLng));
                for (LatLng point : geofencePoints) {
                    Log.d("GeofencePoints", "Lat: " + point.latitude + ", Lng: " + point.longitude);
                }
            }
        });
    }

    private void drawGeofencePolygon() {
        Log.d("GeofencingActivity", "drawGeofencePolygon() called");
        if (geofencePoints.size() >= 3) {
            if (geofencePolygon != null) {
                geofencePolygon.remove();
            }
            PolygonOptions polygonOptions = new PolygonOptions()
                    .addAll(geofencePoints)
                    .strokeWidth(2)
                    .strokeColor(getResources().getColor(R.color.polygonStrokeColor))
                    .fillColor(getResources().getColor(R.color.polygonFillColor));
            geofencePolygon = googleMap.addPolygon(polygonOptions);
            Log.d("GeofencingActivity", "PolygonOptions: " + polygonOptions.toString());
        }
    }

    private void saveGeofenceToDatabase() {

        // Convert geofencePoints to a list of JSON objects for each point
        JSONArray pointsArray = new JSONArray();
        for (LatLng point : geofencePoints) {
            JSONObject pointObject = new JSONObject();
            try {
                pointObject.put("lat", point.latitude);
                pointObject.put("long", point.longitude);
                pointsArray.put(pointObject);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        // Create the JSON object representing the geofence
        JSONObject fenceObject = new JSONObject();
        try {
            fenceObject.put("nbPoints", geofencePoints.size());
            fenceObject.put("points", pointsArray);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // Make an HTTP request to save the geofence to the server
        saveGeofenceToServer(fenceObject);
    }

    private void saveGeofenceToServer(JSONObject fenceObject) {
        String url = "http://82.180.161.23/set_geofence";

        OkHttpClient client = new OkHttpClient();

        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        RequestBody requestBody = RequestBody.create(fenceObject.toString(), JSON);

        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                // Handle network error
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    // Geofence data saved successfully
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(GeofencingActivity.this, "Geofence created and saved!", Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    // Handle non-successful response (e.g., 404, 500, etc.)
                }
            }
        });
    }

    private void loadGeofencesFromServer() {
        String url = "http://82.180.161.23/get_geofence";

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                // Handle network error
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    parseGeofencesResponse(responseBody);
                } else {
                    // Handle non-successful response (e.g., 404, 500, etc.)
                }
            }
        });
    }

    private void parseGeofencesResponse(String responseBody) {
        try {
            JSONObject responseJson = new JSONObject(responseBody);
            int numFences = responseJson.getInt("nbFence");
            JSONArray geofencesArray = responseJson.getJSONArray("fences");

            geofences.clear();
            for (int i = 0; i < numFences; i++) {
                JSONObject geofenceObject = geofencesArray.getJSONObject(i);
                int nbPoints = geofenceObject.getInt("nbPoints");
                JSONArray pointsArray = geofenceObject.getJSONArray("points");

                List<LatLng> points = new ArrayList<>();
                for (int j = 0; j < nbPoints; j++) {
                    JSONObject pointObject = pointsArray.getJSONObject(j);
                    double lat = pointObject.getDouble("lat");
                    double lng = pointObject.getDouble("long");
                    points.add(new LatLng(lat, lng));
                }

                GeofenceModel geofence = new GeofenceModel(i, nbPoints, "Geofence " + (i + 1), points);
                geofences.add(geofence);
            }

            // Update the spinner adapter with geofence names
            runOnUiThread(() -> {
                geofenceAdapter.clear();
                for (GeofenceModel geofence : geofences) {
                    geofenceAdapter.add(geofence.getName());
                }
                geofenceAdapter.notifyDataSetChanged();

                // Show the first geofence on the map by default
                if (!geofences.isEmpty()) {
                    showGeofenceOnMap(geofences.get(0));
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void showGeofenceOnMap(GeofenceModel geofence) {
        // Clear previous geofence from the map
        if (geofencePolygon != null) {
            geofencePolygon.remove();
        }

        // Clear geofence points and add new points
        geofencePoints.clear();
        for (LatLng point : geofence.getPoints()) {
            geofencePoints.add(point);
        }

        // Redraw the geofence polygon
        drawGeofencePolygon();

        // Move the map camera to center on the geofence
        LatLng center = calculateGeofenceCenter(geofence.getPoints());
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(center, 15f));
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    // Helper method to calculate the center of the geofence
    private LatLng calculateGeofenceCenter(List<LatLng> points) {
        double latSum = 0;
        double lngSum = 0;
        int numPoints = points.size();

        for (LatLng point : points) {
            latSum += point.latitude;
            lngSum += point.longitude;
        }

        return new LatLng(latSum / numPoints, lngSum / numPoints);
    }

    // Helper method to create GeofencingRequest
    private GeofencingRequest createGeofenceRequest() {
        List<Geofence> geofencesList = new ArrayList<>();

        // Convert the geofencePoints list to Geofence objects
        for (int i = 0; i < geofencePoints.size(); i++) {
            Geofence geofence = new Geofence.Builder()
                    .setRequestId("Geofence" + (i + 1))
                    .setCircularRegion(geofencePoints.get(i).latitude, geofencePoints.get(i).longitude, GEOFENCE_RADIUS_IN_METERS)
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT)
                    .build();

            geofencesList.add(geofence);
        }

        // Create a GeofencingRequest with the geofences
        GeofencingRequest.Builder builder = new GeofencingRequest.Builder();
        builder.setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER);
        builder.addGeofences(geofencesList);

        return builder.build();
    }

    // Helper method to get PendingIntent for geofence transitions
    private PendingIntent getGeofencePendingIntent() {

        Intent intent = new Intent(this, YourGeofenceTransitionService.class);
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }
}

