package io.nayuki.flac.app;

import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.io.File;
import java.io.IOException;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.swing.JFrame;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicSliderUI;
import javax.swing.plaf.metal.MetalSliderUI;
import io.nayuki.flac.decode.FlacDecoder;


public final class SeekableFlacPlayerGui {
	
	private static FlacDecoder decoder;
	private static SourceDataLine line;
	private static JSlider slider;
	private static BasicSliderUI sliderUi;
	private static double seekRequest;
	private static Object lock;
	
	
	public static void main(String[] args) throws LineUnavailableException, IOException {
		if (args.length != 1) {
			System.err.println("Usage: java SeekableFlacPlayerGui InFile.flac");
			System.exit(1);
			return;
		}
		
		decoder = new FlacDecoder(new File(args[0]));
		while (decoder.readAndHandleMetadataBlock() != null);
		if (decoder.streamInfo.numSamples == 0)
			throw new IllegalArgumentException("Unknown audio length");
		
		AudioFormat format = new AudioFormat(decoder.streamInfo.sampleRate,
			decoder.streamInfo.sampleDepth, decoder.streamInfo.numChannels, true, false);
		DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
		line = (SourceDataLine)AudioSystem.getLine(info);
		line.open(format);
		line.start();
		
		lock = new Object();
		seekRequest = -1;
		
		slider = new JSlider(SwingConstants.HORIZONTAL, 0, 10000, 0);
		sliderUi = new MetalSliderUI();
		slider.setUI(sliderUi);
		slider.setPreferredSize(new Dimension(800, 50));
		slider.addMouseListener(new MouseAdapter() {
			public void mousePressed(MouseEvent ev) {
				moveSlider(ev);
			}
			public void mouseReleased(MouseEvent ev) {
				moveSlider(ev);
				synchronized(lock) {
					seekRequest = (double)slider.getValue() / slider.getMaximum();
					lock.notify();
				}
			}
		});
		slider.addMouseMotionListener(new MouseMotionAdapter() {
			public void mouseDragged(MouseEvent ev) {
				moveSlider(ev);
			}
		});
		
		JFrame frame = new JFrame("FLAC Player");
		frame.add(slider);
		frame.pack();
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setResizable(false);
		frame.setVisible(true);
		
		new PlayerThread().start();
	}
	
	
	private static void moveSlider(MouseEvent ev) {
		slider.setValue(sliderUi.valueForXPosition(ev.getX()));
	}
	
	
	private static final class PlayerThread extends Thread {
		public void run() {
			try {
				int bytesPerSample = decoder.streamInfo.sampleDepth / 8;
				int[][] samples = new int[8][65536];
				long position = 0;
				long startTime = line.getMicrosecondPosition();
				while (true) {
					double seekReq;
					synchronized(lock) {
						seekReq = seekRequest;
						seekRequest = -1;
					}
					
					int blockSamples;
					if (seekReq == -1)
						blockSamples = decoder.readAudioBlock(samples, 0);
					else {
						position = Math.round(seekReq * decoder.streamInfo.numSamples);
						seekReq = -1;
						blockSamples = decoder.seekAndReadAudioBlock(position, samples, 0);
						line.flush();
						startTime = line.getMicrosecondPosition() - Math.round(position * 1e6 / decoder.streamInfo.sampleRate);
					}
					{
						double timePos = (line.getMicrosecondPosition() - startTime) / 1e6;
						double songProportion = timePos * decoder.streamInfo.sampleRate / decoder.streamInfo.numSamples;
						final int sliderPos = (int)Math.round(songProportion * slider.getMaximum());
						SwingUtilities.invokeLater(new Runnable() {
							public void run() {
								if (!slider.getValueIsAdjusting())
									slider.setValue(sliderPos);
							}
						});
					}
					if (blockSamples == 0) {
						synchronized(lock) {
							while (seekRequest == -1)
								lock.wait();
						}
						continue;
					}
					
					byte[] buf = new byte[blockSamples * decoder.streamInfo.numChannels * bytesPerSample];
					for (int i = 0, k = 0; i < blockSamples; i++) {
						for (int ch = 0; ch < decoder.streamInfo.numChannels; ch++) {
							int val = samples[ch][i];
							for (int j = 0; j < bytesPerSample; j++, k++)
								buf[k] = (byte)(val >>> (j << 3));
						}
					}
					line.write(buf, 0, buf.length);
					position += blockSamples;
				}
			} catch (IOException|InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
}
