package org.traffic.flow;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Properties;

import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.TraverseModeSet;
import org.traffic.flow.collector.BatchCollector;
import org.traffic.flow.collector.Collector;
import org.traffic.request.CSVRequestSetting;



public class TrafficFlowMain {

	public static void main(String[] args) {
		
		if(args.length < 2){
			System.out.println("[ERROR] no arguments passed in");
			return;
		}
		
		
		if(args[0].equals("-b")){
			//running batch collector
			runBatchCollector(args[1]);
			
		}else if(args[0].equals("-a")){
			//running aggrigator
			runCollector(args[1]);
			
		}else{
			System.out.println("[ERROR] unable to recognize arguments passed in");
			return;
		}
		
		//runCollector("/Users/Naz/Desktop/collection.properties");
		//String configFile = "/Users/Naz/Desktop/carson/projects/ireland/batch-context.xml";
		//runBatchCollector(configFile);
		
		 
	}
	
	public static void runBatchCollector(String configFile){
		BatchCollector batchCollector = BatchCollector.setup(configFile);
		batchCollector.run();
	}
	
	
	public static void runCollector(String configFile){
		
		String graphPath = "/var/otp/graphs/";
		String dataPath = null;
		String outputPath = null;
		

		CSVRequestSetting setting = new CSVRequestSetting();
		setting.timeFormat = "yyyy-mm-dd HH:mm:ss";
		setting.start_datetime = 0;
		setting.origin_lat = 1;
		setting.origin_lon = 2;
		setting.dest_lat = 3;
		setting.dest_lon = 4;
		
		RoutingRequest route = new RoutingRequest();
		route.routerId = "";
		route.maxWalkDistance = 2000;
		route.clampInitialWait = 1800;
		route.arriveBy = false;
		route.batch = false;
		route.modes = new TraverseModeSet("CAR");
		
		int searchCutOff = 0;
		
		try {
			Properties prop = readProperties(configFile);
			
			
			if(prop.getProperty("graph_path") == null || prop.getProperty("data_path") == null || prop.getProperty("output_path") == null){
				throw new IOException("Unable to find required properties from file:"+configFile);
			}
			
			graphPath = prop.getProperty("graph_path");
			dataPath = prop.getProperty("data_path");
			outputPath = prop.getProperty("output_path");
			
			if(prop.getProperty("router_id") != null){
				route.routerId =  prop.getProperty("router_id");
			}
			
			if(prop.getProperty("search_cutoff") != null){
				searchCutOff =  Integer.parseInt(prop.getProperty("router_id"));
			}
			
			if(prop.getProperty("datetime_format")!=null){
				setting.timeFormat = prop.getProperty("datetime_format");
			}

			setting.dest_lat = Integer.parseInt(prop.getProperty("dest_lat"));
			setting.dest_lon = Integer.parseInt(prop.getProperty("dest_lon"));
			setting.origin_lat = Integer.parseInt(prop.getProperty("origin_lat"));
			setting.origin_lon = Integer.parseInt(prop.getProperty("origin_lon"));
			setting.start_datetime = Integer.parseInt(prop.getProperty("start_datetime"));
			
			
			
		} catch (IOException e) {
			
			System.out.println("[ERROR] Unable to load properties");
			e.printStackTrace();
		}
		
		setting.filePath = dataPath;
		
		System.out.println("[Info] initilizeing collector");
		Collector processor = new Collector(graphPath,outputPath,setting,route);
		
		if(searchCutOff >0){
			processor.setSearchCutoffMinutes(searchCutOff);
		}
		
		processor.run();
	}
	
	
	
	public static Properties readProperties(String propFileName) throws IOException {
		 
		
		Properties properties = new Properties();
		File file = new File(propFileName);
		FileInputStream fileInput = new FileInputStream(file);

		properties.load(fileInput);
		fileInput.close();
 
		return properties;
	}
	
	 

}
