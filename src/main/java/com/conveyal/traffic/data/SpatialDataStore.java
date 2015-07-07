package com.conveyal.traffic.data;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;

import org.mapdb.*;
import org.mapdb.DB.BTreeMapMaker;
import org.mapdb.Fun.Tuple2;

import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.index.quadtree.Quadtree;

public class SpatialDataStore {

	public static int Z_INDEX = 18;

	DB db;
	BTreeMap<Long,SpatialDataItem> map;

	/** <<patternId, calendarId>, trip id> */
	public NavigableSet<Tuple2<Tuple2<Integer, Integer>, Long>> tileIndex;

	/**
	 * Create a new DataStore.
	 * @param directory Where should it be created?
	 * @param dataFile What should it be called?
	 */
	public SpatialDataStore(File directory, String dataFile, Serializer serializer) {
	
		if(!directory.exists())
			directory.mkdirs();
		
		DBMaker dbm = DBMaker.newFileDB(new File(directory, dataFile + ".db"))
				.closeOnJvmShutdown()
				.mmapFileEnable()
				.cacheLRUEnable()
				.cacheSize(1000000);

		db = dbm.make();

		
	    BTreeMapMaker maker = db.createTreeMap(dataFile);

	    maker = maker.valueSerializer(serializer)
				.keySerializer(BTreeKeySerializer.ZERO_OR_POSITIVE_LONG);

		map = maker.makeOrGet();

		tileIndex = db.createTreeSet(dataFile + "_tileIndex").makeOrGet();

		// bind tile indexes to item
		Bind.secondaryKeys(map, tileIndex, (key, spatialDataItem) -> spatialDataItem.getTiles(Z_INDEX));
	}
	
	public void save(SpatialDataItem obj) {
		map.put(obj.id, obj);
	}

	public void delete(SpatialDataItem obj) {
		map.remove(obj.id);
	}

	public SpatialDataItem getById(Long id) {
		return map.get(id); 
	}
	
	public List<SpatialDataItem> getByEnvelope(Envelope env) {

		List<SpatialDataItem> items = new ArrayList();
		HashSet<Long> foundItems = new HashSet();

		for(Tuple2 tile : getTilesForEnvelope(env)) {
			for(Object o : Fun.filter(tileIndex, tile)) {
				if (!foundItems.contains(o)) {
					foundItems.add((Long)o);
					items.add(map.get(o));
				}
			}
		}

		return items;
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

	public Fun.Tuple2<Integer, Integer>[] getTilesForEnvelope(Envelope env) {

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

		int tileCount = (Math.abs(maxX - minX) + 1) * (Math.abs(maxY - minY) + 1);

		Fun.Tuple2<Integer, Integer>[] tiles = new Fun.Tuple2[tileCount];

		int i = 0;
		for(int tileX = minX; tileX <= maxX; tileX++) {
			for(int tileY = minY; tileY <= maxY; tileY++) {
				tiles[i] = new Fun.Tuple2(tileX, tileY);
				i++;
			}
		}

		return tiles;
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
