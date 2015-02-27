package org.traffic.flow.core;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.opentripplanner.common.MavenVersion;
import org.opentripplanner.routing.graph.Edge;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.core.State;

public class StreetList implements Serializable {

    private static final long serialVersionUID = MavenVersion.VERSION.getUID();

	public Map<Integer, Path> streets = null;
	public Graph graph = null;


	public StreetList(int size) {
		this.streets = new ConcurrentHashMap<Integer, Path>(size);
	}
	
	// adds a edge
	public void add(State state) {
		Edge edge = state.getBackEdge();

		if (edge == null) {
			return;
		}
		Path street = streets.get(edge.getId());
		if (street != null) {
			// increases the internal counter
			street.use();
		} else {
			street = new Path(edge);
			streets.put(edge.getId(), street);
		}

	}
	
	public StringBuilder toGeoJson(StringBuilder stringBuilder) {
		boolean first = true;
		//geojson content
		for (Path street : streets.values()) {
			
			String json = street.toGeoJson();
			if( json != null){
				if (!first) {
					stringBuilder.append(",");
				}else{
					first = false;
				}
				stringBuilder.append(street.toGeoJson());
			}
		}

		return stringBuilder;
	}
	
	
	public Collection<Path> values(){
		return this.streets.values();
	}
	

}
