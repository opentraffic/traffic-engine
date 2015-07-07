package com.conveyal.traffic.osm;

import com.conveyal.traffic.data.SpatialDataItem;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.GeometryFactory;

import java.io.Serializable;

/**
 * Created by kpw on 6/15/15.
 */
public class OSMArea implements Serializable {

    public long zoneOffset;
    public String osmId;
    public Envelope env;

    public OSMArea(String osmId, long zoneOffset, Envelope env) {
        this.osmId = osmId;
        this.zoneOffset = zoneOffset;
        GeometryFactory gf = new GeometryFactory();
        this.env = env;

    }
}
