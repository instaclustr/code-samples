import java.io.IOException;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.imps.CuratorFrameworkState;
import org.apache.curator.framework.recipes.leader.LeaderLatch;
import org.apache.curator.framework.recipes.locks.InterProcessSemaphoreMutex;
import org.apache.curator.framework.recipes.shared.SharedCount;
import org.apache.curator.retry.RetryNTimes;

/*
 * Demonstration of Apache Zookeeper (and Curator) Meet The Dining Philosophers (Problem)!
 * Paul Brebner, Instaclustr.com, March 2021
 * 
 * Change numForks to also increase numPhilosophers for more load.
 * runTime is time in ms before termination.
 * 
 */

public class ZookeeperMeetsDiningPhilosophers {
	
	static boolean verbose = false;
	static boolean silent = true; // if true then no updates just final results
	
	static InterProcessSemaphoreMutex sharedLock = null;
	
	static CuratorFramework client = null;
	
	static int numForks = 5;
	static int numPhilosophers = numForks;
	static int runTime = 10000; // ms
	
	static Philosopher[] philosophers = new Philosopher[numPhilosophers];
	
	// Array of semaphores, each fork has its own, to use a fork you have to get a lock
	// Forks are numbered from 1 to numForks, so element 0 isn't used.
	static InterProcessSemaphoreMutex[] forks = new InterProcessSemaphoreMutex[numForks+1];
	
	// random topics for leader to announce for thinking about
	static String[] topics = {"Ducks", "Aesthetics", "Cosmology", "Ethics", "Epistemology", "Logic", "Politics", "Religion", "Science", "Linguistics", "Mind", "Law"};
	
	// counter for successful meals
	static SharedCount meals = null;
	
	// counter for attempted meals
	static SharedCount attemptedMeals = null;
	
	// keep track of total time that forks have been used for eating
	static SharedCount forkTime = null;
	
	// keep track of time waiting for forks, i.e. time when fork is unavailable for anyone else
	static SharedCount waitTime = null;
	
	// keep track of Philosopher times, eat time
	static SharedCount eatTime = null;

	// and Philosopher think time
	static SharedCount thinkTime = null;
	
	static SharedCount elections = null;

	public ZookeeperMeetsDiningPhilosophers() {
	}

	public static void main(String[] args) {
		
		String ZKIPLocal = "127.0.0.1";
		String ZKPort = "2181";
		
		String localZK = ZKIPLocal+ ":" + ZKPort;
		String ensemble1 = "IP1:2181, IP2:2181, IP3:2181";	// syntax for ensemble
		
		// connect
		int sleepMsBetweenRetries = 100;
		int maxRetries = 3;
		RetryPolicy retryPolicy = new RetryNTimes(
		  maxRetries, sleepMsBetweenRetries);

		String zk = localZK;
		
		client = CuratorFrameworkFactory
				  .newClient(zk, retryPolicy);
		
		client.start();
		
		CuratorFrameworkState state = client.getState();
		
		System.out.println("client state = " + state.toString());
		
		try {
			client.blockUntilConnected(10, java.util.concurrent.TimeUnit.SECONDS);
		} catch (InterruptedException e2) {
			System.out.println("Giving up waiting for ZK!");
			System.exit(0);
		}
		
		System.out.println("Curator client connected to ZK at IP=" + zk);
			
		// start and set shared metric counters
		// note that SharedCount is persistent across client restart so need to set to zero
		meals = new SharedCount(client, "/counters/meals", 0);
		try {
			meals.start();
			meals.setCount(0);  
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		
		attemptedMeals = new SharedCount(client, "/counters/attemptedMeals", 0);
		try {
			attemptedMeals.start();
			attemptedMeals.setCount(0);  
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		
		forkTime = new SharedCount(client, "/counters/forkTime", 0);
		try {
			forkTime.start();
			forkTime.setCount(0);
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		
		waitTime = new SharedCount(client, "/counters/waitTime", 0);
		try {
			waitTime.start();
			waitTime.setCount(0);
		} catch (Exception e1) {
			e1.printStackTrace();
		}

		eatTime = new SharedCount(client, "/counters/eatTime", 0);
		try {
			eatTime.start();
			eatTime.setCount(0);
		} catch (Exception e1) {
			e1.printStackTrace();
		}
				
		thinkTime = new SharedCount(client, "/counters/thinkTime", 0);
		try {
			thinkTime.start();
			thinkTime.setCount(0);
		} catch (Exception e1) {
			e1.printStackTrace();
		}
					
		elections = new SharedCount(client, "/counters/elections", 0);
		try {
			elections.start();
			elections.setCount(0); 
		} catch (Exception e1) {
			e1.printStackTrace();
		}
				
		// create a lock for each fork
		for (int i=1; i <= numForks; i++)
			forks[i] = new InterProcessSemaphoreMutex(client, "/mutex/fork" + i);
		
		System.out.println("Created forks = philosphers = " + numForks);
		
		System.out.println("Runing for time (ms) = " + runTime);		

		
		// start timer
		long startTimeMS = System.currentTimeMillis();
		
		// create N Philosopher threads, more threads puts more load on ZK
		int n = numPhilosophers;
		for (int i=1; i <= n; i++)
		{
			Philosopher p = new Philosopher("Philosopher_" + i, i);
			philosophers[i-1] = p;
			p.start();
		}
		
		try {
			Thread.sleep(runTime);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		int numMeals = meals.getCount();
		
		System.out.println("......... Meal Over! All go home! Final Statistics.......");
		
		long stopTimeMS = System.currentTimeMillis();
		long totalTimeMS = stopTimeMS - startTimeMS;
		long totalTimeS = totalTimeMS/1000;
		
		System.out.println("Total time (MS) = " + totalTimeMS);
		
		int e = elections.getCount();
		
		System.out.println("Elections = " + e);
		
		double electionRate = (e * 1.0/ totalTimeS);
		
		System.out.println("Election rate/s = " + electionRate);
		
		System.out.println("meals = " + numMeals);
		
		int attempted = attemptedMeals.getCount();
		
		System.out.println("attempted Meals = " + attempted);
		
		double mealRatio = (numMeals * 1.0/attempted) * 100.0;
		
		System.out.println("meal succes = " + mealRatio + "%");
		
		int forkTMS = forkTime.getCount();
		
		System.out.println("Total fork time (MS) = " + forkTMS);
		
		double forkUtilisation = ((forkTMS * 2.0) / (totalTimeMS * numForks)) * 100.0;
		
		System.out.println("Fork Utilisation = " + forkUtilisation + "%");
		
		int waitTMS = waitTime.getCount();
		
		System.out.println("wait (hungry) Time (MS) = " + waitTMS);
		
		double waitUtilisation = ((waitTMS * 1.0) / (totalTimeMS * numForks)) * 100.0;
		System.out.println("wait Utilisation (hungry) = " + waitUtilisation + "%");
		
		double rest = 100.0 - forkUtilisation - waitUtilisation;
		System.out.println("Unused Utilisation = " + rest + "%");			


		int eatTMS = eatTime.getCount();
		
		System.out.println("Philosophers eat time (ms) = " + eatTMS);
		
		int thinkTMS = thinkTime.getCount();
		
		System.out.println("Philosophers think time (ms) = " + thinkTMS);
		
		double eatUtilisation = ((eatTMS * 1.0) / (totalTimeMS * numPhilosophers)) * 100.0;
		
		double thinkUtilisation = ((thinkTMS * 1.0) / (totalTimeMS * numPhilosophers)) * 100.0;

		System.out.println("Philosophers Eat Utilisation = " + eatUtilisation + "%");

		System.out.println("Philosophers Think Utilisation = " + thinkUtilisation + "%");
		
		double philosophersWaitUtilisation = 100.0 - eatUtilisation - thinkUtilisation;
		
		System.out.println("Philosophers Wait (Hungry) Utilisation = " + philosophersWaitUtilisation + "%");
		
		
		// Stop the Philosophers
		for (int i=1; i <= n; i++)
			philosophers[i-1].stop();
		
		try {
			Thread.sleep(10000);
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
		client.close();
		
		System.out.println("Curator client stopped, goodbye");
	}

}
