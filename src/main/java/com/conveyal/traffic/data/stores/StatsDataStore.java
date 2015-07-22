package com.conveyal.traffic.data.stores;

import com.conveyal.traffic.data.SpeedSample;
import com.conveyal.traffic.data.seralizers.SegmentStatisticsSerializer;
import com.conveyal.traffic.data.stats.SegmentStatistics;
import com.conveyal.traffic.data.stats.SummaryStatistics;
import org.mapdb.*;
import org.mapdb.DB.BTreeMapMaker;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

public class StatsDataStore {

	private static final Logger log = Logger.getLogger( StatsDataStore.class.getName());

	DB db;

	ExecutorService executor;

	BTreeMap<Fun.Tuple2<Long, Integer>, SegmentStatistics> statsMap;
	Set<Integer> weekSet;

	Queue<SpeedSample> sampleQueue = new ConcurrentLinkedQueue<>();
	AtomicLong processedSamples = new AtomicLong();

	/**
	 * Create a new DataStore.
	 * @param directory Where should it be created?
	 */
	public StatsDataStore(File directory) {

		if(!directory.exists())
			directory.mkdirs();

		DBMaker dbm = DBMaker.newFileDB(new File(directory, "stats.db"))
				.mmapFileEnableIfSupported()
				.cacheWeakRefEnable()
				.cacheSize(100000)
				.compressionEnable()
				.asyncWriteEnable()
				.asyncWriteFlushDelay(1000)
				.closeOnJvmShutdown();

	    db = dbm.make();

		BTreeMapMaker statsMapMaker = db.createTreeMap("statsMap");
		statsMap = statsMapMaker
				.keySerializer(BTreeKeySerializer.TUPLE2)
				.valueSerializer(new SegmentStatisticsSerializer())
				.makeOrGet();

		DB.BTreeSetMaker weekSetMaker = db.createTreeSet("weekSet");

		weekSet = weekSetMaker
				.serializer(BTreeKeySerializer.ZERO_OR_POSITIVE_INT)
				.makeOrGet();

		executor = Executors.newFixedThreadPool(1);

		Runnable statsCollector = () -> {

			int sampleCount = 0;

			while(true) {
				try {

					SpeedSample speedSample = sampleQueue.poll();
					processedSamples.incrementAndGet();

					if(speedSample != null) {
						sampleCount++;
						this.save(speedSample);
					}
					else {
						this.db.commit();
						Thread.sleep(1000);
					}

					if(sampleCount > 100000) {
						this.db.commit();
						sampleCount = 0;
					}
				}
				catch (Exception e) {
					e.printStackTrace();
				}
			}
		};

		executor.execute(statsCollector);

	}

	public String getStatistics() {
		Store store = Store.forDB(db);
		return  "Stats: " + store.calculateStatistics();
	}

	public long getSampleQueueSize() {
		return sampleQueue.size();
	}

	public long getProcessedSamples() {
		return processedSamples.get();
	}


	public List<Integer> getWeekList() {
		List<Integer> list = new ArrayList();
		list.addAll(weekSet);
		return list;
	}

	public boolean weekExists(long week){
		return weekSet.contains(week);
	}

	public void addSpeedSample(SpeedSample speedSample) {
		sampleQueue.add(speedSample);
	}


	public void save(SpeedSample speedSample) {

		synchronized (this) {
			int week = SegmentStatistics.getWeekSinceEpoch(speedSample.getTime());
			int hour = SegmentStatistics.getHourOfWeek(speedSample.getTime());

			weekSet.add(week);

			Fun.Tuple2<Long, Integer> sampleId = new Fun.Tuple2<>(speedSample.getSegmentId(), week);

			SegmentStatistics segmentStatistics;

			segmentStatistics = statsMap.get(sampleId);

			if(segmentStatistics == null)
				segmentStatistics = new SegmentStatistics();

			segmentStatistics.addSample(speedSample);

			statsMap.put(sampleId, segmentStatistics);

		}
	}

	public SummaryStatistics collectSummaryStatistics(Long segmentId, Set<Integer> weeks, Set<Integer>hours) {
		SummaryStatistics summaryStatistics = new SummaryStatistics(true);

		if(weeks == null || weeks.size() == 0) {
			NavigableMap<Fun.Tuple2<Long, Integer>, SegmentStatistics> subMap = statsMap.subMap(new Fun.Tuple2(segmentId, null), true, new Fun.Tuple2(segmentId, Fun.HI), true);
			for(SegmentStatistics stats : subMap.values()) {
				summaryStatistics.add(stats);
			}
		}
		else {
			for(Integer week : weeks) {
				NavigableMap<Fun.Tuple2<Long, Integer>, SegmentStatistics> subMap = statsMap.subMap(new Fun.Tuple2(segmentId, week), true, new Fun.Tuple2(segmentId, week), true);
				for(SegmentStatistics stats : subMap.values()) {
					summaryStatistics.add(stats);
				}

			}
		}

		return summaryStatistics;
	}

	public SummaryStatistics collectSummaryStatistics(Set<Long> segmentIds, Set<Integer> weeks, Set<Integer>hours) {
		SummaryStatistics summaryStatistics = new SummaryStatistics(true);

		for(Long segmentId : segmentIds) {
			if(weeks == null || weeks.size() == 0) {
				NavigableMap<Fun.Tuple2<Long, Integer>, SegmentStatistics> subMap = statsMap.subMap(new Fun.Tuple2(segmentId, null), true, new Fun.Tuple2(segmentId, Fun.HI), true);
				for(SegmentStatistics stats : subMap.values()) {
					summaryStatistics.add(stats);
				}
			}
			else {
				for(Integer week : weeks) {
					NavigableMap<Fun.Tuple2<Long, Integer>, SegmentStatistics> subMap = statsMap.subMap(new Fun.Tuple2(segmentId, week), true, new Fun.Tuple2(segmentId, week), true);
					for(SegmentStatistics stats : subMap.values()) {
						summaryStatistics.add(stats);
					}

				}
			}
		}
		return summaryStatistics;
	}

	public SummaryStatistics getSegmentStatistics(Long segmentId, Integer week) {
		SummaryStatistics summaryStatistics = new SummaryStatistics(true);
		NavigableMap<Fun.Tuple2<Long, Integer>, SegmentStatistics> subMap;

		if(week == null)
			subMap = statsMap.subMap(new Fun.Tuple2(segmentId, null), true, new Fun.Tuple2(segmentId, Fun.HI), true);
		else
			subMap = statsMap.subMap(new Fun.Tuple2(segmentId, week), true, new Fun.Tuple2(segmentId, week), true);

		for(SegmentStatistics stats : subMap.values()) {
			summaryStatistics.add(stats);
		}

		return summaryStatistics;
	}

	public static String getId(long week, long hour) {
		return week + "_" + hour;
	}

	public long size() {
		return statsMap.sizeLong();
	}
	
}
