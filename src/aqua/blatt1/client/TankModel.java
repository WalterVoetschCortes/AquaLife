package aqua.blatt1.client;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.FishLocation;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.RecordsState;
import aqua.blatt1.common.msgtypes.*;

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
	protected RecordsState record = RecordsState.IDLE;
	protected int localFishies;
	protected boolean initiatorReady = false;
	protected boolean waitForIDLE = false;
	protected int showGlobalSnapshot;
	protected boolean showDialog;
	public static final int NUMTHREADS = 5;
	ExecutorService executor = Executors.newFixedThreadPool(NUMTHREADS);
	Map<String, InetSocketAddress> homeAgent = new HashMap<>();

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
			homeAgent.put(fish.getId(), null);
		}
	}

	synchronized void receiveFish(FishModel fish) {
		fish.setToStart();
		fishies.add(fish);
		updatefishLocation(fish);
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

			if (fish.disappears()){
				it.remove();
			}
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

	public void initiateSnapshot() {
		if (record == RecordsState.IDLE) {
			localFishies = fishies.size();
			record = RecordsState.BOTH;
			initiatorReady = true;
			forwarder.sendSnapshotMarker(leftNeighbor, new SnapshotMarker());
			forwarder.sendSnapshotMarker(rightNeighbor, new SnapshotMarker());
		}

	}

	public void onReceiveCollector(Collector collector) {
		waitForIDLE = true;
		executor.execute(new Runnable() {
			@Override
			public void run() {
				while (waitForIDLE)
					if (record == RecordsState.IDLE) {
						int currentFishState = collector.getLocalfishies();
						int newFishState = currentFishState + localFishies;
						forwarder.sendCollector(leftNeighbor, new Collector(newFishState));
						waitForIDLE = false;
					}
			}
		});

		if (initiatorReady) {
			initiatorReady = false;
			showDialog=true;
			System.out.println(collector.getLocalfishies() + " fishies");
			showGlobalSnapshot = collector.getLocalfishies();
		}
	}

	public void receiveSnapshotMarker(InetSocketAddress sender, SnapshotMarker snapshotMarker) {
		if (record == RecordsState.IDLE) {
			localFishies = fishies.size();
			if (!leftNeighbor.equals(rightNeighbor)) {
				if (sender.equals(leftNeighbor)) {
					record = RecordsState.RIGHT;
				} else if (sender.equals(rightNeighbor)) {
					record = RecordsState.LEFT;
				}
			} else {
				record = RecordsState.BOTH;
			}
			if (leftNeighbor.equals(rightNeighbor)) {
				forwarder.sendSnapshotMarker(leftNeighbor, snapshotMarker);
			} else {
				forwarder.sendSnapshotMarker(leftNeighbor, snapshotMarker);
				forwarder.sendSnapshotMarker(rightNeighbor, snapshotMarker);
			}

		} else {
			if (!leftNeighbor.equals(rightNeighbor)) {
				if (sender.equals(leftNeighbor)) {
					if (record == RecordsState.BOTH) {
						record = RecordsState.RIGHT;
					}
					if (record == RecordsState.LEFT) {
						record = RecordsState.IDLE;
					}
				} else {
					if (record == RecordsState.BOTH) {
						record = RecordsState.LEFT;
					}
					if (record == RecordsState.RIGHT) {
						record = RecordsState.IDLE;
					}
				}
			} else {
				record = RecordsState.IDLE;
			}
		}
		if (initiatorReady && record == RecordsState.IDLE) {
			forwarder.sendCollector(leftNeighbor, new Collector(localFishies));
		}
	}

	public void locateFishGlobally(String fishID) {
		if (homeAgent.get(fishID) == null) { //fish is in this tank, so locate fish locally
			locateFishLocally(fishID);
		} else {
			InetSocketAddress currentFishLocation = homeAgent.get(fishID);
			forwarder.sendLocationRequest(currentFishLocation, new LocationRequest(fishID));
		}
	}

	public void locateFishLocally(String fishID){
		//if fish with fishID is in tank, mark it with method FishModel.toggle
		for(FishModel fish: this.fishies){
			if(fish.getId().equals(fishID)){
				fish.toggle();
			}
		}
	}

	public void updatefishLocation(FishModel fish) {
		String fishID = fish.getId();
		if (homeAgent.containsKey(fishID)) {
			homeAgent.put(fishID, null);
		} else {
			forwarder.sendResoultionRequest(new NameResolutionRequest(fish.getTankId(), fishID));
		}
	}

	public void handleResponse(InetSocketAddress homeLocation, String fishID) {
		forwarder.sendCurrentFishLocation(homeLocation, fishID);
	}

	public void updateCurrentLocation(String fishID, InetSocketAddress currentLocation) {
		homeAgent.put(fishID, currentLocation);
	}


}
