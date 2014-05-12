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
	private static final double MAX_DELTA_OUTLIER = 0.2;
	
	/** The maximum number of standard deviations before a value is considered an outlier. */
	private static final double MAX_STD_OUTLIER_BASELINE = 2.0;
	private static final double MAX_STD_OUTLIER_EXPOSURE = 2.5;
	private static final double MAX_STD_OUTLIER_RECOVERY = 2.5;
	private static final double MAX_STD_OUTLIER_END = 2.0;
	
	/** The time ticks in minutes **/
	private ArrayList<Double> mTime;
	/** The normalized resistance values dR/R in percent **/
	private ArrayList<Double> mNormalizedResistances;
	/** Contains the highest resistance delta at each exposure level. **/
	private ArrayList<Double> mMaxResponses;
	/** Contains the concentration values in ppm */
	private ArrayList<Double> mConcentrations;

	/** The initial resistance as calculated by the average resistance from 45 - 60 min */
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
	

	public GraphPanel(ArrayList<Double> timeData,
			ArrayList<Double> resistanceData,
			String concentration, String fileName, int removeOutliers, double stdThreshold, int smoothDataPeriod) {
		super();
		super.setSize(GRAPH_WIDTH, GRAPH_HEIGHT);
		super.setPreferredSize(new Dimension(GRAPH_WIDTH, GRAPH_HEIGHT));
		super.setBackground(Color.WHITE);
		
		mTime = new ArrayList<Double>();
		mNormalizedResistances = new ArrayList<Double>();
		mMaxResponses = new ArrayList<Double>();
		mConcentrations = new ArrayList<Double>();
		mMinResistance = Double.POSITIVE_INFINITY;
		mMaxResistance = Double.NEGATIVE_INFINITY;
		mFileName = fileName;
		
		processData(timeData, resistanceData, concentration);
		for (int i = 0; i < removeOutliers; ++i) {
			removeDataOutliers(stdThreshold);
		}
		if (smoothDataPeriod > 0) {
			smoothData(smoothDataPeriod);
		}
		calculateAxisValues();
	}
	
	/**
	 * Applys a SMA algorithm on the passed in {@link ArrayList<Double>}
	 * 
	 * @param resistances The data to apply the SMA algorithm on.
	 */
	private void smoothData(int smaPeriod) {
		for (int i = smaPeriod - 1; i < mNormalizedResistances.size(); ++i) {
			double averageData = 0;
			for (int j = i - (smaPeriod - 1); j <= i; ++j) {
				averageData += mNormalizedResistances.get(j);
			}
			mNormalizedResistances.set(i, averageData / smaPeriod);
		}
	}
	
	/**
	 * Processes the data into the format required. 
	 *	Parses the gas name and concentrations.
	 * 	Converts time to minutes. 
	 *	Converts resistances to normalized resistances.
	 * @param times
	 * @param resistances
	 * @param concentration
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
			/** Initialize the max Responses to 0. The values are then compared with the abs(delta) */
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
		
		/** Normalize resistances */
		for (int i = 0; i < resistances.size(); ++i) {
			/** Calculate normalized resistance **/
			double resistance = resistances.get(i);
			double normalizedResistance = (resistance - mInitialResistance) / 
					mInitialResistance * 100;
			mNormalizedResistances.add(normalizedResistance);
		}
	}
		
	/**
	 * Gets the values of the Axis labels and normalizes (rounds off) to the nearest value.
	 */
	private void calculateAxisValues() {
		for (int i = 0; i < mNormalizedResistances.size(); ++i) {
			double normalizedResistance = mNormalizedResistances.get(i);
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
		
		mResistanceRange = mMaxResistance - mMinResistance;
		mMaxResistance += mResistanceRange * 0.2;
		mMinResistance -= mResistanceRange * 0.2;
		
		mResistanceRange = mMaxResistance - mMinResistance;
		boolean smallResistance = false;
		if (mResistanceRange < 100) {
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
	
	private void removeDataOutliers(double stdThreshold) {
		/** Remove outliers from baseline */
		int startIndex = 0;
		int stopIndex = findTimeIndex(DURATION_BASELINE, 0, mTime.size());
		removePeriodOutliers(startIndex, stopIndex, stdThreshold);
		for (int i = 0; i < mConcentrations.size(); ++i) {
			/** Remove outliers from exposure */
			startIndex = findTimeIndex(DURATION_BASELINE + DURATION_EXPOSURE * i + DURATION_RECOVERY * i, 0, mTime.size());
			stopIndex = findTimeIndex(DURATION_BASELINE + DURATION_EXPOSURE * (i + 1) + DURATION_RECOVERY * i, 0, mTime.size());
			removePeriodOutliers(startIndex, stopIndex, stdThreshold);
			/** Remove outliers from recovery */
			startIndex = stopIndex;
			stopIndex = findTimeIndex(DURATION_BASELINE + DURATION_EXPOSURE * (i + 1) + DURATION_RECOVERY * (i + 1), 0, mTime.size());
			removePeriodOutliers(startIndex, stopIndex, stdThreshold);
		}
		/** Remove outliers from end */
		startIndex = stopIndex;
		stopIndex = mTime.size() - 1;
		removePeriodOutliers(startIndex, stopIndex, stdThreshold);
	}
	
	private static double findMedian(ArrayList<Double> values, int index) {
		if (values.size() > 0 && values != null) {
			double pivot = values.get(values.size() / 2);
			ArrayList<Double> smallerThan = new ArrayList<Double>();
			ArrayList<Double> greaterThan = new ArrayList<Double>();
			for (int i = 0; i < values.size(); ++i) {
				double value = values.get(i);
				if (value < pivot) {
					smallerThan.add(value);
				} else if (value > pivot) {
					greaterThan.add(value);
				}
			}
			if (index <= smallerThan.size()) {
				return findMedian(smallerThan, index);
			} else if (index > values.size() - greaterThan.size()) {
				return findMedian(greaterThan, index - (values.size() - greaterThan.size()));
			} else {
				return pivot;
			}
		}
		return Double.NaN;
	}
		
	/**
	 * Removes the outliers from the normalized data for the given start and stop time.
	 * @param startTime The starting time in minutes.
	 * @param stopTime The stopping time in minutes.
	 * @param stdThreshold The number of standard deviations allowed before considering it an outlier.
	 */
	private void removePeriodOutliers(int startIndex, int stopIndex, double stdThreshold) {
		int range = stopIndex - startIndex;
		ArrayList<Double> periodData = new ArrayList<Double>();
		double periodVariance = 0;
		periodData.addAll(mNormalizedResistances.subList(startIndex, stopIndex + 1));
		double periodMedian = findMedian(periodData, periodData.size() / 2);
		for (int i = startIndex; i <= stopIndex; ++i) {
			double normalizedResistance = mNormalizedResistances.get(i);
			periodVariance += Math.pow(normalizedResistance - periodMedian, 2);
		}
		periodVariance /= (range - 1);
		double periodStd = Math.sqrt(periodVariance);
		
		for (int i = startIndex; i < stopIndex && i < mNormalizedResistances.size() - 1; ++i) {
			double normalizedResistance = mNormalizedResistances.get(i);
			double zScore = Math.abs((normalizedResistance - periodMedian) / periodStd);
			if (zScore > stdThreshold) {
				double previousResistance = 0;
				double nextResistance = mNormalizedResistances.get(i + 1);
				if (i > 0) {
					previousResistance = mNormalizedResistances.get(i - 1);
				} else {
					previousResistance = mNormalizedResistances.get(i + 1);
				}
				/**
				periodAverage *= range;
				periodAverage -= normalizedResistance;
				periodAverage += previousResistance;
				periodAverage /= range;
				periodSquaredAverage *= range;
				periodSquaredAverage -= normalizedResistance * normalizedResistance;
				periodSquaredAverage += previousResistance * previousResistance;
				periodSquaredAverage /= range;
				periodVariance = periodSquaredAverage - periodAverage;
				periodStd = Math.sqrt(periodVariance);
				*/
				mNormalizedResistances.set(i, previousResistance);
			}
		}
	}
	
	/**
	 * Performs a binary search on the time {@link ArrayList<Double>} find the closest 
	 * start and stop time. Can't use {@link ArrayList#indexOf(Object)} since it might not
	 * be an exact minute at the poll time.
	 * 
	 * @param time The time in minutes.
	 */
	private int findTimeIndex(double timeToFind, int low, int high) {
		int length = mTime.size() - 1;
		if (length > 0) {
			if (timeToFind < mTime.get(0)) {
				return 0;
			} else if (timeToFind > mTime.get(length)) {
				return length;
			}
		}
		int mid = (low + high) / 2;
		double time = mTime.get(mid);
		if (time == timeToFind) {
			return mid;
		} else if (low > high) {
			return mid;
		} else if (time > timeToFind) {
			return findTimeIndex(timeToFind, low, mid - 1);
		} else if (time < timeToFind) {
			return findTimeIndex(timeToFind, mid + 1, high);
		}
		return mid;
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
		for (int i = 1; i < mNormalizedResistances.size(); ++i) {
			double startResistance = mNormalizedResistances.get(i - 1);
			double endResistance = mNormalizedResistances.get(i);
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
			g.drawLine((int) startX, (int) startY, (int) endX, (int) endY);
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
			double exposureEnd = exposureStart + DURATION_EXPOSURE;
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
