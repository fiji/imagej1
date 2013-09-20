package ij.plugin;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.measure.Calibration;

/** This plugin animates stacks. */
public class Animator implements PlugIn {

	private static double animationRate = Prefs.getDouble(Prefs.FPS, 7.0);
	private static int firstFrame=0, lastFrame=0;
	private ImagePlus imp;
	private StackWindow swin;
	private int slice;
	private int nSlices;	
	/** Set 'arg' to "set" to display a dialog that allows the user to specify the
		animation speed. Set it to "start" to start animating the current stack.
		Set it to "stop" to stop animation. Set it to "next" or "previous"
		to stop any animation and display the next or previous frame. 
	*/
	public void run(String arg) {
		imp = WindowManager.getCurrentImage();
		if (imp==null)
			{IJ.noImage(); return;}
    	nSlices = imp.getStackSize();
		if (nSlices<2)
			{IJ.error("Stack required."); return;}
		ImageWindow win = imp.getWindow();
		if (win==null || !(win instanceof StackWindow)) {
			if (arg.equals("next"))
				imp.setSlice(imp.getCurrentSlice()+1);
			else if (arg.equals("previous"))
				imp.setSlice(imp.getCurrentSlice()-1);
			if (win!=null) imp.updateStatusbarValue();
			return;
		}
		swin = (StackWindow)win;
		ImageStack stack = imp.getStack();
		slice = imp.getCurrentSlice();
		IJ.register(Animator.class);
		
		if (arg.equals("options")) {
			doOptions();
			return;
		}
			
		if (arg.equals("start")) {
			startAnimation();
			return;
		}
		//if (swin.getAnimate()) // "stop", "next" and "previous" all stop animation
		//	stopAnimation();		//AJ- commented this out in order to continue animating while changing z

		if (arg.equals("stop")) {
			stopAnimation();  //AJ- added this so you can stop animation
			return;
		}
			
		if (arg.equals("next")) {
			nextSlice();
			return;
		}
		
		if (arg.equals("previous")) {
			previousSlice();
			return;
		}
		
		if (arg.equals("set")) {
			setSlice();
			return;
		}
	}

	void stopAnimation() {
		swin.setAnimate(false);
		IJ.wait(500+(int)(1000.0/animationRate));
		imp.unlock(); 
	}

	void startAnimation() {
		int first=firstFrame, last=lastFrame;
		if (first<1 || first>nSlices || last<1 || last>nSlices)
			{first=1; last=nSlices;}
		if (swin.getAnimate())
			{stopAnimation(); return;}
		imp.unlock(); // so users can adjust brightness/contrast/threshold
		swin.setAnimate(true);
		long time, nextTime=System.currentTimeMillis();
		Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
		int sliceIncrement = 1;
		Calibration cal = imp.getCalibration();
		if (cal.fps!=0.0)
			animationRate = cal.fps;
		if (animationRate<0.1)
			animationRate = 1.0;
		int frames = imp.getNFrames();
		int slices = imp.getNSlices();
		
		if (imp.isDisplayedHyperStack() && frames>1) {
			int frame = imp.getFrame();
			first = 1;
			last = frames;
			while (swin.getAnimate()) {
				time = System.currentTimeMillis();
				if (time<nextTime)
					IJ.wait((int)(nextTime-time));
				else
					Thread.yield();
				nextTime += (long)(1000.0/animationRate);
				frame += sliceIncrement;
				if (frame<first) {
					frame = first+1;
					sliceIncrement = 1;
				}
				if (frame>last) {
					if (cal.loop) {
						frame = last-1;
						sliceIncrement = -1;
					} else {
						frame = first;
						sliceIncrement = 1;
					}
				}
				imp.setPosition(imp.getChannel(), imp.getSlice(), frame);
			}
			return;
		}

		if (imp.isDisplayedHyperStack() && slices>1) {
			slice = imp.getSlice();
			first = 1;
			last = slices;
			while (swin.getAnimate()) {
				time = System.currentTimeMillis();
				if (time<nextTime)
					IJ.wait((int)(nextTime-time));
				else
					Thread.yield();
				nextTime += (long)(1000.0/animationRate);
				slice += sliceIncrement;
				if (slice<first) {
					slice = first+1;
					sliceIncrement = 1;
				}
				if (slice>last) {
					if (cal.loop) {
						slice = last-1;
						sliceIncrement = -1;
					} else {
						slice = first;
						sliceIncrement = 1;
					}
				}
				imp.setPosition(imp.getChannel(), slice, imp.getFrame());
			}
			return;
		}
		
		long startTime=System.currentTimeMillis();
		int count = 0;
		double fps = 0.0;
		while (swin.getAnimate()) {
			time = System.currentTimeMillis();
			count++;
			if (time>startTime+1000L) {
				startTime=System.currentTimeMillis();
				fps=count;
				count=0;
			}
			IJ.showStatus((int)(fps+0.5) + " fps");
			if (time<nextTime)
				IJ.wait((int)(nextTime-time));
			else
				Thread.yield();
			nextTime += (long)(1000.0/animationRate);
			slice += sliceIncrement;
			if (slice<first) {
				slice = first+1;
				sliceIncrement = 1;
			}
			if (slice>last) {
				if (cal.loop) {
					slice = last-1;
					sliceIncrement = -1;
				} else {
					slice = first;
					sliceIncrement = 1;
				}
			}
			swin.showSlice(slice);
		}
		
	}

	void doOptions() {
		if (firstFrame<1 || firstFrame>nSlices || lastFrame<1 || lastFrame>nSlices)
			{firstFrame=1; lastFrame=nSlices;}
		if (imp.isDisplayedHyperStack()) {
			int frames = imp.getNFrames();
			int slices = imp.getNSlices();
			firstFrame = 1;
			if (frames>1)
				lastFrame = frames;
			else if (slices>1)
				lastFrame=slices;
		}
		boolean start = !swin.getAnimate();
		Calibration cal = imp.getCalibration();
		if (cal.fps!=0.0)
			animationRate = cal.fps;
		else if (cal.frameInterval!=0.0 && cal.getTimeUnit().equals("sec"))
			animationRate = 1.0/cal.frameInterval;
		int decimalPlaces = (int)animationRate==animationRate?0:3;
		GenericDialog gd = new GenericDialog("Animation Options");
		gd.addNumericField("Speed (0.1-1000 fps):", animationRate, decimalPlaces);
		if (!imp.isDisplayedHyperStack()) {
			gd.addNumericField("First Frame:", firstFrame, 0);
			gd.addNumericField("Last Frame:", lastFrame, 0);
		}
		gd.addCheckbox("Loop Back and Forth", cal.loop);
		gd.addCheckbox("Start Animation", start);
		gd.showDialog();
		if (gd.wasCanceled()) {
			if (firstFrame==1 && lastFrame==nSlices)
				{firstFrame=0; lastFrame=0;}
			return;
		}
		double speed = gd.getNextNumber();
		if (!imp.isDisplayedHyperStack()) {
			firstFrame = (int)gd.getNextNumber();
			lastFrame = (int)gd.getNextNumber();
		}
		if (firstFrame==1 && lastFrame==nSlices)
			{firstFrame=0; lastFrame=0;}
		cal.loop = gd.getNextBoolean();
		Calibration.setLoopBackAndForth(cal.loop);
		start = gd.getNextBoolean();
		if (speed>1000.0) speed = 1000.0;
		//if (speed<0.1) speed = 0.1;
		animationRate = speed;
		if (animationRate!=0.0)
			cal.fps = animationRate;
		if (start && !swin.getAnimate())
			startAnimation();
	}
	
	void nextSlice() {
		jumpSlice(1);
	}
	
	
	void previousSlice() {
		jumpSlice(-1);
	}	
	
	void jumpSlice(int pn) {
		if (!imp.lock())
			return;
		boolean hyperstack = imp.isDisplayedHyperStack();
		int channels = imp.getNChannels();
		int slices = imp.getNSlices();
		int frames = imp.getNFrames();
		if (swin.getAnimate() && (channels*slices*frames==Math.max(channels,Math.max(slices,frames))) )
			{stopAnimation(); return;} //if only one dimension, stop animating
		if (hyperstack && frames>1 && !((slices>1||channels>1)&&(IJ.controlKeyDown()||IJ.spaceBarDown()||IJ.altKeyDown()) || swin.getAnimate())){
			int t = imp.getFrame() + pn;
			if (t>frames) t = frames;
			if (t<1) t = 1;
			swin.setPosition(imp.getChannel(), imp.getSlice(), t);
		} else if (hyperstack && slices>1 && !(channels>1&& (IJ.altKeyDown() || IJ.spaceBarDown()) || ((swin.getAnimate()|| IJ.controlKeyDown()) && frames==1)) ) {
			int z = imp.getSlice() + pn;
			if (z>slices) z = slices;
			if (z<1) z = 1;
			swin.setPosition(imp.getChannel(), z, imp.getFrame());
		} else if (hyperstack && channels>1) {
			int c = imp.getChannel() + pn;
			if (c>channels) c = channels;
			if (c<1) c = 1;
			swin.setPosition(c, imp.getSlice(), imp.getFrame());
		} else {
			if (IJ.altKeyDown())
				slice+=(pn*10);
			else
				slice+=pn;
			if (slice>nSlices)
				slice = nSlices;
			if (slice<1)
				slice = 1;
			swin.showSlice(slice);
		}
		imp.updateStatusbarValue();
		imp.unlock();
	}

	void setSlice() {
        GenericDialog gd = new GenericDialog("Set Slice");
        gd.addNumericField("Slice Number (1-"+nSlices+"):", slice, 0);
        gd.showDialog();
        if (!gd.wasCanceled()) {
        	int n = (int)gd.getNextNumber();
        	if (imp.isDisplayedHyperStack())
        		imp.setPosition(n);
        	else
        		imp.setSlice(n);
        }
	}

	/** Returns the current animation speed in frames per second. */
	public static double getFrameRate() {
		return animationRate;
	}

}
