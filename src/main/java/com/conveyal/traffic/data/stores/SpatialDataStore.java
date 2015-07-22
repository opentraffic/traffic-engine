package com.conveyal.traffic.data.stores;

import java.io.File;
import java.time.ZoneId;
import java.util.*;
import java.util.Map.Entry;

import com.conveyal.traffic.data.SpatialDataItem;
import org.mapdb.*;
import org.mapdb.DB.BTreeMapMaker;
import org.mapdb.Fun.Tuple3;

import com.vividsolutions.jts.geom.Envelope;


public class SpatialDataStore {

	public static int Z_INDEX = 18;

	DB db;
	BTreeMap<Long,SpatialDataItem> map;

	String dataFile;

	/** <<patternId, calendarId>, trip id> */
	public NavigableSet<Tuple3<Integer, Integer, Long>> tileIndex;

	/**
	 * Create a new DataStore.
	 * @param directory Where should it be created?
	 * @param dataFile What should it be called?
	 */
	public SpatialDataStore(File directory, String dataFile, Serializer serializer, Integer cacheSize) {

		this.dataFile = dataFile;

		if(!directory.exists())
			directory.mkdirs();
		
		DBMaker dbm = DBMaker.newFileDB(new File(directory, dataFile + ".db"))
				.mmapFileEnableIfSupported()
				.cacheLRUEnable()
				.cacheSize(cacheSize)
				.closeOnJvmShutdown();

		db = dbm.make();

	    BTreeMapMaker maker = db.createTreeMap(dataFile)
				.valueSerializer(serializer)
				.keySerializer(BTreeKeySerializer.ZERO_OR_POSITIVE_LONG);

		map = maker.makeOrGet();

		tileIndex = db.createTreeSet(dataFile + "_tileIndex")
				.serializer(BTreeKeySerializer.TUPLE3).makeOrGet();
	}

	public String getStatistics() {
		Store store = Store.forDB(db);
		return dataFile + ": " + store.calculateStatistics();
	}
	
	public void save(SpatialDataItem obj) {
		map.put(obj.id, obj);

		for(Tuple3<Integer, Integer, Long> tuple : obj.getTiles(Z_INDEX)) {
			tileIndex.add(tuple);
		}
		db.commit();
	}

	public void save(List<SpatialDataItem> objs) {
		for(SpatialDataItem obj : objs) {
			if (map.containsKey(obj.id))
				continue;

			map.put(obj.id, obj);

			for (Tuple3<Integer, Integer, Long> tuple : obj.getTiles(Z_INDEX)) {
				tileIndex.add(tuple);
			}
		}
		db.commit();
	}

	public void delete(List<SpatialDataItem> objs) {
		for (SpatialDataItem obj : objs) {
			if(!map.containsKey(obj.id))
				continue;

			map.remove(obj.id);
			for (Tuple3<Integer, Integer, Long> tuple : obj.getTiles(Z_INDEX)) {
				tileIndex.remove(tuple);
			}
		}
		db.commit();
	}

	public void delete(SpatialDataItem obj) {
		map.remove(obj.id);
		for(Tuple3<Integer, Integer, Long> tuple : obj.getTiles(Z_INDEX)) {
			tileIndex.remove(tuple);
		}
		db.commit();
	}

	public SpatialDataItem getById(Long id) {
		return map.get(id); 
	}

	public List<SpatialDataItem> getByEnvelope(Envelope env) {

		List<Long> ids = getIdsByEnvelope(env);
		List<SpatialDataItem> items = new ArrayList<>();
		for (long id : ids) {
			items.add(map.get(id));
		}

		return items;
	}

	public List<Long> getIdsByEnvelope(Envelope env) {

		int y1 = getTileY(env.getMinY(), Z_INDEX);
		int x1 = getTileX(env.getMinX(), Z_INDEX);
		int y2 = getTileY(env.getMaxY(), Z_INDEX);
		int x2 = getTileX(env.getMaxX(), Z_INDEX);

		int minY;
		int maxY;
		int minX;
		int maxX;

		if(x1 < x2) {
			minX = x1;
			maxX = x2;
		} else {
			minX = x2;
			maxX = x1;
		}

		if(y1 < y2) {
			minY = y1;
			maxY = y2;
		} else {
			minY = y2;
			maxY = y1;
		}

		minX--;
		maxX++;

		minY--;
		maxY++;

		List<Long> ids = new ArrayList();

		for(int tileX = minX; tileX <= maxX; tileX++) {
			NavigableSet<Tuple3<Integer, Integer, Long>> xSubset = tileIndex.subSet(
					new Tuple3(tileX, minY, null), true, // inclusive lower bound, null tests lower than anything
					new Tuple3(tileX, maxY, Fun.HI), true  // inclusive upper bound, HI tests higher than anything
			);

			for (Tuple3<Integer, Integer, Long> item : xSubset) {
				ids.add(item.c);
			}
		}

		return ids;
	}
	
	public Collection<SpatialDataItem> getAll() {
		return map.values();
	}

	public Collection<Entry<Long, SpatialDataItem>> getEntries () {
		return map.entrySet();
	}
	
	public Integer size() {
		return map.keySet().size();
	}
	
	public boolean contains (Long id) {
		return map.containsKey(id);
	}



	public static int getTileX(final double lon, final int zoom) {
		int xtile = (int)Math.floor( (lon + 180) / 360 * (1<<zoom) ) ;
		return xtile;
	}

	public static int getTileY(final double lat, final int zoom) {
		int ytile = (int)Math.floor( (1 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2 * (1<<zoom) );

		return ytile;
	}

	public static double tile2lon(int x, int z) {
		return x / Math.pow(2.0, z) * 360.0 - 180;
	}

	public static double tile2lat(int y, int z) {
		double n = Math.PI - (2.0 * Math.PI * y) / Math.pow(2.0, z);
		return Math.toDegrees(Math.atan(Math.sinh(n)));
	}

	public static Envelope tile2Envelope(final int x, final int y, final int zoom) {
		double maxLat = tile2lat(y, zoom);
		double minLat = tile2lat(y + 1, zoom);
		double minLon = tile2lon(x, zoom);
		double maxLon = tile2lon(x + 1, zoom);
		return new Envelope(minLon, maxLon, minLat, maxLat);
	}
	
}
