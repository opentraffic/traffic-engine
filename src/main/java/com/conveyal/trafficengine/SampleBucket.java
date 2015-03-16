package com.conveyal.trafficengine;

import java.io.Serializable;

public class SampleBucket implements Serializable{
	private static final long serialVersionUID = 2703516691932416832L;
	public int count;
	public double mean;
	
	SampleBucket(){
		count=0;
		mean=0;
	}

	public void update(SpeedSample ss) {
		mean = (mean*count + ss.speed)/(count+1);
		count += 1;
	}
	
	public String toString(){
		return "[sample bucket count:"+this.count+" mean:"+this.mean+"]";
	}

}
