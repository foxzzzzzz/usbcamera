package com.serenegiant.usbcameratest6;
/*
 * UVCCamera
 * library and sample to access to UVC web camera on non-rooted Android device
 *
 * Copyright (c) 2014-2015 saki t_saki@serenegiant.com
 *
 * File name: MainActivity.java
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 * All files in the folder are under this Apache License, Version 2.0.
 * Files in the jni/libjpeg, jni/libusb and jin/libuvc folder may have a different license, see the respective files.
*/

import java.io.File;

import android.app.Activity;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.media.AudioManager;
import android.media.MediaScannerConnection;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;

import com.serenegiant.encoder.MediaMuxerWrapper;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.USBMonitor.OnDeviceConnectListener;
import com.serenegiant.usb.USBMonitor.UsbControlBlock;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.usb.UVCCameraHandler;
import com.serenegiant.widget.UVCCameraTextureView;

public final class MainActivity extends Activity {
	private static final boolean DEBUG = true;	// TODO set false on release
	private static final String TAG = "MainActivity";

	/**
	 * for accessing USB
	 */
	private USBMonitor mUSBMonitor;
	/**
	 * Handler to execute camera releated methods sequentially on private thread
	 */
	private UVCCameraHandler mHandler;
	/**
	 * for camera preview display
	 */
	private UVCCameraTextureView mUVCCameraViewL;
	private UVCCameraTextureView mUVCCameraViewR;
	/**
	 * for open&start / stop&close camera preview
	 */
	private ToggleButton mCameraButton;
	
	private CheckBox mSoundOnCheckBox;
	private CheckBox mSensitivityCheckBox;
	private CheckBox mSpeedCheckBox;
	
	private SeekBar mVolumeSeekBar;
	private SeekBar mSpeedSeekBar;
	private SeekBar mSensitivitySeekBar;
	
	private TextView mTextViewVolumeValue;
	private TextView mTextViewSensitivityValue;
	private TextView mTextViewSpeedValue;
	
	private LocationManager mLocationManager;
	private GPSListener mGpsListener;
	private Location mGpsBeforeLocation;
	private long mGpsStartTime = -1;
	
	private AudioManager mAudioManager ;
    private int mVolumeMax;
    private int mCurrentVolume;
    private int mWarningSpeedLimit;
    
	private SoundPool mSoundPool;
	private int mSound_Beep;  
	
	/**
	 * button for start/stop recording
	 */
	private ImageButton mCaptureButton;

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (DEBUG) Log.v(TAG, "onCreate:");
		setContentView(R.layout.activity_main);
		mCameraButton = (ToggleButton)findViewById(R.id.camera_button);
		mCameraButton.setOnClickListener(mOnClickListener);
		mCaptureButton = (ImageButton)findViewById(R.id.capture_button);
		mCaptureButton.setOnClickListener(mOnClickListener);
		mCaptureButton.setVisibility(View.INVISIBLE);
		
		mUVCCameraViewL = (UVCCameraTextureView)findViewById(R.id.camera_view_L);
		mUVCCameraViewL.setAspectRatio(UVCCamera.DEFAULT_PREVIEW_WIDTH / (float)UVCCamera.DEFAULT_PREVIEW_HEIGHT);
		mUVCCameraViewL.setSurfaceTextureListener(mSurfaceTextureListener);
		mUVCCameraViewL.setOnLongClickListener(mOnLongClickListener);
		
		mUSBMonitor = new USBMonitor(this, mOnDeviceConnectListener);
		mHandler = UVCCameraHandler.createHandler(this);
		
				
		UI_Volume_Init();
		UI_Speed_Init();
		UI_Sensitivity_Init();
		StartLocationService() ;
		SoundPool_Init();
	}

	private void UI_Volume_Init()
	{
		mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
		mVolumeMax = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
		mCurrentVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
		
		mVolumeSeekBar = (SeekBar)findViewById(R.id.seekBar_Volume);	
		mVolumeSeekBar.setMax(mVolumeMax);
		mVolumeSeekBar.setProgress(mCurrentVolume);
		mVolumeSeekBar.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		
		mTextViewVolumeValue = (TextView)findViewById(R.id.textView_VolumeValue);	
		mTextViewVolumeValue.setText(String.valueOf(mVolumeSeekBar.getProgress()));		
	
		mSoundOnCheckBox = (CheckBox)findViewById(R.id.checkBox_Sound);
		mSoundOnCheckBox.setChecked(true);
		mSoundOnCheckBox.setOnCheckedChangeListener(mOnCheckBoxCheckedListener);		
	}
	
	private void UI_Speed_Init()
	{
		mWarningSpeedLimit = 50;
		
		mSpeedSeekBar = (SeekBar)findViewById(R.id.seekBar_Speed);		
		mSpeedSeekBar.setMax(200);		
		mSpeedSeekBar.setProgress(mWarningSpeedLimit);		
		mSpeedSeekBar.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		
		mTextViewSpeedValue = (TextView)findViewById(R.id.textView_SpeedValue);		
		mTextViewSpeedValue.setText(String.valueOf(mSpeedSeekBar.getProgress()));	
		
		mSpeedCheckBox = (CheckBox)findViewById(R.id.checkBox_Speed);
		mSpeedCheckBox.setChecked(true);
		mSpeedCheckBox.setOnCheckedChangeListener(mOnCheckBoxCheckedListener);	
		
	}
	
	private void UI_Sensitivity_Init()
	{
		mSensitivitySeekBar = (SeekBar)findViewById(R.id.seekBar_Sensitivity);		
		mSensitivitySeekBar.setMax(9);
		mSensitivitySeekBar.setProgress(5);
		mSensitivitySeekBar.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		
		mTextViewSensitivityValue = (TextView)findViewById(R.id.textView_SensitivityValue);
		mTextViewSensitivityValue.setText(String.valueOf(mSensitivitySeekBar.getProgress()));
		
		mSensitivityCheckBox = (CheckBox)findViewById(R.id.checkBox_Sensitivity);
		mSensitivityCheckBox.setChecked(true);
		mSensitivityCheckBox.setOnCheckedChangeListener(mOnCheckBoxCheckedListener);
	
	}


	private void SoundPool_Init()
	{
		mSoundPool = new SoundPool(1, AudioManager.STREAM_MUSIC, 0 );
		mSound_Beep = mSoundPool.load(this, R.raw.censor_beep, 1);  
	}
	
	private void playSound(){
		mSoundPool.play( mSound_Beep, 0.1f, 0.1f, 0, 0, 1);  
	}
	
	@Override
	public void onResume() {
		super.onResume();
		if (DEBUG) Log.v(TAG, "onResume:");
		mUSBMonitor.register();
	}

	@Override
	public void onPause() {
		if (DEBUG) Log.v(TAG, "onPause:");
//		mHandler.stopRecording();
//		mHandler.stopPreview();
    	mHandler.close();	// #close include #stopRecording and #stopPreview
		mCameraButton.setChecked(false);
		mCaptureButton.setVisibility(View.INVISIBLE);
		mUSBMonitor.unregister();
		super.onPause();
	}

	@Override
	public void onDestroy() {
		if (DEBUG) Log.v(TAG, "onDestroy:");
        if (mHandler != null) {
	        mHandler.release();
	        mHandler = null;
        }
        if (mUSBMonitor != null) {
	        mUSBMonitor.destroy();
	        mUSBMonitor = null;
        }
        mUVCCameraViewL = null;
        mUVCCameraViewR = null;
        mCameraButton = null;
        mCaptureButton = null;
		super.onDestroy();
	}

	private final SeekBar.OnSeekBarChangeListener mOnSeekBarChangeListener = new OnSeekBarChangeListener() {
		
		@Override
		public void onStopTrackingTouch(SeekBar seekBar) {
			// TODO Auto-generated method stub
			
		}
		
		@Override
		public void onStartTrackingTouch(SeekBar seekBar) {
			// TODO Auto-generated method stub
			
		}
		
		@Override
		public void onProgressChanged(SeekBar seekBar, int progress,boolean fromUser) {
			switch(seekBar.getId())
			{
			case R.id.seekBar_Volume:
				mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
                        progress, 0);
				mTextViewVolumeValue.setText(String.valueOf(progress));
				break;
			case R.id.seekBar_Sensitivity:
				mTextViewSensitivityValue.setText(String.valueOf(progress));
				break;
			
			case R.id.seekBar_Speed:
				mWarningSpeedLimit = progress ;
				mTextViewSpeedValue.setText(String.valueOf(mWarningSpeedLimit));
				seekBar.setProgress(mWarningSpeedLimit);
				break;
			
			}
			// TODO Auto-generated method stub
			
		}
	};
	
	private final CompoundButton.OnCheckedChangeListener mOnCheckBoxCheckedListener = new CompoundButton.OnCheckedChangeListener() {
		
		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

			switch ( buttonView.getId()) {
			
			case R.id.checkBox_Sound:
				if( isChecked == true )
				{
					mVolumeSeekBar.setEnabled(true);
				}
				else
				{
					mVolumeSeekBar.setEnabled(false);
				}
				break;
			case R.id.checkBox_Sensitivity:
				if( isChecked == true )
				{
					mSensitivitySeekBar.setEnabled(true);
				}
				else
				{
					mSensitivitySeekBar.setEnabled(false);
				}
				break;
			case R.id.checkBox_Speed:
				if( isChecked == true )
				{
					mSpeedSeekBar.setEnabled(true);
				}
				else
				{
					mSpeedSeekBar.setEnabled(false);
				}
				break;				
			}
			// TODO Auto-generated method stub
			
		}
	};
	/**
	 * event handler when click camera / capture button
	 */
	private final OnClickListener mOnClickListener = new OnClickListener() {
		@Override
		public void onClick(final View view) {
			switch (view.getId()) {
			case R.id.camera_button:
				if (!mHandler.isOpened()) {
					CameraDialog.showDialog(MainActivity.this);
				} else {
					mHandler.close();
					mCaptureButton.setVisibility(View.INVISIBLE);
				}
				break;
			case R.id.capture_button:
				if (mHandler.isOpened()) {
					if (!mHandler.isRecording()) {
						mCaptureButton.setColorFilter(0xffff0000);	// turn red
						mHandler.startRecording();
					} else {
						mCaptureButton.setColorFilter(0);	// return to default color
						mHandler.stopRecording();
					}
				}
				break;
			}
		}
	};

	/**
	 * capture still image when you long click on preview image(not on buttons)
	 */
	private final OnLongClickListener mOnLongClickListener = new OnLongClickListener() {
		@Override
		public boolean onLongClick(final View view) {
			switch (view.getId()) {
			case R.id.camera_view_L:
//			case R.id.camera_view_R:
				if (mHandler.isOpened()) {
					final File outputFile = MediaMuxerWrapper.getCaptureFile(Environment.DIRECTORY_DCIM, ".png");
					mHandler.captureStill(outputFile.toString());
					try {
						if (DEBUG) Log.i(TAG, "MediaScannerConnection#scanFile");
						MediaScannerConnection.scanFile(getApplicationContext(), new String[]{ outputFile.toString() }, null, null);
					} catch (final Exception e) {
						Log.e(TAG, "MediaScannerConnection#scanFile:", e);
					}
					return true;
				}
			}
			return false;
		}
	};

	private void startPreview() {
		mHandler.startPreview();
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				mCaptureButton.setVisibility(View.VISIBLE);
			}
		});
	}

	private final OnDeviceConnectListener mOnDeviceConnectListener = new OnDeviceConnectListener() {
		@Override
		public void onAttach(final UsbDevice device) {
			Toast.makeText(MainActivity.this, "USB_DEVICE_ATTACHED", Toast.LENGTH_SHORT).show();
		}

		@Override
		public void onConnect(final UsbDevice device, final UsbControlBlock ctrlBlock, final boolean createNew) {
			if (DEBUG) Log.v(TAG, "onConnect:");
			mHandler.open(ctrlBlock);
			startPreview();
		}

		@Override
		public void onDisconnect(final UsbDevice device, final UsbControlBlock ctrlBlock) {
			if (DEBUG) Log.v(TAG, "onDisconnect:");
			if (mHandler != null) {
				mHandler.close();
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						if (!isFinishing())
						try {
							mCaptureButton.setVisibility(View.INVISIBLE);
							mCameraButton.setChecked(false);
						} catch (final Exception e) {
						}
					}
				});
			}
		}
		@Override
		public void onDettach(final UsbDevice device) {
			Toast.makeText(MainActivity.this, "USB_DEVICE_DETACHED", Toast.LENGTH_SHORT).show();
		}

		@Override
		public void onCancel() {
		}
	};

	/**
	 * to access from CameraDialog
	 * @return
	 */
	public USBMonitor getUSBMonitor() {
		return mUSBMonitor;
	}

	private final TextureView.SurfaceTextureListener mSurfaceTextureListener
		= new TextureView.SurfaceTextureListener() {

			@Override
			public void onSurfaceTextureAvailable(final SurfaceTexture surface, final int width, final int height) {
				final Surface _surface = new Surface(surface);
				mHandler.addSurface(surface.hashCode(), _surface, false, null);
			}

			@Override
			public void onSurfaceTextureSizeChanged(final SurfaceTexture surface, final int width, final int height) {
			}

			@Override
			public boolean onSurfaceTextureDestroyed(final SurfaceTexture surface) {
				mHandler.removeSurface(surface.hashCode());
				return true;
			}

			@Override
			public void onSurfaceTextureUpdated(final SurfaceTexture surface) {
				// TODO Auto-generated method stub

			}

	};
	
	private void StartLocationService() {
		mLocationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
		mGpsListener = new GPSListener();
		long minTime = 0;
		float minDistance = 0;
		mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, minTime, minDistance, mGpsListener);
		Toast.makeText(getApplicationContext(),"gps has been started",Toast.LENGTH_SHORT).show();		
	}
	
	private class GPSListener implements LocationListener {
		@Override
		public void onLocationChanged(Location location) {
			float cur_speed = 0;
			if (mGpsStartTime == -1)
			{
				mGpsBeforeLocation = location;
				mGpsStartTime = location.getTime();
			}
			
			
			float distance[] = new float[1];
			double curlatitude = location.getLatitude();
			double curlongitude = location.getLongitude();
			double belatitude = mGpsBeforeLocation.getLatitude();
			double belongitude = mGpsBeforeLocation.getLongitude();
						
			
			Location.distanceBetween(belatitude, belongitude, curlatitude, curlongitude, distance);
			long delay = location.getTime() - mGpsStartTime;
			double speed = distance[0]/delay;
			double speedKMH = speed * 3600;
			
			Double latitude = location.getLatitude();
			Double longitude = location.getLongitude();
			String msg = "Latitude :" + latitude + "\nLongitude: "+ longitude + "Speed:" + 
					Float.toString(cur_speed) +"hasspeed:" + location.hasSpeed() + "calspeed:" + speed + "curkmhspeed:" + speedKMH;
			
			
			if( speedKMH > mWarningSpeedLimit)
			{
				playSound();
			}
			
		    //do something
			Log.i("GPSListener",msg);
			Toast.makeText(getApplicationContext(),msg,Toast.LENGTH_SHORT).show();
			mGpsBeforeLocation = location;
		}


		@Override
		public void onProviderDisabled(String provider) {

		    Log.i("===========================", "==============================");
		    Log.i("onProviderDisabled", "==============================");
		    Log.i("===========================", "==============================");
		}
		@Override
		public void onProviderEnabled(String provider) {
		    Log.i("===========================", "==============================");
		    Log.i("onProviderEnabled", "==============================");
		    Log.i("===========================", "==============================");

		}
		@Override
		public void onStatusChanged(String provider, int status, Bundle extras) {
		    // TODO Auto-generated method stub

		}
	}
}
