package io.opentraffic.trafficengine;

import com.vividsolutions.jts.geom.Coordinate;
import io.opentraffic.engine.geom.LineSegment;
import junit.framework.TestCase;

public class LineSegmentTest extends TestCase {

	public void testBasic(){
		LineSegment ls1 = new LineSegment(new Coordinate(0,0), new Coordinate(4,0));
		LineSegment ls2 = new LineSegment(new Coordinate(1,-1), new Coordinate(1,1));
		
		assertEquals( 0.25, ls1.intersectionDistance( ls2 ) );
		
		assertEquals( 0.5, ls2.intersectionDistance( ls1 ) );
	}


}
