package com.conveyal.traffic.data.stores;

import com.conveyal.traffic.data.SpatialDataItem;
import com.conveyal.traffic.data.stores.SpatialDataStore;
import com.conveyal.traffic.geom.StreetSegment;
import org.mapdb.*;
import org.mapdb.DB.BTreeMapMaker;
import org.mapdb.Fun.Tuple3;

import java.io.File;
import java.util.*;


public class StreetDataStore extends SpatialDataStore {

	public Map<Tuple3<Long, Long, Long>, Long> segmentIndex;
	public Map<Long, Integer> segmentTypeMap;

	public StreetDataStore(File directory, String dataFile, Serializer serializer, Integer cacheSize) {
		super(directory, dataFile, serializer, cacheSize);


		BTreeMapMaker idMapMaker = db.createTreeMap(dataFile + "_segmentIndex")
				.valueSerializer(Serializer.LONG)
				.keySerializer(BTreeKeySerializer.TUPLE3);

		segmentIndex = idMapMaker.makeOrGet();

		BTreeMapMaker segmentTypeMapMaker = db.createTreeMap(dataFile + "_segmentTypeIndex")
				.valueSerializer(Serializer.INTEGER)
				.keySerializer(BTreeKeySerializer.ZERO_OR_POSITIVE_LONG);

		segmentTypeMap = segmentTypeMapMaker.makeOrGet();
	}
	
	public void save(SpatialDataItem obj) {
		Tuple3 segmentId = ((StreetSegment)obj).getSegmentId();
		if (segmentIndex.containsKey(segmentId))
			return;

		segmentIndex.put(segmentId, obj.id);

		super.save(obj);

	}

	public int getSegmentTypeById(long id) {
		if(!segmentTypeMap.containsKey(id)) {
			StreetSegment segment = (StreetSegment)map.get(id);
			segmentTypeMap.put(id, segment.streetType);
		}

		return segmentTypeMap.get(id);
	}

	@Override
	public void save(List<SpatialDataItem> objs) {

		List<SpatialDataItem> segments = new ArrayList<SpatialDataItem>();
		for(SpatialDataItem obj : objs) {

			Tuple3 segmentId = ((StreetSegment)obj).getSegmentId();
			if (segmentIndex.containsKey(segmentId))
				continue;

			segments.add(obj);

			segmentIndex.put(segmentId, obj.id);
		}

		super.save(segments);
	}

	@Override
	public void delete(List<SpatialDataItem> objs) {
		List<SpatialDataItem> segments = new ArrayList<SpatialDataItem>();
		for(SpatialDataItem obj : objs) {

			Tuple3 segmentId = ((StreetSegment)obj).getSegmentId();
			if (!segmentIndex.containsKey(segmentId))
				continue;

			segments.add(obj);

			segmentIndex.remove(segmentId);
		}

		super.delete(segments);
	}

	@Override
	public void delete(SpatialDataItem obj) {
		Tuple3 segmentId = ((StreetSegment)obj).getSegmentId();
		segmentIndex.remove(segmentId);

		super.delete(obj);
	}

	public SpatialDataItem getBySegmentId(Tuple3<Long, Long, Long> segmentId) {
		if(!segmentIndex.containsKey(segmentId))
			return null;
		return map.get(segmentIndex.get(segmentId));
	}

	public boolean contains(Tuple3<Long, Long, Long> segmentId) {
		return segmentIndex.containsKey(segmentId);
	}

}
