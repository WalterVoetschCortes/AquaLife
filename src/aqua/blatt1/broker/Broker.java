package aqua.blatt1.broker;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.msgtypes.DeregisterRequest;
import aqua.blatt1.common.msgtypes.HandoffRequest;
import aqua.blatt1.common.msgtypes.RegisterRequest;
import aqua.blatt1.common.msgtypes.RegisterResponse;
import messaging.Endpoint;
import messaging.Message;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Broker {

    private boolean done = false;
    private Endpoint endpoint = new Endpoint(4711); //endpoint listens on port 4711
    private ClientCollection clients = new ClientCollection(); //broker keeps a list of available clients

    public static void main(String[] args) {
        Broker broker = new Broker(); //instantiates a new broker
        broker.broker(); //starts the broker method
    }

    public void broker(){

        // infinite loop blocking message being repaired:
        while( !done ) {

            //incoming messages must be decoded and the appropriate methods called:
            Message msg = endpoint.blockingReceive();

            //register method is called with a RegisterRequest:
            if (msg.getPayload() instanceof RegisterRequest) {
                register(msg);
            }

            //deregister method is called with a DeregisterRequest
            if (msg.getPayload() instanceof DeregisterRequest) {
                deregister(msg);
            }

            //handoffFish method is called upon a HandoffRequest
            if (msg.getPayload() instanceof HandoffRequest) {
                HandoffRequest handoffRequest = (HandoffRequest) msg.getPayload();
                InetSocketAddress inetSocketAddress = msg.getSender();
                handoffFish(handoffRequest,inetSocketAddress);
            }
        }
    }

    private void register(Message msg) {
        String id = "tank" + clients.size(); //broker assigns a new ID
        clients.add(id, msg.getSender()); //new client is added to the clients list
        endpoint.send(msg.getSender(), new RegisterResponse(id)); //RegisterResponse message
    }

    private void deregister(Message msg) {
        clients.remove(clients.indexOf(((DeregisterRequest) msg.getPayload()).getId())); //broker removes the client from the client list
    }

    private void handoffFish(HandoffRequest handoffRequest, InetSocketAddress inetSocketAddress) {
        //broker determines the affected neighbor and gives the HandoffRequest this further:
        int index = clients.indexOf(inetSocketAddress);
        FishModel fishModel = handoffRequest.getFish();
        Direction direction = fishModel.getDirection();

        InetSocketAddress neighborReceiver;
        if (direction == Direction.LEFT) {
            neighborReceiver = (InetSocketAddress) clients.getLeftNeighorOf(index);
        }
        else {
            neighborReceiver = (InetSocketAddress) clients.getRightNeighorOf(index);
        }

        endpoint.send(neighborReceiver, handoffRequest);
    }




    }
