package com.conveyal.traffic.data;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.mapdb.BTreeKeySerializer;
import org.mapdb.BTreeMap;
import org.mapdb.DB;
import org.mapdb.DB.BTreeMapMaker;
import org.mapdb.DBMaker;
import org.mapdb.Fun;
import org.mapdb.Pump;
import org.mapdb.Fun.Tuple2;

import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.index.quadtree.Quadtree;

public class SpatialDataStore {

	DB db;
	Map<String,SpatialDataItem> map;
	
	private Quadtree spatialIndex = new Quadtree();
	
	public static String dataPath = null;
	
	/**
	 * Create a new DataStore.
	 * @param directory Where should it be created?
	 * @param dataFile What should it be called?
	 * @param transactional Should MapDB's transactional support be enabled?
	 * @param weakRefCache Should we use a weak reference cache instead of the default fixed-size cache?
	 * @param useJavaSerialization Should java serialization be used instead of mapdb serialization (more tolerant to class version changes)?
	 */
	public SpatialDataStore(File directory, String dataFile, boolean transactional, boolean weakRefCache, boolean useJavaSerialization) {
	
		if(!directory.exists())
			directory.mkdirs();
		
		DBMaker dbm = DBMaker.newFileDB(new File(directory, dataFile + ".db"))
			.closeOnJvmShutdown();
		
		if (!transactional)
			dbm = dbm.transactionDisable();
		
		if (weakRefCache)
			dbm = dbm.cacheWeakRefEnable();
		
	    db = dbm.make();
		
	    BTreeMapMaker maker = db.createTreeMap(dataFile);
	    
	    // this probably ought to cache the serializer.
	    //if (useJavaSerialization)
	    maker = maker.valueSerializer(new ClassLoaderSerializer());
	    
		map = maker.makeOrGet();
		
		reindex();
	}
	
	public void save(SpatialDataItem obj) {
		map.put(obj.id, obj);
		spatialIndex.insert(obj.geometry.getEnvelopeInternal(), obj);
		db.commit();
	}
	
	public void saveWithoutCommit(SpatialDataItem obj) {
		map.put(obj.id, obj);
		spatialIndex.insert(obj.geometry.getEnvelopeInternal(), obj);
	}
	
	public void commit() {
		db.commit();
	}
	
	public void delete(SpatialDataItem obj) {
		map.remove(obj.id);
		spatialIndex.remove(obj.geometry.getEnvelopeInternal(), obj);
		db.commit();
	}

	public SpatialDataItem getById(String id) {
		return map.get(id); 
	}
	
	public List<SpatialDataItem> getByEnvelope(Envelope env) {
		
		List<SpatialDataItem> possibleItems = spatialIndex.query(env);
		List<SpatialDataItem> actualItems = new ArrayList<>();

		for(SpatialDataItem item : possibleItems)
			if(env.intersects(item.geometry.getEnvelopeInternal()))
				actualItems.add(item);

		return actualItems;
		
	}
	
	public Collection<SpatialDataItem> getAll() {
		return map.values();
	}
	
	public void reindex() {	
		 for(SpatialDataItem obj : getAll()) {
			 Envelope env = obj.geometry.getEnvelopeInternal();
			 if(env.getArea() > 0)
			 	spatialIndex.insert(env, obj);
		 }
		 
	}
	
	public Collection<Entry<String, SpatialDataItem>> getEntries () {
		return map.entrySet();
	}
	
	public Integer size() {
		return map.keySet().size();
	}
	
	public boolean contains (String id) {
		return map.containsKey(id);
	}
	
}
