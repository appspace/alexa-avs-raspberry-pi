/*******************************************************************************
 * Copyright (c) ecobee, Inc. 2016. All rights reserved.
 *******************************************************************************/
package com.amazon.alexa.avs.app.gui;

import com.amazon.alexa.avs.Visualizer;

public class GuiProgressBar extends javax.swing.JProgressBar implements Visualizer {

	/**
     * Creates a GUI progress bar
     * with the specified minimum and maximum.
     * Sets the initial value of the progress bar to the specified minimum.
     * By default, a border is painted but a progress string is not.
     * <p>
     * The <code>BoundedRangeModel</code> that holds the progress bar's data
     * handles any issues that may arise from improperly setting the
     * minimum, initial, and maximum values on the progress bar.
     * See the {@code BoundedRangeModel} documentation for details.
     *
     * @param min  the minimum value of the progress bar
     * @param max  the maximum value of the progress bar
	 */
	public GuiProgressBar(int min, int max) {
		super(min, max);
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = 3991164596153280483L;
}
