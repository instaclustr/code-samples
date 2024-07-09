import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.apache.curator.framework.recipes.leader.LeaderLatch;

/*
 * Dining Philosopher Rules
 * 1 5 philosophers
 * 2 seated around a table
 * 3 Alternate thinking and eating
 * 4 Can only eat with both forks (left and right)
 * 5 When they stop eating they return both forks
 * 
 * pi needs both forks fi and fi+1, except if i>max when it's f1 again
 * simple algorithm! Just think for random time, try and get both forks, eat for random time, else give up after another time period and revert to thinking.
 */


public class Philosopher extends Thread {

	private Thread t;
	private int threadNum = -1;
	private String threadName; 
	
	LeaderLatch leaderLatch = null;
	
	int maxThinkTime = 1000;
	int maxForkWaitTime = 100;
	int maxEatTime = 1000;
		
	int bossNumTimes = 0;
	
	public Philosopher() {
	}
	
	public Philosopher(String name, int n)
	{
		threadName = name;
		threadNum = n;
		System.out.println("Created thread " + threadName);
	}
	
	public void run()
	{
		System.out.println("Running " + threadName);
		
		leaderLatch = new LeaderLatch(ZookeeperMeetsDiningPhilosophers.client, "/mutex/leader/taskA", threadName);
		try {
			leaderLatch.start();
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		
		while (true)
		{ // while
			if (leaderLatch.hasLeadership())
			{
				String topic = ZookeeperMeetsDiningPhilosophers.topics[ThreadLocalRandom.current().nextInt(0, ZookeeperMeetsDiningPhilosophers.topics.length)];
				if (!ZookeeperMeetsDiningPhilosophers.silent)
					System.out.println("----> " + threadName + " is the BOSS! Everyone now think about " + topic);	
				
				// update global elections
				boolean success = false;
				while (!success)
				{
					int before = ZookeeperMeetsDiningPhilosophers.elections.getCount();
					int after = (int) (before + 1);
				
					try {
						success = ZookeeperMeetsDiningPhilosophers.elections.trySetCount(after);
						if (success)
						{
							if (ZookeeperMeetsDiningPhilosophers.verbose)
								System.out.println(">> " + threadName + " incremented elections to " + after);
						}
						else
							if (ZookeeperMeetsDiningPhilosophers.verbose)
								System.out.println("!! " + threadName + " failed to increment!");
					} catch (Exception e) {				
						e.printStackTrace();
					}
				}
			}
			
			if (!ZookeeperMeetsDiningPhilosophers.silent)
				System.out.println(threadName + " thinking...");
			
			// Think first
			int think = ThreadLocalRandom.current().nextInt(1, maxThinkTime);
			try {
				Thread.sleep(think);
			} catch (InterruptedException e2) {
				e2.printStackTrace();
			}
			
			// update global thinkTime
			boolean success = false;
			while (!success)
			{
				int before = ZookeeperMeetsDiningPhilosophers.thinkTime.getCount();
				int after = (int) (before + think);
				
				try {
					success = ZookeeperMeetsDiningPhilosophers.thinkTime.trySetCount(after);
					if (success)
					{
						if (ZookeeperMeetsDiningPhilosophers.verbose)
							System.out.println(">> " + threadName + " incremented thinkTime to " + after);
					}
					else
						if (ZookeeperMeetsDiningPhilosophers.verbose)
							System.out.println("!! " + threadName + " failed to increment!");
				} catch (Exception e) {				
					e.printStackTrace();
				}
			}
			
			if (leaderLatch.hasLeadership())
			{
				try {
					leaderLatch.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
				if (ZookeeperMeetsDiningPhilosophers.verbose)
					System.out.println("----> " + threadName + " was the BOSS but is now not the BOSS, count=" + ++bossNumTimes);
				
				leaderLatch = new LeaderLatch(ZookeeperMeetsDiningPhilosophers.client, "/mutex/leader/taskA", threadName);
				try {
					leaderLatch.start();
				} catch (Exception e1) {
					e1.printStackTrace();
				}	
			}
			
			
			// Time to EAT!
			// Try getting 2 forks in strict order, left then right (Philosophers are inflexible)
			
			// update global eat attempts
			success = false;
			while (!success)
			{
				int before = ZookeeperMeetsDiningPhilosophers.attemptedMeals.getCount();
				int after = (int) (before + 1);
				try {
					success = ZookeeperMeetsDiningPhilosophers.attemptedMeals.trySetCount(after);
					if (success)
					{
						if (ZookeeperMeetsDiningPhilosophers.verbose) 
							System.out.println(">> " + threadName + " incremented attemptedMeals to " + after);
					}
					else if (ZookeeperMeetsDiningPhilosophers.verbose)
							System.out.println("!! " + threadName + " failed to increment!");

					} catch (Exception e) {				
							e.printStackTrace();
				}
			}
			
			int leftFork = threadNum;
			int rightFork = ((threadNum + 1) % ZookeeperMeetsDiningPhilosophers.numPhilosophers) + 1;
			
			try {
				if (!ZookeeperMeetsDiningPhilosophers.silent)
					System.out.println(threadName + " is Hungry, wants left fork " + leftFork);
			
				boolean gotLeft = ZookeeperMeetsDiningPhilosophers.forks[leftFork].acquire(maxForkWaitTime, TimeUnit.MILLISECONDS);
				
				if (gotLeft)
				{
				
					if (!ZookeeperMeetsDiningPhilosophers.silent)
						System.out.println(threadName + " got left fork " + leftFork);
					if (!ZookeeperMeetsDiningPhilosophers.silent)
						System.out.println(threadName + " wants right fork " + rightFork);
				
					// measure the wait time for right fork given left fork is now unavailable for anyone else, this is fork contention time
					long t0 = System.currentTimeMillis();
					
					boolean gotRight = ZookeeperMeetsDiningPhilosophers.forks[rightFork].acquire(maxForkWaitTime, TimeUnit.MILLISECONDS);
				
					long t1 = System.currentTimeMillis();
				
					long waitTime = t1 - t0;
				
					// add waitTime to global fork wait time
					success = false;
					while (!success)
					{
						int before = ZookeeperMeetsDiningPhilosophers.waitTime.getCount();
						int after = (int) (before + waitTime);
				
						success = ZookeeperMeetsDiningPhilosophers.waitTime.trySetCount(after);
						if (success)
						{
							if (ZookeeperMeetsDiningPhilosophers.verbose)
								System.out.println(">> " + threadName + " incremented waitTime to " + after);
						}
						else 
							if (ZookeeperMeetsDiningPhilosophers.verbose)
								System.out.println("!! " + threadName + " failed to increment!");
					}
				
					if (gotRight)
					{
						if (!ZookeeperMeetsDiningPhilosophers.silent)
							System.out.println(threadName + " got right fork " + rightFork);
			
						// eat
						if (!ZookeeperMeetsDiningPhilosophers.silent)
							System.out.println(threadName + " eating yum yum yum yum");
				
						int eat = ThreadLocalRandom.current().nextInt(1, maxEatTime);
						Thread.sleep(eat);
				
						// Put both forks back on the table
						ZookeeperMeetsDiningPhilosophers.forks[leftFork].release();
						ZookeeperMeetsDiningPhilosophers.forks[rightFork].release();
				
				
						if (!ZookeeperMeetsDiningPhilosophers.silent)
							System.out.println(threadName + " finished eating! Putting both forks back: " + leftFork + ", " + rightFork);
				
						// update eatTime
						success = false;
						while (!success)
						{
							int before = ZookeeperMeetsDiningPhilosophers.eatTime.getCount();
							int after = (int) (before + eat);
							try {
								success = ZookeeperMeetsDiningPhilosophers.eatTime.trySetCount(after);
								if (success)
								{
									if (ZookeeperMeetsDiningPhilosophers.verbose)
										System.out.println(">> " + threadName + " incremented eatTime to " + after);
								}
								else if (ZookeeperMeetsDiningPhilosophers.verbose)
									System.out.println("!! " + threadName + " failed to increment!");

							} catch (Exception e) {				
								e.printStackTrace();
							}
						}
											
						// incremement global meals count
						success = false;
						while (!success)
						{
							int before = ZookeeperMeetsDiningPhilosophers.meals.getCount();
							int after = before + 1;
							success = ZookeeperMeetsDiningPhilosophers.meals.trySetCount(after);
							if (success)
							{
								if (ZookeeperMeetsDiningPhilosophers.verbose)
									System.out.println(">> " + threadName + " incremented meals to " + after);
							}
							else if (ZookeeperMeetsDiningPhilosophers.verbose)
								System.out.println("!! " + threadName + " failed to increment!");
						}
				
						// increment forkTime with eat time
						success = false;
						while (!success)
						{
							int before = ZookeeperMeetsDiningPhilosophers.forkTime.getCount();
							int after = before + eat;
							success = ZookeeperMeetsDiningPhilosophers.forkTime.trySetCount(after);
							if (success)
							{
								if (ZookeeperMeetsDiningPhilosophers.verbose)
									System.out.println(">> " + threadName + " incremented forkTime to " + after);
							}
							else if (ZookeeperMeetsDiningPhilosophers.verbose)
								System.out.println("!! " + threadName + " failed to increment!");
						}
					} // gotRight
					else 
					{
						if (!ZookeeperMeetsDiningPhilosophers.silent)
							System.out.println("*** " + threadName + " gave up waiting for right fork " + rightFork);
					
						ZookeeperMeetsDiningPhilosophers.forks[leftFork].release();
						if (!ZookeeperMeetsDiningPhilosophers.silent)
							System.out.println(threadName + " putting left fork back: " + leftFork);
					}
				} // gotLeft
				else if (!ZookeeperMeetsDiningPhilosophers.silent)
					System.out.println("*** " + threadName + " gave up waiting for left fork " + leftFork);

			} catch (Exception e) {
				e.printStackTrace();	
			} // try
		} // while true
	}

	public static void main(String[] args) {

		Philosopher p1 = new Philosopher("Popper", 1);
		Philosopher p2 = new Philosopher("Kuhn", 2);
		
		p1.start();
		p2.start();
	}
}
