package edu.ucr.nanosensorgrapher;
import java.awt.GridLayout;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;


public class DataProcessingPanel extends JPanel {

	private static final String REMOVE_OUTLIERS_LABEL = "Remove Outliers (Number of passes - 0 for no outlier removal):";
	private static final String STD_THRESHOLD_LABEL = "Standard Deviation Threshold:";
	private static final String SMOOTH_DATA_LABEL = "Apply Smooth Moving Average (Period - 0 for no smoothing):";
	private static final String BASELINE_DRIFT_LABEL = "Account for baseline drift using simple linear regression";
	
	private JLabel mRemoveOutliersLabel;
	private JSpinner mRemoveOutliers;
	private JLabel mOutlierStdThresholdLabel;
	private JSpinner mOutlierStdThreshold;
	private JLabel mSmoothDataLabel;
	private JSpinner mSmoothDataPeriod;
	private JCheckBox mBaselineDriftCheckBox;

	public DataProcessingPanel() {
		super(new GridLayout(4, 1));
		super.setBorder(new EmptyBorder(10, 10, 10, 10));
		mRemoveOutliersLabel = new JLabel(REMOVE_OUTLIERS_LABEL);
		mRemoveOutliersLabel.setHorizontalAlignment(SwingConstants.CENTER);
		mRemoveOutliers = new JSpinner();
		mRemoveOutliers.setModel(new SpinnerNumberModel(0, 0, 10, 1));
		mRemoveOutliers.setEditor(new JSpinner.NumberEditor(mRemoveOutliers, "##"));
		mOutlierStdThresholdLabel = new JLabel(STD_THRESHOLD_LABEL);
		mOutlierStdThresholdLabel.setHorizontalAlignment(SwingConstants.CENTER);
		mOutlierStdThreshold = new JSpinner();
		mOutlierStdThreshold.setModel(new SpinnerNumberModel(0.0, 0.0, 5.0, 0.1)); 
		mOutlierStdThreshold.setEditor(new JSpinner.NumberEditor(mOutlierStdThreshold, "0.0"));
		mSmoothDataLabel = new JLabel(SMOOTH_DATA_LABEL);
		mSmoothDataLabel.setHorizontalAlignment(SwingConstants.CENTER);
		mSmoothDataPeriod = new JSpinner();
		mSmoothDataPeriod.setModel(new SpinnerNumberModel(0, 0, 10, 1));
		mSmoothDataPeriod.setEditor(new JSpinner.NumberEditor(mSmoothDataPeriod, "##"));
		mBaselineDriftCheckBox = new JCheckBox(BASELINE_DRIFT_LABEL);
		super.add(mRemoveOutliersLabel);
		super.add(mRemoveOutliers);
		super.add(mOutlierStdThresholdLabel);
		super.add(mOutlierStdThreshold);
		super.add(mSmoothDataLabel);
		super.add(mSmoothDataPeriod);
		super.add(mBaselineDriftCheckBox);
	}
	
	public int getOutlierRemoval() {
		return (int) mRemoveOutliers.getValue();
	}
	
	public double getOutlierStdThreshold() {
		return (double) mOutlierStdThreshold.getValue();
	}
	
	public int getSmoothDataPeriod() {
		return (int) mSmoothDataPeriod.getValue();
	}
	
	public boolean getBaselineDrift() {
		return mBaselineDriftCheckBox.isSelected();
	}

}
