package com.conveyal.traffic.osm;

import com.conveyal.traffic.data.SpatialDataItem;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.GeometryFactory;

/**
 * Created by kpw on 6/15/15.
 */
public class OSMArea extends SpatialDataItem {

    public OSMArea(String osmId, Envelope env) {
        this.id = osmId;
        GeometryFactory gf = new GeometryFactory();
        this.geometry = gf.toGeometry(env);

    }
}
