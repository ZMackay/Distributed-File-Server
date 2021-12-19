/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ClientGUI;

import com.formdev.flatlaf.FlatDarculaLaf;

/**
 *
 * @author Jacob
 */
public class DistSystGUI {

    /**
     * @param args the command line arguments
     */
    
    public static LoginGUI login; 
   //public static ClientAPI clapi = new ClientAPI();
    
    
    public static void main(String[] args) {
        // TODO code application logic here
        
        FlatDarculaLaf.setup();
        login = new LoginGUI();
        login.setVisible(true);
        
        
        
        
        
    }
    
}
