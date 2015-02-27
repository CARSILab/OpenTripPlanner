package org.traffic.request;

import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import com.csvreader.CsvReader;

public class RequestQueue {

   public boolean skipHeaders = true;
   
   public Queue<Request> queue = new LinkedList<Request>();
   public Map<String,Request> trips = new HashMap<String,Request>();
   
   public void addRequest(Request request){
	   //getting key by rounding to 5th decimal point with precision of about 1.1 meeter
	   String key =request.getKey(10000);

	   Request trip = trips.get(key);
	   
	   if(trip != null){
		   
		   trip.timeslices.add(request.getSlice());
		   
	   }else{
		   queue.add(request);
		   trips.put(key, request);
	   }
	   
	   
   }
   
   public Request getNext(){
	   return queue.poll();
   }
   
   public boolean isEmpty(){
	   return queue.isEmpty();
   }

   public void loadCSV(CSVRequestSetting setting) {
	   System.out.println("INFO: reading from CSV file:"+setting.filePath);
       try {
           CsvReader reader = new CsvReader(setting.filePath, ',', Charset.forName("UTF8"));
           if (skipHeaders) {
               reader.readHeaders();
           }
           
           SimpleDateFormat timeFormat = new SimpleDateFormat(setting.timeFormat);
           
           //point x and y are expressed in WGS84
           while (reader.readRecord()) {
        	   try{
	               double dest_lat = Double.parseDouble(reader.get(setting.dest_lat));
	               double dest_lon = Double.parseDouble(reader.get(setting.dest_lon));
	               
	               double origin_lat = Double.parseDouble(reader.get(setting.origin_lat));
	               double origin_lon = Double.parseDouble(reader.get(setting.origin_lon));
	               
	               Date dateTime = null;
	               
	               //if the time stamp is malformed or missing we move on
	               try{
	            	   dateTime = timeFormat.parse(reader.get(setting.start_datetime));
	               }catch(Exception e){
	            	   continue;
	               }
	               
	               Request request = new Request(origin_lon, origin_lat, dest_lon, dest_lat,dateTime);
	               this.addRequest(request);
        	   }catch(Exception e){
        		   System.out.println("WARNING: unable to load a row from CSV");
        	   }
           }
           reader.close();
       } catch (Exception e) {
           System.out.println("ERROR: exception while loading request from CSV file:"+setting.filePath);
           e.printStackTrace();
       }
   }

}
