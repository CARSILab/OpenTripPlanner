package org.traffic.flow.types;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.jdom.Parent;
import org.opentripplanner.analyst.core.Sample;
import org.opentripplanner.common.MavenVersion;
import org.opentripplanner.routing.core.State;
import org.opentripplanner.routing.graph.Edge;
import org.opentripplanner.routing.spt.ShortestPathTree;
import org.traffic.flow.core.Path;
import org.traffic.request.Request;


public class TrafficFlow implements Serializable {

    private static final long serialVersionUID = MavenVersion.VERSION.getUID();
    private final MavenVersion mavenVersion = MavenVersion.VERSION;
	
	Map<String, Flow> flows = null;
	int graphSize = 0;

	public TrafficFlow(int graphSize) {
		this.flows = new ConcurrentHashMap<String, Flow>();
		this.graphSize = graphSize;
	}
	
	

	// Reconstructs the path from a given state
	public void backtrackState(State state, String timeslice) {
		
		
		//checking if time entry exists if not create one
		Flow flow = this.flows.get(timeslice);
		
		if (flow == null) {
			flow = new Flow((int)this.graphSize/4);
			flow.timeslice = timeslice;
		    this.flows.put(timeslice, flow);
		}
		
	    flow.backtrackState(state);
	}
	

    //going through each flow and saveing it to a file
	public void saveGeoJson(String outputpath){
		
		if (!outputpath.contains("{}")) {
            System.out.println("[ERROR] output filename must contain placeholder '{}'.");
            System.exit(-1);
        }
		
		for(Entry<String, Flow> flow : flows.entrySet()) {
		    String key = flow.getKey();
		    Flow value = flow.getValue();
		    
		    value.saveGeoJson(outputpath.replace("{}", "_"+key));
		}
		
	}




}
