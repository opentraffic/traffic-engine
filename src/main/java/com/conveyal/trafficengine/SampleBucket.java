package com.conveyal.trafficengine;

import java.io.Serializable;

public class SampleBucket implements Serializable{
	private static final long serialVersionUID = 2703516691932416832L;
	int count;
	double mean;
	
	SampleBucket(){
		count=0;
		mean=0;
	}

	public void update(SpeedSample ss) {
		mean = (mean*count + ss.speed)/(count+1);
		count += 1;
	}

}
