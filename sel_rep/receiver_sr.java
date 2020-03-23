import java.io.IOException; 
import java.net.DatagramPacket; 
import java.net.DatagramSocket; 
import java.net.InetAddress; 
import java.net.SocketException; 
import java.lang.*;
import java.util.*;

public class receiver_sr 
{ 
	static boolean debug;
	static int my_port=12345;
	static int MAX_PACKETS=1500;
	static double DROP_PROB=0.5;
	static int WINDOW_SIZE = 4;
	static int BUFFER_SIZE = 10;
	static int exp_seq_no; //first 4 bytes as sequence no
	
	static int resends[];
	static double recv_time[];
	static List <DatagramPacket> buffer;
	static List <Integer> seq_buf;
	
    public static void main(String[] args) throws IOException 
    { 
    	int params = args.length;
    	
    	for(int i=0; i<params; i++)
    	{
    		if(args[i].startsWith("-"))
    		{
    			if(args[i].equals("-d"))
    				debug = true;
    			else if(args[i].equalsIgnoreCase("-p"))
    				my_port = Integer.parseInt(args[i+1]);
    			else if(args[i].equalsIgnoreCase("-n"))
    				MAX_PACKETS = Integer.parseInt(args[i+1]);
    			else if(args[i].equalsIgnoreCase("-W"))
    				WINDOW_SIZE = Integer.parseInt(args[i+1]);
    			else if(args[i].equalsIgnoreCase("-B"))
    				BUFFER_SIZE = Integer.parseInt(args[i+1]);
    			else if(args[i].equalsIgnoreCase("-e"))
    				DROP_PROB = Float.parseFloat(args[i+1]);
    		}
    	}
    	
    	double start = System.nanoTime();
        // Step 1 : Create a socket to listen at port 
        DatagramSocket ds = new DatagramSocket(my_port); 
        byte[] receive = new byte[4096]; 
  
        buffer = new ArrayList <DatagramPacket>();
        seq_buf = new ArrayList <Integer>();
        recv_time = new double[MAX_PACKETS];
        resends = new int[MAX_PACKETS];
        
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
            resends[curr_seq_no-1]++;
            int len = DpReceive.getLength();   
//            System.out.println("RECEIVED: " + curr_seq_no + " length: "+len);
            
            double tp = 0;
            for(int i=0; i<len; i++)
            	tp += Math.pow(0.123456789, i);
         
            
            double prob = Math.random();

//            System.out.println(prob);
            if(prob > DROP_PROB || resends[curr_seq_no-1] >= 10) // packet not dropped
            {
            	
            	recv_time[curr_seq_no-1] = tm;
            	if(buffer.size() < BUFFER_SIZE)
            	{
	            	if(curr_seq_no <= exp_seq_no)
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
	                    if(curr_seq_no == exp_seq_no)
	                    {
	                    	if(debug)
	                    		System.out.printf("Seq %d: Time Received: %.3f Packet Dropped: false\n", curr_seq_no, (float)recv_time[curr_seq_no-1]);
	                    	exp_seq_no++;
		                    while(seq_buf.contains(exp_seq_no))
		                    {
		                    	int idx = seq_buf.indexOf(exp_seq_no);
		                    	seq_buf.remove(idx);
		                    	buffer.remove(idx);
		                    	if(debug)
		                    		System.out.printf("Seq %d: Time Received: %.3f Packet Dropped: false\n", exp_seq_no, (float)recv_time[exp_seq_no-1]);
		                    	exp_seq_no++;
		                    }
	                    }
	            	}            	
	            	else if((curr_seq_no < exp_seq_no + WINDOW_SIZE+1) && (curr_seq_no > exp_seq_no))
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
                        buffer.add(DpReceive);
                        seq_buf.add(curr_seq_no);
            		}	            	
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
//        System.out.println("Received " + MAX_PACKETS + " packets");
        System.exit(0);
    } 
} 
