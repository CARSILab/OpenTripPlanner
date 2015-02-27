package org.traffic.request;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.opentripplanner.common.model.GenericLocation;

public class Request {
	
	public long id = -1;
	
	public Date dateTime;
	public int currentSlice = 1;
	public List<String> timeslices = new ArrayList<String>();
	
	public GenericLocation origin;
	public GenericLocation destination;
	
	
	
	public Request(double origin_lon,double origin_lat,double dest_lon, double dest_lat, Date dateTime){

		this.dateTime = dateTime;
		
		this.destination = new GenericLocation(dest_lat,dest_lon);
		
		this.origin = new GenericLocation(origin_lat,origin_lon);
		
		this.calculateTimeSlice();
		
	}
	
	private String getKey(GenericLocation location, int rounder){
		double partOne =  Math.round(location.getLat() * rounder)/(double)rounder;
		double partTwo = Math.round( location.getLng() * rounder)/(double)rounder;
		return String.valueOf(partOne)+":"+String.valueOf(partTwo);
		
	}
	
	public String getKey(int rounder){
		return getKey(origin,rounder)+":"+getKey(destination,rounder);
	}
	
	public String getSlice(){
		if(timeslices.size() < currentSlice){
			return "0";
		}
		String slice = timeslices.get(currentSlice-1);
		currentSlice++;
		
		return slice;
	}
	
	private void calculateTimeSlice(){
		//round time by every 15 mins (15*60*1000) == 900000
		long timestamp = Math.round( (double)( (double)this.dateTime.getTime()/(double)(900000) )) * (900000) ;
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(timestamp);
		this.timeslices.add(cal.get(Calendar.DAY_OF_WEEK)+"_"+cal.get(Calendar.HOUR_OF_DAY)+"_"+cal.get(Calendar.MINUTE));
	}
}
