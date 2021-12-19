/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ClientGUI;

import java.io.File;
import javax.swing.JProgressBar;

/**
 *
 * @author Jacob
 */
public class DownloadThread extends Thread {
    
    String file;
    JProgressBar pb;
    ClientAPI clapi;
    
    public DownloadThread(String file, JProgressBar pb, ClientAPI clapi) {
        this.file = file;
        this.pb = pb;
        this.clapi = clapi;
    } 
    
    public void run() {
        clapi.downloadFile(file, pb);
    }
    
}
