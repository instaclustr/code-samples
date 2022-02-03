package DroneMaths;

import java.util.concurrent.ThreadLocalRandom;
// import LatLon;

// drone maths
// given a start location can pick a target destination with specified range.
// This means that in practice the Drone could perform multiple deliveries?
// i.e. do 1 delivery, if charge > 60% say then do another etc.
// But always need to ensure that it can return to base
// simplest assumption is just to force it to do longer deliveries so it always does exactly 1 I guess

// bother random numbers don't work with Cadence, test for turning off

public class DroneMaths {
	
	static final boolean noRandom = false; // turn random off

	public DroneMaths() {
		// TODO Auto-generated constructor stub
	}

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		// home to playground
		// System.out.println(distance(-35.20586, 149.09462, -35.20578, 149.09278) + " km ");
		System.out.println(distance(-35.20586, 149.09462, -35.20578, 149.09278)*1000 + " m ");
		
		System.out.println("bearing in degress = " + bearing(-35.20586, 149.09462, -35.29569, 149.09462));
		System.out.println("bearing in degress = " + bearing(-35.29569, 149.09462, -35.20586, 149.09462));


		LatLon x = nextLatLon(-35.20586, 149.09462, 10, 180);
		x.println();
		System.out.println(distance(-35.20586, 149.09462, -35.29569, 149.09462)*1000 + " m ");
		
		LatLon start = new LatLon(-35.20586, 149.09462);
		// LatLon end = new LatLon(-35.29569, 149.09462);
		// LatLon end = nextPosition(start, 3, 180);
		LatLon end = newDestination(start, 0.1, 3);
		LatLon here = start;
		double speed = 20.0; // max drone speed is 10km/h
		double time = 60; // time in seconds each move
		double tripTime = 0;
		boolean arrived = false;
		double charge = 100.0;
		double initialCharge = 100.0;
		double maxFlightTime = 30 * 60; // s
		while (!arrived)
		{
			tripTime += time;
			charge = initialCharge - ((tripTime/maxFlightTime) * 100.0);
			double distance = distance(here, end);
			System.out.println("Distance to go = " + distance + " km");
			LatLon next = nextPosition(here, end, speed, time);
			System.out.println("next location = " + next.toString());
			double dtravelled = distance(here, next);
			System.out.println("Distance travelled = " + dtravelled);
			System.out.println("Charge = " + charge);

			if (end.sameLocation(next)) // within 1 m
			{
				speed = 0;
				arrived = true;
				System.out.println("Arrived! tripTime = " + tripTime + " s");
				System.out.println("Charge = " + charge);
			}
			else
				here = next;
		}

	}

// need new method that generates end coordinates from start with max distance and random distance
// this is stupid!
	static LatLon newDestinationXXX(LatLon start, double maxDistance)
	{
		// how to do it? Generate random lat lon 
		// 
		double oneM = 0.00001;
		double oneKm = 1000 * oneM;
		double lat = start.lat;
		double lon = start.lon;
		while (true)
		{
			double x;
			double y;
			
			if (noRandom)
				 x = (oneKm * maxDistance) / 2.0;
			else  x = ThreadLocalRandom.current().nextDouble(-(oneKm * maxDistance), (oneKm * maxDistance));
			if (noRandom)
				y = (oneKm * maxDistance) / 2.0;
			else y = ThreadLocalRandom.current().nextDouble(-(oneKm * maxDistance), (oneKm * maxDistance));
			
			LatLon next = new LatLon(lat + x, lon + y);
			if (distance(start, next) <= maxDistance)
				return next;
		}
	}
	
	static LatLon newDestination(LatLon start, double minDistance, double maxDistance)
	{
		double bearing;
		double distance;
		
		if (noRandom) 
			bearing = 90.0;
		else bearing = ThreadLocalRandom.current().nextDouble(0, 360);
		if (noRandom)
			distance = minDistance;
		else distance = ThreadLocalRandom.current().nextDouble(minDistance, maxDistance);
		return nextPosition(start, distance, bearing);
	}
	
	static double distance(LatLon start, LatLon end)
	{
		return distance(start.lat, start.lon, end.lat, end.lon);
	}
	
	static double distance(double lat1, double lon1, double lat2, double lon2)
	{
		double theta = lon1 - lon2;
		double dist = Math.sin(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2)) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.cos(Math.toRadians(theta));
		dist = Math.acos(dist);
		dist = Math.toDegrees(dist);
		dist = dist * 60 * 1.1515;
		// conver to km
		dist = dist * 1.609344;
		
		return (dist);
	}
	
	public static double bearing(LatLon start, LatLon end)
	{
		return bearing(start.lat, start.lon, end.lat, end.lon);
	}
	
	// bearing in degrees
	public static double bearing(double lat1, double lon1, double lat2, double lon2) {
	    double dLon = (lon2-lon1);
	    double x = Math.sin(Math.toRadians(dLon)) * Math.cos(Math.toRadians(lat2));
	    double y = Math.cos(Math.toRadians(lat1))*Math.sin(Math.toRadians(lat2)) - Math.sin(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))*Math.cos(Math.toRadians(dLon));
	    double bearing = Math.toDegrees((Math.atan2(x, y)));
		return bearing;
	}
	
	// do we need a helper method to truncate to the position?
	// i.e. we don't want to overshoot the destination location, could either slow down as we approach
	// or just come to a dead halt when we reach it - try a method which uses speed, which if 0 means we don't move yet
	// this one has start and end coordinates and speed and time until next way point (in seconds)
	// speed is km/h and time is seconds
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
			// should reduce speed to 0 as a side-effect, possibly just return null result to indicate we've reached the destination?
			if (distanceInTime >= maxDistance)
				return end;
			else
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
	    // double brng = 1.57;  //Bearing is 90 degrees converted to radians.
	   // double d = 15;  //Distance in km

	    //double lat2 = 52.20444; // - the lat result I'm hoping for
	    //double lon2 = 0.36056; // - the long result I'm hoping for.

		double brng = Math.toRadians(b);
	    double lat1R = Math.toRadians(lat1); //Current lat point converted to radians
	    double lon1R = Math.toRadians(lon1); //Current long point converted to radians

	    double lat2 = Math.asin( Math.sin(lat1R)*Math.cos(d/R) +
	            Math.cos(lat1R)*Math.sin(d/R)*Math.cos(brng));

	    double lon2 = lon1R + Math.atan2(Math.sin(brng)*Math.sin(d/R)*Math.cos(lat1R),
	            Math.cos(d/R)-Math.sin(lat1R)*Math.sin(lat2));

	    lat2 = Math.toDegrees(lat2);
	    lon2 = Math.toDegrees(lon2);

	    // System.out.println(lat2 + ", " + lon2 + " degrees");
	    return new LatLon(lat2, lon2);
	}
	
	
}
