package io.opentraffic.engine.osm;

import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.GeometryFactory;

import java.io.Serializable;

public class OSMArea implements Serializable, Comparable<OSMArea> {

    static public OSMArea MIN = MIN();

    public Long id;
    public long zoneOffset;
    public int x, y, z;
    public String placeName;
    public long placePop;
    public Envelope env;

    public OSMArea(long id, int x, int y, int z, String placeName, Long placePop, long zoneOffset, Envelope env) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.z = z;

        this.placeName = placeName;

        if(placePop != null)
            this.placePop = placePop;

        this.zoneOffset = zoneOffset;
        GeometryFactory gf = new GeometryFactory();
        this.env = env;

    }

    private OSMArea() {

    }

    @Override
    public int compareTo(OSMArea o) {
        return id.compareTo(o.id);
    }

    private static OSMArea MIN() {
        OSMArea min = new OSMArea();
        min.id = Long.MIN_VALUE;
        return min;
    }
}
