package com.conveyal.traffic.geom;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import com.conveyal.osmlib.Way;
import com.conveyal.traffic.data.SpatialDataItem;
import com.conveyal.traffic.osm.OSMDataStore;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.linearref.LengthIndexedLine;

public class StreetSegment extends SpatialDataItem {
	
	public static int TYPE_PRIMARY = 1;
	public static int TYPE_SECONDARY = 2;
	public static int TYPE_TERTIARY = 3;
	public static int TYPE_OTHER = 4;
	
	//final public Way way;
	final public long startNodeId; // first node id 
	final public long endNodeId; // last node id, inclusive
	final public long wayId;
	final public double length;
	final public boolean oneway;
	
	final public int streetType;

	public StreetSegment(long wayId,long startNodeId, long endNodeId, Way way, LineString geometry, double length, boolean oneway) {
		
		this.wayId = wayId;
		this.startNodeId = startNodeId;
		this.endNodeId = endNodeId;
		
		this.geometry = geometry;
		
		this.length = length;
		this.oneway = oneway;
		
		this.id = this.toString();
	
		// Check to make sure it's a highway
		String highwayType = way.getTag("highway");
		
		// Check to make sure it's an acceptable type of highway
		String[] primaryTypes = {"motorway","trunk",
				"primary","motorway_link"};
		
		// Check to make sure it's an acceptable type of highway
		String[] secondaryTypes = {"secondary"};
		
		// Check to make sure it's an acceptable type of highway
		String[] tertiaryTypes = {"tertiary"};
		
		// Check to make sure it's an acceptable type of highway
		String[] otherTypes = {"unclassified","residential","service",};
	
		
		if(OSMDataStore.among(highwayType,primaryTypes) ){
			streetType = TYPE_PRIMARY;
		}
		else if(OSMDataStore.among(highwayType,secondaryTypes) ){
			streetType = TYPE_SECONDARY;
		}
		else if(OSMDataStore.among(highwayType,tertiaryTypes) ){
			streetType = TYPE_TERTIARY;
		}
		else {
			streetType = TYPE_OTHER;
		}
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

}
