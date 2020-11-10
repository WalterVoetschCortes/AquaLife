package aqua.blatt1.broker;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.msgtypes.*;
import messaging.Endpoint;
import messaging.Message;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Broker {

    private boolean done = false;
    private int clientNumber = 0;
    private Endpoint endpoint = new Endpoint(4711); //endpoint listens on port 4711
    private volatile ClientCollection clients = new ClientCollection(); //broker keeps a list of available clients

    private int POOL_SIZE = 3;
    //thread pool of constant size for processing incoming requests:
    private ExecutorService executor = Executors.newFixedThreadPool(POOL_SIZE);
    //to synchronize competing accesses of client collection:
    private ReadWriteLock clientLock = new ReentrantReadWriteLock();

    //thread that gives the user the opportunity to shut down the server with a graphical input mask.
    //once the user has made the appropriate input, this thread should set the stopRequested flag.
    Thread stopThread = new Thread(new StopDialog(this));
    protected volatile boolean stopRequestFlag = false;


    public static void main(String[] args) {
        Broker broker = new Broker(); //instantiates a new broker
        broker.broker(); //starts the broker method
    }

    //(inner) class BrokerTask, handles the processing and answering of messages
    private class BrokerTask implements Runnable {
        Message m;
        private BrokerTask(Message m) {
            this.m = m;
        }
        @Override
        public void run() {
            Serializable s = m.getPayload();

            //register method is called with a RegisterRequest:
            if (s instanceof RegisterRequest) {
                register(m);
            }

            //deregister method is called with a DeregisterRequest
            if (s instanceof DeregisterRequest) {
                deregister(m);
            }

            //handoffFish method is called upon a HandoffRequest
            if (s instanceof HandoffRequest) {
                HandoffRequest handoffRequest = (HandoffRequest) m.getPayload();
                InetSocketAddress inetSocketAddress = m.getSender();
                handoffFish(handoffRequest,inetSocketAddress);
            }
        }
    }

    public void broker(){

        stopThread.start();
        while(!stopRequestFlag) {
            //incoming messages must be decoded and the appropriate methods called:
            Message m = endpoint.blockingReceive();

            //For every incoming message will
            //a new instance of BrokerTask be created and this is assigned to the ExecutorService
            //passed for execution.
            executor.execute(new BrokerTask(m));
        }
        executor.shutdown();

    }

    private void register(Message msg) {
        String id = "tank" + clientNumber; //broker assigns a new ID
        clientNumber++;

        clientLock.writeLock().lock();
        clients.add(id, msg.getSender()); //new client is added to the clients list
        clientLock.writeLock().unlock();

        Neighbor newNeighbor = new Neighbor(id); //to get neighbors of the new client

        //address of new client:
        InetSocketAddress newClientAddress = (InetSocketAddress) clients.getClient(clients.indexOf(id));

        //the first client in the distributed environment is its own left and right neighbor:
        if(clients.size() == 1){
            endpoint.send(msg.getSender(), new NeighborUpdate(newClientAddress, newClientAddress));
        } else {
            //right neighbor of new client receives addresses of his new left and right neighbor:
            endpoint.send(newNeighbor.getRightNeighborSocket(), new NeighborUpdate(newClientAddress,
                    newNeighbor.getInitialRightNeighborSocket()));

            //left neighbor of new client receives addresses of his left and new right neighbor:
            endpoint.send(newNeighbor.getLeftNeighborSocket(), new NeighborUpdate(newNeighbor.getInitialLeftNeighborSocket(),
                    newClientAddress));

            endpoint.send(newClientAddress, new NeighborUpdate(newNeighbor.getLeftNeighborSocket(),
                    newNeighbor.getRightNeighborSocket()));
        }

        endpoint.send(msg.getSender(), new RegisterResponse(id)); //RegisterResponse message
    }

    private void deregister(Message msg) {
        String removeID = ((DeregisterRequest) msg.getPayload()).getId(); //ID of the deleted client
        Neighbor removedNeighbor = new Neighbor(removeID); //to get neighbors of the deleted client

        //affected clients are the left and right neighbors of the client to be deleted:
        endpoint.send(removedNeighbor.getLeftNeighborSocket(), new NeighborUpdate(
                removedNeighbor.getInitialLeftNeighborSocket(), removedNeighbor.getRightNeighborSocket()));
        endpoint.send(removedNeighbor.getRightNeighborSocket(), new NeighborUpdate(
                removedNeighbor.getLeftNeighborSocket(), removedNeighbor.getInitialRightNeighborSocket()));

        clientLock.writeLock().lock();
        //broker removes the client from the client list:
        clients.remove(clients.indexOf(removeID));
        clientLock.writeLock().unlock();
    }

    private void handoffFish(HandoffRequest handoffRequest, InetSocketAddress inetSocketAddress) {
        //broker determines the affected neighbor and gives the HandoffRequest this further:
        int index = clients.indexOf(inetSocketAddress);
        FishModel fishModel = handoffRequest.getFish();
        Direction direction = fishModel.getDirection();

        InetSocketAddress neighborReceiver;

        clientLock.readLock().lock();
        if (direction == Direction.LEFT) {
            neighborReceiver = (InetSocketAddress) clients.getLeftNeighorOf(index);
        }
        else {
            neighborReceiver = (InetSocketAddress) clients.getRightNeighorOf(index);
        }
        clientLock.readLock().unlock();

        endpoint.send(neighborReceiver, handoffRequest);
    }

    final class Neighbor {
        private String id;

        public Neighbor(String id) {
            this.id = id;
        }

        public InetSocketAddress getRightNeighborSocket() {
            InetSocketAddress rightNeighborSocket;
            rightNeighborSocket = (InetSocketAddress) clients.getRightNeighorOf(clients.indexOf(id));
            return rightNeighborSocket;
        }

        public InetSocketAddress getInitialRightNeighborSocket() {
            InetSocketAddress initialRightNeighborSocket;
            int indexInitialRightNeighborSocket = clients.indexOf(clients.getRightNeighorOf(clients.indexOf(id)));
            initialRightNeighborSocket = (InetSocketAddress) clients.getRightNeighorOf(indexInitialRightNeighborSocket);
            return initialRightNeighborSocket;
        }

        public InetSocketAddress getLeftNeighborSocket() {
            InetSocketAddress leftNeighborSocket;
            leftNeighborSocket = (InetSocketAddress) clients.getLeftNeighorOf(clients.indexOf(id));
            return leftNeighborSocket;
        }

        public InetSocketAddress getInitialLeftNeighborSocket() {
            InetSocketAddress initialLeftNeighborSocket;
            int indexInitialLeftNeighborSocket = clients.indexOf(clients.getLeftNeighorOf(clients.indexOf(id)));
            initialLeftNeighborSocket = (InetSocketAddress) clients.getLeftNeighorOf(indexInitialLeftNeighborSocket);
            return initialLeftNeighborSocket;
        }
    }




}
