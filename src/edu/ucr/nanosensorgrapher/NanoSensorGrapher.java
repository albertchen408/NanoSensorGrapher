package edu.ucr.nanosensorgrapher;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * This program takes a text file or text file sequence input with 2 columns, Time (x) 
 * and Resistance (y). The program will the plot the data and output an image file of the
 * graph as well as a text file with 2 columns showing the maximum response for each
 * exposure.
 * 
 * @author Albert Chen
 *
 */
public class NanoSensorGrapher extends WindowAdapter
{
	private static final int FRAME_WIDTH = 600;
	private static final int FRAME_HEIGHT = 500;

	private static final boolean DEBUG_SHOW_FRAME = false;

	private static final String ERROR_FILE_SELECTION = "Error: No file selected";
	private static final String ERROR_INVALID_FILE = "Error: Invalid file(s): \n";
	private static final String ERROR_FILE_READ = "Error: Error parsing data file(s): \n";
	private static final String ERROR_GAS_SELECTION = "Error: Unable to parse custom " +
			"gas concentration";
	private static final String INFO_SELECTED_FILES = "Selected File(s): \n";
	private static final String INFO_PROCESSED_FILES = "Processed File(s): \n";

	private static final String START_BUTTON_LABEL = "Start";
	private static final String PROCESSING_BUTTON_LABEL = "Processing...";
	private static final String FRAME_TITLE = "Nano Sensor Data Grapher";
	
	private static final String IMAGE_FILETYPE = "png";
	private static final String RESPONSE_FILETYPE = "txt";

	private static JFrame mFrame = new JFrame(FRAME_TITLE);
	private static FileSelectionPanel mFilePanel = new FileSelectionPanel();
	private static GasSelectionPanel mGasPanel = new GasSelectionPanel();
	private static DataProcessingPanel mDataProcessingPanel = new DataProcessingPanel();
	private static JButton mStartButton = new JButton(START_BUTTON_LABEL);
	
	private static int mThreads;
	
	private static String mInvalidFiles;
	
	private static ActionListener mActionListener = new ActionListener() {
		@Override
		public void actionPerformed(ActionEvent event)
		{
			if (event.getSource() == mStartButton) {
				mStartButton.setEnabled(false);
				mStartButton.setText(PROCESSING_BUTTON_LABEL);
				processFiles();
			}
		}
	};

	private static void processFiles() {
		ArrayList<File> selectedFiles = mFilePanel.getSelectedFiles();
		if (selectedFiles == null) {
			JOptionPane.showMessageDialog(mFrame, ERROR_FILE_SELECTION);
			mStartButton.setEnabled(true);
			return;
		}
		String gasConcentrations = mGasPanel.getGasSelection();
		if (gasConcentrations == null || gasConcentrations.isEmpty()) {
			JOptionPane.showMessageDialog(mFrame, ERROR_GAS_SELECTION);
			mStartButton.setEnabled(true);
			return;
		}
		
		int outlierRemoval = mDataProcessingPanel.getOutlierRemoval();
		double outlierStdThreshold = mDataProcessingPanel.getOutlierStdThreshold();
		int smoothDataPeriod = mDataProcessingPanel.getSmoothDataPeriod();
		
		String fileList = "";
		for (int i = 0; i < selectedFiles.size(); ++i) {
			fileList = fileList + selectedFiles.get(i).getAbsolutePath() + "\n";
		}
		String selectedFileList = INFO_SELECTED_FILES + fileList;
		JOptionPane.showMessageDialog(mFrame, selectedFileList);
		
		for (int i = 0; i < selectedFiles.size(); ++i) {
			final File file = selectedFiles.get(i);
			final String finalGasConcentration = gasConcentrations;
			final String finalFileList = fileList;
			final int finalOutlierRemoval = outlierRemoval;
			final double finalOutlierStdThreshold = outlierStdThreshold;
			final int finalSmoothDataPeriod = smoothDataPeriod;
			/**
			 * Generate a new thread for each file since majority of time will be blocked
			 * based on disk I/O.
			 */
			Thread processingThread = new Thread(new Runnable()
			{
				@Override
				public void run() {
					try {
						generateGraph(file, finalGasConcentration, finalOutlierRemoval, finalOutlierStdThreshold, finalSmoothDataPeriod);
					} catch (IOException e) {
						JOptionPane.showMessageDialog(mFrame, ERROR_FILE_READ);
						e.printStackTrace();
					} catch (FileException e) {
						mInvalidFiles += file.getAbsolutePath() + "\n";
					} finally {
						mThreads--;
						if (mThreads == 0) {
							String processedFiles = INFO_PROCESSED_FILES + finalFileList;
							JOptionPane.showMessageDialog(mFrame, processedFiles);
							if (mInvalidFiles != null && !mInvalidFiles.isEmpty()) {
								mInvalidFiles = ERROR_INVALID_FILE + mInvalidFiles;
								JOptionPane.showMessageDialog(mFrame, mInvalidFiles);
								mInvalidFiles = "";
							}
							mStartButton.setText(START_BUTTON_LABEL);
							mStartButton.setEnabled(true);
						}
					}
				}
			});
			processingThread.start();
			mThreads++;
		}
	}
	
	public static void generateGraph(File file, String concentration, int outlierRemoval, double outlierStdThreshold, int smoothDataPeriod) throws IOException, FileException {
		/** Read data from files and store into ArrayLists **/
		BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
		ArrayList<Double> timeData = new ArrayList<Double>();
		ArrayList<Double> resistanceData = new ArrayList<Double>();
		String line;
		while((line = bufferedReader.readLine()) != null) {
			if (line != null) {
				line = line.trim();
				String[] values = line.split("\t");
				if (values.length != 2) {
					throw (new FileException());
				}
				timeData.add(Double.parseDouble(values[0]));
				resistanceData.add(Double.parseDouble(values[1]));
			}
		}
		bufferedReader.close();
		
		/** Generate output file names **/
		String absolutePath = file.getAbsolutePath();
		String folderPath = absolutePath.substring(0, absolutePath.lastIndexOf("\\") + 1);
		String dataFileName = file.getName();
		String fileName = dataFileName.substring(0, dataFileName.lastIndexOf('.') + 1);
		String imageFileName = fileName + IMAGE_FILETYPE;
		String responseFileName = fileName + RESPONSE_FILETYPE;
		String responseFilePath = folderPath + responseFileName;
		String imageFilePath = folderPath + imageFileName;

		/** Create graph **/
		JFrame graphFrame = new JFrame(dataFileName);
		GraphPanel graphPanel = 
				new GraphPanel(timeData, resistanceData, concentration, dataFileName, outlierRemoval, outlierStdThreshold, smoothDataPeriod);
		graphFrame.setBackground(Color.WHITE);
		graphFrame.getContentPane().add(graphPanel);
		if (DEBUG_SHOW_FRAME) {
			graphFrame.setVisible(true);
		}
		graphFrame.pack();

		/** Output graph as image file **/
		BufferedImage image = (BufferedImage)
				graphPanel.createImage(graphPanel.getWidth(), graphPanel.getHeight());
		Graphics2D g2d = image.createGraphics();
		graphPanel.paintComponent(g2d);
		try {
			ImageIO.write(image, IMAGE_FILETYPE, new File(imageFilePath));
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		/** Get max response data and output as txt file **/
		ArrayList<Double> concentrations = graphPanel.getConcentrations();
		ArrayList<Double> responses = graphPanel.getMaxResponses();
		File responseFile = new File(responseFilePath);
		BufferedWriter writer = new BufferedWriter(new FileWriter(responseFile));
		for (int i = 0; i < concentrations.size(); ++i) {
			String outputString = concentrations.get(i) + "\t" + responses.get(i);
			writer.newLine();
			writer.write(outputString);
		}
		writer.close();
	}
	
	public static void main(String[] args) {
		mFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		mFrame.setLayout(new GridLayout(4, 1));
		
		mStartButton.addActionListener(mActionListener);
		
		mFrame.getContentPane().add(mFilePanel);
		mFrame.getContentPane().add(mGasPanel);
		mFrame.getContentPane().add(mDataProcessingPanel);
		mFrame.getContentPane().add(mStartButton);

		mFrame.setSize(FRAME_WIDTH, FRAME_HEIGHT);
		mFrame.setVisible(true);
		mFrame.pack();
	}
}
