 package com.conveyal.traffic.stats;

import java.io.Serializable;

import com.conveyal.traffic.geom.Crossing;
import com.conveyal.traffic.geom.StreetSegment;

 public class SpeedSample implements Serializable {

	 final String streetSegmentId;
	 final String streetSegmentTileId;
	 final int streetSegmentType;

	 final long time;
	 final double speed;

	 public SpeedSample(long time, double speed, String streetSegmentId, String streetSegmentTileId, int streetSegmentType) {
		 this.streetSegmentId = streetSegmentId;
		 this.streetSegmentType = streetSegmentType;
		 this.streetSegmentTileId = streetSegmentTileId;
		 this.time = time;
		 this.speed = speed;
	 }

	 public String toString() {
		 return "[SpeedSample:" + streetSegmentId + " speed:" + speed + "]";
	 }

	 public String getSegmentId() {
		 return this.streetSegmentId;
	 }

	 public int getSegmentType() {
		 return this.streetSegmentType;
	 }

	 public long getTime() {
		return this.time;
	 }

	 public double getSpeed() {
		return this.speed;
	 }
}
