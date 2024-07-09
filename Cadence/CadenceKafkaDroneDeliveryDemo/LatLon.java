package DroneDeliveryDemo;

import java.math.BigDecimal;

public class LatLon {
	
	double lat;
	double lon;

	public LatLon(double lat, double lon) {
		this.lat = lat;
		this.lon = lon;
	}
	
	// return true if same coords within 5 decimal places = 1m
	boolean sameLocation(LatLon x)
	{
		BigDecimal alat = new BigDecimal(this.lat);
		BigDecimal blat = new BigDecimal(x.lat);

		BigDecimal alon = new BigDecimal(this.lon);
		BigDecimal blon = new BigDecimal(x.lon);

		alat = alat.setScale(5, BigDecimal.ROUND_DOWN);
		blat = blat.setScale(5, BigDecimal.ROUND_DOWN);
		alon = alon.setScale(5, BigDecimal.ROUND_DOWN);
		blon = blon.setScale(5, BigDecimal.ROUND_DOWN);
		return alat.equals(blat) && alon.equals(blon);
	}

	public void println()
	{
		System.out.println("lat " + lat + ", lon " + lon);
	}
	
	public String toString()
	{
		return "lat " + lat + ", lon " + lon;
	}
}
