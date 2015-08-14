OpenTripPlanner Traffic Flow
==================================
Extending OpenTripPlanner and its Analyst module to account for traffic flow. 

Flow runs in Two Modes, example configs are provided in the example folder

Features:
-----------------
- Improve the Analyst module by adding the ability to aggregate various data such as road utility in single origin to single destination and in single origin to multiple destination scenario(batch)
- Grouping traffic flow data by time slices of a certain interval of the trip start time, the data then can me aggregated over periods of a day, week, or month
- Exporting any traffic flow path data into geojson


How To
==================================

Note:  Xmx15G refers to the amount of memory in gigabytes that is to be allocated

Building Graph
-----------------
To Build Graph Use:
```
java -Xmx2G -jar target/otp.jar --build /path/to/downloads/pdx
```

Note: a build-old script is provided if you would like to use the graph builder via XML configuration


Mode: Aggregate Flow
-----------------
It aggregates routes paths by 15 minute intervals, the routes are saved to disk as GeoJSON.

required properties in config:
```
graph_path = /path/to/graph
data_path = /path/to/csv/data
output_path	= /path/where/output/will/be/saved/flow_{}.json
dest_lat = column number of destination lat
dest_lon = column number of destination lng
origin_lat = column number of origin lat
origin_lon = column number of origin lng
start_datetime	= column of the start datetime of the trip
datetime_format = the format of how datetime is stored ex.yyyy-mm-dd HH:mm:ss
route_mode = (Optional) sets the type of paths to find, default is CAR
```
command to run:
```
java -Xmx15G -cp otp.jar org.traffic.flow.TrafficFlowMain -a /path/to/file.properties
```
note:  Xmx15G refers to the amount of memory in megabytes that is to be allocated

Mode: Batch Flow:
-----------------
Spits out the distance in meters and route path from every origin to every destination the configuration for this mode is in the xml format example configuration is provided in the examples folder.

command to run:
```
java -Xmx15G -cp otp.jar org.traffic.flow.TrafficFlowMain -b /path/to/config.xml
```

Development
==================================
Flow Module is located under: "otp-core/src/main/java/org/traffic"
compiled output directory: "otp-core/target"

Terminal command to compile code:
```
mvn clean package
```
