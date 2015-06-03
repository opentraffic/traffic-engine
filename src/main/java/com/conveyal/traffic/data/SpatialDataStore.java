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
		
		/*try {
			Logger.info(directory.getCanonicalPath());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}*/
		
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
	    //	maker = maker.valueSerializer(new ClassLoaderSerializer());
	    
		map = maker.makeOrGet();
		
		reindex();
	}
	
	// TODO: add all the other arguments about what kind of serialization, transactions, etc.
	public SpatialDataStore(File directory, String dataFile, List<Fun.Tuple2<String,SpatialDataItem>>inputData) {
		
		if(!directory.exists())
			directory.mkdirs();
		
		/*try {
			Logger.info(directory.getCanonicalPath());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}*/
		
		db = DBMaker.newFileDB(new File(directory, dataFile + ".db"))
			.transactionDisable()
			.closeOnJvmShutdown()
	        .make();
        
		Comparator<Tuple2<String, SpatialDataItem>> comparator = new Comparator<Fun.Tuple2<String,SpatialDataItem>>(){

			@Override
			public int compare(Tuple2<String, SpatialDataItem> o1,
					Tuple2<String, SpatialDataItem> o2) {
				return o1.a.compareTo(o2.a);
			}
		};

		// need to reverse sort list
		Iterator<Fun.Tuple2<String,SpatialDataItem>> iter = Pump.sort(inputData.iterator(),
                true, 100000,
                Collections.reverseOrder(comparator), //reverse  order comparator
                db.getDefaultSerializer()
                );
		
		
		BTreeKeySerializer<String> keySerializer = BTreeKeySerializer.STRING;
		
		map = db.createTreeMap(dataFile)
        	.pumpSource(iter)
        	.pumpPresort(100000) 
        	.keySerializer(keySerializer)
        	.make();
		
		// close/flush db 
		db.close();
		
		// re-connect with transactions enabled
		db = DBMaker.newFileDB(new File(directory, dataFile + ".db"))
				.closeOnJvmShutdown()
		        .make();
		
		map = db.getTreeMap(dataFile);
		
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
		
		List<SpatialDataItem> items = spatialIndex.query(env);
		
		return items; 
		
	}
	
	public Collection<SpatialDataItem> getAll() {
		return map.values();
	}
	
	public void reindex() {	
		 for(SpatialDataItem obj : getAll()) {
			 spatialIndex.insert(obj.geometry.getEnvelopeInternal(), obj);
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
