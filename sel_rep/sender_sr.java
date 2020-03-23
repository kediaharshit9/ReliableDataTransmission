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
  
public class sender_sr
{ 
	static int PACKET_LENGTH = 1500;
	static int PACKET_GEN_RATE = 300;
	static boolean debug;
	static InetAddress ip;
	static int dest_port = 12345;
	static int MAX_PACKETS = 1500;
	static int WINDOW_SIZE = 4;
	static int MAX_BUFFER_SIZE = 10;
	
	static List <DatagramPacket> buffer;
	static List <Integer> seq_buf;
	static List <Timer> timer;
	static List <Integer> running;
	
	static int nextseqno;
	static int base;
	static int to_send;
	static DatagramSocket ds;
	static byte[] receive;
	static Thread loader;
	static Thread acker;
	
	static int retrans;
	static double rdt[];
	static double starts[];
	static double ends[];
	static int resends[];
	static int acked[];
	static Lock lock;
	static Lock buf_lock;
	static Random random;
	static int total_recv;
    public static void main(String args[]) throws IOException 
    { 
        // Step 1:Create the socket object for carrying the data. 
        ds = new DatagramSocket(); 
        ip = InetAddress.getLocalHost(); 
    	int params = args.length;
    	
    	for(int i=0; i<params; i++)
    	{
    		if(args[i].startsWith("-"))
    		{
    			if(args[i].equalsIgnoreCase("-d"))
    				debug = true;
    			else if(args[i].equalsIgnoreCase("-s"))
    				ip = InetAddress.getByName(args[i+1]);
    			else if(args[i].equalsIgnoreCase("-p"))
    				dest_port = Integer.parseInt(args[i+1]);
    			else if(args[i].equalsIgnoreCase("-l"))
    				PACKET_LENGTH = Integer.parseInt(args[i+1]);
    			else if(args[i].equalsIgnoreCase("-r"))
    				PACKET_GEN_RATE = Integer.parseInt(args[i+1]);
    			else if(args[i].equalsIgnoreCase("-n"))
    				MAX_PACKETS = Integer.parseInt(args[i+1]);
    			else if(args[i].equalsIgnoreCase("-w"))
    				WINDOW_SIZE = Integer.parseInt(args[i+1]);
    			else if(args[i].equalsIgnoreCase("-b"))
    				MAX_BUFFER_SIZE = Integer.parseInt(args[i+1]);
    		}
    	}
    	
    	double begg = System.nanoTime();  

        buffer = new ArrayList<DatagramPacket>();
        seq_buf = new ArrayList<Integer>();
        timer = new ArrayList<Timer>();
        running = new ArrayList<Integer>();
        
        rdt = new double[MAX_PACKETS];
        starts = new double[MAX_PACKETS];
        ends = new double[MAX_PACKETS];
        resends = new int[MAX_PACKETS];
        acked= new int[MAX_PACKETS];
        random = new Random();
        
        total_recv = 0;
        lock = new ReentrantLock();
        buf_lock = new ReentrantLock();
        base = 1;
		nextseqno = 1;
		to_send = 0;
        receive = new byte[32];
        retrans = 0;
        
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
	       				int l = 40 + random.nextInt(PACKET_LENGTH-40);
	       				byte buf[] = new byte[l];
	       				buf[0] = (byte) (nextseqno >> 24);
	       				buf[1] = (byte) (nextseqno >> 16);
	       				buf[2] = (byte) (nextseqno >> 8);
	       				buf[3] = (byte) (nextseqno);
	       				DatagramPacket dp = new DatagramPacket(buf, buf.length, ip, dest_port);
	       	    		
	       				buf_lock.lock();
	       				seq_buf.add(nextseqno);
	       				buffer.add(dp);	
	       				running.add(0);
	       				timer.add(new Timer());
	       				buf_lock.unlock();
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
                    
                    if(acked[seq_recv-1] == 0)
                    {
                    	acked[seq_recv-1]=1;
                    	total_recv++;
                    }
                    lock.lock();
                    ends[seq_recv-1] = ((double) System.nanoTime() - begg)/1000000;
                    lock.unlock();

                    buf_lock.lock();
                    int idx = seq_buf.indexOf(seq_recv);
                    if(idx == 0)
                    	base = seq_recv+1;
                    buffer.remove(idx);
                    seq_buf.remove(idx);
                    timer.get(idx).cancel();
                    timer.remove(idx);
                    running.remove(idx);
                    buf_lock.unlock();
                    
                    rdt[seq_recv-1] = ends[seq_recv-1] - starts[seq_recv-1];
                    
                    if(debug)
                    	System.out.printf("Seq %d : Time Generated: %.3f ms RTT: %.3f ms, Number of Attempts: %d\n", seq_recv,(float) starts[seq_recv-1],(float) rdt[seq_recv-1], resends[seq_recv-1]);
                    
                    receive = new byte[32];
                    
                    if(total_recv>=MAX_PACKETS)
                    	break;
        		}
//        		System.out.println("All acknowledged, thread exiting");
        		
        	}
        });
        
        loader.start();
        acker.start();
        
        //sending packets
        while(true)
        {
//        	System.out.println(base);
        	if(base>MAX_PACKETS)
        		break;   
        	if(total_recv>=MAX_PACKETS)
            	break;
        	to_send = 0;
        	        	
        	buf_lock.lock();
    		while(to_send < WINDOW_SIZE && to_send < buffer.size())
    		{    			
    			if(running.get(to_send) == 0)
    			{    				
        			running.remove(to_send);
        			timer.remove(to_send);
        			
    				DatagramPacket DpSend = buffer.get(to_send);
            		ds.send(DpSend);
            		
            		lock.lock();
            		starts[seq_buf.get(to_send)-1] = ((double) System.nanoTime() - begg)/1000000;
            		lock.unlock();            		
            		resends[seq_buf.get(to_send)-1]++;
            		
//            		System.out.println("Sent: " + seq_buf.get(to_send));
            		
            		Timer t = new Timer();
            		t.schedule(new MyTimerTask(seq_buf.get(to_send)) {
//                    @Override
                    public void run() {
//                    	System.out.println("to_send value "+ seq_num);
                    	buf_lock.lock();
                    	int idx = seq_buf.indexOf(seq_num);
                        running.set(idx, 0);
                        buf_lock.unlock();                        
                    }
	                }, 50);
					timer.add(to_send, t);
            		running.add(to_send, 1);
    			}
    			to_send++;
    		}
    		buf_lock.unlock();
    		
    		try {
    			// sleep 50 ns
				Thread.sleep(0, 50);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }
      
        try {
			loader.join();
			acker.join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        
        double sum = 0;
        retrans = 0;
        for(int i=0; i< MAX_PACKETS; i++)
        {
//        	rdt[i] = ends[i] - starts[i];
        	sum+= rdt[i];
        	retrans += resends[i]-1;
        }
        
        System.out.println("PACKET_GEN_RATE: " + PACKET_GEN_RATE);
        System.out.println("MAX_PACKET_LENGTH: "+ PACKET_LENGTH);
        System.out.printf("Average retransmissions: %.3f\n",  ((float)retrans/(float)MAX_PACKETS));
        System.out.printf("Average RTT: %.3f\n", (float)sum/MAX_PACKETS );
        System.exit(0);
    } 
       
}

abstract class MyTimerTask extends TimerTask
{
	int seq_num;
	public MyTimerTask(int index) {
        this.seq_num = index;
    }	
}