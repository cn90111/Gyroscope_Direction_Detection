/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.BluetoothChat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * This is the main Activity that displays the current chat session.
 */
public class BluetoothChat extends Activity {
	
	private Sensor gyroSensor;
	boolean gyroPresent;
    private SensorManager mSensorManager;
    
    Button buttonStart;
    RelativeLayout background;
    boolean addGO = false;
 
    TextView textView_State;
    ImageView secondImage;
	
    // Debugging
    private static final String TAG = "BluetoothChat";
    private static final boolean D = true;

    // Message types sent from the BluetoothChatService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;

    // Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_ENABLE_BT = 3;

    // Layout Views
    private ListView mConversationView;
    private EditText mOutEditText;
    private Button mSendButton;

    // Name of the connected device
    private String mConnectedDeviceName = null;
    // Array adapter for the conversation thread
    private ArrayAdapter<String> mConversationArrayAdapter;//�˸m�C��
    // String buffer for outgoing messages
    private StringBuffer mOutStringBuffer;
    // Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;//�ۤv���Ūްt�վ�
    // Member object for the chat services
    private BluetoothChatService mChatService = null;//�Ūު��A�Ⱥ�


    @Override
    public void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);
        if(D) Log.e(TAG, "+++ ON CREATE +++");

        // Set up the window layout
        setContentView(R.layout.main);//��������wLayout�A���Τ���Activity
        
        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null)//�d�ݦ��L�Ūޥ\��
        {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();//��ܧ���
            finish();//�פ�Activity
            return;
        }
        
        mSensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        List<Sensor> sensorList = mSensorManager
				.getSensorList(Sensor.TYPE_GYROSCOPE);
        
        if (sensorList.size() > 0) 
        {
        	gyroSensor = sensorList.get(0);
        	gyroPresent = true;
		} 
		else 
		{
			gyroPresent = false;
		}
    }

    @Override
    public void onStart() 
    {
        super.onStart();
        if(D) 
        {
        	Log.e(TAG, "++ ON START ++");
        }
        
        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) //�d���Ūަ��L���}
        {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);//���ܨϥΪ̥��}�Ū�
        // Otherwise, setup the chat session
        } 
        else 
        {
            if (mChatService == null)//�d�ݦ��L�إߪA�Ⱥݪ����� 
            {
            	setupChat();
            }
        }
    }

    @Override
    public synchronized void onResume() 
    {
        super.onResume();
        if(D) 
        {
        	Log.e(TAG, "+ ON RESUME +");
        }

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (mChatService != null)//�d�ݦ��L�A�Ⱥ� 
        {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mChatService.getState() == BluetoothChatService.STATE_NONE)//getState()�o��Service��e���A
            {
              // Start the Bluetooth chat services
              mChatService.start();//�ҰʪA�Ⱥ�
            }
        }
        
        if (gyroPresent) {
			mSensorManager.registerListener(gyroListener,
					gyroSensor, SensorManager.SENSOR_DELAY_FASTEST);
        }
    }

    private void setupChat() //�۩w�q�Ƶ{��
    {
        Log.d(TAG, "setupChat()");

        // Initialize the array adapter for the conversation thread
        mConversationArrayAdapter = new ArrayAdapter<String>(this, R.layout.message);
        mConversationView = (ListView) findViewById(R.id.in);//�Ť��t��˸m�C��
        mConversationView.setAdapter(mConversationArrayAdapter);//ListView�Ψӳs��Adapter��

        // Initialize the compose field with a listener for the return key
        mOutEditText = (EditText) findViewById(R.id.edit_text_out);
        mOutEditText.setOnEditorActionListener(mWriteListener);//�]�wEdit����ť����A���UEnter��Ĳ�o

        // Initialize the send button with a listener that for click events
        mSendButton = (Button) findViewById(R.id.button_send);
        mSendButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                // Send a message using content of the edit text widget
                TextView view = (TextView) findViewById(R.id.edit_text_out);
                String message = view.getText().toString();//�o��TextView�����e
                if(message.equals(""))
                {
                	sendMessage("OK");
                }
                sendMessage(message);//�۩w�q�Ƶ{���A�ǰe�T��
            }
        });
        
        // Initialize the BluetoothChatService to perform bluetooth connections
        mChatService = new BluetoothChatService(this, mHandler);//this����ۤv��Service�ݥH�s��

        // Initialize the buffer for outgoing messages
        mOutStringBuffer = new StringBuffer("");
        
        buttonStart = (Button)findViewById(R.id.buttonStart);
        buttonStart.setOnClickListener(new OnClickListener() 
        {	
			@Override
			public void onClick(View v) 
			{
				// TODO Auto-generated method stub
				setContentView(R.layout.activity_main);//��������wLayout�A���Τ���Activity
				
				textView_State = (TextView) findViewById(R.id.textView_State);
				secondImage = (ImageView) findViewById(R.id.imageView1);
				
				textView_State.setText("���L�S �жi�J�L�S��");
				secondImage.setImageResource(R.drawable.red_light);

				
				addGO = true;
			}
		});
    }

    @Override
    public synchronized void onPause() 
    {
        super.onPause();
        if(D) Log.e(TAG, "- ON PAUSE -");
    }

    @Override
    public void onStop() 
    {
        super.onStop();
        if(D) Log.e(TAG, "-- ON STOP --");
        
        if (gyroPresent) {
			mSensorManager.unregisterListener(gyroListener);
		}
    }

    private SensorEventListener gyroListener = new SensorEventListener() 
	{
    	float tempX=0.0f,tempY=0.0f,tempZ=0.0f;
    	float dispX,dispY,dispZ;
    	
    	float maxTake = 0.18f;
		float minTake = -0.18f;
		
		float changeColorGate = 1.25f; //�ӷP�׽վ�
		
		int n = 0;
		boolean getBase = false;
		
		@Override
		public void onSensorChanged(SensorEvent event) 
		{
			// TODO Auto-generated method stub
			
			if(addGO)
			{
				dispZ = event.values[2] - tempZ;
					
				if(dispZ < minTake || dispZ > maxTake)
				{

						
					if(dispZ > changeColorGate)
					{
						n++;
						if(n>50)
						{
							
							textView_State.setText("���L�S �жi�J�L�S��");
							secondImage.setImageResource(R.drawable.red_light);
								
						}
					}
					else
					{
						n=0;
					}
				}	
			}
		}

		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) 
		{
			// TODO Auto-generated method stub
			
		}
    	
	};
	
	public void testOK()
	{
		if(addGO)
		{
			textView_State.setText("�w�L�S ���樮���w");
			secondImage.setImageResource(R.drawable.green_light);
		}
	}
	
    @Override
    public void onDestroy() 
    {
        super.onDestroy();
        // Stop the Bluetooth chat services
        if (mChatService != null) 
        {
        	mChatService.stop();
        }
        if(D) 
        {
        	Log.e(TAG, "--- ON DESTROY ---");
        }
    }

    private void ensureDiscoverable() 
    {
        if(D) 
        {
        	Log.d(TAG, "ensure discoverable");
        }
        if (mBluetoothAdapter.getScanMode() !=
            BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) //getScanMode()�����o���y�Ҧ�
        {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);//�}�����O�H�i�H�j����
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);//�[�J�B�~����A�}��j��������300��
            startActivity(discoverableIntent);//����Activity�ña��ƹL�h�A���L�n���^�Ӯɷ|���}�s��Activity
        }
    }

    /**
     * Sends a message.
     * @param message  A string of text to send.
     */
    private void sendMessage(String message) 
    {
        // Check that we're actually connected before trying anything
        if (mChatService.getState() != BluetoothChatService.STATE_CONNECTED) 
        {
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes();
            mChatService.write(send);

            // Reset out string buffer to zero and clear the edit text field
            mOutStringBuffer.setLength(0);
            mOutEditText.setText(mOutStringBuffer);
        }
    }

    // The action listener for the EditText widget, to listen for the return key
    private TextView.OnEditorActionListener mWriteListener =
        new TextView.OnEditorActionListener() 
    {
        public boolean onEditorAction(TextView view, int actionId, KeyEvent event) 
        {
            // If the action is a key-up event on the return key, send the message
            if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_UP) 
            {
                String message = view.getText().toString();
                sendMessage(message);
            }
            if(D) 
            {
            	Log.i(TAG, "END onEditorAction");
            }
            return true;
        }
    };

    private final void setStatus(int resId) {
        final ActionBar actionBar = getActionBar();
        actionBar.setSubtitle(resId);
    }

    private final void setStatus(CharSequence subTitle) {
        final ActionBar actionBar = getActionBar();
        actionBar.setSubtitle(subTitle);
    }

    // The Handler that gets information back from the BluetoothChatService
    private final Handler mHandler = new Handler() 
    {
        @Override
        public void handleMessage(Message msg) 
        {
            switch (msg.what) 
            {
            case MESSAGE_STATE_CHANGE://�T�����A���
                if(D) 
                {
                	Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                }
                switch (msg.arg1)
                {
	                case BluetoothChatService.STATE_CONNECTED://���A���w�s��
	                    setStatus(getString(R.string.title_connected_to, mConnectedDeviceName));
	                    mConversationArrayAdapter.clear();//�M�ųo��Adapter
	                    break;
	                case BluetoothChatService.STATE_CONNECTING://���A���s����
	                    setStatus(R.string.title_connecting);
	                    break;
	                case BluetoothChatService.STATE_LISTEN://���A��ť?
	                case BluetoothChatService.STATE_NONE://���A���L
	                    setStatus(R.string.title_not_connected);
	                    break;
                }
                break;
            case MESSAGE_WRITE://�g�T��
                byte[] writeBuf = (byte[]) msg.obj;
                // construct a string from the buffer
                SimpleDateFormat formatterW = new SimpleDateFormat("mm:ss");
                
                String writeMessage = new String(writeBuf);
                Date curDateW = new Date(System.currentTimeMillis()) ; // �����e�ɶ�
                
                String strW = formatterW.format(curDateW);
                
                mConversationArrayAdapter.add("Me:  " + writeMessage + "\n�o�e��" + strW);
                break;
            case MESSAGE_READ://Ū�T��
                byte[] readBuf = (byte[]) msg.obj;
                // construct a string from the valid bytes in the buffer
                String readMessage = new String(readBuf, 0, msg.arg1);
                Date curDateR = new Date(System.currentTimeMillis()) ; // �����e�ɶ�
                SimpleDateFormat formatterR = new SimpleDateFormat("mm:ss");
                
                String strR = formatterR.format(curDateR);
                
                if(readMessage.equals("OK"))
                {
                	testOK();
                }
                
                mConversationArrayAdapter.add(mConnectedDeviceName+":  " + readMessage + "\n�o�e��" + strR);
                break;
            case MESSAGE_DEVICE_NAME://�˸m�W�ٰT��
                // save the connected device's name
                mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                Toast.makeText(getApplicationContext(), "Connected to "
                               + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                break;
            case MESSAGE_TOAST://��w�T��
                Toast.makeText(getApplicationContext(),msg.getData().getString(TOAST),
                               Toast.LENGTH_SHORT).show();
                break;
            }
        }
    };

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(D) Log.d(TAG, "onActivityResult " + resultCode);
        switch (requestCode) 
        {
        case REQUEST_CONNECT_DEVICE_SECURE://�w�����n�D�s���]��
            // When DeviceListActivity returns with a device to connect
            if (resultCode == Activity.RESULT_OK) {
                connectDevice(data, true);
            }
            break;
        case REQUEST_CONNECT_DEVICE_INSECURE://�����w�����s���]��
            // When DeviceListActivity returns with a device to connect
            if (resultCode == Activity.RESULT_OK) {
                connectDevice(data, false);
            }
            break;
        case REQUEST_ENABLE_BT://�ШD���\�Ť�
            // When the request to enable Bluetooth returns
            if (resultCode == Activity.RESULT_OK) {
                // Bluetooth is now enabled, so set up a chat session
                setupChat();
            } else {
                // User did not enable Bluetooth or an error occurred
                Log.d(TAG, "BT not enabled");
                Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void connectDevice(Intent data, boolean secure)//�s���˸m 
    {
        // Get the device MAC address
        String address = data.getExtras()
            .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        // Get the BluetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);//�o�컷�誺�Ť��]��
        // Attempt to connect to the device
        mChatService.connect(device, secure);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)//����U�����Menu�ɷ|Ĳ�o���ʧ@
    {
        MenuInflater inflater = getMenuInflater();//�����X�{
        inflater.inflate(R.menu.option_menu, menu);//�����X�{
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)//����UMenu�ﶵ�|Ĳ�o���ʧ@ 
    {
        Intent serverIntent = null;
        switch (item.getItemId()) //���o��檺�ﶵ�s��
        {
	        case R.id.secure_connect_scan://�w�����s�����y
	            // Launch the DeviceListActivity to see devices and do scan
	            serverIntent = new Intent(this, DeviceListActivity.class);
	            startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);//����Activity�ña��ƹL�h�A�n���^�Ӯɷ|�^�_�쥻��Activity
	            return true;
	        case R.id.insecure_connect_scan://���w�����s�����y
	            // Launch the DeviceListActivity to see devices and do scan
	            serverIntent = new Intent(this, DeviceListActivity.class);
	            startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_INSECURE);
	            return true;
	        case R.id.discoverable://�i�o�{
	            // Ensure this device is discoverable by others
	            ensureDiscoverable();
	            return true;
        }
        return false;
    }

    
}