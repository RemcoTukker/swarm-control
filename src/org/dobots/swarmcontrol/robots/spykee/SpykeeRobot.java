// Based on source code of Main.java from Jack Veenstra
// Original can be found at
//   http://code.google.com/p/spykee-remote/
//
// Copyright 2011 Jack Veenstra
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.dobots.swarmcontrol.robots.spykee;

import org.dobots.robots.parrot.Parrot;
import org.dobots.robots.spykee.Spykee;
import org.dobots.robots.spykee.SpykeeController;
import org.dobots.robots.spykee.SpykeeTypes;
import org.dobots.swarmcontrol.BaseActivity;
import org.dobots.swarmcontrol.ConnectListener;
import org.dobots.swarmcontrol.R;
import org.dobots.swarmcontrol.RemoteControlHelper;
import org.dobots.swarmcontrol.RemoteControlHelper.Move;
import org.dobots.swarmcontrol.RemoteControlListener;
import org.dobots.swarmcontrol.RobotInventory;
import org.dobots.swarmcontrol.robots.RobotType;
import org.dobots.swarmcontrol.robots.WifiRobot;
import org.dobots.swarmcontrol.robots.parrot.ParrotRobot;
import org.dobots.utility.Utils;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.ToggleButton;

public class SpykeeRobot extends WifiRobot implements RemoteControlListener {

	private static String TAG = "Spykee";

	private static final int DIALOG_SETTINGS_ID = 1;

	private static final int SETTINGS_ID = CONNECT_ID + 1;
	private static final int INVERT_ID = SETTINGS_ID + 1;
	private static final int ACCEL_ID = INVERT_ID + 1;
	private static final int ADVANCED_CONTROL_ID = ACCEL_ID + 1;
	private static final int VIDEO_ID = ADVANCED_CONTROL_ID + 1;
	private static final int AUDIO_ID = VIDEO_ID + 1;
	private static final int VIDEO_SCALE_ID = AUDIO_ID + 1;

	private boolean connected;
	
	private Spykee m_oSpykee;

	private SpykeeSensorGatherer m_oSensorGatherer;

	private RemoteControlHelper m_oRemoteCtrl;

	private Button m_btnDock;
	private ToggleButton m_btnLed1;
	private Button m_btnLed2;
	private Button m_btnLed3;
	
	private LinearLayout m_layControls;
	
	private double m_dblSpeed;

	private Dialog m_dlgConnectDialog;
	
	private String m_strAddress = null;
	private String m_strPort = null;
	private String m_strLogin = null;
	private String m_strPassword = null;
	private boolean m_bSettingsValid = false;

	public SpykeeRobot(BaseActivity i_oOwner) {
		super(i_oOwner);
	}
	
	public SpykeeRobot() {
		super();
	}

    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);

    	// TODO make generic
    	int nIndex = (Integer) getIntent().getExtras().get("InventoryIndex");
    	if (nIndex == -1) {
			m_oSpykee = new Spykee();
	        connectToRobot();
    	} else {
    		m_oSpykee = (Spykee) RobotInventory.getInstance().getRobot(nIndex);
    		m_bKeepAlive = true;
    	}
    	m_oSpykee.setHandler(m_oUiHandler);
		
		m_oSensorGatherer = new SpykeeSensorGatherer(this, m_oSpykee);
		m_dblSpeed = m_oSpykee.getBaseSped();

		m_oRemoteCtrl = new RemoteControlHelper(m_oActivity, m_oSpykee, this);
        m_oRemoteCtrl.setProperties();

        updateButtons(false);

        if (m_oSpykee.isConnected()) {
			updateButtons(true);
		}
        
    }

	@Override
	protected void setProperties(RobotType i_eRobot) {
    	m_oActivity.setContentView(R.layout.spykee_main);
    	
    	m_layControls = (LinearLayout) m_oActivity.findViewById(R.id.layControls);
    	
    	m_btnDock = (Button) m_oActivity.findViewById(R.id.btnDock);
    	m_btnDock.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View arg0) {
				switch (m_oSpykee.getDockState()) {
				case DOCKED:
					m_oSpykee.undock();
					break;
				case DOCKING:
					m_oSpykee.cancelDock();
					break;
				case UNDOCKED:
					m_oSpykee.dock();
					m_btnDock.setText("Cancel Dock");
					break;
				}
			}
		});
    	
    	OnClickListener oLedClickListener = new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				int nLed = (Integer)v.getTag();
				m_oSpykee.setLed(nLed, ((ToggleButton)v).isChecked());
			}
		};
    	
    	m_btnLed1 = (ToggleButton) m_oActivity.findViewById(R.id.btnLed1);
    	m_btnLed1.setTag(0);
    	m_btnLed1.setOnClickListener(oLedClickListener);

    	m_btnLed2 = (ToggleButton) m_oActivity.findViewById(R.id.btnLed2);
    	m_btnLed2.setTag(1);
    	m_btnLed2.setOnClickListener(oLedClickListener);

    	m_btnLed3 = (ToggleButton) m_oActivity.findViewById(R.id.btnLed3);
    	m_btnLed3.setTag(2);
    	m_btnLed3.setOnClickListener(oLedClickListener);
    	
    }

	@Override
	public void handleUIMessage(Message msg) {
		super.handleUIMessage(msg);
		
		switch (msg.what) {
		case SpykeeTypes.LOGIN_ERROR:
			showToast("Login failed, please check your settings!", Toast.LENGTH_LONG);
			connectingProgressDialog.dismiss();
			break;

		case SpykeeController.SPYKEE_DOCK:
			if (msg.arg1 == SpykeeController.SPYKEE_DOCK_DOCKED) {
				m_btnDock.setText(R.string.spykee_undock);
			} else if (msg.arg1 == SpykeeController.SPYKEE_DOCK_UNDOCKED) {
				m_btnDock.setText(R.string.spykee_dock);
			}
		case SpykeeController.SPYKEE_BATTERY_LEVEL:
		case SpykeeController.SPYKEE_VIDEO_FRAME:
		case SpykeeController.SPYKEE_AUDIO:
			m_oSensorGatherer.dispatchMessage(msg);
			break;
		}
		
	}

    @Override
    public void onDestroy() {
    	super.onDestroy();

    	if (m_oSpykee.isConnected() && !m_bKeepAlive) {
    		m_oSpykee.disconnect();
    		m_oSpykee.destroy();
    	}

//    	m_oSensorGatherer.stopThread();
    }
    
    @Override
    public void onStop() {
    	super.onStop();
    	
    	m_oSpykee.setVideoEnabled(false);
    	m_oSpykee.setAudioEnabled(false);
    	
    	if (m_oSpykee.isConnected() && !m_bKeepAlive) {
    		m_oSpykee.disconnect();
    	}
    }

    @Override
    public void onPause() {
    	super.onPause();

    	m_bAccelerometer = false;
    }

    @Override
    public void onRestart() {
    	super.onRestart();
    	
    	if (m_strMacAddress != "" && !m_bKeepAlive) {
    		connectToRobot();
    	}

    }

    @Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		menu.add(0, SETTINGS_ID, 2, "Settings");
		return true;
	}
    
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
    	if (m_oSpykee.isConnected()) {
    		if (menu.findItem(VIDEO_ID) == null) {
	    		menu.add(0, VIDEO_ID, 3, "Video");
	    		menu.add(0, AUDIO_ID, 4, "Audio");
    			menu.add(0, VIDEO_SCALE_ID, 8, "Zoom Video");
    		}
    	} else {
    		if (menu.findItem(VIDEO_ID) != null) {
    			menu.removeItem(VIDEO_ID);
    			menu.removeItem(AUDIO_ID);
    			menu.removeItem(VIDEO_SCALE_ID);
    		}
    	}
    	if (m_oRemoteCtrl.isControlEnabled()) {
    		if (menu.findItem(INVERT_ID) == null) {
				menu.add(0, INVERT_ID, 5, "Invert Driving");
				menu.add(0, ACCEL_ID, 6, "Accelerometer");
				menu.add(0, ADVANCED_CONTROL_ID, 7, "Advanced Control");
    		}
		} else
			if (menu.findItem(INVERT_ID) != null) {
				menu.removeItem(INVERT_ID);
				menu.removeItem(ACCEL_ID);
				menu.removeItem(ADVANCED_CONTROL_ID);
			}
    	
    	Utils.updateOnOffMenuItem(menu.findItem(ACCEL_ID), m_bAccelerometer);
    	Utils.updateOnOffMenuItem(menu.findItem(ADVANCED_CONTROL_ID), m_oRemoteCtrl.isAdvancedControl());
    	Utils.updateOnOffMenuItem(menu.findItem(INVERT_ID), m_oSpykee.isInverted());
    	Utils.updateOnOffMenuItem(menu.findItem(VIDEO_ID), m_oSpykee.isVideoEnabled());
    	Utils.updateOnOffMenuItem(menu.findItem(AUDIO_ID), m_oSpykee.isAudioEnabled());
    	Utils.updateOnOffMenuItem(menu.findItem(VIDEO_SCALE_ID), m_oSensorGatherer.isVideoScaled());
    	
		return true;
    }

	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		switch (item.getItemId()) {
		case SETTINGS_ID:
    		showDialog(DIALOG_SETTINGS_ID);
    		break;
		case INVERT_ID:
			m_oSpykee.setInverted(!m_oSpykee.isInverted());
			return true;
		case ACCEL_ID:
			m_bAccelerometer = !m_bAccelerometer;

			if (m_bAccelerometer) {
				m_bSetAccelerometerBase = true;
			} else {
				m_oSpykee.moveStop();
			}
			break;
		case ADVANCED_CONTROL_ID:
			m_oRemoteCtrl.toggleAdvancedControl();
			break;
		case VIDEO_ID:
			m_oSensorGatherer.setVideoEnabled(!m_oSpykee.isVideoEnabled());
			break;
		case AUDIO_ID:
			m_oSensorGatherer.setAudioEnabled(!m_oSpykee.isAudioEnabled());
			break;
		case VIDEO_SCALE_ID:
			m_oSensorGatherer.setVideoScaled(!m_oSensorGatherer.isVideoScaled());
			break;
		}

		return super.onMenuItemSelected(featureId, item);
	}

	@Override
	protected void disconnect() {
		m_oSpykee.disconnect();
	}
	
	public void onConnect() {
		connected = true;
		updateButtons(true);
		m_oSensorGatherer.onConnect();
	}
	
	@Override
	public void onDisconnect() {
		connected = false;
		updateButtons(false);
		m_oRemoteCtrl.resetLayout();
	}

	@Override
	protected void connectToRobot() {

        if (checkSettings()) {
        	showConnectingDialog();
		
        	m_oSpykee.setConnection(m_strAddress, m_strPort, m_strLogin, m_strPassword);
			m_oSpykee.connect();
		} else {
			Utils.showToast("Connection Settings not valid, please check your settings and try connecting again!", Toast.LENGTH_LONG);
		}
	}

	public static void connectToSpykee(final BaseActivity m_oOwner, Spykee i_oSpykee, final ConnectListener i_oConnectListener) {
		ParrotRobot m_oRobot = new ParrotRobot(m_oOwner) {
			public void onConnect() {
				i_oConnectListener.onConnect(true);
			};
			public void onDisconnect() {
				i_oConnectListener.onConnect(false);
			};
		};
		
		m_oRobot.showConnectingDialog();
		
		if (i_oSpykee.isConnected()) {
			i_oSpykee.disconnect();
		}

		i_oSpykee.setHandler(m_oRobot.getUIHandler());
		i_oSpykee.setConnection("192.168.1.101", "9000", "admin", "IcePizza");
		i_oSpykee.connect();
	}

	@Override
	protected void resetLayout() {
		m_oRemoteCtrl.resetLayout();
		
		m_oSensorGatherer.resetLayout();
	}

	@Override
	protected void updateButtons(boolean i_bEnabled) {
		m_oRemoteCtrl.updateButtons(i_bEnabled);
		
		m_btnDock.setEnabled(i_bEnabled);
		m_btnLed1.setEnabled(i_bEnabled);
		m_btnLed2.setEnabled(i_bEnabled);
		m_btnLed3.setEnabled(i_bEnabled);
	}

    /**
     * This is called when a dialog is created for the first time.  The given
     * "id" is the same value that is passed to showDialog().
     */
    @Override
    protected Dialog onCreateDialog(int id) {
    	LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
    	View layout;
    	AlertDialog.Builder builder = new AlertDialog.Builder(this);
    	switch (id) {
    	case DIALOG_SETTINGS_ID:
        	layout = inflater.inflate(R.layout.spykee_settings, null);
        	builder.setTitle("Spykee Connection Settings");
        	builder.setView(layout);
        	builder.setPositiveButton("Save", new DialogInterface.OnClickListener() {
    			public void onClick(DialogInterface arg0, int arg1) {
    				adjustConnection();
    			}
    		});
        	m_dlgConnectDialog = builder.create();
        	return m_dlgConnectDialog;
    	}
    	return null;
    }

    /**
     * This is called each time a dialog is shown.
     */
    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
    	if (id == DIALOG_SETTINGS_ID) {
    		// Pre-fill the text fields with the saved login settings.
    		EditText editText;
    		if (m_strAddress != null) {
    			editText = (EditText) dialog.findViewById(R.id.txtAddress);
    			editText.setText(m_strAddress);
    		}
    		if (m_strPort != null) {
    			editText = (EditText) dialog.findViewById(R.id.txtPort);
    			editText.setText(m_strPort);
    		}
    		if (m_strLogin != null) {
    			editText = (EditText) dialog.findViewById(R.id.txtLogin);
    			editText.setText(m_strLogin);
    		}
    		if (m_strPassword != null) {
    			editText = (EditText) dialog.findViewById(R.id.txtPassword);
    			editText.setText(m_strPassword);
    		}
    	}
    }
    
    private boolean checkSettings() {
		// Read the login settings (if any) from the preferences file.
		SharedPreferences prefs = getPreferences(MODE_PRIVATE);
		m_strAddress = prefs.getString(SpykeeTypes.SPYKEE_PREFS_ADDRESS, SpykeeTypes.SPYKEE_DEFAULT_ADDRESS);
		m_strPort = prefs.getString(SpykeeTypes.SPYKEE_PREFS_PORT, SpykeeTypes.SPYKEE_DEFAULT_PORT);
		m_strLogin = prefs.getString(SpykeeTypes.SPYKEE_PREFS_LOGIN, SpykeeTypes.SPYKEE_DEFAULT_LOGIN);
		m_strPassword = prefs.getString(SpykeeTypes.SPYKEE_PREFS_PASSWORD, SpykeeTypes.SPYKEE_DEFAULT_PASSWORD);
		
		m_bSettingsValid = ((m_strAddress != "") &&
							(m_strPort != "") &&
							(m_strLogin != "") &&
							(m_strPassword != "") && (m_strPassword != null));
		return m_bSettingsValid;
    }

    private void adjustConnection() {
    	// Read the login settings from the text fields.
    	EditText editText = (EditText) m_dlgConnectDialog.findViewById(R.id.txtAddress);
		String strAddress = editText.getText().toString();
    	editText = (EditText) m_dlgConnectDialog.findViewById(R.id.txtPort);
		String strPort = editText.getText().toString();
    	editText = (EditText) m_dlgConnectDialog.findViewById(R.id.txtLogin);
		String strLogin = editText.getText().toString();
    	editText = (EditText) m_dlgConnectDialog.findViewById(R.id.txtPassword);
		String strPassword = editText.getText().toString();
		
		// Save the current login settings
		SharedPreferences prefs = getPreferences(MODE_PRIVATE);
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(SpykeeTypes.SPYKEE_PREFS_ADDRESS, strAddress);
		editor.putString(SpykeeTypes.SPYKEE_PREFS_PORT, strPort);
		editor.putString(SpykeeTypes.SPYKEE_PREFS_LOGIN, strLogin);
		editor.putString(SpykeeTypes.SPYKEE_PREFS_PASSWORD, strPassword);
		editor.commit();
		
		if (!checkSettings()) {
			Utils.showToast("Connection Settings not valid, please check again!", Toast.LENGTH_LONG);
		}
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
		
		Utils.showLayout(m_layControls, i_bEnable);
	}

}