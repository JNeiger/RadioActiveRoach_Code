/*
 * Copyright (C) 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.android.glass.hud;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.PaintDrawable;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.View;
import android.view.SurfaceView;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.VideoView;

/**
 * Receives audio input from the microphone and displays a visualization of that data as a waveform
 * on the screen.
 */
public class WaveformActivity extends Activity {
    
    private TextView mBottomRightView;
    private TextView mBottomLeftView;
    private TextView mMainTextView;
    private VideoView mVideoView;
    private View mBackground;
    private ServerSocket server;

    private ConnectionThread mConnectionThread;

	// Creates all the different text views
	// Starts the
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_hud);

        mBottomRightView = (TextView) findViewById(R.id.bottom_right_text_view);
        mBottomLeftView = (TextView) findViewById(R.id.bottom_left_text_view);
        mMainTextView = (TextView) findViewById(R.id.main_text_view);
        mVideoView = (VideoView) findViewById(R.id.video_stream_view);
        mBackground = (View) findViewById(R.id.background_view);
        
        mBottomRightView.setBackgroundColor(Color.TRANSPARENT);
        mBottomLeftView.setBackgroundColor(Color.TRANSPARENT);
        mMainTextView.setBackgroundColor(Color.TRANSPARENT);
        
        mVideoView.setVisibility(View.INVISIBLE);
        mBottomRightView.post(new Runnable() {
            @Override
            public void run() {
            	mBottomRightView.setText("Initializing");
            }
        });
        
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "Driver Station");
        wl.acquire();
		
		// Failed attempt to get a video stream to the display
        /*
        mVideoView.setVideoPath("rtsp://192.168.1.102:554");
        MediaController mc = new MediaController(this);
        mVideoView.setMediaController(mc);
        mVideoView.setOnPreparedListener(new OnPreparedListener()
        {
			@Override
			public void onPrepared(MediaPlayer arg0) {
				mVideoView.start();
			}
        });
        */
    }

    @Override
    protected void onResume() {
        super.onResume();

        setViewText("Creating Server Thread", mBottomLeftView);
        
        mConnectionThread = new ConnectionThread();
        mConnectionThread.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        
        if (mConnectionThread != null) {
        	mConnectionThread.stopRunning();
        	mConnectionThread = null;
        }
        
        finish();
    }


	// Second thread to communicate directly with the driver station
    private class ConnectionThread extends Thread {
    	
    	private boolean shouldContinue = true;
    	private boolean isBackgroundDefault = true;
    	
    	@Override
    	public void run()
    	{
    		android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_MORE_FAVORABLE);
    		
    		Socket client = null;
    		BufferedReader in = null;
    		
    		setViewText("Creating Server", mBottomRightView);
    		
    		//Continue trying to find the client while waiting
    		while (client == null)
    		{
	    		try {
	    			//Create Server (Tcp: 38300) Along with timeout (in ms)
	    			server = new ServerSocket(38300);
	    			server.setSoTimeout(3000000);
	    			
	    			setViewText("Trying to Connect", mBottomRightView);
	    			
	    			client = server.accept();
	    			
	    			in = new BufferedReader(new InputStreamReader(client.getInputStream()));
	    			
	    		} catch (Exception e)
	    		{
	    			e.printStackTrace();
	    		} finally {
	    			//Make sure you are connected
	    			try {
	    				if (server != null)
	    					server.close();
	    				
	    				setViewText("Checking Connection", mBottomRightView);
	    				
	    			} catch (Exception e)
	    			{
	    				e.printStackTrace();
	    			}
	    		}
	    		
	    		//Connnected
	    		if (client != null)
	    		{
	    			//Start the display loop
	    			communicationLoop(in, client);
	    			SystemClock.sleep(100);
	    			//Once the shutdown signal is giving; Shutdown
	    			try {
						client.close();
						server.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
	    			finish();
	    		}else
	    		{
	    			setViewText("Client Not Found", mBottomRightView);
	    		}
    		}
    	}
    	
    	private void communicationLoop(BufferedReader in, Socket client)
    	{
    		setViewText("Connected", mBottomRightView);
    		long timeSinceLastMessage = 0l;
    		//Continue reading from the client
    		while (shouldContinue())
			{
				final String input;
				try {
					if (in.ready())
					{
						input = in.readLine();
						System.out.println("Client -- " + input);
						
						interpretString(input);
						
						timeSinceLastMessage = 0l;
						
						setViewText(input, mBottomLeftView);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
				
				//Shutdown if connection lags out
				if (timeSinceLastMessage > 50)
				{
					setViewText("Shutting Down", mMainTextView);
					stopRunning();
				}
				timeSinceLastMessage++;
				
            	SystemClock.sleep(200);
			}
    	}
    	
    	private void interpretString(String s)
    	{    		
    		try {
				// Data was sent in the format
				//		[2 digit numeric header][Data]
			
    			boolean resetBackground = true;
    			int header = Integer.parseInt(s.substring(0,2));
    			String data = s.substring(2);
	    		//Check to see what the data contains
	    		switch (header)
	    		{
	    			case 0://Text to display
	    				setViewText(data, mMainTextView);
	    				break;
	    			case 1://Ready to fire ETC
	    				if (data.toLowerCase().contains("ready")) {
    						System.out.println("Setting to green");
    						if (isBackgroundDefault)						
    							setViewBackgroundColor(Color.GREEN, mBackground);
    						
	    					isBackgroundDefault = false;
	    					resetBackground = false;
	    				}
						
	    				data.replace("Ready", "");
	    				setViewText(data, mMainTextView);
	    				break;
	    			case 2://Distance until able to shoot
	    				setViewText(data, mMainTextView);
	    				break;
	    			case 3://Pressure of low side
	    				setViewText(data, mMainTextView);
	    				break;
	    			case 4://Ball feed distance
	    				setViewText(data, mMainTextView);
	    				break;
	    			case 99://Shutdown
	    				setViewText("Shutting Down", mMainTextView);
	    				stopRunning();
	    				break;
	    			default:
	    				setViewText("Default", mMainTextView);
	    				break;
	    		}
	    		
	    		//Reset background to original only if it is green
	    		if (resetBackground &&  !isBackgroundDefault){
	    			System.out.println("Reseting Background");
	    			setViewBackgroundColor(Color.BLACK, mBackground);
	    			isBackgroundDefault = true;
	    		}
	    		
    		}
    		catch (Exception e)
    		{
    			setViewText("Error, Message is not valid", mMainTextView);
    			System.out.println(e.getMessage());
    			return;
    		}
    	}
    	

    	private synchronized boolean shouldContinue() {
            return shouldContinue;
        }

        public synchronized void stopRunning() {
            shouldContinue = false;
        }
    }
	
    public void setViewText(final String text, final TextView view)
	{
		view.post(new Runnable() {
			@Override
			public void run() {
				view.setText(text);
			}
		});
	}
	
    public void setViewBackgroundColor(final int color, final View view)
    {
    	runOnUiThread(new Runnable() {
    		@Override
    		public void run() {
    			view.setBackgroundColor(color);
    		}
    	});
    }
}
