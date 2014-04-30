--------------------------------------------------------------------------------
Selecting Files
--------------------------------------------------------------------------------
1) Individual Files
	- Click "Open File" and select the file to process.

2) Sequence of Files
	- Make sure the filenames end with a dash followed by a number. 
		Processes up to 1000
	- Click "Open File" and select the first file in the sequence and check "Load file sequence"
		For example if "file-10.ext" is selected it will process from 10 to 1000.

3) Selecting a Folder
	- Select a file in the folder to process and check "Load all files in folder" 
		or check "Load all files in folder" and select a folder.

--------------------------------------------------------------------------------
Selecting Gas
--------------------------------------------------------------------------------
1) Preset Gas
	- Preset gasses and exposures can be selected from the dropdown list. 
		The gas selection is the name followed by each exposure concentration; space delimited.
		This assumes the standard 60 min baseline followed by 20/15 exposure to analyte then air followed by 45 min air.
2) Custom Gas
	- Custom gas names and concentrations can be entered by checking "Custom gas or concentration"
		Enter the gas name followed by a space delimited list of exposures in ppm.
		This assumes the standard 60 min baseline followed by 20/15 exposure to analyte then air followed by 45 min air.
		Do NOT use spaces in the gas name. 

--------------------------------------------------------------------------------
Processing
--------------------------------------------------------------------------------
1) Initial Resistance
	- Initial resistance is determined by the average resistance from 45-60 min.

2) Removing outliers
	- Outliers are automatically removed. If there is a sudden spike of more than 50% between two data points it is removed.
		For example if x_5 is 10 and x_6 is 100, x_6 will be replaced with x_5

3) File Format
	- Each line should be time in seconds followed by resistance (tab delimited).

--------------------------------------------------------------------------------
Output
--------------------------------------------------------------------------------
1) PNG image output of the plot.
	- A plot with the processed deltaR/R vs time will be output
	- The concentration axis is on the right and the concentration vs time will be super imposed.
	- File name processed and time processed will be shown at the top

2) A text file will be output with the concentration and max delta R at each exposure.
