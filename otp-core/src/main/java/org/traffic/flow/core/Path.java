package org.traffic.flow.core;

import java.io.Serializable;

import org.opentripplanner.common.MavenVersion;
import org.opentripplanner.routing.graph.Edge;

public class Path implements Serializable {

    private static final long serialVersionUID = MavenVersion.VERSION.getUID();

	public Edge edge = null;
	
	public long edge_id;
	
	public long usage = 1;
	
	public Path(Edge edge){
		this.edge_id = edge.getId();
		this.edge = edge;
	}
	
	public void use(){
		this.usage++;
	}
	
	public String toGeoJson(){
		try{
		return  "{ \"type\": \"Feature\", \"geometry\": \n"
				+ "{\"type\": \"LineString\",\n"
	        +"\"coordinates\": [\n"
	          	+"["+edge.getFromVertex().getLon()+","+edge.getFromVertex().getLat()+"],\n"
	          	+"["+edge.getToVertex().getLon()+", "+edge.getToVertex().getLat()+"]\n"
	          +"]\n"
	        +"},\n"
	      +"\"properties\": {\n"
	        +"\"flow\":"+this.usage+",\n"
	        +"\"id\":"+this.edge_id+"\n"
	        +"}\n"
	     +" }\n";
		}catch(Exception e){
			//System.out.println("WARNING: Uable to Converting a Street to Geo Json");
		}
		return null;
	}
}
