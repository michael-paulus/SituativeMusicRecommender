package com.michael.situativemusicrecommender;

import java.util.Map;

public class OSMNode {

    private String id;

    private String lat;

    private String lon;

    private final Map<String, String> tags;

    private String version;

    public OSMNode(String id, String latitude, String longitude, String version, Map<String, String> tags) {
        this.id=id;
        this.lat=latitude;
        this.lon=longitude;
        this.tags = tags;
        this.version = version;
    }

    String getId() {
        return id;
    }

    String getLat() {
        return lat;
    }

    String getLon() {
        return lon;
    }

}
