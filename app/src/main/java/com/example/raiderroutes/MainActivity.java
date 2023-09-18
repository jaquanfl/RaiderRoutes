package com.example.raiderroutes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.content.Context;
import android.location.Location;
import android.os.Bundle;
import android.content.pm.PackageManager;
import android.Manifest;
import android.graphics.Color;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.SearchView;
import android.widget.Toast;

import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.*;
import com.google.android.gms.location.*;
import org.json.*;
import java.io.*;
import java.util.*;
import com.google.maps.*;
import com.google.maps.model.*;
import com.google.android.gms.maps.model.LatLng;

// Test
class Building {
    private String name;
    private LatLng location; // LatLng for building's coordinates

    public Building(String name, LatLng location) {
        this.name = name;
        this.location = location;
    }

    // Getters for name and location
    public String getName() {
        return name;
    }

    public LatLng getLocation() {
        return location;
    }
}
public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {
    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private LatLng userLocation;
    private  LatLng destinationLocation;
    private AutoCompleteTextView autoCompleteTextView;
    private Polyline currentPolyline;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        // Check if the app has permission to access fine location
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Request the permission
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);

        }

        autoCompleteTextView = findViewById(R.id.autoCompleteTextView);
        autoCompleteTextView.setThreshold(1);
        populateSuggestions();
        autoCompleteTextView.setOnItemClickListener((parent, view, position, id) -> {
            String selectedBuildingName = (String) parent.getItemAtPosition(position);
            handleSearch(selectedBuildingName);
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap){
        mMap = googleMap;

        // Map Coordinates
        LatLng campus = new LatLng(33.5844661,-101.8748002);
        LatLng southwestBound = new LatLng(33.57750138866316, -101.89616999491503);
        LatLng northeastBound = new LatLng(33.59350044394497, -101.87004542952994);
        LatLngBounds bounds = new LatLngBounds(southwestBound,northeastBound);

        // Map Rules
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.getUiSettings().setCompassEnabled(true);
        googleMap.setMinZoomPreference(15);
        googleMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
        googleMap.moveCamera(CameraUpdateFactory.newLatLng(campus));
        googleMap.moveCamera(CameraUpdateFactory.zoomTo(17));
        googleMap.setLatLngBoundsForCameraTarget(bounds);

        // Enable user's location on the map
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);

            // Get user's location and move the camera
            fusedLocationProviderClient.getLastLocation()
                    .addOnSuccessListener(this, location -> {
                        if (location != null) {
                            userLocation = new LatLng(location.getLatitude(), location.getLongitude());
                        }
                    });

        }

        startLocationUpdates();
    }

    private void drawPolyline(List<LatLng> points) {
        // Clear the existing polyline
        if (currentPolyline != null) {
            currentPolyline.remove();
        }

        PolylineOptions polylineOptions = new PolylineOptions()
                .addAll(points)
                .width(10)
                .color(Color.RED);
        currentPolyline = mMap.addPolyline(polylineOptions);
    }

    private List<Building> parseJSONFile() {
        System.out.println("parseJSONFile()");
        List<Building> buildingList = new ArrayList<>();

        try {
            InputStream inputStream = getAssets().open("TTUBuildings.json");
            int size = inputStream.available();
            byte[] buffer = new byte[size];
            inputStream.read(buffer);
            inputStream.close();
            String json = new String(buffer, "UTF-8");
            // Parse the JSON data
            JSONObject jsonObject = new JSONObject(json);
            JSONArray buildingsArray = jsonObject.getJSONArray("buildings");
            for (int i = 0; i < buildingsArray.length(); i++) {
                JSONObject buildingObject = buildingsArray.getJSONObject(i);
                String name = buildingObject.getString("name");
                double latitude = buildingObject.getDouble("latitude");
                double longitude = buildingObject.getDouble("longitude");

                // Create a Building object and add it to the list
                Building building = new Building(name, new LatLng(latitude, longitude));
                buildingList.add(building);
            }
        } catch (IOException | JSONException e) {
            e.printStackTrace();
            // Handle the exception
        }

        return buildingList;
    }

    private void calculateAndDisplayRoute(LatLng userLocation, LatLng destination) {
        // Build the request
        String apiKey = "AIzaSyCsLiIDu-pZ1GFi1tcTHeRZPaVkK1nAonw";
        GeoApiContext context = new GeoApiContext.Builder()
                .apiKey(apiKey)
                .build();

        // Make the request
        DirectionsApiRequest request = DirectionsApi.newRequest(context)
                .origin(userLocation.latitude + "," + userLocation.longitude)
                .destination(destination.latitude + "," + destination.longitude)
                .mode(TravelMode.WALKING);

        try {
            DirectionsResult result = request.await();
            // Handle the result and draw the route on the map
            drawRoute(result);
        } catch (Exception e) {
            e.printStackTrace();
            // Handle the error
        }
    }

    private void drawRoute(DirectionsResult directionsResult) {
        DirectionsRoute route = directionsResult.routes[0]; // Assuming you want the first route

        List<LatLng> routePoints = new ArrayList<>();
        for (com.google.maps.model.LatLng point : route.overviewPolyline.decodePath()) {
            routePoints.add(new LatLng(point.lat, point.lng));
        }

        drawPolyline(routePoints);
    }

    private LatLng searchBuildingByName(String buildingName) {
        List<Building> buildingList = parseJSONFile();
        for (Building building : buildingList) {
            if (building.getName().equalsIgnoreCase(buildingName)) {
                return building.getLocation();
            }
        }
        return null; // Building not found
    }

    private void handleSearch(String query) {
        mMap.clear();
        query = query.trim();
        LatLng buildingLocation = searchBuildingByName(query);
        if (buildingLocation != null) {
            // Building found, calculate and display the route
            destinationLocation = buildingLocation;
            if (userLocation != null) {
                calculateAndDisplayRoute(userLocation, destinationLocation);

                // Calculate bounds to include both userLocation and destinationLocation
                LatLngBounds.Builder builder = new LatLngBounds.Builder();
                builder.include(userLocation);
                builder.include(destinationLocation);
                LatLngBounds bounds = builder.build();

                // Animate the camera to show the full route with padding
                int padding = 100; // Adjust this value to control padding around the route
                CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, padding);
                mMap.animateCamera(cameraUpdate);

                mMap.addMarker(new MarkerOptions()
                        .position(buildingLocation)
                        .title(query)); // Use the query as the marker title
            }
        } else {
            // Building not found, show a message to the user
            Toast.makeText(this, "Building not found", Toast.LENGTH_SHORT).show();
        }

        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(autoCompleteTextView.getWindowToken(), 0);
        autoCompleteTextView.clearFocus();
        View mapView = getSupportFragmentManager().findFragmentById(R.id.map).getView();
        mapView.requestFocus();
    }


    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationRequest locationRequest = new LocationRequest.Builder(5000).build();

            LocationCallback locationCallback = new LocationCallback() {
                @Override
                public void onLocationResult(LocationResult locationResult) {
                    if (locationResult != null) {
                        Location location = locationResult.getLastLocation();
                        LatLng userLocation = new LatLng(location.getLatitude(), location.getLongitude());

                        // Update the route based on the new user location
                        if (destinationLocation != null) {
                            calculateAndDisplayRoute(userLocation, destinationLocation);
                        }
                    }
                }
            };

            fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, null);
        }
    }

    private void populateSuggestions() {
        List<Building> buildingList = parseJSONFile();
        List<String> buildingNames = new ArrayList<>();

        for (Building building : buildingList) {
            buildingNames.add(building.getName());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, buildingNames);
        autoCompleteTextView.setAdapter(adapter);
    }
}
