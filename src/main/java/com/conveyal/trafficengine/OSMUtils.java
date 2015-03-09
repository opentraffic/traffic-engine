package com.conveyal.trafficengine;

import com.conveyal.osmlib.Node;
import com.conveyal.osmlib.OSM;
import com.conveyal.osmlib.Way;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;

public class OSMUtils {
	static public LineString getLineStringForWay(Way way, OSM osm) {		
		Coordinate[] coords = new Coordinate[way.nodes.length];
		for(int i=0; i<coords.length; i++){
			Long nd = way.nodes[i];
			Node node = osm.nodes.get(nd);
			
			if(node==null){
				throw new RuntimeException( "Way contains unknown node "+nd );
			}
			
			coords[i] = new Coordinate( node.getLon(), node.getLat() );
		}
		
		return new GeometryFactory().createLineString(coords);
	}
}
