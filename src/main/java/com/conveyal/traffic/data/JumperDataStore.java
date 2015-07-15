package com.conveyal.traffic.data;

import com.conveyal.traffic.data.seralizers.JumperSerializer;
import com.conveyal.traffic.data.seralizers.SegmentStatisticsSerializer;
import com.conveyal.traffic.data.seralizers.TypeStatisticsSerializer;
import com.conveyal.traffic.geom.Jumper;
import com.conveyal.traffic.stats.SegmentStatistics;
import com.conveyal.traffic.stats.SpeedSample;
import com.conveyal.traffic.stats.SummaryStatistics;
import com.conveyal.traffic.stats.TypeStatistics;
import org.mapdb.*;
import org.mapdb.DB.BTreeMapMaker;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class JumperDataStore {

	private static final Logger log = Logger.getLogger( JumperDataStore.class.getName());

	DB db;

	Map<Fun.Tuple2<Long, Long>,Jumper> jumperMap = new ConcurrentHashMap<>();
	NavigableSet<Fun.Tuple2<Long, Long>> jumperStartIndex;
	NavigableSet<Fun.Tuple2<Long, Long>> jumperEndIndex;

	/**
	 * Create a new DataStore.
	 * @param directory Where should it be created?
	 */
	public JumperDataStore(File directory) {

		if(!directory.exists())
			directory.mkdirs();

		DBMaker dbm = DBMaker.newFileDB(new File(directory, "jumpers.db"))
				.mmapFileEnableIfSupported()
				.cacheLRUEnable()
				.cacheSize(100000)
				.asyncWriteEnable()
				.asyncWriteFlushDelay(1000)
				.closeOnJvmShutdown();

	    db = dbm.make();

		jumperMap = db.createTreeMap("jumperMap")
				.valueSerializer(new JumperSerializer())
				.makeOrGet();

		jumperStartIndex = db.createTreeSet("startIndex")
				.serializer(BTreeKeySerializer.TUPLE2)
				.makeOrGet();

		jumperEndIndex = db.createTreeSet("endIndex")
				.serializer(BTreeKeySerializer.TUPLE2)
				.makeOrGet();

	}

	public Jumper getJumper(Long startNodeId, Long endNodeId) {
		return jumperMap.get(new Fun.Tuple2<>(startNodeId, endNodeId));
	}

	public void addJumper(Jumper jumper) {

		Fun.Tuple2<Long,Long> jumperId = jumper.getStartEndTuple();

		if(jumperMap.containsKey(jumperId))
			return;

		NavigableSet<Fun.Tuple2<Long, Long>> startSubset = jumperStartIndex.subSet(
				new Fun.Tuple2(jumperId.b, null), true, // inclusive lower bound, null tests lower than anything
				new Fun.Tuple2(jumperId.b, Fun.HI), true  // inclusive upper bound, HI tests higher than anything
		);

		for(Fun.Tuple2<Long, Long> adjacentJumperId : startSubset) {
			Jumper adjacentJumper = jumperMap.get(adjacentJumperId);

			if(adjacentJumper.startNodeId == jumper.endNodeId && adjacentJumper.endNodeId == jumper.startNodeId)
				continue;

			Jumper mergedJumper = adjacentJumper.merge(jumper);
			if(mergedJumper != null) {
				jumperMap.put(mergedJumper.getStartEndTuple(), mergedJumper);
				jumperStartIndex.add(mergedJumper.getStartEndTuple());
				jumperEndIndex.add(mergedJumper.getEndStartTuple());
			}

		}

		NavigableSet<Fun.Tuple2<Long, Long>> endSubset = jumperEndIndex.subSet(
				new Fun.Tuple2(jumperId.a, null), true, // inclusive lower bound, null tests lower than anything
				new Fun.Tuple2(jumperId.a, Fun.HI), true  // inclusive upper bound, HI tests higher than anything
		);

		for(Fun.Tuple2<Long, Long> adjacentJumperId : endSubset) {
			Jumper adjacentJumper = jumperMap.get(new Fun.Tuple2<>(adjacentJumperId.b, adjacentJumperId.a));

			if(adjacentJumper.startNodeId == jumper.endNodeId && adjacentJumper.endNodeId == jumper.startNodeId)
				continue;

			Jumper mergedJumper = adjacentJumper.merge(jumper);
			if(mergedJumper != null) {
				jumperMap.put(mergedJumper.getStartEndTuple(), mergedJumper);
				jumperStartIndex.add(mergedJumper.getStartEndTuple());
				jumperEndIndex.add(mergedJumper.getEndStartTuple());
			}

		}

		jumperMap.put(jumper.getStartEndTuple(), jumper);
		jumperStartIndex.add(jumper.getStartEndTuple());
		jumperEndIndex.add(jumper.getEndStartTuple());


	}

	public void save() {
		db.commit();
	}


}
