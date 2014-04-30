package edu.ucr.nanosensorgrapher;

import java.awt.GridLayout;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

public class GasSelectionPanel extends JPanel
{
	private static final String GAS_LABEL = "Select a gas: ";
	private static final String CUSTOM_CONCENTRATION_LABEL = "Custom gas or " +
			"concentration (must be in the same format as in the dropdown list)";
	private static final String[] GAS_CONCENTRATIONS = {
			"1,3-Butadiene 0.1 0.25 0.5 1 2",
			"Acetaldehyde 20 50 100 200 400",
			"Acrolein 0.01 0.025 0.05 0.1 0.2",
			"Benzene 0.1 0.25 0.5 1 2",
			"CO 5 25 50 75 100",
			"CO2 500 1000 2500 5000 10000",
			"Ethylbenzene 10 25 50 100 200",
			"Formaldehyde 0.3 0.75 1.5 3 6",
			"H2 100 200 250 500 1000 2000",
			"H2S 0.5 2.5 10 20 40",
			"Hg 0.005 0.01 0.025 0.05 0.1",
			"Napthalene 1 2.5 5 10 20",
			"NH3 0.5 2.5 5 25 50 100",
			"n-Hexane 50 100 250 500 1000",
			"NOx 0.1 0.5 2.5 5 10",
			"O3 0.01 0.025 0.05 0.1 0.2",
			"PAHs 0.01 0.025 0.05 0.1 0.2",
			"SO2 0.2 0.5 1 2 5",
			"Styrene 10 25 50 100 200",
			"Toluene 20 50 100 200 400",
			"Xylenes 10 25 50 100 200"
		};

	private JLabel mGasLabel;
	private JComboBox mGasSelection;
	private JCheckBox mConcentrationCheckBox;
	private JTextField mCustomConcentration;

	public GasSelectionPanel() {
		super(new GridLayout(4, 1));
		super.setBorder(new EmptyBorder(10, 10, 10, 10));
		mGasLabel = new JLabel(GAS_LABEL);
		mGasLabel.setHorizontalAlignment(SwingConstants.CENTER);
		mGasSelection = new JComboBox(GAS_CONCENTRATIONS);
		mConcentrationCheckBox = new JCheckBox(CUSTOM_CONCENTRATION_LABEL);
		mConcentrationCheckBox.setHorizontalAlignment(SwingConstants.CENTER);
		mCustomConcentration = new JTextField();
		super.add(mGasLabel);
		super.add(mGasSelection);
		super.add(mConcentrationCheckBox);
		super.add(mCustomConcentration);
	}
	
	/**
	 * getGasSelection checks to see if the custom checkbox is selected. If it is, it
	 * parses the custom typed in concentration to see if it is in the proper format. If
	 * it's an invalid format it will return null.
	 * 
	 * If the custom checkbox isn't selected it returns the one selected in the drop down
	 * box.
	 * 
	 * @return The gas concentration String
	 */
	public String getGasSelection() {
		if (mConcentrationCheckBox.isSelected()) {
			String concentration = mCustomConcentration.getText();
			String[] parsedConcentrations = concentration.split(" ");
			for (int i = 1; i < parsedConcentrations.length; ++i) {
				try {
					double ppm = Double.parseDouble(parsedConcentrations[i]);
				} catch (NumberFormatException e) {
					return null;
				}
			}
			return concentration;
		}
		return GAS_CONCENTRATIONS[mGasSelection.getSelectedIndex()];
	}
}
