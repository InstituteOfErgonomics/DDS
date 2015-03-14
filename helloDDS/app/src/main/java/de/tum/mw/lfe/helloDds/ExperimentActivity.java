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

import java.util.LinkedList;
import java.util.Random;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;


public class ExperimentActivity extends Activity{
	private static final String TAG = "DDS.Activity";	

    private IntentFilter mFilter;
   
	private PowerManager.WakeLock mWakeLock = null;	
	private ExperimentActivity mContext = this;
	private Handler mHandler = new Handler();

    private static BtThread mBtThread = null;



    private LinkedList receiveHistory = new LinkedList();

    private Handler mBtHandler = new Handler() {//we receive the command that the bluetooth thread has received
        public void handleMessage(Message msg) {
            // Act on the message
            if (msg.what == BtThread.BT_RX_CALLBACK){

                String tx = (String)msg.obj;

                Log.i(TAG,">>>>>"+tx);



                //we don't decode the commands in this demo, we put them in a command-history-array
                // and print this to a textview


                receiveHistory.addLast(tx);
                if(receiveHistory.size() > 7){
                    receiveHistory.remove(0);
                }

                StringBuilder temp = new StringBuilder();
                temp.append("");
                for (int i = receiveHistory.size() -1; i >=0 ; i--) {
                    temp.append(receiveHistory.get(i));
                    temp.append('\n');
                }

                TextView tv = (TextView) findViewById(R.id.textView3);
                tv.setText(temp.toString());
            }

        }
    };


    private void stopBtThread(){
	    if (mBtThread != null){
	    	mBtThread.end();
	    	mBtThread = null;
	    } 		 
    }
    
    
    private void kickOffBtThread(){
		if (mBtThread == null){
			Log.d(TAG, "start mBtThread");
			mBtThread = new BtThread(this, mBtHandler);
			mBtThread.start();
	    }
	} 
	   
 
  //receive bluetooth broadcasts
   private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {

        		if(BtThread.checkIfMyDevice(device)){
        			stopBtThread();
        			kickOffBtThread();
        		}
            }
            else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {

        		if(BtThread.checkIfMyDevice(device)){
        			stopBtThread();
        			kickOffBtThread();
        		}           	            	
            }
            else if (BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED.equals(action)) {

        		if(BtThread.checkIfMyDevice(device)){
        			stopBtThread();
        			kickOffBtThread();
        		}
            }
            else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {

        		if(BtThread.checkIfMyDevice(device)){
        			stopBtThread();
        			kickOffBtThread();
        		}
            }           
        }
    };	
	
    
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	    
        //no title
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        //full screen
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,  WindowManager.LayoutParams.FLAG_FULLSCREEN);
        //full light
        //android.provider.Settings.System.putInt(getContentResolver(), android.provider.Settings.System.SCREEN_BRIGHTNESS, 255);
		

	    //setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

		setContentView(R.layout.main);
				
	    getWakeLock();
		
        kickOffBtThread();
        

		mFilter = new IntentFilter();
		mFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
		mFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED);
		mFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
		mFilter.addAction(BluetoothDevice.ACTION_FOUND);
	    this.registerReceiver(mReceiver, mFilter);

	}

	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		this.unregisterReceiver(mReceiver);		
		
		stopBtThread();
		
        if(mWakeLock != null){
         	mWakeLock.release();
        }
		
	}
	
	@Override
	public void onPause() {
        super.onPause();
	    //unregisterReceiver(mReceiver);

	}
	
	
	@Override
	public void onResume() {
        super.onResume();
        
	}
	
   protected void getWakeLock(){
	    try{
			PowerManager powerManger = (PowerManager) getSystemService(Context.POWER_SERVICE);
	        mWakeLock = powerManger.newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP|PowerManager.FULL_WAKE_LOCK, "de.tum.ergonomie.buttons");
	        mWakeLock.acquire();
		}catch(Exception e){
       	Log.e(TAG,"get wakelock failed:"+ e.getMessage());
		}	
   }
	
	private void error(final String msg){//toast and log some errors
		toasting(msg, Toast.LENGTH_LONG);
		Log.e(TAG,msg);
	}
	
	private void toasting(final String msg, final int duration){
		Context context = getApplicationContext();
		CharSequence text = msg;
		Toast toast = Toast.makeText(context, text, duration);
		toast.show();		
	}
		
}