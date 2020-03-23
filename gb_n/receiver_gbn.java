import java.io.IOException; 
import java.net.DatagramPacket; 
import java.net.DatagramSocket; 
import java.net.InetAddress; 
import java.net.SocketException; 
import java.lang.*;
import java.util.*;

public class receiver_gbn 
{ 
	static boolean debug;
	static int my_port=12345;
	static int MAX_PACKETS=1500;
	static double DROP_PROB=0.1;
	
	static int resends[];
	static int exp_seq_no; //first 4 bytes as sequence no
	
    public static void main(String[] args) throws IOException 
    { 
    	int params = args.length;
    	for(int i=0; i<params; i++)
    	{
    		if(args[i].startsWith("-"))
    		{
    			if(args[i].equals("-d"))
    				debug = true;
    			else if(args[i].equals("-p"))
    				my_port = Integer.parseInt(args[i+1]);
    			else if(args[i].equals("-n"))
    				MAX_PACKETS = Integer.parseInt(args[i+1]);
    			else if(args[i].equals("-e"))
    				DROP_PROB = Float.parseFloat(args[i+1]);
    		}
    	}
    	
    	resends = new int[MAX_PACKETS];
    	
    	double start = System.nanoTime();
        // Step 1 : Create a socket to listen at port
        DatagramSocket ds = new DatagramSocket(my_port); 
        byte[] receive = new byte[4096]; 
  
        DatagramPacket DpReceive = null; 
        exp_seq_no = 1;
        System.out.println("UDP server up...\n");
        
        while (exp_seq_no <= MAX_PACKETS) 
        { 
            // Step 2 : create a DatgramPacket to receive the data. 
            DpReceive = new DatagramPacket(receive, receive.length); 
            ds.receive(DpReceive); 
          
            double tm = ((double) System.nanoTime() - start)/1000000;
            
            int curr_seq_no = (receive[0]<<24)&0xff000000|
            				  (receive[1]<<16)&0x00ff0000|
            				  (receive[2]<< 8)&0x0000ff00|
            				  (receive[3]<< 0)&0x000000ff;
            int len = DpReceive.getLength();   
            
            resends[curr_seq_no-1]++;
            
            double prob = Math.random();
            double tp = 0;
            for(int i=0; i<len; i++)
            {
            	tp += Math.tanh((Math.pow(0.123456789, i)));
            }
//            System.out.println("Length  : "+ len + "JUNK: " + tp);
            if(prob > DROP_PROB || resends[curr_seq_no-1] >=5) // packet not dropped
            {
//            	System.out.println("RECEIVED: " + curr_seq_no);
            	if(curr_seq_no == exp_seq_no)
            	{
            		byte buf[] = new byte[7];
            		buf[0] = receive[0];
            		buf[1] = receive[1];
            		buf[2] = receive[2];
            		buf[3] = receive[3];
            		buf[4] = 'A';
            		buf[5] = 'C';
            		buf[6] = 'K';
            		DatagramPacket DpSend = new DatagramPacket(buf, buf.length, DpReceive.getAddress(), DpReceive.getPort());
                    ds.send(DpSend);
                    tp += 1;
                    exp_seq_no++;
                    if(debug)
                    	System.out.printf("Seq %d: Time Received: %.3f Packet Dropped: false\n", curr_seq_no, (float)tm);
            	}
            }
            else
            {
            	if(debug)
            		System.out.printf("Seq %d: Time Received: %.3f Packet Dropped: true\n", curr_seq_no, (float)tm);
            }
            receive = new byte[4096];
        }
        ds.close();
    } 
  
} 
