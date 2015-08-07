package io.opentraffic.engine;

import java.util.*;
import java.util.logging.Logger;

public class TrafficEngineWorker implements Runnable {

	private static final Logger log = Logger.getLogger( TrafficEngineWorker.class.getName() );

	private final Long id;

	private TrafficEngine engine;

	public TrafficEngineWorker(TrafficEngine engine) {
		this.id = UUID.randomUUID().getLeastSignificantBits();
		this.engine = engine;
	}
	
	public Long getId() {
		return this.id;
	}

	@Override
	public void run() {

		// process queue location updates
		while(true) {

			try {
				engine.vehicleState.processLocationUpdates();

				try {
					Thread.sleep(500);
				} catch (Exception e) {

				}
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

}
