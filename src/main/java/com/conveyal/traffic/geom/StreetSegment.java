package com.conveyal.traffic.geom;

import java.util.ArrayList;
import java.util.List;

import com.conveyal.osmlib.Way;
import com.conveyal.traffic.data.SpatialDataItem;
import com.conveyal.traffic.osm.OSMDataStore;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.linearref.LengthIndexedLine;

public class StreetSegment extends SpatialDataItem {

	// osm way highway tag values for roadway types
	static String[] primaryTypes = {"motorway","trunk","primary","motorway_link"};
	static String[] secondaryTypes = {"secondary"};
	static String[] tertiaryTypes = {"tertiary"};
	static String[] residentialTypes = {"residential"};

	public static int TYPE_PRIMARY = 1;
	public static int TYPE_SECONDARY = 2;
	public static int TYPE_TERTIARY = 3;
	public static int TYPE_RESIDENTIAL = 4;
	public static int TYPE_OTHER = 5;
	public static int TYPE_NON_ROADWAY = -1;

	//final public Way way;
	final public long startNodeId; // first node id 
	final public long endNodeId; // last node id, inclusive
	final public long wayId;
	final public double length;
	final public boolean oneway;
	
	final public int streetType;
	
	public StreetSegment(Way way, long wayId,long startNodeId, long endNodeId, LineString geometry, double length) {

		this.streetType = getRodwayType(way);
		this.oneway = isOneWay(way);
		
		this.wayId = wayId;
		this.startNodeId = startNodeId;
		this.endNodeId = endNodeId;
		
		this.geometry = geometry;
		
		this.length = length;
		// create composite segmentId
		this.id = this.toString();

	}
	
	public List<TripLine> generateTripLines() {
		
		LengthIndexedLine lengthIndexedLine = new LengthIndexedLine(this.geometry);
		
		double scale = (lengthIndexedLine.getEndIndex() - lengthIndexedLine.getStartIndex()) / this.length;
		
		List<TripLine> tripLines = new ArrayList<TripLine>();
		
		tripLines.add(OSMDataStore.createTripLine(this, 1, lengthIndexedLine, (OSMDataStore.INTERSECTION_MARGIN_METERS) * scale, OSMDataStore.INTERSECTION_MARGIN_METERS));
		tripLines.add(OSMDataStore.createTripLine(this, 2, lengthIndexedLine, ((length - OSMDataStore.INTERSECTION_MARGIN_METERS) * scale), length - OSMDataStore.INTERSECTION_MARGIN_METERS));
	
		return tripLines;
	}
	
	public void truncateGeometry() {
		//GeometryFactory gf = new GeometryFactory();
		//this.geometry = gf.createPoint(this.geometry.getCoordinate());
	}
	
	public String toString() {
		return "ss_" + this.wayId + ":" + this.startNodeId + "-" + this.endNodeId;
	}

	static public boolean isOneWay(Way way) {
		return way.tagIsTrue("oneway") ||
				(way.hasTag("highway") && way.getTag("highway").equals("motorway")) ||
				(way.hasTag("junction") && way.getTag("junction").equals("roundabout"));
	}

	static public int getRodwayType(Way way) {

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

		int type = getRodwayType(way);

		if(type == TYPE_PRIMARY || type == TYPE_SECONDARY || type == TYPE_TERTIARY || type == TYPE_RESIDENTIAL)
			return true;
		else
			return false;
	}

}
