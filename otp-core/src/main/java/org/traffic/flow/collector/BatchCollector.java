package org.traffic.flow.collector;


import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.TimeZone;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.Resource;

import lombok.Setter;

import org.opentripplanner.analyst.batch.Accumulator;
import org.opentripplanner.analyst.batch.Individual;
import org.opentripplanner.analyst.batch.Population;
import org.opentripplanner.analyst.batch.ResultSet;
import org.opentripplanner.analyst.batch.aggregator.Aggregator;
import org.opentripplanner.analyst.core.Sample;
import org.opentripplanner.analyst.request.SampleFactory;
import org.opentripplanner.common.model.GenericLocation;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.State;
import org.opentripplanner.routing.error.VertexNotFoundException;
import org.opentripplanner.routing.services.GraphService;
import org.opentripplanner.routing.services.SPTService;
import org.opentripplanner.routing.spt.ShortestPathTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.FileSystemResource;
import org.traffic.flow.types.Flow;
import org.traffic.flow.types.TrafficFlow;


/*
 * based on the OTP BatchProcesser class
 */

public class BatchCollector {

	private static final Logger LOG = LoggerFactory
			.getLogger(BatchCollector.class);

	@Autowired
	private GraphService graphService;
	@Autowired
	private SPTService sptService;
	@Autowired
	private SampleFactory sampleFactory;

	@Resource
	private Population origins;
	@Resource
	private Population destinations;
	@Resource
	private RoutingRequest prototypeRoutingRequest;

	@Setter
	private Aggregator aggregator;
	@Setter
	private Accumulator accumulator;
	@Setter
	private int logThrottleSeconds = 4;
	@Setter
	private int searchCutoffSeconds = -1;

	/**
	 * Empirical results for a 4-core processor (with 8 fake hyperthreading
	 * cores): Throughput increases linearly with nThreads, up to the number of
	 * physical cores. Diminishing returns beyond 4 threads, but some
	 * improvement is seen up to 8 threads. The default value includes the
	 * hyperthreading cores, so you may want to set nThreads manually in your
	 * IoC XML.
	 */
	@Setter
	private int nThreads = Runtime.getRuntime().availableProcessors();

	@Setter
	private String date = "2011-02-04";
	@Setter
	private String time = "08:00 AM";
	@Setter
	private TimeZone timeZone = TimeZone.getDefault();
	@Setter
	private String outputPath = "/tmp/analystOutput";
	@Setter
	private float checkpointIntervalMinutes = -1;

	private long startTime = -1;
	private long lastLogTime = 0;

	Flow flow = null;

	/**
	 * Cut off the search instead of building a full path tree. Can greatly
	 * improve run times.
	 */
	public void setSearchCutoffMinutes(int minutes) {
		this.searchCutoffSeconds = minutes * 60;
	}

	public static BatchCollector setup(String configFile) {
		org.springframework.core.io.Resource appContextResource;

		appContextResource = new FileSystemResource(configFile);

		GenericApplicationContext ctx = new GenericApplicationContext();
		XmlBeanDefinitionReader xmlReader = new XmlBeanDefinitionReader(ctx);
		xmlReader.loadBeanDefinitions(appContextResource);
		ctx.refresh();
		//ctx.registerShutdownHook();
		BatchCollector processor = ctx.getBean(BatchCollector.class);

		return processor;
	}

	public void run() {
		
		flow = new Flow(graphService.getGraph().countEdges());
		
		origins.setup();
		destinations.setup();
		linkIntoGraph(destinations);
		// Set up a thread pool to execute searches in parallel
		LOG.info("Number of threads: {}", nThreads);
		ExecutorService threadPool = Executors.newFixedThreadPool(nThreads);
		// ECS enqueues results in the order they complete (unlike invokeAll,
		// which blocks)
		CompletionService<Void> ecs = new ExecutorCompletionService<Void>(
				threadPool);


		startTime = System.currentTimeMillis();
		int nTasks = 0;
		for (Individual oi : origins) { // using filtered iterator
			ecs.submit(new BatchAnalystTask(nTasks, oi), null);
			++nTasks;
		}
		LOG.info("created {} tasks.", nTasks);
		int nCompleted = 0;
		try { // pull Futures off the queue as tasks are finished
			while (nCompleted < nTasks) {
				try {
					ecs.take().get(); // call get to check for exceptions in the
										// completed task
					
				} catch (ExecutionException e) {
					LOG.error("exception in thread task: {}", e);
				}
				++nCompleted;
				projectRunTime(nCompleted, nTasks);
			}
		} catch (InterruptedException e) {
			LOG.warn("run was interrupted after {} tasks", nCompleted);
		}
		threadPool.shutdown();
		flow.saveGeoJson(outputPath);
		
		LOG.info("DONE: Processed :"+nCompleted);
	}

	private void projectRunTime(int current, int total) {
		long currentTime = System.currentTimeMillis();
		// not threadsafe, but the worst thing that will happen is a double log
		// message
		// anyway we are using this in the controller thread now
		if (currentTime > lastLogTime + logThrottleSeconds * 1000) {
			lastLogTime = currentTime;
			double runTimeMin = (currentTime - startTime) / 1000.0 / 60.0;
			double projectedMin = (total - current) * (runTimeMin / current);
			LOG.info("received {} results out of {}", current, total);
			LOG.info("running {} min, {} min remaining (projected)",
					(int) runTimeMin, (int) projectedMin);
		}
	}


	private RoutingRequest buildRequest(Individual i) {
		RoutingRequest req = prototypeRoutingRequest.clone();
		req.setDateTime(date, time, timeZone);
		if (searchCutoffSeconds > 0) {
			req.worstTime = req.dateTime
					+ (req.arriveBy ? -searchCutoffSeconds
							: searchCutoffSeconds);
		}
		GenericLocation latLon = new GenericLocation(i.lat, i.lon);
		req.batch = true;
		if (req.arriveBy)
			req.setTo(latLon);
		else
			req.setFrom(latLon);
		try {
			req.setRoutingContext(graphService.getGraph(req.routerId));
			return req;
		} catch (VertexNotFoundException vnfe) {
			LOG.debug("no vertex could be created near the origin point");
			return null;
		}
	}

	/**
	 * Generate samples for (i.e. non-invasively link into the Graph) only those
	 * individuals that were not rejected by filters. Other Individuals will
	 * have null samples, indicating that they should be skipped.
	 */
	private void linkIntoGraph(Population p) {
		LOG.info("linking population {} to the graph...", p);
		int n = 0, nonNull = 0;
		for (Individual i : p) {
			Sample s = sampleFactory.getSample(i.lon, i.lat);
			i.sample = s;
			n += 1;
			if (s != null)
				nonNull += 1;
		}
		LOG.info("successfully linked {} individuals out of {}", nonNull, n);
	}

	/*
	 * processing spt
	 */

	public void processSample(ShortestPathTree spt, Sample sample) {
		State s0 = spt.getState(sample.v0);
		State s1 = spt.getState(sample.v1);
		if (s0 != null) {
			backtrack(s0);
		} else if (s1 != null) {
			backtrack(s1);
		} else {
			System.out.println("WARNING: sample data not set");
		}
	}

	public void backtrack(State state) {
		System.out.printf("---- FOLLOWING CHAIN OF STATES ----\n");
		while (state != null) {
			System.out.printf("%s via %s by %s\n", state, state.getBackEdge(),
					state.getBackMode());
			state = state.getBackState();
		}
		System.out.printf("---- END CHAIN OF STATES ----\n");
	}

	/**
	 * A single computation to perform for a single origin. Runnable, not
	 * Callable. We want accumulation to happen in the worker thread. Handling
	 * all accumulation in the controller thread risks amassing a queue of large
	 * result sets.
	 */
	private class BatchAnalystTask implements Runnable {

		protected final int i;
		protected final Individual oi;

		public BatchAnalystTask(int i, Individual oi) {
			this.i = i;
			this.oi = oi;
		}

		@Override
		public void run() {
			LOG.debug("calling origin : {}", oi);
			RoutingRequest req = buildRequest(oi);
			if (req != null) {
				ShortestPathTree spt = sptService.getShortestPathTree(req);
				
				StringBuilder stats = new StringBuilder();
				
				// going through all the destinations to trace back its computed path
				for (Individual indiv : destinations) {
					//process flow
					flow.processBatchSample(spt, indiv.sample);
					

					stats.append(oi.label+","+oi.lat+","+oi.lon+",");
					stats.append(indiv.label+","+indiv.lat+","+indiv.lon+",");
					
					try{
						//process stats
						State state = spt.getState(indiv.sample.v0);
	
						if (state == null) {
							state = spt.getState(indiv.sample.v1);
						}
						
						// incase the second point is not set as well
						if (state != null) {
							stats.append(state.distance+"\n");
						}else{
							stats.append("0\n");
						}
						
					} catch(Exception e){
						stats.append("0\n");
					}

				}

				//outputs to in format name_origin_destination 
				String subName = outputPath.replace("{}", String.format("stats_%s", oi.label));
				subName.replace(".json", ".csv");
				
				saveToFile(subName, stats.toString());

				req.cleanup();

			}
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

}
