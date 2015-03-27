package com.conveyal.trafficengine;

import java.io.Serializable;

public class SampleBucket implements Serializable{
	int TOP_SPEED = 35;
	double BUCKET_WIDTH = 0.5;
	
	private static final long serialVersionUID = 2703516691932416832L;
	public int count;
	public double mean;
	public int[] buckets = new int[(int)(TOP_SPEED/BUCKET_WIDTH)+1];
	
	SampleBucket(){
		count=0;
		mean=0;
	}

	public void update(SpeedSample ss) {
		mean = (mean*count + ss.speed)/(count+1);
		count += 1;
		
		if( ss.speed < TOP_SPEED ){
			int bucket = (int)(ss.speed/BUCKET_WIDTH);
			buckets[bucket] += 1;
		}
	}
	
	public String toString(){
		return "[sample bucket count:"+this.count+" mean:"+this.mean+"]";
	}

}
