package io.opentraffic.engine.geom;

import com.vividsolutions.jts.geom.Coordinate;

public class LineSegment {

	private static final long serialVersionUID = 1l;

	Coordinate p0;
	Coordinate p1;

	public LineSegment(Coordinate p0, Coordinate p1) {
		this.p0 = p0;
		this.p1 = p1;
	}

	public Double intersectionDistance(LineSegment q) {
		double rx = p1.x - p0.x;
		double ry = p1.y - p0.y;
		double sx = q.p1.x - q.p0.x;
		double sy = q.p1.y - q.p0.y;

		double r_cross_x = crossProduct(rx, ry, sx, sy);

		// line segments parallel, never cross
		if (r_cross_x == 0) {
			return null;
		}

		double ax = q.p0.x - this.p0.x;
		double ay = q.p0.y - this.p0.y;
		double bx = sx / r_cross_x;
		double by = sy / r_cross_x;

		double ret = crossProduct(ax, ay, bx, by);

		return ret;

	}

	double crossProduct(double ux, double uy, double vx, double vy) {
		return ux * vy - uy * vx;
	}

}
