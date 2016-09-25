/*******************************************************************************
 * Copyright (c) ecobee, Inc. 2016. All rights reserved.
 *******************************************************************************/
package com.amazon.alexa.avs;

public class SystemOutVisualizer implements Visualizer {

	@Override
	public void setValue(int val) {
		System.out.println("Progress: "+val);
	}

	@Override
	public void setIndeterminate(boolean b) {
		// TODO Auto-generated method stub

	}
}
