package org.dobots.swarmcontrol.robots.roboscooper;

import org.dobots.robots.BrainlinkDevice;
import org.dobots.robots.BrainlinkDevice.BrainlinkSensors;
import org.dobots.robots.roboscooper.RoboScooper;
import org.dobots.robots.roboscooper.RoboScooperTypes;
import org.dobots.swarmcontrol.BaseActivity;
import org.dobots.swarmcontrol.ConnectListener;
import org.dobots.swarmcontrol.R;
import org.dobots.swarmcontrol.RemoteControlHelper;
import org.dobots.swarmcontrol.RemoteControlHelper.Move;
import org.dobots.swarmcontrol.RemoteControlListener;
import org.dobots.swarmcontrol.RobotInventory;
import org.dobots.swarmcontrol.robots.BluetoothRobot;
import org.dobots.swarmcontrol.robots.RobotType;
import org.dobots.swarmcontrol.robots.nxt.BTConnectable;
import org.dobots.utility.Utils;

import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.os.Message;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.LinearLayout;
import android.widget.Toast;

public class RoboScooperRobot extends BluetoothRobot implements BTConnectable, RemoteControlListener {
	
	private static String TAG = "RoboScooper";
	
	private static final int ACCEL_ID = CONNECT_ID + 1;
	private static final int ADVANCED_CONTROL_ID = ACCEL_ID + 1;
	
	private static final int REMOTE_CTRL_GRP = GENERAL_GRP + 1;

	private boolean connected;
	
	private RoboScooper m_oRoboScooper;

	private BrainlinkSensorGatherer m_oSensorGatherer;

	private RemoteControlHelper m_oRemoteCtrl;

	private Button m_btnTalk;
	private Button m_btnWhack;
	private Button m_btnVision;
	private Button m_btnStop;
	private Button m_btnAutonomous;
	private Button m_btnPickup;
	private Button m_btnDump;
	
	private CheckBox m_cbAccelerometer;
	private CheckBox m_cbLight;
	private CheckBox m_cbBattery;
	
	private LinearLayout m_layControls1;
	private LinearLayout m_layControls2;
	
	public RoboScooperRobot(BaseActivity m_oOwner) {
		super(m_oOwner);
	}
	
	public RoboScooperRobot() {
		super();
	}

    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);

    	int nIndex = (Integer) getIntent().getExtras().get("InventoryIndex");
    	if (nIndex == -1) {
    		m_oRoboScooper = new RoboScooper();
	        connectToRobot();
    	} else {
    		m_oRoboScooper = (RoboScooper) RobotInventory.getInstance().getRobot(nIndex);
    		m_bKeepAlive = true;
    	}
    	m_oRoboScooper.setHandler(m_oUiHandler);
		
		m_oSensorGatherer = new BrainlinkSensorGatherer(this, m_oRoboScooper);

		m_oRemoteCtrl = new RemoteControlHelper(m_oActivity, m_oRoboScooper, this);
        m_oRemoteCtrl.setProperties();
		
        updateButtons(false);

        if (m_oRoboScooper.isConnected()) {
			updateButtons(true);
		}
        
        if (!BrainlinkDevice.checkForConfigFile(getResources(), RoboScooperTypes.SIGNAL_FILE_NAME, RoboScooperTypes.SIGNAL_FILE_ENCODED)) {
        	Utils.showToast("Failed to install device config file", Toast.LENGTH_LONG);
        }
    }

	@Override
	protected void setProperties(RobotType i_eRobot) {
        m_oActivity.setContentView(R.layout.roboscooper_main);
        
        m_layControls1 = (LinearLayout) findViewById(R.id.layControls1);
        m_layControls2 = (LinearLayout) findViewById(R.id.layControls2);
     
    	m_btnTalk = (Button) findViewById(R.id.btnTalk);
    	m_btnTalk.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				m_oRoboScooper.setTalkMode();
			}
		});
    	
    	m_btnWhack = (Button) findViewById(R.id.btnWhack);
    	m_btnWhack.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				m_oRoboScooper.setWhackMode();
			}
		});
    	
    	m_btnVision = (Button) findViewById(R.id.btnVision);
    	m_btnVision.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				m_oRoboScooper.setVisionMode();
			}
		});
    	
    	m_btnStop = (Button) findViewById(R.id.btnStop);
    	m_btnStop.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				m_oRoboScooper.stop();
			}
		});
    	
    	m_btnAutonomous = (Button) findViewById(R.id.btnAutonomous);
    	m_btnAutonomous.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				m_oRoboScooper.setAutonomous();
			}
		});
    	
    	m_btnPickup = (Button) findViewById(R.id.btnPickUp);
    	m_btnPickup.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				m_oRoboScooper.pickUp();
			}
		});
    	
    	m_btnDump = (Button) findViewById(R.id.btnDump);
    	m_btnDump.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				m_oRoboScooper.dump();
			}
		});
    	
    	m_cbAccelerometer = (CheckBox) findViewById(R.id.cbAccelerometer);
    	m_cbAccelerometer.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				m_oSensorGatherer.enableSensor(BrainlinkSensors.ACCELEROMETER, isChecked);
			}
    	});
    	
    	m_cbLight = (CheckBox) findViewById(R.id.cbLight);
    	m_cbLight.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				m_oSensorGatherer.enableSensor(BrainlinkSensors.LIGHT, isChecked);
			}
    	});
    	
    	m_cbBattery = (CheckBox) findViewById(R.id.cbBattery);
    	m_cbBattery.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				m_oSensorGatherer.enableSensor(BrainlinkSensors.BATTERY, isChecked);
			}
    	});
    	
	}
	
	protected void handleUIMessage(Message msg) {
		switch (msg.what) {
		case RoboScooperTypes.INITIALISATION_FAILED:
			showToast("Brainlink initialisation failed, make sure that the signal definition file was copied to ...", Toast.LENGTH_LONG);
			updateButtons(false);
			break;
		}
		
		super.handleUIMessage(msg);
	}

	public void resetLayout() {
		m_oRemoteCtrl.resetLayout();
		
		m_cbAccelerometer.setChecked(false);
		m_cbBattery.setChecked(false);
		m_cbLight.setChecked(false);

		Utils.showLayout(m_layControls1, false);
		Utils.showLayout(m_layControls2, false);

		m_oSensorGatherer.initialize();
	}
        
        
    @Override
    public void onDestroy() {
    	super.onDestroy();

    	shutDown();
    }
    
    protected void shutDown() {

    	m_oSensorGatherer.stopThread();
    	
    	if (m_oRoboScooper.isConnected() && !m_bKeepAlive) {
    		m_oRoboScooper.destroy();
    	}
    }

    @Override
    public void onStop() {
    	super.onStop();
    	
//    	m_oSensorGatherer.pauseThread();
    	resetLayout();
    	
    	if (m_oRoboScooper.isConnected() && !m_bKeepAlive) {
    		m_oRoboScooper.disconnect();
    	}
    }

    @Override
    public void onPause() {
    	super.onPause();

//    	m_bAccelerometer = false;
    }

    @Override
    public void onRestart() {
    	super.onRestart();
    	
    	if (m_strMacAddress != "" && !m_bKeepAlive) {
    		connectToRobot(m_oBTHelper.getRemoteDevice(m_strMacAddress));
    	}

//    	m_oSensorGatherer.resumeThread();
    }

    @Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		menu.add(REMOTE_CTRL_GRP, ACCEL_ID, 4, "Accelerometer");
		menu.add(REMOTE_CTRL_GRP, ADVANCED_CONTROL_ID, 5, "Advanced Control");
		
		return true;
    }
		
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
    	menu.setGroupVisible(REMOTE_CTRL_GRP, m_oRemoteCtrl.isControlEnabled());
    	
    	Utils.updateOnOffMenuItem(menu.findItem(ACCEL_ID), m_bAccelerometer);
    	Utils.updateOnOffMenuItem(menu.findItem(ADVANCED_CONTROL_ID), m_oRemoteCtrl.isAdvancedControl());
    	
		return true;
    }

	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		switch (item.getItemId()) {
		case ACCEL_ID:
			m_bAccelerometer = !m_bAccelerometer;

			if (m_bAccelerometer) {
				m_bSetAccelerometerBase = true;
			} else {
				m_oRoboScooper.moveStop();
			}
		case ADVANCED_CONTROL_ID:
			m_oRemoteCtrl.toggleAdvancedControl();
			break;
		}

		return super.onMenuItemSelected(featureId, item);
	}

	@Override
	protected void disconnect() {
		if (m_oRoboScooper.isConnected()) {
			m_oRoboScooper.disconnect();
		}
	}

	@Override
	public void connectToRobot(BluetoothDevice i_oDevice) {
		if (m_oBTHelper.initBluetooth()) {
			m_strMacAddress = i_oDevice.getAddress();
			showConnectingDialog();
			
			if (m_oRoboScooper.isConnected()) {
				m_oRoboScooper.disconnect();
			}

			m_oRoboScooper.setConnection(i_oDevice);
			m_oRoboScooper.connect();
		}
	}

	public static void connectToRoboScooper(final BaseActivity m_oOwner, RoboScooper i_oRoboScooper, BluetoothDevice i_oDevice, final ConnectListener i_oConnectListener) {
		RoboScooperRobot m_oRobot = new RoboScooperRobot(m_oOwner) {
			public void onConnect() {
				i_oConnectListener.onConnect(true);
			};
			public void onDisconnect() {
				i_oConnectListener.onConnect(false);
			};
		};
		m_oRobot.showConnectingDialog();
		
		if (i_oRoboScooper.isConnected()) {
			i_oRoboScooper.disconnect();
		}
		
		i_oRoboScooper.setConnection(i_oDevice);
		i_oRoboScooper.connect();
		i_oRoboScooper.setHandler(m_oRobot.getUIHandler());
	}
	
	@Override
	public boolean isPairing() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	protected void onConnect() {
		connected = true;
		updateButtons(true);
	}

	@Override
	protected void onDisconnect() {
		connected = false;
		updateButtons(false);
		m_oRemoteCtrl.resetLayout();
		m_oSensorGatherer.initialize();
	}

	@Override
	protected void updateButtons(boolean i_bEnabled) {
		m_oRemoteCtrl.updateButtons(i_bEnabled);
		
		m_btnAutonomous.setEnabled(i_bEnabled);
		m_btnDump.setEnabled(i_bEnabled);
		m_btnPickup.setEnabled(i_bEnabled);
		m_btnStop.setEnabled(i_bEnabled);
		m_btnTalk.setEnabled(i_bEnabled);
		m_btnVision.setEnabled(i_bEnabled);
		m_btnWhack.setEnabled(i_bEnabled);
		
		m_cbAccelerometer.setEnabled(i_bEnabled);
		m_cbBattery.setEnabled(i_bEnabled);
		m_cbLight.setEnabled(i_bEnabled);
	}

	@Override
	public void onMove(Move i_oMove, double i_dblSpeed, double i_dblAngle) {
		m_oRemoteCtrl.onMove(i_oMove, i_dblSpeed, i_dblAngle);
	}

	@Override
	public void onMove(Move i_oMove) {
		m_oRemoteCtrl.onMove(i_oMove);
	}

	@Override
	public void enableControl(boolean i_bEnable) {
		m_oRemoteCtrl.enableControl(i_bEnable);
		
		Utils.showLayout(m_layControls1, i_bEnable);
		Utils.showLayout(m_layControls2, i_bEnable);
	}

}
