package com.conveyal.traffic.stats;

import java.util.concurrent.ConcurrentHashMap;

public class SegmentTimeBins {

	public static long BIN_SIZE = 15 * 60 * 1000; // default 15 minute time bin

	ConcurrentHashMap<Long,SegmentStatistics> statsTimeBins = new ConcurrentHashMap<Long,SegmentStatistics>();
	
	public void addSample(SpeedSample ss) {
		long timeBin = getTimeBin(ss.time);
		
		if(!statsTimeBins.contains(timeBin)) {
			statsTimeBins.putIfAbsent(timeBin, new SegmentStatistics());
		}
		
		statsTimeBins.get(timeBin).addSample(ss);
		
	}
	
	public void collectStatisics() {
		
	}
	
	static long getTimeBin(long time) {
		return  (time - (time % BIN_SIZE));
	}
}

