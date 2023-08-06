package com.bacar.trackat_android;

import com.google.android.gms.maps.model.LatLng;

import java.util.List;


public class GeofenceModel {
    private int id;
    private int numPoints;
    private String name; // Add the name field
    private List<LatLng> points;

    public GeofenceModel(int id, int numPoints, String name, List<LatLng> points) {
        this.id = id;
        this.numPoints = numPoints;
        this.name = name;
        this.points = points;
    }

    public int getId() {
        return id;
    }

    public int getNumPoints() {
        return numPoints;
    }

    public String getName() {
        return name;
    }

    public List<LatLng> getPoints() {
        return points;
    }
}