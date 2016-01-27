package io.opentraffic.engine.geom;

import com.conveyal.osmlib.Way;
import io.opentraffic.engine.data.SpatialDataItem;
import io.opentraffic.engine.osm.OSMDataStore;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.LineString;
import org.mapdb.Fun;

public class StreetSegment extends SpatialDataItem {

	// osm way highway tag values for roadway types
	static String[] primaryTypes = {"motorway","trunk","primary","primary_link","motorway_link","unclassified"};
	static String[] secondaryTypes = {"secondary"};
	static String[] tertiaryTypes = {"tertiary"};
	static String[] residentialTypes = {"residential"};

	public static int TYPE_NON_ROADWAY = 0;
	public static int TYPE_PRIMARY = 1;
	public static int TYPE_SECONDARY = 2;
	public static int TYPE_TERTIARY = 3;
	public static int TYPE_RESIDENTIAL = 4;
	public static int TYPE_OTHER = 5;

	//final public Way way;
	final public long startNodeId; // first node id 
	final public long endNodeId; // last node id, inclusive
	final public long wayId;
	final public double length;
	final public boolean oneway;

	final public int streetType;
	
	public StreetSegment(long id, Way way, long wayId,long startNodeId, long endNodeId, LineString geometry, double length) {
		super(id, geometry);

		this.streetType = getRoadwayType(way);
		this.oneway = isOneWay(way);
		
		this.wayId = wayId;
		this.startNodeId = startNodeId;
		this.endNodeId = endNodeId;

		this.length = length;
	}

	public StreetSegment(long id, int streetType, boolean oneway, long wayId,long startNodeId, long endNodeId, Coordinate coords[], double length) {
		super(id, coords);

		this.streetType = streetType;
		this.oneway = oneway;

		this.wayId = wayId;
		this.startNodeId = startNodeId;
		this.endNodeId = endNodeId;

		this.length = length;
	}

	public boolean disjoint(StreetSegment segment) {
		if(segment.endNodeId == this.startNodeId || this.endNodeId == segment.startNodeId)
			return false;

		double dist1 = OSMDataStore.getDistance(this.lons[this.lons.length-1], this.lats[this.lons.length-1], segment.lons[0], segment.lats[0]);
		double dist2 = OSMDataStore.getDistance(this.lons[0], this.lats[0], segment.lons[segment.lons.length-1], segment.lats[segment.lons.length-1]);

		if(dist1 < OSMDataStore.MIN_SEGMENT_LEN || dist2 < OSMDataStore.MIN_SEGMENT_LEN)
			return false;

		return true;
	}

	public void truncateGeometry() {
		//GeometryFactory gf = new GeometryFactory();
		//this.geometry = gf.createPoint(this.geometry.getCoordinate());
	}

	public Fun.Tuple3<Long, Long, Long> getSegmentId() {
		return new Fun.Tuple3<>(this.wayId, this.startNodeId, this.endNodeId);
	}
	
	public String toString() {
		return "ss_" + this.wayId + ":" + this.startNodeId + "-" + this.endNodeId;
	}

	static public boolean isOneWay(Way way) {
		return way.tagIsTrue("oneway") ||
				(way.hasTag("highway") && way.getTag("highway").equals("motorway")) ||
				(way.hasTag("junction") && way.getTag("junction").equals("roundabout"));
	}

	static public int getRoadwayType(Way way) {

		String highwayType = way.getTag("highway");

		if (highwayType == null)
			return TYPE_NON_ROADWAY;

		if (OSMDataStore.among(highwayType, primaryTypes))
			return TYPE_PRIMARY;
		else if (OSMDataStore.among(highwayType, secondaryTypes))
			return TYPE_SECONDARY;
		else if (OSMDataStore.among(highwayType, tertiaryTypes))
			return TYPE_TERTIARY;
		else if (OSMDataStore.among(highwayType, residentialTypes))
			return TYPE_RESIDENTIAL;
		else
			return TYPE_OTHER;
	}

	static public boolean isTrafficEdge(Way way) {

		int type = getRoadwayType(way);

		if(type == TYPE_PRIMARY || type == TYPE_SECONDARY || type == TYPE_TERTIARY || type == TYPE_RESIDENTIAL || type == TYPE_OTHER)
			return true;
		else
			return false;
	}

}
