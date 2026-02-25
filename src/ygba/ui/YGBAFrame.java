package ygba.ui;

import java.io.File;
import java.net.URL;
import java.util.Arrays;

import javax.swing.JFrame;
import javax.swing.WindowConstants;

import ygba.util.Log;

public final class YGBAFrame extends JFrame {

	private static final long serialVersionUID = 1L;

	public YGBAFrame() {
		super("YahGBA");

		setLocation(0, 0);
		setResizable(true);

		setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
	}

	private static boolean configureFromArgs(String[] args) {
		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			if ("--bios".equals(arg)) {
				if (i + 1 >= args.length) {
					System.out.println("Missing value for --bios");
					return false;
				}
				System.setProperty("ygba.bios", new File(args[++i]).getPath());
			} else if ("--rom".equals(arg)) {
				if (i + 1 >= args.length) {
					System.out.println("Missing value for --rom");
					return false;
				}
				System.setProperty("ygba.rom", new File(args[++i]).getPath());
			} else if ("--debugger".equals(arg)) {
				System.setProperty("ygba.debugger.autostart", "true");
			} else if ("--debug-console".equals(arg)) {
				System.setProperty("ygba.debug.console", "true");
				} else if ("--status-log".equals(arg)) {
					System.setProperty("ygba.debug.status", "true");
				} else if ("--trace-video".equals(arg)) {
					System.setProperty("ygba.trace.video", "true");
			} else if ("--log-file".equals(arg)) {
					if (i + 1 >= args.length) {
						System.out.println("Missing value for --log-file");
						return false;
					}
					System.setProperty("ygba.log.file", new File(args[++i]).getPath());
				} else if ("--auto-dump-interval-ms".equals(arg)) {
					if (i + 1 >= args.length) {
						System.out.println("Missing value for --auto-dump-interval-ms");
						return false;
					}
					System.setProperty("ygba.auto.dump.interval.ms", args[++i]);
				} else if ("--auto-dump-duration-ms".equals(arg)) {
					if (i + 1 >= args.length) {
						System.out.println("Missing value for --auto-dump-duration-ms");
						return false;
					}
					System.setProperty("ygba.auto.dump.duration.ms", args[++i]);
				} else if ("--auto-dump-exit".equals(arg)) {
					System.setProperty("ygba.auto.dump.exit", "true");
				} else if ("--save-dir".equals(arg)) {
					if (i + 1 >= args.length) {
						System.out.println("Missing value for --save-dir");
						return false;
					}
					System.setProperty("ygba.save.dir", new File(args[++i]).getPath());
				} else if ("--help".equals(arg) || "-h".equals(arg)) {
					System.out.println("Usage: ./gradlew run --args='[--bios <file>] [--rom <file>] [--debugger] [--debug-console] [--status-log] [--trace-video] [--log-file <file>] [--auto-dump-interval-ms <ms>] [--auto-dump-duration-ms <ms>] [--auto-dump-exit] [--save-dir <dir>]'");
					return false;
				} else {
					System.out.println("Unknown argument: " + arg);
				return false;
			}
		}
		return true;
	}

	private static void printStartupDiagnostics(String[] args) {
		System.out.println("[BOOT] args=" + Arrays.toString(args));
		System.out.println("[BOOT] os=" + System.getProperty("os.name") +
				" arch=" + System.getProperty("os.arch") +
				" java=" + System.getProperty("java.version"));
		System.out.println("[BOOT] cwd=" + new File(".").getAbsoluteFile().toString());

		try {
			URL codeSource = YGBAFrame.class.getProtectionDomain().getCodeSource().getLocation();
			System.out.println("[BOOT] codeSource=" + codeSource);
		} catch (SecurityException e) {
			System.out.println("[BOOT] codeSource=<unavailable>");
		}

		URL classResource = YGBAFrame.class.getResource("YGBAFrame.class");
		if (classResource != null) {
			System.out.println("[BOOT] classResource=" + classResource);
		}

		String bios = System.getProperty("ygba.bios");
		String rom = System.getProperty("ygba.rom");
		String logFile = System.getProperty("ygba.log.file");
		boolean traceVideo = Boolean.getBoolean("ygba.trace.video");
		boolean hleSwi = Boolean.parseBoolean(System.getProperty("ygba.hle.swi", "false"));
		System.out.println("[BOOT] biosArg=" + (bios != null ? new File(bios).getAbsolutePath() : "<default>"));
		System.out.println("[BOOT] romArg=" + (rom != null ? new File(rom).getAbsolutePath() : "<default>"));
		System.out.println("[BOOT] logFile=" + (logFile != null ? new File(logFile).getAbsolutePath() : "<none>"));
		String saveDir = System.getProperty("ygba.save.dir", ".");
		System.out.println("[BOOT] traceVideo=" + traceVideo);
		System.out.println("[BOOT] hleSwi=" + hleSwi);
		System.out.println("[BOOT] saveDir=" + new File(saveDir).getAbsolutePath());
	}

	public static void main(String[] args) {
		if (!configureFromArgs(args)) return;
		Log.initFromProperties();
		printStartupDiagnostics(args);

		YGBAApplet ygbaApplet = new YGBAApplet(false);
		ygbaApplet.init();

		YGBAFrame ygbaFrame = new YGBAFrame();
		ygbaFrame.add(ygbaApplet);
		ygbaFrame.pack();
		ygbaFrame.setVisible(true);

		// In desktop mode we call applet lifecycle hooks manually.
		ygbaApplet.start();
	}

}
