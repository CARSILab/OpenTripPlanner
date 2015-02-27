package org.traffic.flow.types;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;

import org.opentripplanner.analyst.core.Sample;
import org.opentripplanner.common.MavenVersion;
import org.opentripplanner.routing.core.State;
import org.opentripplanner.routing.spt.ShortestPathTree;
import org.traffic.flow.core.StreetList;



public class Flow implements Serializable {

    private static final long serialVersionUID = MavenVersion.VERSION.getUID();
    private final MavenVersion mavenVersion = MavenVersion.VERSION;

	public StreetList traffic = null;
	public String timeslice= "0";
	
	public Flow(int size) {
		this.traffic = new StreetList(size);
	}

	public void saveGeoJson(String path) {
		this.saveToFile(path, this.toGeoJson());
	}
	
	// processes Batch Destination samples against the origin shortest path tree
	public void processBatchSample(ShortestPathTree spt, Sample sample) {

		if(sample == null){
			return;
		}

		State state = spt.getState(sample.v0);

		if (state == null) {
			state = spt.getState(sample.v1);
		}

		// incase the second point is not set as well
		if (state != null) {
			this.backtrackState(state);
		}
		

	}

	// Reconstructs the path from a given state
	public void backtrackState(State state) {
		while (state != null) {

			// adding edge to aggregator
			this.traffic.add(state);

			state = state.getBackState();
		}
	}
	
	public String toGeoJson() {
		StringBuilder stringBuilder = new StringBuilder();

		//geojson shell
		stringBuilder.append("{\n \"type\": \"FeatureCollection\",\n \"features\": [\n");
		
		stringBuilder = traffic.toGeoJson(stringBuilder);
		
		//end of geojson shell
		stringBuilder.append("] }");

		return stringBuilder.toString();
	}

	
	public void saveToFile(String path, String content) {
		FileWriter fileWriter = null;
		try {
			File newTextFile = new File(path);
			fileWriter = new FileWriter(newTextFile);
			fileWriter.write(content);
			fileWriter.close();
		} catch (IOException ex) {
			System.out.println("ERROR: Uable to write to path: " + path);
		} finally {
			try {
				fileWriter.close();
				System.out.println("Success: Saved file: " + path);
			} catch (IOException ex) {
				System.out.println("ERROR: Uable to close file: " + path);
			}
		}
	}
	
	
}
