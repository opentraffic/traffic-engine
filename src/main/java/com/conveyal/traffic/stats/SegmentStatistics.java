package com.conveyal.traffic.stats;

public class SegmentStatistics {
	
	long sampleCount = 0;
	double sampleSum = 0.0; 
	
	public void addSample(SpeedSample ss) {
		sampleCount++;
		sampleSum += ss.getSpeed();
	}
}
