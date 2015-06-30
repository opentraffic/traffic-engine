package com.conveyal.traffic.osm;

import com.conveyal.traffic.data.SpatialDataItem;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.GeometryFactory;

/**
 * Created by kpw on 6/15/15.
 */
public class OSMArea extends SpatialDataItem {

    public long zoneOffset;

    public OSMArea(String osmId, long zoneOffset, Envelope env) {
        this.id = osmId;
        this.zoneOffset = zoneOffset;
        GeometryFactory gf = new GeometryFactory();
        this.geometry = gf.toGeometry(env);

    }
}
