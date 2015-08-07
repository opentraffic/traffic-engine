package io.opentraffic.engine.data.stores;

import org.mapdb.Atomic;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import java.io.File;

/**
 * Created by kpw on 8/3/15.
 */
public class IdStore {

    private static final long  INITIAL_VALUE = 1_000_000_000;

    private DB db;
    private Atomic.Long id;


    public IdStore(File directory, String idName) {

        if(!directory.exists())
            directory.mkdirs();

        DBMaker dbm = DBMaker.newFileDB(new File(directory, "id_" + idName + ".db"))
                .mmapFileEnableIfSupported()
                .closeOnJvmShutdown();

        db = dbm.make();

        id = db.createAtomicLong(idName, INITIAL_VALUE);
    }

    public long getNextId() {
        return id.getAndIncrement();
    }

    public long getCurrentCount() {
        return id.get() - INITIAL_VALUE;
    }

}
