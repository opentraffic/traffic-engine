package io.opentraffic.engine.data;

import java.io.Serializable;
import java.util.List;

import io.opentraffic.engine.data.stores.SpatialDataStore;
import io.opentraffic.engine.geom.GPSPoint;
import com.vividsolutions.jts.geom.*;
import org.mapdb.Fun;

public abstract class  SpatialDataItem implements Serializable {

	private static final long serialVersionUID = 1L;

	final public Long id;

	final public double lons[];
	final public double lats[];

	public String uniqueId;

	public SpatialDataItem(Long id, Geometry geom) {
		this.id = id;

		if(geom.getGeometryType().equals("LineString")) {

			Coordinate coords[] = geom.getCoordinates();
			lons = new double[coords.length];
			lats = new double[coords.length];

			for(int i = 0; i < coords.length; i++) {
				lons[i] = coords[i].x;
				lats[i] = coords[i].y;
			}
		}
		else {
			lons = new double[0];
			lats = new double[0];
			new Exception("Can't store non LineString geometries.");
		}
	}

	public SpatialDataItem(Long id, List<GPSPoint> points) {
		this.id = id;

		lons = new double[points.size()];
		lats = new double[points.size()];

		int i =0;
		for(GPSPoint point : points) {
			lons[i] = point.lon;
			lats[i] = point.lat;
			i++;
		}
	}

	public SpatialDataItem(Long id, Coordinate[] coords) {
		this.id = id;

		lons = new double[coords.length];
		lats = new double[coords.length];

		for(int i = 0; i < coords.length; i++) {
			lons[i] = coords[i].x;
			lats[i] = coords[i].y;
		}
	}


	public Fun.Tuple3<Integer, Integer, Long>[] getTiles(int zIndex) {

		int minY = Integer.MAX_VALUE;
		int minX = Integer.MAX_VALUE;
		int maxY = Integer.MIN_VALUE;
		int maxX = Integer.MIN_VALUE;

		for(int i = 0; i < lons.length; i++) {
			int x = SpatialDataStore.getTileX(lons[i], zIndex);
			int y = SpatialDataStore.getTileY(lats[i], zIndex);

			if(x < minX)
				minX = x;
			if(x > maxX)
				maxX = x;
			if(y < minY)
				minY = y;
			if(y > maxY)
				maxY = y;

		}

		int tileCount = (maxX - minX + 1) * (maxY - minY + 1);

		Fun.Tuple3<Integer, Integer, Long>[] tiles = new Fun.Tuple3[tileCount];

		int i = 0;
		for(int tileX = minX; tileX <= maxX; tileX++) {
			for(int tileY = minY; tileY <= maxY; tileY++) {
				tiles[i] = new Fun.Tuple3(tileX, tileY, this.id);
				i++;
			}
		}

		return tiles;
	}

	@Override
	public boolean equals(Object object) {
		if (object instanceof SpatialDataItem && ((SpatialDataItem)object).id == this.id) {
			return true;
		} else {
			return false;
		}
	}

	public Coordinate[] getCoordinates() {

		Coordinate coords[] = new Coordinate[lats.length];
		for(int i = 0; i < lats.length; i++) {
			coords[i] = new Coordinate(lons[i], lats[i]);
		}

		return coords;
	}

	public Geometry getGeometry() {

		return new GeometryFactory().createLineString(getCoordinates());
	}
	
}
