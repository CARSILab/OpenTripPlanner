package org.traffic.request;

public class CSVRequestSetting {
	
	//holds the path to the csv
	public String filePath;
	
	//column number for the request time
	public int time;
	
	//column number for the destination longitude
	public int dest_lon;
	//column number for the destination latitude
	public int dest_lat;
	
	//column number for the origin longitude
	public int origin_lon;
	//column number for the origin latitude
	public int origin_lat;
	
	//column number for when the trip was started
	public String timeFormat;
	public int start_datetime;
	

}
