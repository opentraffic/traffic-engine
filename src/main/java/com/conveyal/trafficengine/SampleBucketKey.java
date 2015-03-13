package com.conveyal.trafficengine;

import java.io.Serializable;
import java.util.Calendar;

public class SampleBucketKey implements Serializable, Comparable<SampleBucketKey>{
	private static final long serialVersionUID = -7676107981084641015L;
	public long wayId;
	public int startNodeIndex;
	public int endNodeIndex;
	public int hourOfWeek;

	public SampleBucketKey(SpeedSample ss) {
		this.wayId = ss.c0.tripline.wayId;
		this.startNodeIndex = ss.c0.tripline.ndIndex;
		this.endNodeIndex = ss.c1.tripline.ndIndex;
		this.hourOfWeek = getHourOfWeek( ss.c1.timeMillis );
	}

	private int getHourOfWeek(long timeMillis) {
		Calendar c = Calendar.getInstance();
		c.setTimeInMillis( timeMillis );
		
		int dowVal = c.get(Calendar.DAY_OF_WEEK);
		int dow=0;
		switch(dowVal){
		case Calendar.SUNDAY: dow=0; break;
		case Calendar.MONDAY: dow=1; break;
		case Calendar.TUESDAY: dow=2; break;
		case Calendar.WEDNESDAY: dow=3; break;
		case Calendar.THURSDAY: dow=4; break;
		case Calendar.FRIDAY: dow=5; break;
		case Calendar.SATURDAY: dow=6; break;
		}
		
		int hourOfDay = c.get(Calendar.HOUR_OF_DAY);
		
		return dow*24+hourOfDay;
	}

	@Override
	public int compareTo(SampleBucketKey o) {
		long c = this.wayId - o.wayId;
		if(c < 0){
			return -1;
		} else if(c > 0){
			return 1;
		}
		
		int ic = this.endNodeIndex - o.endNodeIndex;
		if( ic != 0 ) {
			return ic;
		}
		
		ic = this.startNodeIndex - o.startNodeIndex;
		if( ic != 0 ){
			return ic;
		}
		
		ic = this.hourOfWeek - o.hourOfWeek;
		if( ic != 0 ){
			return ic;
		}
		
		return 0;
		
	}
	
	public String toString(){
		return "[bucket "+this.wayId+":"+this.startNodeIndex+"-"+this.endNodeIndex+" "+this.hourOfWeek+"]";
	}

}
