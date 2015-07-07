package com.conveyal.traffic.data;

import com.conveyal.traffic.data.seralizers.SegmentStatisticsSerializer;
import com.conveyal.traffic.data.seralizers.TypeStatisticsSerializer;
import com.conveyal.traffic.stats.SegmentStatistics;
import com.conveyal.traffic.stats.SpeedSample;
import com.conveyal.traffic.stats.SummaryStatistics;
import com.conveyal.traffic.stats.TypeStatistics;
import org.mapdb.BTreeKeySerializer;
import org.mapdb.DB;
import org.mapdb.DB.BTreeMapMaker;
import org.mapdb.DBMaker;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class StatsDataStore {

	private static final Logger log = Logger.getLogger( StatsDataStore.class.getName());

	DB db;

	ExecutorService executor;

	Map<Long, Map<Long,TypeStatistics>> weekTypeMap = new ConcurrentHashMap<>();
	Map<Long,TypeStatistics> cumulativeTypeMap;

	Map<Long, Map<Long,SegmentStatistics>> weekHourMap = new ConcurrentHashMap<>();
	Map<Long,SegmentStatistics> cumulativeHourMap;

	Queue<SpeedSample> sampleQueue = new ConcurrentLinkedQueue<>();

	/**
	 * Create a new DataStore.
	 * @param directory Where should it be created?
	 */
	public StatsDataStore(File directory) {

		if(!directory.exists())
			directory.mkdirs();

		DBMaker dbm = DBMaker.newFileDB(new File(directory, "stats.db"))
				.closeOnJvmShutdown()
				.cacheLRUEnable()
				.cacheSize(100000)
				.mmapFileEnable()
				.asyncWriteEnable()
				.asyncWriteFlushDelay(10000)
				.closeOnJvmShutdown();

	    db = dbm.make();

		Map<String, Object> maps =  db.getAll();
		for(String mapId : maps.keySet()) {
			if(mapId.startsWith("week_")) {
				Long week = Long.parseLong(mapId.replace("week_", ""));
				weekHourMap.put(week, (Map<Long,SegmentStatistics>)maps.get(mapId));
			}

			if(mapId.startsWith("type_")) {
				Long week = Long.parseLong(mapId.replace("type_", ""));
				weekTypeMap.put(week, (Map<Long, TypeStatistics>) maps.get(mapId));
			}
		}

		BTreeMapMaker cumulativeHourMaker = db.createTreeMap("cumulativeHourMap");
		cumulativeHourMaker = cumulativeHourMaker
				.keySerializer(BTreeKeySerializer.ZERO_OR_POSITIVE_LONG)
				.valueSerializer(new SegmentStatisticsSerializer());
		cumulativeHourMap = cumulativeHourMaker.makeOrGet();

		BTreeMapMaker cumulativeTypeMaker = db.createTreeMap("cumulativeTypeMap");
		cumulativeTypeMaker = cumulativeTypeMaker
				.keySerializer(BTreeKeySerializer.ZERO_OR_POSITIVE_LONG)
				.valueSerializer(new TypeStatisticsSerializer());
		cumulativeTypeMap = cumulativeTypeMaker.makeOrGet();

		executor = Executors.newFixedThreadPool(1);

		Runnable statsCollector = () -> {

			while(true) {
				try {
					SpeedSample speedSample = sampleQueue.poll();
					if(speedSample != null)
						this.save(speedSample);
				}
				catch (Exception e) {
					e.printStackTrace();
				}
			}
		};

		executor.execute(statsCollector);
	}

	public long getSampleQueueSize() {
		return sampleQueue.size();
	}

	public Map<Long,SegmentStatistics> getWeekMap(long week) {

		String key = "week_" + week;

		if(!weekHourMap.containsKey(week)) {
			BTreeMapMaker hourMaker = db.createTreeMap(key);
			hourMaker = hourMaker
					.keySerializer(BTreeKeySerializer.ZERO_OR_POSITIVE_LONG)
					.valueSerializer(new SegmentStatisticsSerializer());

			Map<Long,SegmentStatistics> weekMap = hourMaker.makeOrGet();
			weekHourMap.put(week, weekMap);
		}

		return weekHourMap.get(week);
	}

	public Map<Long,TypeStatistics> getTypeMap(long week) {

		String key = "type_" + week;

		if(!cumulativeTypeMap.containsKey(week)) {
			BTreeMapMaker typeMaker = db.createTreeMap(key);
			typeMaker = typeMaker
					.keySerializer(BTreeKeySerializer.ZERO_OR_POSITIVE_LONG)
					.valueSerializer(new TypeStatisticsSerializer());

			Map<Long,TypeStatistics> typeMap = typeMaker.makeOrGet();
			weekTypeMap.put(week, typeMap);
		}

		return weekTypeMap.get(week);
	}

	public List<Long> getWeekList() {
		List<Long> list = new ArrayList();
		list.addAll(weekHourMap.keySet());
		return list;
	}

	public void addSpeedSample(SpeedSample speedSample) {
		sampleQueue.add(speedSample);
	}

	public void save(long time, long segmentId, int segmentType, SegmentStatistics segmentStats) {

		long week = SegmentStatistics.getWeekSinceEpoch(time);

		Map<Long,TypeStatistics> typeData = getTypeMap(week);

		Map<Long,SegmentStatistics> hourData = getWeekMap(week);

		if(hourData.containsKey(segmentId))
			hourData.get(segmentId).addStats(segmentStats);
		else
			hourData.put(segmentId, segmentStats);


		SegmentStatistics segmentStatistics;
		if(cumulativeHourMap.containsKey(segmentId))
			segmentStatistics = cumulativeHourMap.get(segmentId);
		else
			segmentStatistics = new SegmentStatistics();

		segmentStatistics.addStats(segmentStats);

		cumulativeHourMap.put(segmentId, segmentStatistics);

	}

	public void save(SpeedSample speedSample) {

		long week = SegmentStatistics.getWeekSinceEpoch(speedSample.getTime());

		/*Map<Long,TypeStatistics> typeData = getTypeMap(week);

		if(typeData.containsKey(speedSample.getSegmentTileId()))
			typeData.get(speedSample.getSegmentTileId()).addSample(speedSample);
		else {
			TypeStatistics typeStatistics = new TypeStatistics();
			typeStatistics.addSample(speedSample);
			typeData.put(speedSample.getSegmentTileId(), typeStatistics);
		}*/

		Map<Long,SegmentStatistics> hourData = getWeekMap(week);

		if(hourData.containsKey(speedSample.getSegmentId()))
			hourData.get(speedSample.getSegmentId()).addSample(speedSample);
		else {
			SegmentStatistics segmentStatistics = new SegmentStatistics();
			segmentStatistics.addSample(speedSample);
			hourData.put(speedSample.getSegmentId(), segmentStatistics);
		}

		SegmentStatistics segmentStatistics;
		if(cumulativeHourMap.containsKey(speedSample.getSegmentId()))
			segmentStatistics = cumulativeHourMap.get(speedSample.getSegmentId());
		else
			segmentStatistics = new SegmentStatistics();

		segmentStatistics.addSample(speedSample);

		cumulativeHourMap.put(speedSample.getSegmentId(), segmentStatistics);

	}

	public SummaryStatistics collectSummaryStatisics(Long segmentId) {

		SummaryStatistics summaryStatistics = new SummaryStatistics();

		if(cumulativeHourMap.containsKey(segmentId))
			summaryStatistics = cumulativeHourMap.get(segmentId).collectSummaryStatisics();

		return summaryStatistics;
	}

	public SegmentStatistics getSegmentStatisics(Long segmentId) {
		return cumulativeHourMap.get(segmentId);
	}

	public Integer size() {
		return cumulativeHourMap.keySet().size();
	}

	public boolean contains (String id) {
		return cumulativeHourMap.containsKey(id);
	}

	public static String getId(long week, long hour) {
		return week + "_" + hour;
	}
	
}
