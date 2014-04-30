package edu.ucr.nanosensorgrapher;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.font.TextAttribute;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.text.AttributedString;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;

import javax.imageio.ImageIO;
import javax.swing.JPanel;

public class GraphPanel extends JPanel
{
	private static final int GRAPH_FONT_SIZE = 24;
	
	private static final int GRAPH_WIDTH = 1000;
	private static final int GRAPH_HEIGHT = 1000;
	private static final int GRAPH_AXIS_PADDING = 150;
	private static final int GRAPH_MINOR_AXIS_TICK_SIZE = 10;
	private static final int GRAPH_MAJOR_AXIS_TICK_SIZE = 15;
	private static final int GRAPH_TIME_AXIS_TICKS = 10;
	private static final int GRAPH_TIME_AXIS_TICK_ROUNDOFF = 5;
	private static final int GRAPH_CONCENTRATION_AXIS_TICKS = 4;
	private static final int GRAPH_RESISTANCE_AXIS_TICKS = 9;
	private static final int GRAPH_RESISTANCE_AXIS_TICK_ROUNDOFF = 10; 

	private static final String GRAPH_TIME_AXIS_LABEL = "Time (mins)";
	private static final String GRAPH_CONCENTRATION_AXIS_LABEL = " (ppm)";
	private static final String GRAPH_RESISTANCE_AXIS_LABEL = "\u0394R/R0 (%)";
	private static final String GRAPH_INITIAL_RESISTANCE_LABEL = "R0 = ";
	private static final String GRAPH_INITIAL_RESISTANCE_OMEGA_SYMBOL = "\u03A9";
	
	/** Percentage the axis should exceed the max value by **/
	private static final double AXIS_RESISTANCE_MARGIN = 1.2; 
	private static final double AXIS_CONCENTRATION_MARGIN = 1.25;

	/** Initial duration to get baseline (60 min) **/
	private static final double DURATION_BASELINE = 60.0;
	/** Initial duration to start calculating the initial resistance **/
	private static final double DURATION_RESISTANCE = 45.0;
	/** Exposure duration (15 min) **/
	private static final double DURATION_EXPOSURE = 15.0;
	/** Recovery duration after each exposure (20 min) **/
	private static final double DURATION_RECOVERY = 20.0;
	/** Additional recovery duration after last exposure/recovery set **/
	private static final double DURATION_END = 40.0;
	
	/** The maximum change between two points before it is considered an outlier **/
	private static final double MAX_DELTA_OUTLIER = 0.5;
	
	/** The time ticks in minutes **/
	private ArrayList<Double> mTime;
	/** The normalized resistance values **/
	private ArrayList<Double> mResistances;
	
	/** Contains the highest resistance delta at each exposure level. **/
	private ArrayList<Double> mMaxResponses;
	
	private double mInitialResistance;
	
	/** Axis label values **/
	private double mResistanceAxis;
	private double mMinResistance;
	private double mMaxResistance;
	private double mResistanceRange;
	private double mConcentrationAxis;
	private double mTimeAxis;
	
	private String mGasName;
	private String mFileName;
	private ArrayList<Double> mConcentrations;
	

	public GraphPanel(ArrayList<Double> timeData,
			ArrayList<Double> resistanceData,
			String concentration, String fileName) {
		super();
		super.setSize(GRAPH_WIDTH, GRAPH_HEIGHT);
		super.setPreferredSize(new Dimension(GRAPH_WIDTH, GRAPH_HEIGHT));
		super.setBackground(Color.WHITE);
		
		mTime = new ArrayList<Double>();
		mResistances = new ArrayList<Double>();
		mMaxResponses = new ArrayList<Double>();
		mConcentrations = new ArrayList<Double>();
		mFileName = fileName;
		
		removeDataOutliers(resistanceData);
		processData(timeData, resistanceData, concentration);
	}
	
	/**
	 * Converts resistance values to normalized resistances.
	 * Converts time values from seconds to minutes.
	 * Calculates the min and max normalized resistance for axis labels
	 * Calculates the min and max concentration for axis labels.
	 * Calculates the initial resistance (average resistance from 45 to 60 min)
	 * Normalizes the max axis values so the major tick marks are round numbers.
	 */
	private void processData(ArrayList<Double> times,
			ArrayList<Double> resistances,
			String concentration) {
		/** Parse concentrations **/
		String concentrations[] = concentration.split(" ");
		mGasName = concentrations[0];
		for (int i = 1; i < concentrations.length; ++i) {
			double ppm = Double.parseDouble(concentrations[i]);
			mConcentrations.add(ppm);
			/** 
			 * Add to max responses so set can be called when calculating max for each
			 * concentration period.
			 */
			mMaxResponses.add(0.0);
		}

		double numInitialResistanceValues = 0;
		for (int i = 0; i < resistances.size(); ++i) {
			/** Convert time to minutes **/
			double timeSec = times.get(i);
			double timeMin = timeSec / 60.0;
			mTime.add(timeMin);
			
			/** Calculate baseline resistance **/
			double resistance = resistances.get(i);
			if (timeMin > DURATION_RESISTANCE && timeMin < DURATION_BASELINE) {
				mInitialResistance += resistance;
				numInitialResistanceValues++;
			}
		}
		mInitialResistance /= numInitialResistanceValues;
		
		for (int i = 0; i < resistances.size(); ++i) {
			/** Calculate normalized resistance **/
			double resistance = resistances.get(i);
			double normalizedResistance = (resistance - mInitialResistance) / 
					mInitialResistance * 100;
			mResistances.add(normalizedResistance);
			
			/** Get the most positive or most negative value **/
			if (normalizedResistance < mMinResistance) {
				mMinResistance = normalizedResistance;
			}
			if (normalizedResistance > mMaxResistance) {
				mMaxResistance = normalizedResistance;
			}
			if (Math.abs(mResistanceAxis) < Math.abs(normalizedResistance)) {
				mResistanceAxis = normalizedResistance;
			}

			/** Calculate the max response for each exposure period **/
			double time = mTime.get(i) - DURATION_BASELINE;
			double periodMax = 0.0;
			for (int j = 0; j < mConcentrations.size(); ++j) {
				double exposureStart = j * DURATION_EXPOSURE + j * DURATION_RECOVERY;
				double exposureEnd = exposureStart + DURATION_RECOVERY;
				if (time > exposureStart && time < exposureEnd && 
						Math.abs(periodMax) < Math.abs(normalizedResistance)) {
						periodMax = normalizedResistance;
						mMaxResponses.set(j, periodMax);
				}
			}
		}
		
		if (mMaxResistance > 0) {
			mMaxResistance *= AXIS_RESISTANCE_MARGIN;
		} else {
			mMaxResistance += mMaxResistance * (AXIS_RESISTANCE_MARGIN - 1);
		}
		if (mMinResistance > 0) {
			mMinResistance -= mMinResistance * (AXIS_RESISTANCE_MARGIN - 1);
		} else {
			mMinResistance *= AXIS_RESISTANCE_MARGIN;
		}
		
		mResistanceRange = mMaxResistance - mMinResistance;
		boolean smallResistance = false;
		if (mResistanceRange < 10) {
			mMaxResistance *= 1000;
			mMinResistance *= 1000;
			mResistanceRange *= 1000;
			smallResistance = true;
		}
		// Round resistance range to nearest multiple of 10
		mResistanceRange = Math.floor(mResistanceRange);
		int resistanceStep = (int) (mResistanceRange / GRAPH_RESISTANCE_AXIS_TICKS);
		if (resistanceStep % GRAPH_RESISTANCE_AXIS_TICK_ROUNDOFF != 0) {
			resistanceStep += GRAPH_RESISTANCE_AXIS_TICK_ROUNDOFF -
					(resistanceStep % GRAPH_RESISTANCE_AXIS_TICK_ROUNDOFF);
		}
		mMaxResistance = Math.floor(mMaxResistance);
		if (mMaxResistance % 5 != 0) {
			mMaxResistance += 5 - (mMaxResistance % 5);
		}
		mMinResistance = mMaxResistance - resistanceStep * GRAPH_RESISTANCE_AXIS_TICKS;

		if (smallResistance) {
			mMaxResistance /= 1000;
			mMinResistance /= 1000;
		}
		mResistanceRange = mMaxResistance - mMinResistance;


		/**
		mResistanceAxis = Math.floor(mResistanceAxis);
		int resistanceStep = (int) (mResistanceAxis / GRAPH_RESISTANCE_AXIS_TICKS);
		if (resistanceStep % GRAPH_RESISTANCE_AXIS_TICK_ROUNDOFF != 0) {
			resistanceStep += GRAPH_RESISTANCE_AXIS_TICK_ROUNDOFF -
					(resistanceStep % GRAPH_RESISTANCE_AXIS_TICK_ROUNDOFF);
		}
		mResistanceAxis = resistanceStep * (GRAPH_RESISTANCE_AXIS_TICKS);
		*/
		
		mConcentrationAxis = mConcentrations.get(mConcentrations.size() - 1) *
				AXIS_CONCENTRATION_MARGIN; 

		mTimeAxis = DURATION_BASELINE + mConcentrations.size() * 
				(DURATION_EXPOSURE + DURATION_RECOVERY) + DURATION_END;

		/** Normalize the time step to round up to the nearest 5 **/
		int timeStep = (int) mTimeAxis / (GRAPH_TIME_AXIS_TICKS);
		if (timeStep % GRAPH_TIME_AXIS_TICK_ROUNDOFF != 0) {
			mTimeAxis += (GRAPH_TIME_AXIS_TICK_ROUNDOFF -
					(timeStep % GRAPH_TIME_AXIS_TICK_ROUNDOFF)) * GRAPH_TIME_AXIS_TICKS;
		}
	}
	
	/**
	 * Removes outliers from the resistance data. Outliers are determined by if the change
	 * between two data points is greater than 50%. This needs to be called before
	 * normalizing and determining the maxes for the data.
	 * 
	 * This function modifies the member variable for the resistance values 
	 * and does not return a value.
	 * 
	 * @param resistances ArrayList<Double> of resistance values
	 */
	private void removeDataOutliers(ArrayList<Double> resistances) {
		double previousResistance = 0;
		for (int i = 0; i < mResistances.size(); ++i) {
			double resistance = mResistances.get(i);
			if (previousResistance != 0) {
				double resistanceChange = Math.abs((resistance - previousResistance) / 
						previousResistance);
				if (resistanceChange > MAX_DELTA_OUTLIER) {
					resistances.set(i, previousResistance);
				}
			}
			previousResistance = resistance;
		}
	}
	
	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		g.clearRect(0, 0, getWidth(), getHeight());
		drawResistanceData(g);
		drawExposureData(g);
		drawAxis(g);
	}
	
	/**
	 * Draws the time axis and labels
	 * @param g
	 */
	private void drawTimeAxis(Graphics g) {
		g.setColor(Color.BLACK);
		/** Time axis bottom x-axis **/
		g.drawLine(GRAPH_AXIS_PADDING,
				GRAPH_HEIGHT - GRAPH_AXIS_PADDING,
				GRAPH_WIDTH - GRAPH_AXIS_PADDING,
				GRAPH_HEIGHT - GRAPH_AXIS_PADDING);
		int graphWidth = GRAPH_WIDTH - 2 * GRAPH_AXIS_PADDING;
		int tickSpacing = graphWidth / (GRAPH_TIME_AXIS_TICKS + 1);
		int timeSpacing = (int) (mTimeAxis / GRAPH_TIME_AXIS_TICKS);

		g.setFont(new Font("Arial", Font.PLAIN, GRAPH_FONT_SIZE));
		FontMetrics fontMetric = g.getFontMetrics();
		int fontHeight = fontMetric.getHeight();

		/** Time axis tick marks and labels **/
		for (int i = 0; i < GRAPH_TIME_AXIS_TICKS + 1; ++i) {
			int startX = GRAPH_AXIS_PADDING + i * tickSpacing;
			int stopX = startX;
			int startY = GRAPH_HEIGHT - GRAPH_AXIS_PADDING;
			int stopY = startY - GRAPH_MINOR_AXIS_TICK_SIZE;
			if (i % 2 == 0) {
				stopY = startY - GRAPH_MAJOR_AXIS_TICK_SIZE;
				int time = (int) timeSpacing * i;
				String timeString = "" + time;
				int stringWidth = fontMetric.stringWidth(timeString);
				g.drawString(timeString,
						startX - stringWidth / 2,
						GRAPH_HEIGHT - GRAPH_AXIS_PADDING + fontHeight);
			}
			g.drawLine(startX, startY, stopX, stopY);
		}
		int stringWidth = fontMetric.stringWidth(GRAPH_TIME_AXIS_LABEL);
		g.drawString(GRAPH_TIME_AXIS_LABEL,
				(GRAPH_WIDTH - stringWidth) / 2,
				GRAPH_HEIGHT - GRAPH_AXIS_PADDING + 2 * fontHeight); 
	}
	
	/**
	 * Draws the concentration axis and labels
	 * @param g
	 */
	private void drawConcentrationAxis(Graphics g) {
		g.setColor(Color.BLACK);
		/** Concentration axis right side y-axis**/
		g.drawLine(GRAPH_WIDTH - GRAPH_AXIS_PADDING,
				GRAPH_AXIS_PADDING,
				GRAPH_WIDTH - GRAPH_AXIS_PADDING,
				GRAPH_HEIGHT - GRAPH_AXIS_PADDING);
		int graphHeight = GRAPH_HEIGHT - 2 * GRAPH_AXIS_PADDING;
		int tickSpacing = graphHeight / (GRAPH_CONCENTRATION_AXIS_TICKS + 1);
		double concentrationSpacing = mConcentrationAxis / (GRAPH_CONCENTRATION_AXIS_TICKS + 1);

		g.setFont(new Font("Arial", Font.PLAIN, GRAPH_FONT_SIZE));
		FontMetrics fontMetric = g.getFontMetrics();
		int fontHeight = fontMetric.getHeight();

		for (int i = 0; i < GRAPH_CONCENTRATION_AXIS_TICKS + 1; ++i) {
			int startX = GRAPH_WIDTH - GRAPH_AXIS_PADDING;
			int stopX = startX - GRAPH_MINOR_AXIS_TICK_SIZE;
			int startY = GRAPH_HEIGHT - GRAPH_AXIS_PADDING - i * tickSpacing;
			int stopY = startY;
			if (i % 2 == 0) {
				stopX = startX - GRAPH_MAJOR_AXIS_TICK_SIZE;
				double concentration = concentrationSpacing * i;
				String concentrationString = "" + concentration;
				g.drawString(concentrationString,
						(int) (GRAPH_WIDTH - GRAPH_AXIS_PADDING + fontHeight / 2.0),
						(int) (startY + fontHeight / 3.0));
			}
			g.drawLine(startX, startY, stopX, stopY);
		}
		
		Graphics2D g2d = (Graphics2D) g;
		AffineTransform originalTransform = g2d.getTransform();
		g2d.setColor(Color.BLACK);
		g2d.rotate(Math.toRadians(-90), GRAPH_HEIGHT / 2, GRAPH_WIDTH / 2);
		String concentrationAxisLabel = mGasName + GRAPH_CONCENTRATION_AXIS_LABEL;
		int stringWidth = fontMetric.stringWidth(concentrationAxisLabel);
		g2d.drawString(concentrationAxisLabel,
				(GRAPH_HEIGHT - stringWidth) / 2,
				GRAPH_WIDTH - GRAPH_AXIS_PADDING + 3 * fontHeight);
		g2d.setTransform(originalTransform);
	}
	
	private void drawResistanceAxis(Graphics g) {
		/** Resistance axis left side y-axis **/
		g.drawLine(GRAPH_AXIS_PADDING,
				GRAPH_AXIS_PADDING,
				GRAPH_AXIS_PADDING,
				GRAPH_HEIGHT - GRAPH_AXIS_PADDING);
				int graphHeight = GRAPH_HEIGHT - 2 * GRAPH_AXIS_PADDING;
		int tickSpacing = graphHeight / (GRAPH_RESISTANCE_AXIS_TICKS);
		mResistanceRange = mMaxResistance - mMinResistance;
		double resistanceSpacing = mResistanceRange / (GRAPH_RESISTANCE_AXIS_TICKS);

		g.setFont(new Font("Arial", Font.PLAIN, GRAPH_FONT_SIZE));
		FontMetrics fontMetric = g.getFontMetrics();
		int fontHeight = fontMetric.getHeight();

		for (int i = 0; i < GRAPH_RESISTANCE_AXIS_TICKS; ++i) {
			int startX = GRAPH_AXIS_PADDING;
			int stopX = startX + GRAPH_MINOR_AXIS_TICK_SIZE;
			int startY = GRAPH_HEIGHT - GRAPH_AXIS_PADDING - i * tickSpacing;
			int stopY = startY;
			if (i % 2 == 0) {
				stopX = startX + GRAPH_MAJOR_AXIS_TICK_SIZE;
				double resistance = mMinResistance + resistanceSpacing * i;
				DecimalFormat df = new DecimalFormat("0.0");
				if (resistanceSpacing < 1) {
					df = new DecimalFormat("0.00");
				}
				String resistanceString = df.format(resistance);
				int stringWidth = fontMetric.stringWidth(resistanceString);
				g.drawString(resistanceString,
						(int) (GRAPH_AXIS_PADDING - stringWidth - fontHeight / 2.0),
						(int) (startY + fontHeight / 3.0));
			}
			g.drawLine(startX, startY, stopX, stopY);
		}
		
		Graphics2D g2d = (Graphics2D) g;
		AffineTransform originalTransform = g2d.getTransform();
		g2d.setColor(Color.BLACK);
		g2d.rotate(Math.toRadians(-90), GRAPH_HEIGHT / 2, GRAPH_WIDTH / 2);
		AttributedString as = new AttributedString(GRAPH_RESISTANCE_AXIS_LABEL);
		as.addAttribute(TextAttribute.SUPERSCRIPT, TextAttribute.SUPERSCRIPT_SUB, 4, 6);
		as.addAttribute(TextAttribute.SIZE, GRAPH_FONT_SIZE);
		int stringWidth = fontMetric.stringWidth(GRAPH_RESISTANCE_AXIS_LABEL);
		g2d.drawString(as.getIterator(),
				(GRAPH_HEIGHT - stringWidth) / 2,
				GRAPH_AXIS_PADDING - 3 * fontHeight);
		g2d.setTransform(originalTransform);
	}
	
	/**
	 * Draws the axis, labels and initial resistance on the graph.
	 * @param g
	 */
	private void drawAxis(Graphics g) {
		drawTimeAxis(g);
		drawConcentrationAxis(g);
		drawResistanceAxis(g);
		
		/** Top bar **/
		g.drawLine(GRAPH_AXIS_PADDING,
				GRAPH_AXIS_PADDING,
				GRAPH_WIDTH - GRAPH_AXIS_PADDING,
				GRAPH_AXIS_PADDING);
		g.setFont(new Font("Arial", Font.PLAIN, GRAPH_FONT_SIZE));
		FontMetrics fontMetric = g.getFontMetrics();
		int fontHeight = fontMetric.getHeight();
		g.drawString(mFileName, GRAPH_AXIS_PADDING, GRAPH_AXIS_PADDING - fontHeight / 2);
		DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
		Calendar calendar = Calendar.getInstance();
		String date = dateFormat.format(calendar.getTime());
		int stringWidth = fontMetric.stringWidth(date);
		g.drawString(date,
				GRAPH_WIDTH - GRAPH_AXIS_PADDING - stringWidth,
				GRAPH_AXIS_PADDING - fontHeight / 2);
	}
	
	/**
	 * Draws the resistance data and initial resistance label
	 * @param g
	 */
	private void drawResistanceData(Graphics g) {
		g.setColor(Color.BLACK);
		g.setFont(new Font("Arial", Font.PLAIN, GRAPH_FONT_SIZE));
		FontMetrics fontMetric = g.getFontMetrics();
		int fontHeight = fontMetric.getHeight();
		DecimalFormat df = new DecimalFormat("0.00");

		String initialResistance = GRAPH_INITIAL_RESISTANCE_LABEL;
		if (mInitialResistance > 1000) {
			initialResistance += df.format(mInitialResistance / 1000) + "k" +
					GRAPH_INITIAL_RESISTANCE_OMEGA_SYMBOL;
		} else {
			initialResistance += df.format(mInitialResistance) +
					GRAPH_INITIAL_RESISTANCE_OMEGA_SYMBOL;
		}

		AttributedString as = new AttributedString(initialResistance);
		as.addAttribute(TextAttribute.SUPERSCRIPT, TextAttribute.SUPERSCRIPT_SUB, 1, 2);
		as.addAttribute(TextAttribute.SIZE, GRAPH_FONT_SIZE);
		if (mResistanceAxis < 0) {
			g.drawString(as.getIterator(),
					(int) (GRAPH_AXIS_PADDING + fontHeight * 2),
					(int) (GRAPH_HEIGHT - GRAPH_AXIS_PADDING - fontHeight * 2));
		} else {
			g.drawString(as.getIterator(),
					(int) (GRAPH_AXIS_PADDING + fontHeight * 2),
					(int) (GRAPH_AXIS_PADDING + fontHeight * 2));
		}
		for (int i = 1; i < mResistances.size(); ++i) {
			double startResistance = mResistances.get(i - 1);
			double endResistance = mResistances.get(i);
			double startTime = mTime.get(i - 1);
			double endTime = mTime.get(i);
			if (startTime > mTimeAxis) {
				break;
			}
			double startX = (startTime / mTimeAxis) *
						(GRAPH_WIDTH - 2 * GRAPH_AXIS_PADDING) + GRAPH_AXIS_PADDING;
			double endX = (endTime / mTimeAxis) *
						(GRAPH_WIDTH - 2 * GRAPH_AXIS_PADDING) + GRAPH_AXIS_PADDING;
			
			double startY = 0.0;
			double endY = 0.0;
			
			startY = (mMaxResistance - startResistance) / mResistanceRange 
					* (GRAPH_HEIGHT - 2 * GRAPH_AXIS_PADDING) + GRAPH_AXIS_PADDING;
			endY = (mMaxResistance - endResistance) / mResistanceRange 
					* (GRAPH_HEIGHT - 2 * GRAPH_AXIS_PADDING) + GRAPH_AXIS_PADDING;
			
			/**
			if (mResistanceAxis < 0) {
				startY = (startResistance / mResistanceAxis) *
							(GRAPH_HEIGHT - 2 * GRAPH_AXIS_PADDING) + GRAPH_AXIS_PADDING;
				endY = (endResistance / mResistanceAxis) *
							(GRAPH_HEIGHT - 2 * GRAPH_AXIS_PADDING) + GRAPH_AXIS_PADDING;
			} else {
				startY = (GRAPH_HEIGHT - GRAPH_AXIS_PADDING) -
							(startResistance / mResistanceAxis) *
							(GRAPH_HEIGHT - 2 * GRAPH_AXIS_PADDING);
				endY = (GRAPH_HEIGHT - GRAPH_AXIS_PADDING) - 
							(endResistance / mResistanceAxis) *
							(GRAPH_HEIGHT - 2 * GRAPH_AXIS_PADDING);
				
			}
			*/
			g.drawLine((int) startX, (int) startY, (int) endX, (int) endY);
			/**
			if ((mResistanceAxis < 0 && startResistance < 0 && endResistance < 0) ||
					(mResistanceAxis > 0 && startResistance > 0 && endResistance > 0)) {
			}
			*/
		}
	}
	
	/**
	 * Draws the bars for exposure
	 * @param g
	 */
	private void drawExposureData(Graphics g) {
		for (int i = 0; i < mConcentrations.size(); ++i) {
			double exposureStart = DURATION_BASELINE + i * DURATION_EXPOSURE + 
					i * DURATION_RECOVERY;
			double exposureEnd = exposureStart + DURATION_RECOVERY;
			double concentration = mConcentrations.get(i);

			double startX = (exposureStart / mTimeAxis) *
					(GRAPH_WIDTH - 2 * GRAPH_AXIS_PADDING) + GRAPH_AXIS_PADDING;
			double endX = (exposureEnd / mTimeAxis) *
					(GRAPH_WIDTH - 2 * GRAPH_AXIS_PADDING) + GRAPH_AXIS_PADDING;
			
			double startY = (GRAPH_HEIGHT - GRAPH_AXIS_PADDING);
			double endY = (GRAPH_HEIGHT - GRAPH_AXIS_PADDING) - 
						(concentration / mConcentrationAxis) *
						(GRAPH_HEIGHT - 2 * GRAPH_AXIS_PADDING);

			/** Left bar line **/
			g.drawLine((int) startX, (int) startY, (int) startX, (int) endY);
			/** Top bar line **/
			g.drawLine((int) startX, (int) endY, (int) endX, (int) endY);
			/** Right bar line **/
			g.drawLine((int) endX, (int) startY, (int) endX, (int) endY);
		}
	}
	
	public ArrayList<Double> getConcentrations() {
		return mConcentrations;
	}
	
	public ArrayList<Double> getMaxResponses() {
		return mMaxResponses;
	}

}
