package io.nayuki.flac.app;

import java.awt.Dimension;
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
import io.nayuki.flac.decode.FlacDecoder;


public final class SeekableFlacPlayerGui {
	
	private static FlacDecoder decoder;
	private static SourceDataLine line;
	private static JSlider slider;
	
	
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
		
		slider = new JSlider(SwingConstants.HORIZONTAL, 0, 10000, 0);
		slider.setPreferredSize(new Dimension(800, 50));
		
		JFrame frame = new JFrame("FLAC Player");
		frame.add(slider);
		frame.pack();
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setResizable(false);
		frame.setVisible(true);
		
		new PlayerThread().start();
	}
	
	
	private static final class PlayerThread extends Thread {
		public void run() {
			try {
				int bytesPerSample = decoder.streamInfo.sampleDepth / 8;
				int[][] samples = new int[8][65536];
				long position = 0;
				while (true) {
					final int sliderPos = (int)Math.round((double)position / decoder.streamInfo.numSamples * slider.getMaximum());
					SwingUtilities.invokeLater(new Runnable() {
						public void run() {
							slider.setValue(sliderPos);
						}
					});
					
					int blockSamples = decoder.readAudioBlock(samples, 0);
					if (blockSamples == 0)
						break;
					
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
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
}
