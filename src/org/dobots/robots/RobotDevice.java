package org.dobots.robots;

import java.io.IOException;

import org.dobots.swarmcontrol.robots.RobotType;

public interface RobotDevice {

	public RobotType getType();
	public String getAddress();

	public void destroy();
	
	// connection methods
	public void setConnection();
	public void connect() throws IOException;
	public void disconnect();
	public boolean isConnected();
	
	// drive methods
	public void enableControl(boolean i_bEnable);
	
	public void driveForward(double i_dblSpeed);
	public void driveForward(double i_dblSpeed, int i_nRadius);
	
	public void driveBackward(double i_dblSpeed);
	public void driveBackward(double i_dblSpeed, int i_nRadius);
	
	public void rotateClockwise(double i_dblSpeed);
	public void rotateCounterClockwise(double i_dblSpeed);
	
	public void driveStop();
	
	

}
