package com.conveyal.traffic.stats;

import java.util.concurrent.ConcurrentHashMap;

public class StatisticsCollector {
	
	ConcurrentHashMap<String,SegmentTimeBins> segmentStats = new ConcurrentHashMap<String,SegmentTimeBins>();
	
	public void addSample(SpeedSample ss) {

		if(!segmentStats.contains(ss.segmentId)) {
			segmentStats.putIfAbsent(ss.getSegmentId(), new SegmentTimeBins());
		}
			
		segmentStats.get(ss.segmentId).addSample(ss);
	}
			

}
