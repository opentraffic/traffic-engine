package com.conveyal.traffic.data;

import com.conveyal.traffic.stats.SegmentStatistics;
import com.conveyal.traffic.stats.SpeedSample;
import com.conveyal.traffic.stats.SummaryStatistics;
import org.mapdb.DB;
import org.mapdb.DB.BTreeMapMaker;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

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

	Map<Long, Map<Integer,SegmentStatistics>> weekTypeMap = new ConcurrentHashMap<>();
	Map<Integer,SegmentStatistics> cumulativeTypeMap;

	Map<Long, Map<String,SegmentStatistics>> weekHourMap = new ConcurrentHashMap<>();
	Map<String,SegmentStatistics> cumulativeHourMap;

	Queue<SpeedSample> sampleQueue = new ConcurrentLinkedQueue<>();

	/**
	 * Create a new DataStore.
	 * @param directory Where should it be created?
	 */
	public StatsDataStore(File directory) {

		if(!directory.exists())
			directory.mkdirs();

		DBMaker dbm = DBMaker.newFileDB(new File(directory, "stats.db"))
			.closeOnJvmShutdown();

		dbm = dbm.cacheWeakRefEnable();

	    db = dbm.make();

		Map<String, Object> maps =  db.getAll();
		for(String mapId : maps.keySet()) {
			if(mapId.startsWith("week_")) {
				Long week = Long.parseLong(mapId.replace("week_", ""));
				weekHourMap.put(week, (Map<String,SegmentStatistics>)maps.get(mapId));
			}

			if(mapId.startsWith("type_")) {
				Long week = Long.parseLong(mapId.replace("type_", ""));
				weekTypeMap.put(week, (Map<Integer, SegmentStatistics>) maps.get(mapId));
			}
		}

		BTreeMapMaker cumulativeHourMaker = db.createTreeMap("cumulativeHourMap");
		cumulativeHourMaker = cumulativeHourMaker.valueSerializer(Serializer.JAVA);
		cumulativeHourMap = cumulativeHourMaker.makeOrGet();

		BTreeMapMaker cumulativeTypeMaker = db.createTreeMap("cumulativeTypeMap");
		cumulativeTypeMaker = cumulativeTypeMaker.valueSerializer(Serializer.JAVA);
		cumulativeTypeMap = cumulativeTypeMaker.makeOrGet();

		executor = Executors.newFixedThreadPool(1);

		Runnable statsCollector = () -> {

			int penedingCommit = 0;
			while(true) {
				try {
					SpeedSample speedSample = sampleQueue.poll();
					if(speedSample == null) {
						if(penedingCommit > 0){
							commit();
							penedingCommit = 0;
						}

						Thread.sleep(5000);
					}
					else {
						this.save(speedSample, false);
						penedingCommit++;

						if(penedingCommit > 10000) {
							commit();
							penedingCommit = 0;
						}
					}
				}
				catch (Exception e) {
					e.printStackTrace();
				}
			}
		};

		executor.execute(statsCollector);
	}

	public Map<String,SegmentStatistics> getWeekMap(long week) {

		String key = "week_" + week;

		if(!weekHourMap.containsKey(week)) {
			BTreeMapMaker hourMaker = db.createTreeMap(key);
			hourMaker = hourMaker.valueSerializer(Serializer.JAVA);

			Map<String,SegmentStatistics> weekMap = hourMaker.makeOrGet();
			weekHourMap.put(week, weekMap);
		}

		return weekHourMap.get(week);
	}

	public Map<Integer,SegmentStatistics> getTypeMap(long week) {

		String key = "type_" + week;

		if(!cumulativeTypeMap.containsKey(week)) {
			BTreeMapMaker hourMaker = db.createTreeMap(key);
			hourMaker = hourMaker.valueSerializer(Serializer.JAVA);

			Map<Integer,SegmentStatistics> weekMap = hourMaker.makeOrGet();
			weekTypeMap.put(week, weekMap);
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

	public void save(long time, String segmentId, int segmentType, SegmentStatistics segmentStats) {
		save(time, segmentId, segmentType, segmentStats, true);
	}

	public void save(long time, String segmentId, int segmentType, SegmentStatistics segmentStats, boolean commit) {

		long week = SegmentStatistics.getWeekSinceEpoch(time);

		Map<Integer,SegmentStatistics> typeData = getTypeMap(week);

		if(typeData.containsKey(segmentType))
			typeData.get(segmentType).addStats(segmentStats);
		else
			typeData.put(segmentType, segmentStats);

		Map<String,SegmentStatistics> hourData = getWeekMap(week);

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

		if(commit)
			commit();
	}

	public void save(SpeedSample speedSample, boolean commit) {

		long week = SegmentStatistics.getWeekSinceEpoch(speedSample.getTime());

		Map<Integer,SegmentStatistics> typeData = getTypeMap(week);

		if(typeData.containsKey(speedSample.getSegmentType()))
			typeData.get(speedSample.getSegmentType()).addSample(speedSample);
		else {
			SegmentStatistics segmentStatistics = new SegmentStatistics();
			segmentStatistics.addSample(speedSample);
			typeData.put(speedSample.getSegmentType(), segmentStatistics);
		}

		Map<String,SegmentStatistics> hourData = getWeekMap(week);

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

		if(commit)
			commit();
	}


	public void commit() {
		db.commit();
	}

	public SummaryStatistics collectSummaryStatisics(String segmentId) {

		SummaryStatistics summaryStatistics = new SummaryStatistics();

		if(cumulativeHourMap.containsKey(segmentId))
			summaryStatistics = cumulativeHourMap.get(segmentId).collectSummaryStatisics();

		return summaryStatistics;
	}

	public SegmentStatistics getSegmentStatisics(String segmentId) {
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
