import java.io.IOException; 
import java.net.DatagramPacket; 
import java.net.DatagramSocket; 
import java.net.InetAddress; 
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.ExecutorService; 
import java.util.concurrent.Executors; 
import java.util.concurrent.locks.ReentrantLock;
import java.lang.*;
  
public class sender_gbn 
{ 
	static int PACKET_LENGTH = 256;
	static int PACKET_GEN_RATE = 300;
	static boolean debug;
	static InetAddress ip;
	static int dest_port = 12345;
	static int MAX_PACKETS = 1500;
	static int WINDOW_SIZE = 4;
	static int MAX_BUFFER_SIZE = 10;
	
	static List <DatagramPacket> buffer;
	static List <Integer> seq_buf;
	static int nextseqno;
	static int base;
	static int to_send;
	static DatagramSocket ds;
	static byte[] receive;
	static int timeout_flag;
	static Thread loader;
	static Thread acker;
	static Timer timer;
	static int retrans;
	static double rdt[];
	static double starts[];
	static double ends[];
	static int resends[];
	static Lock lock;
	static Lock buf_lock;
	
    public static void main(String args[]) throws IOException 
    { 
    	int params = args.length;
    	
    	for(int i=0; i<params; i++)
    	{
    		if(args[i].startsWith("-"))
    		{
    			if(args[i].equals("-d"))
    				debug = true;
    			else if(args[i].equals("-s"))
    				ip = InetAddress.getByName(args[i+1]);
    			else if(args[i].equals("-p"))
    				dest_port = Integer.parseInt(args[i+1]);
    			else if(args[i].equals("-l"))
    				PACKET_LENGTH = Integer.parseInt(args[i+1]);
    			else if(args[i].equals("-r"))
    				PACKET_GEN_RATE = Integer.parseInt(args[i+1]);
    			else if(args[i].equals("-n"))
    				MAX_PACKETS = Integer.parseInt(args[i+1]);
    			else if(args[i].equals("-w"))
    				WINDOW_SIZE = Integer.parseInt(args[i+1]);
    			else if(args[i].equals("-b"))
    				MAX_BUFFER_SIZE = Integer.parseInt(args[i+1]);
    		}
    	}
    	
    	double begg = System.nanoTime();
        Scanner sc = new Scanner(System.in); 
  
        // Step 1:Create the socket object for carrying the data. 
        ds = new DatagramSocket(); 
        ip = InetAddress.getLocalHost(); 
        
        buffer = new ArrayList<DatagramPacket>();
        seq_buf = new ArrayList<Integer>();
        rdt = new double[MAX_PACKETS];
        starts = new double[MAX_PACKETS];
        ends = new double[MAX_PACKETS];
        resends = new int[MAX_PACKETS];
        
        for(int i=0; i<MAX_PACKETS; i++)
        	resends[i] = 1;
        
        
        lock = new ReentrantLock();
        buf_lock = new ReentrantLock();
        base = 1;
		nextseqno = 1;
		to_send = 0;
		timeout_flag = 0;
        receive = new byte[32];
        retrans = 0;
        timer = new Timer();
        TimerTask timeout = new TimerTask() 
        {
        	@Override
        	public void run()
        	{
        		timeout_flag = 1;
        	}
        };
        
        loader = new Thread(new Runnable() 
        {
           @Override
           public void run()
	       {  
//	       		System.out.println("buffer loader thread is running...");
	       		
	       		int sleep_ns = 1000000000/PACKET_GEN_RATE;
	       		int sleep_ms = sleep_ns/1000000;
	       		
	       		while(nextseqno <= MAX_PACKETS)
	       		{
	       			if(buffer.size() < MAX_BUFFER_SIZE)
	       			{
	       				byte buf[] = new byte[PACKET_LENGTH];
	       				buf[0] = (byte) (nextseqno >> 24);
	       				buf[1] = (byte) (nextseqno >> 16);
	       				buf[2] = (byte) (nextseqno >> 8);
	       				buf[3] = (byte) (nextseqno);
	       				DatagramPacket dp = new DatagramPacket(buf, buf.length, ip, dest_port);
	       	    		
	       				seq_buf.add(nextseqno);
	       				buffer.add(dp);	
//	       				System.out.println("Added in buffer: "+ nextseqno);
	       	    		nextseqno++;
	       			}	       			
	       			
	       			try{
	       				Thread.sleep(sleep_ms, sleep_ns%1000000);
	       			} 
	       			catch (InterruptedException e) {
	       				// TODO Auto-generated catch block
	       				e.printStackTrace();
	       			}
	       		}
//	       		System.out.println("Ending thread buffer loader");
	       	}
        });
        
        acker = new Thread(new Runnable()
        {
        	@Override
        	public void run()
        	{
//        		System.out.println("ack_collector thread is running...");
        		
        		while(true)
        		{
        			DatagramPacket DpRecv = new DatagramPacket(receive, receive.length);
        			
                    try {
        				ds.receive(DpRecv);
        			} catch (IOException e) {
        				// TODO Auto-generated catch block
        				e.printStackTrace();
        			}	
                    
                    int seq_recv = 	(receive[0]<<24)&0xff000000|
          				  			(receive[1]<<16)&0x00ff0000|
          				  			(receive[2]<< 8)&0x0000ff00|
          				  			(receive[3]<< 0)&0x000000ff;
//                    System.out.println("Received Seq: " + seq_recv); 
                    
                    lock.lock();
                    ends[seq_recv-1] = ((double) System.nanoTime() - begg)/1000000;
                    lock.unlock();
                    
                    buf_lock.lock();
                    int idx = seq_buf.indexOf(seq_recv);
                    base = seq_recv+1;
                    buffer.remove(idx);
                    seq_buf.remove(idx);
                    to_send--;
                    buf_lock.unlock();
                    
                    rdt[seq_recv-1] = ends[seq_recv-1] - starts[seq_recv-1];
                    
                    if(debug)
                    	System.out.printf("Seq %d : Time Generated: %.3f ms RTT: %.3f ms, Number of Attempts: %d\n", seq_recv,(float) starts[seq_recv-1],(float) rdt[seq_recv-1], resends[seq_recv-1]);
//                    	System.out.println("Seq "+ seq_recv + ": Time Generated: "+starts[seq_recv-1]+ "ms RTT: " + rdt[seq_recv -1] + "ms, Number of Attempts: "+ resends[seq_recv-1]);
                    
                    receive = new byte[32];
                    if(seq_recv == MAX_PACKETS)
                    {
//                    	System.out.println("ALL Acknowledged, ending thread");
                    	break;
                   	}
        		}
        	}
        });
        
        loader.start();
        acker.start();
        
        //sending packets
        while(true)
        {
        	if(base>MAX_PACKETS)
        		break;
        	
        	if(timeout_flag > 0)
        	{
        		timeout_flag = 0;
        		buf_lock.lock();
        		to_send = 0;
	    		while(to_send < WINDOW_SIZE && to_send < buffer.size())
	    		{
	    			DatagramPacket DpSend = buffer.get(to_send);
	    			resends[seq_buf.get(to_send)-1]++;
	        		try {
						ds.send(DpSend);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
//	        		System.out.println("Sent again: " + seq_buf.get(to_send));
	        		starts[seq_buf.get(to_send)-1] = ((double) System.nanoTime() - begg)/1000000;
	        		to_send++;
	        		retrans++;
	        		timer = new Timer();
	        		timer.schedule(new java.util.TimerTask() {
	                    @Override
	                    public void run() {
	                        timeout_flag = 1;
	                    }
	                }, 50);
	    		}
	    		buf_lock.unlock();
        	}
        	

    		if(to_send < WINDOW_SIZE && to_send < buffer.size())
    		{
//    			System.out.println("Sender: " + to_send+ " "+ buffer.size());
    			timer.cancel();
    			
    			DatagramPacket DpSend = buffer.get(to_send);
        		ds.send(DpSend);
        		lock.lock();
        		starts[seq_buf.get(to_send)-1] = ((double) System.nanoTime() - begg)/1000000;
        		lock.unlock();
        		
//        		System.out.println("Sent: " + seq_buf.get(to_send));
        		buf_lock.lock();
        		to_send++;
        		buf_lock.unlock();
        		timer = new Timer();
        		timer.schedule(new java.util.TimerTask() {
                    @Override
                    public void run() {
                        timeout_flag = 1;
                    }
                }, 50);
    		}  
    		try {
    			// sleep 50 ns
				Thread.sleep(0, 50);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }        
        
        try {
        	timer.cancel();
			loader.join();
			acker.join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        
        double sum = 0;
        for(int i=0; i< MAX_PACKETS; i++)
        {
        	sum+= rdt[i];
        }
        
        System.out.println("PACKET_GEN_RATE: " + PACKET_GEN_RATE);
        System.out.println("PACKET_LENGTH: "+ PACKET_LENGTH);
        System.out.printf("Average retransmissions: %.3f\n", (float) retrans/MAX_PACKETS);
        System.out.printf("Average RTT: %.3f\n", (float)sum/MAX_PACKETS );
        System.exit(0);
    } 
    
}