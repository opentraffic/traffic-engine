package com.conveyal.traffic.stats;

import com.conveyal.traffic.data.ExchangeFormat;
import com.conveyal.traffic.data.SpatialDataStore;
import com.conveyal.traffic.geom.StreetSegment;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;


public class StatisticsCollector {
	
	ConcurrentHashMap<String,SegmentTimeBins> segmentStats = new ConcurrentHashMap<String,SegmentTimeBins>();
	
	public void addSpeedSample(SpeedSample speedSample) {

		if(!segmentStats.contains(speedSample.segmentId)) {
			segmentStats.putIfAbsent(speedSample.segmentId, new SegmentTimeBins());
		}
			
		segmentStats.get(speedSample.segmentId).addSample(speedSample);
	}
	
	public BaselineStatistics getSegmentStatistics(String segmentId) {
		if(!segmentStats.contains(segmentId)) {
			segmentStats.putIfAbsent(segmentId, new SegmentTimeBins());
		}
			
		return segmentStats.get(segmentId).collectBaselineStatisics(); 
	}
	
	public void collectStatistcs(FileOutputStream os, SpatialDataStore sdi) throws IOException {
		
		ExchangeFormat.BaselineTile.Builder tile = ExchangeFormat.BaselineTile.newBuilder();
		
		tile.setHeader(ExchangeFormat.Header.newBuilder()
				.setCreationTimestamp(System.currentTimeMillis())
				.setOsmCommitId(1)
				.setTileX(1)
				.setTileY(1)
				.setTileZ(1));
		
		
		for(String segmentId : segmentStats.keySet()) {
			StreetSegment ss = (StreetSegment)sdi.getById(segmentId);
			
			tile.addSegments(ExchangeFormat.BaselineStats.newBuilder()
					.setSegment(ExchangeFormat.SegmentDefinition.newBuilder()
							.setWayId(ss.wayId)
							.setStartNodeId(ss.startNodeId)
							.setEndNodeId(ss.endNodeId))
					.setAverageSpeed((float) segmentStats.get(segmentId).collectBaselineStatisics().getAverageSpeedKMH()));
		}
		
		os.write(tile.build().toByteArray());
		os.flush();
	}
}
