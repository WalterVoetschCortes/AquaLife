package aqua.blatt1.client;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.msgtypes.Token;

public class TankModel extends Observable implements Iterable<FishModel> {

	public static final int WIDTH = 600;
	public static final int HEIGHT = 350;
	protected static final int MAX_FISHIES = 5;
	protected static final Random rand = new Random();
	protected volatile String id;
	protected final Set<FishModel> fishies;
	protected int fishCounter = 0;
	protected final ClientCommunicator.ClientForwarder forwarder;
	public InetSocketAddress rightNeighbor;
	public InetSocketAddress leftNeighbor;
	protected boolean boolToken;
	Timer timer = new Timer();

	public TankModel(ClientCommunicator.ClientForwarder forwarder) {
		this.fishies = Collections.newSetFromMap(new ConcurrentHashMap<FishModel, Boolean>());
		this.forwarder = forwarder;
	}

	synchronized void onRegistration(String id) {
		this.id = id;
		newFish(WIDTH - FishModel.getXSize(), rand.nextInt(HEIGHT - FishModel.getYSize()));
	}

	public synchronized void newFish(int x, int y) {
		if (fishies.size() < MAX_FISHIES) {
			x = Math.min(x, WIDTH - FishModel.getXSize() - 1);
			y = Math.min(y, HEIGHT - FishModel.getYSize());

			FishModel fish = new FishModel("fish" + (++fishCounter) + "@" + getId(), x, y,
					rand.nextBoolean() ? Direction.LEFT : Direction.RIGHT);

			fishies.add(fish);
		}
	}

	synchronized void receiveFish(FishModel fish) {
		fish.setToStart();
		fishies.add(fish);
	}

	public String getId() {
		return id;
	}

	public synchronized int getFishCounter() {
		return fishCounter;
	}

	public synchronized Iterator<FishModel> iterator() {
		return fishies.iterator();
	}

	private synchronized void updateFishies() {
		for (Iterator<FishModel> it = iterator(); it.hasNext();) {
			FishModel fish = it.next();

			fish.update();

			if (fish.hitsEdge())
				hasToken(fish);

			if (fish.disappears())
				it.remove();
		}
	}

	private synchronized void update() {
		updateFishies();
		setChanged();
		notifyObservers();
	}

	protected void run() {
		forwarder.register();

		try {
			while (!Thread.currentThread().isInterrupted()) {
				update();
				TimeUnit.MILLISECONDS.sleep(10);
			}
		} catch (InterruptedException consumed) {
			// allow method to terminate
		}
	}

	public synchronized void finish() {
		forwarder.deregister(id);
	}


	//the class TankModel must hold the InetSocketAddresses of the neighbors:
	public void updateNeighbors(InetSocketAddress addressLeft, InetSocketAddress addressRight) {
		this.leftNeighbor = addressLeft;
		this.rightNeighbor = addressRight;
	}


	// method for receiving the token:
	public synchronized void receiveToken(Token token){
		/*
		If the TankModel receives the token,
		the boolean variable is set and the timer a timer task for execution
		after e.g. 2 seconds passed.
		In the TimerTask the Boolean variable
		reset and the token forwarded to the left neighbor.
		*/

		this.boolToken = true;

		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				boolToken = false;
				forwarder.sendToken(leftNeighbor, token);
			}
		}, 10*1000);
	}

	// method for querying the token:
	public synchronized void hasToken(FishModel fish){
		/*
		TankModel is only allowed to send fish to neighbors when it holds the token.
		If a fish hits the edge of the aquarium while the aquarium is not holding the token,
		it changes its swimming direction.
		*/

		if(boolToken){
			forwarder.handOff(fish, rightNeighbor, leftNeighbor);
		}else {
			fish.reverse();
		}
	}

}
