package io.opentraffic.engine.osm;

import com.conveyal.osmlib.OSM;
import com.vividsolutions.jts.geom.*;
import io.opentraffic.engine.data.PopulationCenters;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by kpw on 8/20/15.
 */
public class OSMCluster implements Serializable {

    static PopulationCenters populationCenters = new PopulationCenters();

    public Long id;
    public List<OSMArea> osmAreas;
    public String name;
    public Envelope bounds;
    public MultiPolygon coverage;

    public OSMCluster(Long id, OSMArea area) {
        this.id = id;
        this.addArea(area);

        updateName();
    }

    public boolean overlaps(OSMCluster cluster) {
        if(this.id == cluster.id)
            return false;

        Envelope env1 = new Envelope(this.bounds);
        Envelope env2 = new Envelope(cluster.bounds);
        env1.expandBy(env1.getWidth() * 0.05, env1.getHeight() * 0.05);
        env2.expandBy(env2.getWidth() * 0.05, env2.getHeight() * 0.05);
        if(env1.intersects(env2))
            return true;
        else
            return false;

    }

    public void mergeCluster(OSMCluster cluster) {
        if(this.id.equals(cluster.id))
            return;

        for(OSMArea area : cluster.osmAreas) {
            this.addArea(area);
        }
        updateName();
    }

    public void addArea(OSMArea area) {

        if(bounds == null) {
            bounds = new Envelope();
        }
        bounds.expandToInclude(area.env);

        if(osmAreas == null) {
            osmAreas = new ArrayList<>();
        }

        osmAreas.add(area);

        GeometryFactory gf = new GeometryFactory();
        Polygon polygon = (Polygon)gf.toGeometry(area.env);

        Polygon polygonList[] = new Polygon[1];
        polygonList[0] = polygon;

        MultiPolygon multiPolygon = gf.createMultiPolygon(polygonList);

        if(coverage == null) {
            coverage = multiPolygon;
        }
        else {
            Geometry geom = this.coverage.union(multiPolygon);

            if(geom instanceof Polygon) {
                polygonList[0] = (Polygon)geom;

                multiPolygon = gf.createMultiPolygon(polygonList);
            }
            else
                multiPolygon = (MultiPolygon)geom;

            coverage = multiPolygon;
        }
    }

    public void updateName() {

        this.name = populationCenters.getNameForArea(this.bounds);

    }

}
