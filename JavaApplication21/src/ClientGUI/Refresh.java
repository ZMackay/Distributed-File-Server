/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ClientGUI;

/**
 *
 * @author Jacob
 */
public class Refresh implements Runnable {
    public void run() {
        DistSystGUI.login.client.refresh("a");
    }
}
