package aqua.blatt1.broker;

import aqua.blatt2.broker.Poisoner;

import javax.swing.*;

public class StopDialog implements Runnable{
    private Broker broker;

    public StopDialog(Broker broker){
        this.broker = broker;
    }
    @Override
    public void run() {
        JOptionPane.showMessageDialog(null, "press OK button to stop server");
        broker.stopRequestFlag = true;
        new Poisoner().sendPoison();
    }
}
