package org.dobots.swarmcontrol.robots.roomba;

import org.dobots.robots.roomba.Roomba;
import org.dobots.robots.roomba.RoombaBluetooth;
import org.dobots.robots.roomba.RoombaTypes;
import org.dobots.robots.roomba.RoombaTypes.ERoombaSensorPackages;
import org.dobots.swarmcontrol.BaseActivity;
import org.dobots.swarmcontrol.IConnectListener;
import org.dobots.swarmcontrol.IRemoteControlListener;
import org.dobots.swarmcontrol.R;
import org.dobots.swarmcontrol.RemoteControlHelper;
import org.dobots.swarmcontrol.RemoteControlHelper.Move;
import org.dobots.swarmcontrol.RobotInventory;
import org.dobots.swarmcontrol.robots.BluetoothRobot;
import org.dobots.swarmcontrol.robots.RobotCalibration;
import org.dobots.swarmcontrol.robots.RobotType;
import org.dobots.swarmcontrol.robots.nxt.NXTRobot;
import org.dobots.utility.Utils;

import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.ToggleButton;

public class RoombaRobot extends BluetoothRobot implements IRemoteControlListener {
	
	private static String TAG = "Roomba";

	private static final int CONNECT_ID = Menu.FIRST;
	private static final int ACCEL_ID = CONNECT_ID + 1;
	private static final int ADVANCED_CONTROL_ID = ACCEL_ID + 1;
	
	private static final int REMOTE_CTRL_GRP = GENERAL_GRP + 1;
	
	private Roomba m_oRoomba;

	private RoombaSensorGatherer m_oSensorGatherer;

	private RemoteControlHelper m_oRemoteCtrl;

	private boolean m_bControl = false;
	private boolean m_bMainBrushEnabled = false;
	private boolean m_bSideBrushEnabled = false;
	private boolean m_bVacuumEnabled = false;

	private boolean m_bMove;

	private boolean btErrorPending = false;

	private Spinner m_spSensors;
	private Button m_btnClean;
	private Button m_btnStop;
	private Button m_btnDock;
	private Button m_btnMainBrush;
	private Button m_btnSideBrush;
	private Button m_btnVacuum;
	private ToggleButton m_btnPower;
	private Button m_btnAccelerometer;
	private Button m_btnMove;
	private Button m_btnCalibrate;

	private double m_dblSpeed;

	public RoombaRobot(BaseActivity i_oOwner) {
		super(i_oOwner);
	}
	
	public RoombaRobot() {
		super();
	}

    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);

    	int nIndex = (Integer) getIntent().getExtras().get("InventoryIndex");
    	if (nIndex == -1) {
    		m_oRoomba = new Roomba();
    		connectToRobot();
    	} else {
    		m_oRoomba = (Roomba) RobotInventory.getInstance().getRobot(nIndex);
    		m_bKeepAlive = true;
    	}
		m_oRoomba.setHandler(m_oUiHandler);
		
		m_oSensorGatherer = new RoombaSensorGatherer(m_oActivity, m_oRoomba);
		m_dblSpeed = m_oRoomba.getBaseSped();

		m_oRemoteCtrl = new RemoteControlHelper(m_oActivity, m_oRoomba, this);
        m_oRemoteCtrl.setProperties();

    	updateButtons(false);
    	updateControlButtons(false);
    	updatePowerButton(false);
        
        if (m_oRoomba.isConnected()) {
			updatePowerButton(true);
			if (m_oRoomba.isPowerOn()) {
				updateButtons(true);
			}
		}
    }

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		menu.add(REMOTE_CTRL_GRP, ACCEL_ID, 2, "Accelerometer");
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
			item.setTitle("Accelerometer " + (m_bAccelerometer ? "(OFF)" : "(ON)"));

			if (m_bAccelerometer) {
				m_bSetAccelerometerBase = true;
			} else {
				m_oRoomba.moveStop();
			}
		case ADVANCED_CONTROL_ID:
			m_oRemoteCtrl.toggleAdvancedControl();
			break;
		}

		return super.onMenuItemSelected(featureId, item);
	}

	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		m_oSensorGatherer.onCreateContextMenu(menu, v, menuInfo);
	}
	
	public boolean onContextItemSelected(MenuItem item) {
		return m_oSensorGatherer.onContextItemSelected(item);
	}

    public void onDestroy() {
    	super.onDestroy();
    	
    	shutDown();
    }
    
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		
		switch (requestCode) {
		case RobotCalibration.ROBOT_CALIBRATION_RESULT:
			if (resultCode == RESULT_OK) {
				m_dblSpeed = data.getExtras().getDouble(RobotCalibration.CALIBRATED_SPEED);
				m_oRoomba.setBaseSpeed(m_dblSpeed);
				showToast("Calibrated speed saved", Toast.LENGTH_SHORT);
			} else {
				showToast("Calibration discarded", Toast.LENGTH_SHORT);
			}
		}
	};
    
	@Override
	public void onAccelerationChanged(float x, float y, float z, boolean tx) {
		super.onAccelerationChanged(x, y, z, tx);
		
		if (tx && m_bAccelerometer) {
			Log.i("Accel", "x=" + x + ", y=" + y + ", z=" + z); 
			
			int nSpeed = getSpeedFromAcceleration(x, y, z, RoombaTypes.MAX_SPEED, true);
			int nRadius = getRadiusFromAcceleration(x, y, z, RoombaTypes.MAX_RADIUS);

			// if speed is negative the roomba should drive forward
			// if it is positive it should drive backward
			if (nSpeed > SPEED_SENSITIVITY) {
				// remove the speed sensitivity again
				nSpeed -= SPEED_SENSITIVITY; 

				Log.i("Speeds", "speed=" + nSpeed + ", radius=" + nRadius); 

				if (nRadius > RADIUS_SENSITIVITY) {
					m_oRoomba.moveForward(nSpeed, nRadius);
				} else if (nRadius < -RADIUS_SENSITIVITY) {
					m_oRoomba.moveForward(nSpeed, nRadius);
				} else {
					m_oRoomba.moveForward(nSpeed);
				}
			} else if (nSpeed < -SPEED_SENSITIVITY) {
				// remove the speed_sensitivity again
				nSpeed += SPEED_SENSITIVITY;

				Log.i("Speeds", "speed=" + nSpeed + ", radius=" + nRadius); 

				if (nRadius > RADIUS_SENSITIVITY) {
					// 
					m_oRoomba.moveBackward(nSpeed, nRadius);
				} else if (nRadius < -RADIUS_SENSITIVITY) {
					m_oRoomba.moveBackward(nSpeed, nRadius);
				} else {
					m_oRoomba.moveBackward(nSpeed);
				}
			} else {
				
				Log.i("Speeds", "speed=~0" + ", radius=" + nRadius); 

				if (nRadius > RADIUS_SENSITIVITY) {
					// if speed is small we remap the radius to 
					// speed and let it rotate on the spot 
					nSpeed = (int) (nRadius / RoombaTypes.MAX_RADIUS * RoombaTypes.MAX_SPEED);
					m_oRoomba.rotateCounterClockwise(nSpeed);
				} else if (nRadius < -RADIUS_SENSITIVITY) {
					// if speed is small we remap the radius to 
					// speed and let it rotate on the spot 
					nSpeed = (int) (nRadius / RoombaTypes.MAX_RADIUS * RoombaTypes.MAX_SPEED);
					m_oRoomba.rotateClockwise(nSpeed);
				} else {
					m_oRoomba.moveStop();
				}

			}
		}
	}
	
	public void updateControlButtons(boolean visible) {
		m_btnCalibrate.setEnabled(visible);
		
		Utils.showLayout((LinearLayout)m_oActivity.findViewById(R.id.layBrushes), visible);
		Utils.showLayout((LinearLayout)m_oActivity.findViewById(R.id.layRemoteControl), visible);
	}

	public void updateButtons(boolean enabled) {
		m_oRemoteCtrl.updateButtons(enabled);
		
		m_btnClean.setEnabled(enabled);
		m_btnStop.setEnabled(enabled);
		m_btnDock.setEnabled(enabled);
		m_spSensors.setEnabled(enabled);
		
		m_oSensorGatherer.updateButtons(enabled);
	}

	@Override
	protected void disconnect() {
		updatePowerButton(false);
		updateControlButtons(false);
		if (m_oRoomba.isConnected()) {
			m_oRoomba.disconnect();
		}
	}

	@Override
	public void connect(BluetoothDevice i_oDevice) {
		m_strAddress = i_oDevice.getAddress();
		showConnectingDialog();
		
		if (m_oRoomba.isConnected()) {
			m_oRoomba.destroyConnection();
		}
		RoombaBluetooth oRoombaBluetooth = new RoombaBluetooth(i_oDevice);
		m_oRoomba.setConnection(oRoombaBluetooth);
		m_oRoomba.connect();
	}
	
	public void updatePowerButton(boolean enabled) {
		m_btnPower.setEnabled(enabled);
		if (enabled) {
			if (m_oRoomba.isConnected()) {
				m_btnPower.setChecked(m_oRoomba.isPowerOn());
			}
		}
	}

	@Override
	public void onConnect() {
		m_oRoomba.init();
		updatePowerButton(true);
		if (m_oRoomba.isPowerOn()) {
			updateButtons(true);
		}
	}
	
	@Override
	public void onDisconnect() {
		updatePowerButton(false);
		updateButtons(false);
		m_oRemoteCtrl.resetLayout();
		m_oSensorGatherer.stopThread();
	}

	@Override
	protected void handleUIMessage(Message msg) {
		super.handleUIMessage(msg);
	}

	public static void connectToRoomba(final BaseActivity m_oOwner, Roomba i_oRoomba, BluetoothDevice i_oDevice, final IConnectListener i_oConnectListener) {
		NXTRobot m_oRobot = new NXTRobot(m_oOwner) {
			public void onConnect() {
				i_oConnectListener.onConnect(true);
			};
			public void onDisconnect() {
				i_oConnectListener.onConnect(false);
			};
		};
		
		m_oRobot.showConnectingDialog();
		
		if (i_oRoomba.isConnected()) {
			i_oRoomba.disconnect();
		}

		i_oRoomba.setConnection(new RoombaBluetooth(i_oDevice));
		i_oRoomba.connect();
		i_oRoomba.setHandler(m_oRobot.getUIHandler());
	}

	public void resetLayout() {
		m_oRemoteCtrl.resetLayout();
		
		m_spSensors.setSelection(0);
		m_oSensorGatherer.initialize();
	}
	
	@Override
	public void shutDown() {
    	updateButtons(false);
    	updateControlButtons(false);

		m_oSensorGatherer.setSensorPackage(ERoombaSensorPackages.sensPkg_None);
		m_oSensorGatherer.stopThread();
		
		if (m_oRoomba.isConnected() && !m_bKeepAlive) {
			m_oRoomba.disconnect();
		}
	}

	@Override
	protected void setProperties(RobotType i_eRobot) {
        m_oActivity.setContentView(R.layout.roomba_main);
		
        m_spSensors = (Spinner) m_oActivity.findViewById(R.id.spSensors);
		final ArrayAdapter<ERoombaSensorPackages> adapter = new ArrayAdapter<ERoombaSensorPackages>(m_oActivity, 
				android.R.layout.simple_spinner_item, ERoombaSensorPackages.values());
        adapter.setDropDownViewResource(android.R.layout.select_dialog_item);
		m_spSensors.setAdapter(adapter);
		m_spSensors.setOnItemSelectedListener(new OnItemSelectedListener() {

			@Override
			public void onItemSelected(AdapterView<?> parent, View view,
					int position, long id) {
				ERoombaSensorPackages eSensorPkg = adapter.getItem(position);
				m_oSensorGatherer.showSensorPackage(eSensorPkg);
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
				// do nothing
			}
			
		});

		m_btnClean = (Button) m_oActivity.findViewById(R.id.btnClean);
		m_btnClean.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				m_oRoomba.startCleanMode();
			}
		});
		
		m_btnStop = (Button) m_oActivity.findViewById(R.id.btnStop);
		m_btnStop.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				// we can stop any active action by setting the roomba to safe mode
				m_oRoomba.setSafeMode();
			}
		});
		
		m_btnDock = (Button) m_oActivity.findViewById(R.id.btnDock);
		m_btnDock.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				m_oRoomba.seekDocking();
			}
		});

		m_btnCalibrate = (Button) m_oActivity.findViewById(R.id.btnCalibrate);
		m_btnCalibrate.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {

				int nIndex = RobotInventory.getInstance().findRobot(m_oRoomba);
				if (nIndex == -1) {
					nIndex = RobotInventory.getInstance().addRobot(m_oRoomba);
				}
				m_bKeepAlive = true;
				RobotCalibration.createAndShow(m_oActivity, RobotType.RBT_ROOMBA, nIndex, m_dblSpeed);
			}
		});
	
		m_btnMainBrush = (Button) m_oActivity.findViewById(R.id.btnMainBrush);
		m_btnMainBrush.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				m_bMainBrushEnabled = !m_bMainBrushEnabled;
				m_oRoomba.setMainBrush(m_bMainBrushEnabled);
			}
		});

		m_btnSideBrush = (Button) m_oActivity.findViewById(R.id.btnSideBrush);
		m_btnSideBrush.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				m_bSideBrushEnabled = !m_bSideBrushEnabled;
				m_oRoomba.setSideBrush(m_bSideBrushEnabled);
			}
		});

		m_btnVacuum = (Button) m_oActivity.findViewById(R.id.btnVacuum);
		m_btnVacuum.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				m_bVacuumEnabled = !m_bVacuumEnabled;
				m_oRoomba.setVacuum(m_bVacuumEnabled);
			}
		});
		
		m_btnPower = (ToggleButton) m_oActivity.findViewById(R.id.btnPower);
		m_btnPower.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				if (m_oRoomba.isPowerOn()) {
					m_oRoomba.powerOff();
				} else {
					m_oRoomba.powerOn();
				}
				updatePowerButton(true);
				updateButtons(m_oRoomba.isPowerOn());
			}
		});
		
		m_btnAccelerometer = (Button) m_oActivity.findViewById(R.id.btnAccelerometer);
		m_btnAccelerometer.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				m_bAccelerometer = !m_bAccelerometer;

				if (m_bAccelerometer) {
					m_bSetAccelerometerBase = true;
				} else {
					m_oRoomba.moveStop();
				}
				
				if (m_bAccelerometer) {
					((Button) m_oActivity.findViewById(R.id.btnMove)).performClick();
				} else {
					m_oRemoteCtrl.updateButtons(!m_bAccelerometer);
				}
			}
		});
		
	}

	public static String getMacFilter() {
		return RoombaTypes.MAC_FILTER;
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
		updateControlButtons(i_bEnable);
	}

}
