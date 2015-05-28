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
    private ArrayAdapter<String> mConversationArrayAdapter;//裝置列表
    // String buffer for outgoing messages
    private StringBuffer mOutStringBuffer;
    // Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;//自己的藍芽配試器
    // Member object for the chat services
    private BluetoothChatService mChatService = null;//藍芽的服務端


    @Override
    public void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);
        if(D) Log.e(TAG, "+++ ON CREATE +++");

        // Set up the window layout
        setContentView(R.layout.main);//切換到指定Layout，不用切換Activity
        
        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null)//查看有無藍芽功能
        {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();//顯示快顯
            finish();//終止Activity
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
        if (!mBluetoothAdapter.isEnabled()) //查看藍芽有無打開
        {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);//提示使用者打開藍芽
        // Otherwise, setup the chat session
        } 
        else 
        {
            if (mChatService == null)//查看有無建立服務端的物件 
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
        if (mChatService != null)//查看有無服務端 
        {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mChatService.getState() == BluetoothChatService.STATE_NONE)//getState()得到Service當前狀態
            {
              // Start the Bluetooth chat services
              mChatService.start();//啟動服務端
            }
        }
        
        if (gyroPresent) {
			mSensorManager.registerListener(gyroListener,
					gyroSensor, SensorManager.SENSOR_DELAY_FASTEST);
        }
    }

    private void setupChat() //自定義副程式
    {
        Log.d(TAG, "setupChat()");

        // Initialize the array adapter for the conversation thread
        mConversationArrayAdapter = new ArrayAdapter<String>(this, R.layout.message);
        mConversationView = (ListView) findViewById(R.id.in);//藍牙配對裝置列表
        mConversationView.setAdapter(mConversationArrayAdapter);//ListView用來連接Adapter的

        // Initialize the compose field with a listener for the return key
        mOutEditText = (EditText) findViewById(R.id.edit_text_out);
        mOutEditText.setOnEditorActionListener(mWriteListener);//設定Edit的監聽物件，按下Enter時觸發

        // Initialize the send button with a listener that for click events
        mSendButton = (Button) findViewById(R.id.button_send);
        mSendButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                // Send a message using content of the edit text widget
                TextView view = (TextView) findViewById(R.id.edit_text_out);
                String message = view.getText().toString();//得到TextView的內容
                if(message.equals(""))
                {
                	sendMessage("OK");
                }
                sendMessage(message);//自定義副程式，傳送訊息
            }
        });
        
        // Initialize the BluetoothChatService to perform bluetooth connections
        mChatService = new BluetoothChatService(this, mHandler);//this為把自己給Service端以連接

        // Initialize the buffer for outgoing messages
        mOutStringBuffer = new StringBuffer("");
        
        buttonStart = (Button)findViewById(R.id.buttonStart);
        buttonStart.setOnClickListener(new OnClickListener() 
        {	
			@Override
			public void onClick(View v) 
			{
				// TODO Auto-generated method stub
				setContentView(R.layout.activity_main);//切換到指定Layout，不用切換Activity
				
				textView_State = (TextView) findViewById(R.id.textView_State);
				secondImage = (ImageView) findViewById(R.id.imageView1);
				
				textView_State.setText("未過磅 請進入過磅站");
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
		
		float changeColorGate = 1.25f; //敏感度調整
		
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
							
							textView_State.setText("未過磅 請進入過磅站");
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
			textView_State.setText("已過磅 祝行車平安");
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
            BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) //getScanMode()為取得掃描模式
        {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);//開放讓別人可以搜索到
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);//加入額外限制，開放搜索期限為300秒
            startActivity(discoverableIntent);//跳轉Activity並帶資料過去，不過要跳回來時會重開新的Activity
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
            case MESSAGE_STATE_CHANGE://訊息狀態更改
                if(D) 
                {
                	Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                }
                switch (msg.arg1)
                {
	                case BluetoothChatService.STATE_CONNECTED://狀態為已連接
	                    setStatus(getString(R.string.title_connected_to, mConnectedDeviceName));
	                    mConversationArrayAdapter.clear();//清空這個Adapter
	                    break;
	                case BluetoothChatService.STATE_CONNECTING://狀態為連接中
	                    setStatus(R.string.title_connecting);
	                    break;
	                case BluetoothChatService.STATE_LISTEN://狀態為聽?
	                case BluetoothChatService.STATE_NONE://狀態為無
	                    setStatus(R.string.title_not_connected);
	                    break;
                }
                break;
            case MESSAGE_WRITE://寫訊息
                byte[] writeBuf = (byte[]) msg.obj;
                // construct a string from the buffer
                SimpleDateFormat formatterW = new SimpleDateFormat("mm:ss");
                
                String writeMessage = new String(writeBuf);
                Date curDateW = new Date(System.currentTimeMillis()) ; // 獲取當前時間
                
                String strW = formatterW.format(curDateW);
                
                mConversationArrayAdapter.add("Me:  " + writeMessage + "\n發送於" + strW);
                break;
            case MESSAGE_READ://讀訊息
                byte[] readBuf = (byte[]) msg.obj;
                // construct a string from the valid bytes in the buffer
                String readMessage = new String(readBuf, 0, msg.arg1);
                Date curDateR = new Date(System.currentTimeMillis()) ; // 獲取當前時間
                SimpleDateFormat formatterR = new SimpleDateFormat("mm:ss");
                
                String strR = formatterR.format(curDateR);
                
                if(readMessage.equals("OK"))
                {
                	testOK();
                }
                
                mConversationArrayAdapter.add(mConnectedDeviceName+":  " + readMessage + "\n發送於" + strR);
                break;
            case MESSAGE_DEVICE_NAME://裝置名稱訊息
                // save the connected device's name
                mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                Toast.makeText(getApplicationContext(), "Connected to "
                               + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                break;
            case MESSAGE_TOAST://氣泡訊息
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
        case REQUEST_CONNECT_DEVICE_SECURE://安全的要求連接設備
            // When DeviceListActivity returns with a device to connect
            if (resultCode == Activity.RESULT_OK) {
                connectDevice(data, true);
            }
            break;
        case REQUEST_CONNECT_DEVICE_INSECURE://較不安全的連接設備
            // When DeviceListActivity returns with a device to connect
            if (resultCode == Activity.RESULT_OK) {
                connectDevice(data, false);
            }
            break;
        case REQUEST_ENABLE_BT://請求允許藍牙
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

    private void connectDevice(Intent data, boolean secure)//連結裝置 
    {
        // Get the device MAC address
        String address = data.getExtras()
            .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        // Get the BluetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);//得到遠方的藍牙設備
        // Attempt to connect to the device
        mChatService.connect(device, secure);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)//當按下手機的Menu時會觸發的動作
    {
        MenuInflater inflater = getMenuInflater();//讓選單出現
        inflater.inflate(R.menu.option_menu, menu);//讓選單出現
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)//當按下Menu選項會觸發的動作 
    {
        Intent serverIntent = null;
        switch (item.getItemId()) //取得選單的選項編號
        {
	        case R.id.secure_connect_scan://安全的連接掃描
	            // Launch the DeviceListActivity to see devices and do scan
	            serverIntent = new Intent(this, DeviceListActivity.class);
	            startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);//跳轉Activity並帶資料過去，要跳回來時會回復原本的Activity
	            return true;
	        case R.id.insecure_connect_scan://不安全的連接掃描
	            // Launch the DeviceListActivity to see devices and do scan
	            serverIntent = new Intent(this, DeviceListActivity.class);
	            startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_INSECURE);
	            return true;
	        case R.id.discoverable://可發現
	            // Ensure this device is discoverable by others
	            ensureDiscoverable();
	            return true;
        }
        return false;
    }

    
}