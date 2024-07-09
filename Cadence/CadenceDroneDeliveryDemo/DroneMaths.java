package DroneDeliveryDemo;

import java.util.concurrent.ThreadLocalRandom;


// Drone Maths to actually get the drone to move
public class DroneMaths {

	public DroneMaths() {
	}

	// Given start location, compute a destination location within min to max distance away (km)
	static LatLon newDestination(LatLon start, double minDistance, double maxDistance)
	{	
		double bearing = ThreadLocalRandom.current().nextDouble(0, 360);
		double distance = ThreadLocalRandom.current().nextDouble(minDistance, maxDistance);
		return nextPosition(start, distance, bearing);
	}
	
	static double distance(LatLon start, LatLon end)
	{
		return distance(start.lat, start.lon, end.lat, end.lon);
	}
	
	// compute distance in km as crow-flies between two locations.
	static double distance(double lat1, double lon1, double lat2, double lon2)
	{
		double theta = lon1 - lon2;
		double dist = Math.sin(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2)) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.cos(Math.toRadians(theta));
		dist = Math.acos(dist);
		dist = Math.toDegrees(dist);
		dist = dist * 60 * 1.1515;
		// convert to km
		dist = dist * 1.609344;
		
		return (dist);
	}
	
	// given two locations, start and end, compute the bearing from start to end
	public static double bearing(LatLon start, LatLon end)
	{
		return bearing(start.lat, start.lon, end.lat, end.lon);
	}
	
	// bearing in degrees between two locations
	public static double bearing(double lat1, double lon1, double lat2, double lon2) {
	    double dLon = (lon2-lon1);
	    double x = Math.sin(Math.toRadians(dLon)) * Math.cos(Math.toRadians(lat2));
	    double y = Math.cos(Math.toRadians(lat1))*Math.sin(Math.toRadians(lat2)) - Math.sin(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))*Math.cos(Math.toRadians(dLon));
	    double bearing = Math.toDegrees((Math.atan2(x, y)));
		return bearing;
	}
	
	
	// given start and end (goal) locations, compute next way point on a straight line between them given speed (km/h) and flying time (s)
	public static LatLon nextPosition(LatLon start, LatLon end, double speed, double time)
	{
		if (speed <= 0)
			return start;
		else
		{
			double bearing = bearing(start, end);
			double maxDistance = distance(start, end);	// max distance to travel 
			double distanceInTime = speed * (time/(60.0*60.0)); // distance in km traveled at speed in time seconds)
			// if we are going to overshoot the end location, then just stop and hover
			// should reduce speed to 0 as a side-effect, possibly just return null result to indicate we've reached the destination...
			if (distanceInTime >= maxDistance)
				return end;
			else
				// this does the actual calculation using start location, distance and bearing.
				return nextPosition(start, distanceInTime, bearing);	
		}
	}
	
	public static LatLon nextPosition(LatLon start, double distance, double bearing)
	{
		return nextLatLon(start.lat, start.lon, distance, bearing);
	}
	
	// compute next lat lon from start lat lon and d distance in km and b bearing in radians
	public static LatLon nextLatLon(double lat1, double lon1, double d, double b)
	{
		double R = 6378.1;  //Radius of the Earth
		double brng = Math.toRadians(b);
	    double lat1R = Math.toRadians(lat1); //Current lat point converted to radians
	    double lon1R = Math.toRadians(lon1); //Current long point converted to radians
	    double lat2 = Math.asin( Math.sin(lat1R)*Math.cos(d/R) +
	            Math.cos(lat1R)*Math.sin(d/R)*Math.cos(brng));
	    double lon2 = lon1R + Math.atan2(Math.sin(brng)*Math.sin(d/R)*Math.cos(lat1R),
	            Math.cos(d/R)-Math.sin(lat1R)*Math.sin(lat2));
	    lat2 = Math.toDegrees(lat2);
	    lon2 = Math.toDegrees(lon2);
	    return new LatLon(lat2, lon2);
	}	
}
