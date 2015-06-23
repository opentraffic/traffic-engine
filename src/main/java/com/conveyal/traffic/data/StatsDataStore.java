package com.conveyal.traffic.data;

import com.conveyal.traffic.stats.SegmentStatistics;
import com.conveyal.traffic.stats.SegmentTimeBins;
import com.conveyal.traffic.stats.SpeedSample;
import com.conveyal.traffic.stats.SummaryStatistics;
import org.mapdb.DB;
import org.mapdb.DB.BTreeMapMaker;
import org.mapdb.DBMaker;

import java.io.File;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.logging.Level;

public class StatsDataStore {

	private static final Logger log = Logger.getLogger( StatsDataStore.class.getName());

	DB db;

	ExecutorService executor;

	Map<String, Object> weekHourMap = new ConcurrentHashMap<>();
	Map<String,SegmentStatistics> cumulativeHourMap;

	Queue<SpeedSample> sampleQueue = new ConcurrentLinkedQueue<SpeedSample>();

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

		weekHourMap = db.getAll();

		BTreeMapMaker cumulativeHourMaker = db.createTreeMap("cumulativeHourMap");
		cumulativeHourMaker = cumulativeHourMaker.valueSerializer(new ClassLoaderSerializer());

		cumulativeHourMap = cumulativeHourMaker.makeOrGet();

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

						if(penedingCommit > 1000) {
							commit();
							penedingCommit = 0;
						}
					}
				}
				catch (Exception e) {
					log.log(Level.SEVERE, e.toString());
				}
			}
		};

		executor.execute(statsCollector);
	}

	public Map<String,SegmentStatistics> getWeekMap(long week) {

		String key = "week_" + week;

		if(!weekHourMap.containsKey(key)) {
			BTreeMapMaker hourMaker = db.createTreeMap(key);
			hourMaker = hourMaker.valueSerializer(new ClassLoaderSerializer());

			Map<String,SegmentStatistics> weekMap = hourMaker.makeOrGet();
			weekHourMap.put(key, weekMap);
		}

		return (Map<String,SegmentStatistics>)weekHourMap.get(key);
	}

	public void addSpeedSample(SpeedSample speedSample) {
		sampleQueue.add(speedSample);
	}

	public void save(long time, String segmentId, SegmentStatistics segmentStats) {
		save(time, segmentId, segmentStats, true);
	}

	public void save(long time, String segmentId, SegmentStatistics segmentStats, boolean commit) {

		long week = SegmentStatistics.getWeekSinceEpoch(time);

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

	public void save(long time,  Map<String, SegmentStatistics> stats) {

		for(Entry<String,SegmentStatistics> entry : stats.entrySet())
			save(time, entry.getKey(), entry.getValue(), false);

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
