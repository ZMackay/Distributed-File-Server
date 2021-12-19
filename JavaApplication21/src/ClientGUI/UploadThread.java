package ClientGUI;


import java.io.File;
import javax.swing.JProgressBar;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author Jacob
 */
public class UploadThread extends Thread {
    
    File file;
    JProgressBar pb;
    ClientAPI clapi;
    
    public UploadThread(File file, JProgressBar pb, ClientAPI clapi) {
        this.file = file;
        this.pb = pb;
        this.clapi = clapi;
    }
    
    @Override
    public void run() {
        clapi.uploadFile(file, pb);
    }
    
}
