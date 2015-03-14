package de.tum.mw.lfe.helloDds;

//------------------------------------------------------
//Revision History 'helloDDS'
//------------------------------------------------------
//Version  Date		Author		  Mod
//1        Mar, 2015	Michael Krause	  initial
//
//------------------------------------------------------
/*
The MIT License (MIT)

        Copyright (c) 2015 Michael Krause (krause@tum.de), Institute of Ergonomics, Technische Universität München

        Permission is hereby granted, free of charge, to any person obtaining a copy
        of this software and associated documentation files (the "Software"), to deal
        in the Software without restriction, including without limitation the rights
        to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
        copies of the Software, and to permit persons to whom the Software is
        furnished to do so, subject to the following conditions:

        The above copyright notice and this permission notice shall be included in
        all copies or substantial portions of the Software.

        THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
        IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
        FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
        AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
        LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
        OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
        THE SOFTWARE.
*/

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

public class BtThread extends Thread {
	//dont forget: android manifest permission BT 

	private static final String TAG = "DDS.BtThread";		
	
	private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
	

    public static final int RETRY_AFTER_MS = 3000;//retry to reconnect after xxx ms
    public static final int BEACON_MS = 500;//send every xxx ms abeacon
    
    public static final int BT_RX_CALLBACK = 7;
    public static final String MY_DEVICE = "myDDS";
    
    private Activity mParent;
    private BluetoothAdapter mAdapter;
    private BluetoothDevice mDevice;
    private BluetoothSocket mSocket;
	private OutputStream mOut = null;
	private InputStream mIn = null;
	private BufferedReader mReader = null;
    
    
    public Handler mHandler;
    public Handler mCallbackHandler;//return data we will forward it to this handler.
    public int mCallbackHandlerWhatValue = BT_RX_CALLBACK;//msg value for callback.	    
	public static final int CONNECT = 1;	    
	public static final int COMMAND = 2;	    
	public static final int CLOSE   = 3;
	
	
	private long mLastBeacon;//last time we sena a beacon
	private long mLastTry;//last time we tried to connect
    private boolean mEndThread = false;
    private boolean mTryAndTry = true;
	private int mTxCounter = 0;
	private int mRxCounter = 0;
    private boolean mError = false;
	private List<String> mCommands = new ArrayList<String>();
    

	BtThread(Activity parent, Handler callbackHandler){
		mParent = parent;
		mCallbackHandler = callbackHandler;
	}
	
	static  boolean checkIfMyDevice(BluetoothDevice device){ 
		if(device.getName().equals(MY_DEVICE)){
			Log.i(TAG,"checkIfMyDevice() true");
			return true;

		}
		return false;
	}
	
	
    public void sendCommand(String command){
    	//Log.d(TAG,"queued command:+ "+command);
    	synchronized(mCommands){
    		mCommands.add(command);
    	}
    }
    
    public void resetCommandQueue(){
    	synchronized(mCommands){
    		mCommands.clear();
    	}	
    }
    
    
    public void close(){
    	//synchronized(mClient){
    		try {      
			  if (mSocket != null) {
				  mOut.close();
				  mIn.close();
				  mReader.close();
			  }    
			} catch (Exception e) {
 				Log.e(TAG, "close failed: " +e.getMessage());					
			}
		  try{mSocket.close();}catch (Exception e){}
		  mSocket = null;
		  mAdapter = null;
    	//}
    }
    
    public void end(){
    	  mTryAndTry = false;
		  mEndThread = true;
		  close();	
    }
    
    
    public void connect(){
    	synchronized(this){
	    	mAdapter = BluetoothAdapter.getDefaultAdapter();
	    	if (mAdapter == null) {
	    		Log.e(TAG,"connect() mAdapter == null");
	    	    return; 
	    	}

	    	mAdapter.cancelDiscovery();
	    	mDevice = null;
	    	Set<BluetoothDevice>pairedDevices = mAdapter.getBondedDevices();
	    	if(pairedDevices.size() > 0){
		    	for(BluetoothDevice device : pairedDevices){
			    	if(device.getName().equals(MY_DEVICE)){
			    		mDevice = device;
			    		Log.i(TAG,"connect()");
			    	}
		    	}//for	
		    }else{
	    		Log.e(TAG,"connect() pairedDevices.size() <= 0");
		    }
	
	
	    	 // get socket
	    	mSocket = null;
	    	
	    	OutputStream out = null;
	    	try {
	    		mSocket = mDevice.createRfcommSocketToServiceRecord(SPP_UUID); 
	    	} catch (IOException e) {
	    		Log.e(TAG,"connect() get socket failed: " +e.getMessage());
	    		mSocket = null;
	    	}
	
	    	try {           
	    		mSocket.connect(); 
	    	    mOut = mSocket.getOutputStream();
	    	    mIn = mSocket.getInputStream();
	            mReader = new BufferedReader(new InputStreamReader(mIn, "US-ASCII"));
	
	    	} catch (IOException e) {
	    		Log.e(TAG,"connect() connect to socket failed: "+e.getMessage());
	    		mSocket = null;
	    	}    
    	}//synchronized
    }
    
    @Override
    public void run() {
    	
    	Log.d(TAG, "Hello from BtThread");
    	
    	resetCommandQueue();

        mAdapter = BluetoothAdapter.getDefaultAdapter();

        if ((mAdapter != null) && (!mAdapter.isEnabled())) {
            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            mParent.startActivityForResult(enableBluetooth, 0);
        }


        //-------prepare handler & looper--------------
        Looper.prepare();
        mHandler = new Handler();
        
        /*
        mHandler = new Handler() {
                public void handleMessage(Message msg) {
                    // Act on the message
                	// connect to IP/port
                	if (msg.what == CONNECT){
                		//TODO implement
                	}                    	
                	// add dikablis command to command queue----
                	if (msg.what == COMMAND){
                		String command = (String)msg.obj;
                		mCommands.add(command);
                	}
                	// close
                	if (msg.what == CLOSE){
                		//TODO implement
                	
                	}
                }
        };
        Looper.loop();
        */
        
        while(!mEndThread){
        	while(mTryAndTry){
        		
 				try{Thread.sleep(20, 0);//TODO adjust
 				}catch(Exception ex){}
 				
        		
        		  //connect-------------------------------
	    			if ((mSocket == null)  || mError){
		    			try{	
		    				close();
		    				long now = System.currentTimeMillis();
		    				if (now - mLastTry > RETRY_AFTER_MS){//wait before try again
                                mLastTry = now;
		    					connect();
		    				}
		    			}catch(Exception e){
			 				Log.e(TAG, "error while connecting : "+e.getMessage());
			 				try{
		    				//Thread.sleep(RETRY_AFTER_MS, 0);//if connection fails wait before try again
			 				}catch(Exception ex){}
		    			}	
	    			}else{//((mSocket == null)  || mError)
	    				
	    				long now = System.currentTimeMillis();
	    				if (now - mLastBeacon > BEACON_MS){//wait before try again
	    					//sendCommand("#");//send a beacon to the bluetooth device, we are still connected
	    					mLastBeacon = now;
	    				}	    				
		    			//send--------------
	    				/*
		    			try{
		    				synchronized(mCommands){
			    				if ((mOut!=null) && (mCommands.size() > 0)){
				    				String firstCommandInQueue = mCommands.get(0);
				    				byte[] bytes = firstCommandInQueue.getBytes("US-ASCII");
					                mOut.write(bytes);
					                mOut.flush();
		    						Log.d(TAG,Integer.toString(mTxCounter)+" TX: "+firstCommandInQueue);
					                mTxCounter++;
					                mCommands.remove(0);//remove first in queue
			    				} 
		    				}
		    			}catch(Exception e){
			 				Log.e(TAG, "error while send: " +e.getMessage());
			 				mError = true;
		    			}
		    			*/	
		    			//receive--------------
		    			try{
		    				String rxTemp = null;
		    				if ((mIn != null) && (mReader.ready())){
		    					rxTemp = mReader.readLine(); 
		    				}
		    				if (rxTemp != null){
		    					
		    					if (mCallbackHandler != null){//send this command to callback, there it coud be decoded
		    						Message msg = mCallbackHandler.obtainMessage();
		    						msg.what = BT_RX_CALLBACK;
		    						msg.obj = rxTemp;
		    						mCallbackHandler.sendMessage(msg);
		    					}
		    					
		    		        	try{
			    		        	if (rxTemp.startsWith("#")){
			    		        		//TODO you can also decode the commands at this place
			    		        	}
		    		        	}catch(Exception ex){
		    		        		Log.e(TAG, "Failed to: "+ex.getMessage());
		    		        	}
		    					
	    						Log.i(TAG,Integer.toString(mRxCounter)+" RX: "+rxTemp);
		    					mRxCounter++;
		    				}
		    			}catch(Exception e){
			 				Log.e(TAG, "error while receive: "+e.getMessage());
			 				mError = true;
		    			}
	    			}//else	((mSocket == null)  || mError)
	            }
        }//while (mRunClient)
        
        close();
        
    }//while(!mEndThread)
    
    
}









