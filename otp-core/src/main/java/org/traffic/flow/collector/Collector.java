package org.traffic.flow.collector;


import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.Resource;

import org.opentripplanner.analyst.request.SampleFactory;
import org.opentripplanner.routing.algorithm.GenericAStar;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.State;
import org.opentripplanner.routing.core.TraverseModeSet;
import org.opentripplanner.routing.error.VertexNotFoundException;
import org.opentripplanner.routing.impl.GraphServiceImpl;
import org.opentripplanner.routing.services.GraphService;
import org.opentripplanner.routing.services.SPTService;
import org.opentripplanner.routing.spt.ShortestPathTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.traffic.flow.types.TrafficFlow;
import org.traffic.request.CSVRequestSetting;
import org.traffic.request.Request;
import org.traffic.request.RequestQueue;


/*
 * based on the OTP BatchProcesser class
 */

public class Collector {

	private static final Logger LOG = LoggerFactory.getLogger(Collector.class);

	@Autowired
	private GraphService graphService;
	@Autowired
	private SPTService sptService;
	@Autowired
	private SampleFactory sampleFactory;

	private int searchCutoffSeconds = -1;

	@Resource
	private RoutingRequest prototypeRoutingRequest;

	private int nThreads = (int)(Runtime.getRuntime().availableProcessors());

	private long startTime = -1;
	private long lastLogTime = 0;
	
	public String outputPath = null;
	public String debugOutputPath = null;

	RequestQueue requests = new RequestQueue();
	TrafficFlow flow = null;

	
	public Collector(String graphPath, String outpoutPath,CSVRequestSetting setting,RoutingRequest route){
		
		if (!outpoutPath.contains("{}")) {
            System.out.println("[ERROR] output filename must contain placeholder '{}'.");
            System.exit(-1);
        }
		
		GraphServiceImpl graphServiceImpl = new GraphServiceImpl();
		graphServiceImpl.setPath(graphPath);
		graphServiceImpl.startup();

		this.graphService = graphServiceImpl;
		this.sptService = new GenericAStar();
		
		int graphsize = graphService.getGraph().countEdges();
		
		this.flow = new TrafficFlow(graphsize);
		this.prototypeRoutingRequest = route;
		this.outputPath =  outpoutPath;
		this.requests.loadCSV(setting);
	}

	public void run() {
		
		// Set up a thread pool to execute searches in parallel
		LOG.info("Number of threads: {} thread", nThreads);
		ExecutorService threadPool = Executors.newFixedThreadPool(nThreads);
		// ECS enqueues results in the order they complete (unlike invokeAll,
		// which blocks)
		CompletionService<Void> ecs = new ExecutorCompletionService<Void>(
				threadPool);

		startTime = System.currentTimeMillis();
		int nTasks = 0;

		while(!requests.isEmpty()){
			ecs.submit(new FlowTask(requests.getNext()),null);
			++nTasks;
		}

		LOG.info("created {} tasks.", nTasks);
		int nCompleted = 0;
		try { // pull Futures off the queue as tasks are finished
			while (nCompleted < nTasks) {
				
				if(!ecs.take().isDone()){
					continue;
				}
				++nCompleted;
				
				if(nCompleted%100 == 0){
					LOG.debug("got result {}/{}", nCompleted, nTasks);
					projectRunTime(nCompleted, nTasks);
					
				}
			}
		} catch (InterruptedException e) {
			LOG.warn("run was interrupted after {} tasks", nCompleted);
		}
		threadPool.shutdown();
		
		flow.saveGeoJson(outputPath);

		LOG.info("DONE.");
	}
	

	/**
	 * Cut off the search instead of building a full path tree. Can greatly
	 * improve run times.
	 */
	public void setSearchCutoffMinutes(int minutes) {
		this.searchCutoffSeconds = minutes * 60;
	}

	private void projectRunTime(int current, int total) {
		long currentTime = System.currentTimeMillis();
		// not threadsafe, but the worst thing that will happen is a double log
		// message
		// anyway we are using this in the controller thread now
		if (currentTime > lastLogTime ) {
			lastLogTime = currentTime;
			double runTimeMin = (currentTime - startTime) / 1000.0 / 60.0;
			double projectedMin = (total - current) * (runTimeMin / current);
			LOG.info("received {} results out of {}", current, total);
			LOG.info("running {} min, {} min remaining (projected)",
					runTimeMin, projectedMin);
		}
	}

	private RoutingRequest buildRequest(Request request) {
		
		if (request == null)
			return null;

		RoutingRequest req = prototypeRoutingRequest.clone();
		
		if (searchCutoffSeconds > 0) {
			req.worstTime = req.dateTime
					+ (req.arriveBy ? -searchCutoffSeconds
							: searchCutoffSeconds);
		}

		req.setTo(request.destination);
		req.setFrom(request.origin);

		try {
			req.setRoutingContext(graphService.getGraph());
			return req;
		} catch (VertexNotFoundException vnfe) {
			//LOG.debug("no vertex could be created near the origin point");
			return null;
		}
	}


	/**
	 * A single computation to perform for a single origin. Runnable, not
	 * Callable. We want accumulation to happen in the worker thread. Handling
	 * all accumulation in the controller thread risks amassing a queue of large
	 * result sets.
	 */
	private class FlowTask implements Runnable {

		protected final Request request;

		public FlowTask(Request request) {
			this.request = request;
		}

		@Override
		public void run() {
			RoutingRequest req = buildRequest(this.request);
			if (req != null) {
				ShortestPathTree spt = sptService.getShortestPathTree(req);
				
				//finds the state of the destination vertex
				State state = spt.getState(req.getRoutingContext().toVertex);
				
				//go through each of the time slices a given trip has
				for(String timeslice : request.timeslices){
					//backtracks the path to the origin and adds them to the flow
					flow.backtrackState(state,timeslice);
				}

				req.cleanup();

			}
		}
	}

}
