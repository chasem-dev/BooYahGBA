package ygba.ui;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.zip.CRC32;

import javax.imageio.ImageIO;
import javax.swing.JApplet;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import ygba.YGBA;
import ygba.cpu.ARM7TDMI;
import ygba.memory.SavePersistence;
import ygba.dma.DMA;
import ygba.dma.DirectMemoryAccess;
import ygba.gfx.GFXScreen;
import ygba.memory.IORegMemory;
import ygba.memory.Memory;
import ygba.memory.ObjectMemory;
import ygba.memory.VideoMemory;

public final class YGBAApplet extends JApplet implements ActionListener, KeyListener, MouseListener {

	private static final long serialVersionUID = 1L;

	private YGBA ygba;

	private Memory memory;
	private IORegMemory iorMem;

	private GFXScreen gfxScreen;

	private boolean isApplet;
	private boolean debuggerLaunchedOnStart;

	private URL biosURL, romURL;

	private JPanel mainPanel;

	private JPopupMenu popupMenu;

	private JMenu fileMenu;
	private JMenuItem openBIOSMenuItem, openROMMenuItem;
	private JMenuItem resetMenuItem;
	private JCheckBoxMenuItem pauseMenuItem;

	private JMenu toolsMenu;
	private JMenuItem debuggerMenuItem;

	private JMenuItem aboutMenuItem;

	private JFileChooser biosFileChooser, romFileChooser;
	private YGBAFileFilter fileFilter;

	private final static String OpenBIOSCommand = "OPEN_BIOS", OpenROMCommand = "OPEN_ROM", ResetCommand = "RESET", PauseCommand = "PAUSE", LaunchDebuggerCommand = "LAUNCH_DEBUGGER", DisplayAboutInfoCommand = "DISPLAY_ABOUT_INFO";

	private final static int OpenBIOSKey = KeyEvent.VK_F1, OpenROMKey = KeyEvent.VK_F2, ResetKey = KeyEvent.VK_R, PauseKey = KeyEvent.VK_P, LaunchDebuggerKey = KeyEvent.VK_D, DumpFrameKey = KeyEvent.VK_CLOSE_BRACKET;

	private final static String DefaultBIOSFileName = "gba_bios.bin";
	private final static String DefaultROMFileName = "Pokemon - FireRed Version (USA).gba";

	private final static int PaletteBaseAddress = 0x05000000;
	private final static int VideoBaseAddress = 0x06000000;
	private final static int ObjectBaseAddress = 0x07000000;

	private Thread autoDumpThread;
	private volatile boolean autoDumpStopRequested;

	public YGBAApplet() {
		this(true);
	}

	public YGBAApplet(boolean isApplet) {
		this.isApplet = isApplet;
		debuggerLaunchedOnStart = false;
		biosURL = romURL = null;
		autoDumpThread = null;
		autoDumpStopRequested = false;
	}

	private static URL getPathPropertyURL(String propertyName) {
		String path = System.getProperty(propertyName);
		if (path == null || path.trim().length() == 0) return null;

		try {
			return new File(path).toURI().toURL();
		} catch (MalformedURLException e) {
			System.out.println("Invalid path for " + propertyName + ": " + path);
			return null;
		}
	}

	private static String urlOrDefault(URL url) {
		return (url != null) ? url.toString() : "<none>";
	}

	private static long readLongProperty(String propertyName, long defaultValue, long minValue) {
		String value = System.getProperty(propertyName);
		if (value == null || value.trim().length() == 0) return defaultValue;
		try {
			long parsed = Long.parseLong(value.trim());
			if (parsed < minValue) {
				System.out.println("[AUTO_DUMP] ignoring " + propertyName + "=" + value + " (min " + minValue + ")");
				return defaultValue;
			}
			return parsed;
		} catch (NumberFormatException e) {
			System.out.println("[AUTO_DUMP] ignoring invalid " + propertyName + "=" + value);
			return defaultValue;
		}
	}

	private void startAutoDumpLoopIfConfigured() {
		long intervalMs = readLongProperty("ygba.auto.dump.interval.ms", -1, 1);
		if (intervalMs <= 0) return;

		long durationMs = readLongProperty("ygba.auto.dump.duration.ms", 30_000, 1);
		boolean exitOnDone = Boolean.getBoolean("ygba.auto.dump.exit");

		stopAutoDumpLoop();
		autoDumpStopRequested = false;

		autoDumpThread = new Thread(new Runnable() {
			public void run() {
				long startedAt = System.currentTimeMillis();
				long finishedAt = startedAt + durationMs;
				long nextDumpAt = startedAt;
				int dumpCount = 0;

				System.out.printf("[AUTO_DUMP] start intervalMs=%d durationMs=%d exitOnDone=%b%n",
						intervalMs, durationMs, exitOnDone);

				while (!autoDumpStopRequested) {
					long now = System.currentTimeMillis();
					if (now >= finishedAt) break;
					if (now < nextDumpAt) {
						long sleepMs = nextDumpAt - now;
						try {
							Thread.sleep(sleepMs);
						} catch (InterruptedException e) {
							break;
						}
						continue;
					}

					dumpFrame();
					dumpCount++;
					nextDumpAt += intervalMs;
				}

				System.out.printf("[AUTO_DUMP] done dumps=%d%n", dumpCount);
				autoDumpThread = null;
				if (!autoDumpStopRequested && exitOnDone) {
					System.out.println("[AUTO_DUMP] exiting");
					System.exit(0);
				}
			}
		}, "ygba-auto-dump");

		autoDumpThread.setDaemon(true);
		autoDumpThread.start();
	}

	private void stopAutoDumpLoop() {
		autoDumpStopRequested = true;
		Thread thread = autoDumpThread;
		if (thread != null) {
			thread.interrupt();
		}
	}

	public void init() {
		ygba = new YGBA();

		memory = ygba.getMemory();
		iorMem = memory.getIORegMemory();

		gfxScreen = new GFXScreen(ygba.getGraphics());

		openBIOSMenuItem = new JMenuItem("Open BIOS");
		openBIOSMenuItem.setAccelerator(KeyStroke.getKeyStroke(OpenBIOSKey, 0));
		openBIOSMenuItem.setActionCommand(OpenBIOSCommand);
		openBIOSMenuItem.addActionListener(this);
		openROMMenuItem = new JMenuItem("Open ROM");
		openROMMenuItem.setAccelerator(KeyStroke.getKeyStroke(OpenROMKey, 0));
		openROMMenuItem.setActionCommand(OpenROMCommand);
		openROMMenuItem.addActionListener(this);
		resetMenuItem = new JMenuItem("Reset");
		resetMenuItem.setAccelerator(KeyStroke.getKeyStroke(ResetKey, KeyEvent.CTRL_DOWN_MASK));
		resetMenuItem.setActionCommand(ResetCommand);
		resetMenuItem.addActionListener(this);
		pauseMenuItem = new JCheckBoxMenuItem("Pause");
		pauseMenuItem.setAccelerator(KeyStroke.getKeyStroke(PauseKey, KeyEvent.CTRL_DOWN_MASK));
		pauseMenuItem.setActionCommand(PauseCommand);
		pauseMenuItem.addActionListener(this);
		pauseMenuItem.setSelected(false);
		fileMenu = new JMenu("File");
		fileMenu.add(openBIOSMenuItem);
		fileMenu.add(openROMMenuItem);
		fileMenu.addSeparator();
		fileMenu.add(resetMenuItem);
		fileMenu.add(pauseMenuItem);

		debuggerMenuItem = new JMenuItem("Debugger");
		debuggerMenuItem.setAccelerator(KeyStroke.getKeyStroke(LaunchDebuggerKey, KeyEvent.ALT_DOWN_MASK));
		debuggerMenuItem.setActionCommand(LaunchDebuggerCommand);
		debuggerMenuItem.addActionListener(this);
		toolsMenu = new JMenu("Tools");
		toolsMenu.add(debuggerMenuItem);

		aboutMenuItem = new JMenuItem("About");
		aboutMenuItem.setActionCommand(DisplayAboutInfoCommand);
		aboutMenuItem.addActionListener(this);

		setupPopupMenu(false);

		popupMenu = new JPopupMenu();
		popupMenu.add(fileMenu);
		popupMenu.add(toolsMenu);
		popupMenu.add(aboutMenuItem);

		mainPanel = new JPanel();
		mainPanel.setLayout(new java.awt.BorderLayout());
		mainPanel.setBackground(Color.BLACK);
		mainPanel.setFocusable(true);
		mainPanel.requestFocus();
		mainPanel.add(gfxScreen, java.awt.BorderLayout.CENTER);
		mainPanel.addKeyListener(this);
		mainPanel.addMouseListener(this);
		gfxScreen.setFocusable(true);
		gfxScreen.addKeyListener(this);
		gfxScreen.addMouseListener(this);
		setFocusable(true);
		addKeyListener(this);
		addMouseListener(this);

		getContentPane().add(mainPanel);

		if (isApplet) {
			openBIOSMenuItem.setEnabled(false);
			openROMMenuItem.setEnabled(false);

			try {
				biosURL = new URL(getParameter("bios"));
				romURL = new URL(getParameter("rom"));
			} catch (MalformedURLException e) {
			}
		} else {
			fileFilter = new YGBAFileFilter();

			java.io.File cwd = new java.io.File(System.getProperty("user.dir"));

			biosFileChooser = new JFileChooser(cwd);
			biosFileChooser.setFileFilter(fileFilter);
			biosFileChooser.setDialogTitle("Open BIOS");

			romFileChooser = new JFileChooser(cwd);
			romFileChooser.setFileFilter(fileFilter);
			romFileChooser.setDialogTitle("Open ROM");
		}

		if (!isApplet) {
			URL configuredBIOS = getPathPropertyURL("ygba.bios");
			URL configuredROM = getPathPropertyURL("ygba.rom");
			if (configuredBIOS != null) biosURL = configuredBIOS;
			if (configuredROM != null) romURL = configuredROM;

			// Autoload BIOS file if no explicit BIOS path was provided
			if (biosURL == null) {
				java.io.File biosFile = new java.io.File(DefaultBIOSFileName);
				if (biosFile.exists()) {
					try {
						biosURL = biosFile.toURI().toURL();
					} catch (MalformedURLException e) {
					}
				}
			}

			// Autoload ROM file if no explicit ROM path was provided
			if (romURL == null) {
				java.io.File romFile = new java.io.File(DefaultROMFileName);
				if (romFile.exists()) {
					try {
						romURL = romFile.toURI().toURL();
					} catch (MalformedURLException e) {
					}
				}
			}

			System.out.println("[BOOT] selectedBIOS=" + urlOrDefault(biosURL));
			System.out.println("[BOOT] selectedROM=" + urlOrDefault(romURL));
		}
	}

	public void start() {
		if (biosURL != null) {
			boolean loaded = memory.loadBIOS(biosURL);
			System.out.printf("[BOOT] BIOS loaded=%b source=%s size=%d crc32=%08X%n",
					loaded,
					memory.getLoadedBIOSSource(),
					memory.getLoadedBIOSSize(),
					memory.getLoadedBIOSCRC32());
		}
		if (romURL != null) {
			boolean loaded = memory.loadROM(romURL);
			System.out.printf("[BOOT] ROM loaded=%b source=%s size=%d crc32=%08X%n",
					loaded,
					memory.getLoadedROMSource(),
					memory.getLoadedROMSize(),
					memory.getLoadedROMCRC32());
		}
		if (ygba.isReady()) {
			ygba.reset();
			setupSavePersistenceIfConfigured();
			ygba.run();
			setupPopupMenu(true);
			System.out.println("[BOOT] core running");
			System.out.println("[INPUT] A=X B=C START=Enter SELECT=Space/Backspace L=S R=D");
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					requestInputFocus();
				}
			});

			if (Boolean.getBoolean("ygba.debugger.autostart") && !debuggerLaunchedOnStart) {
				debuggerLaunchedOnStart = true;
				ygba.stop();
				new DebuggerDialog(ygba);
				if (!pauseMenuItem.isSelected()) ygba.run();
			}
			startAutoDumpLoopIfConfigured();
		} else {
			System.out.println("[BOOT] core not started (BIOS/ROM not ready)");
		}
	}

	public void stop() {
		stopAutoDumpLoop();
		ygba.stop();
	}

	private void requestInputFocus() {
		mainPanel.requestFocusInWindow();
		gfxScreen.requestFocusInWindow();
		requestFocusInWindow();
	}

	private void setupSavePersistenceIfConfigured() {
		String romSource = memory.getLoadedROMSource();
		if (romSource == null) return;
		String saveDirPath = System.getProperty("ygba.save.dir", ".");
		File saveDir = new File(saveDirPath);
		if (!saveDir.exists()) saveDir.mkdirs();
		ygba.setupSavePersistence(saveDir, romSource);
	}

	public void actionPerformed(ActionEvent ae) {
		String actionCommand = ae.getActionCommand();

		boolean isPaused = pauseMenuItem.isSelected();

		if (actionCommand.equals(OpenBIOSCommand)) {

			int option = biosFileChooser.showOpenDialog(null);
			if (option == JFileChooser.APPROVE_OPTION) {
				try {
					biosURL = biosFileChooser.getSelectedFile().toURI().toURL();
				} catch (MalformedURLException e) {
				}
				ygba.stop();
				memory.unloadBIOS();
				memory.loadBIOS(biosURL);
				if (ygba.isReady()) {
					ygba.reset();
					setupSavePersistenceIfConfigured();
					if (!isPaused)
						ygba.run();
					setupPopupMenu(true);
				} else {
					gfxScreen.clear();
					setupPopupMenu(false);
				}
			}

		} else if (actionCommand.equals(OpenROMCommand)) {

			int option = romFileChooser.showOpenDialog(null);
			if (option == JFileChooser.APPROVE_OPTION) {
				try {
					romURL = romFileChooser.getSelectedFile().toURI().toURL();
				} catch (MalformedURLException e) {
				}
				ygba.stop();
				memory.unloadROM();
				memory.loadROM(romURL);
				if (ygba.isReady()) {
					ygba.reset();
					setupSavePersistenceIfConfigured();
					if (!isPaused)
						ygba.run();
					setupPopupMenu(true);
				} else {
					gfxScreen.clear();
					setupPopupMenu(false);
				}
			}

		} else if (actionCommand.equals(ResetCommand)) {

			ygba.stop();
			ygba.reset();
			if (!isPaused)
				ygba.run();

		} else if (actionCommand.equals(PauseCommand)) {

			if (isPaused)
				ygba.stop();
			else
				ygba.run();

		} else if (actionCommand.equals(LaunchDebuggerCommand)) {

			ygba.stop();
			new DebuggerDialog(ygba);
			if (!isPaused)
				ygba.run();

		} else if (actionCommand.equals(DisplayAboutInfoCommand)) {

			ygba.stop();
			new AboutDialog();
			if (ygba.isReady() & !isPaused)
				ygba.run();

		}
	}

	private static String hex8(int value) {
		return String.format("%08X", value);
	}

	private static String hex4(int value) {
		return String.format("%04X", value & 0xFFFF);
	}

	private static void dumpHalfWord(PrintWriter out, IORegMemory mem, String name, int offset) {
		out.println(name + "=" + hex4(mem.getHalfWord(offset)));
	}

	private static void dumpWord(PrintWriter out, IORegMemory mem, String name, int offset) {
		out.println(name + "=" + hex8(mem.getWord(offset)));
	}

	private long crc32Range(int baseAddress, int size) {
		CRC32 crc32 = new CRC32();
		for (int i = 0; i < size; i++) {
			crc32.update(memory.getByte(baseAddress + i) & 0xFF);
		}
		return crc32.getValue();
	}

	private long crc32StridedRange(int baseAddress, int size, int strideOffset, int stride) {
		CRC32 crc32 = new CRC32();
		for (int i = strideOffset; i < size; i += stride) {
			crc32.update(memory.getByte(baseAddress + i) & 0xFF);
		}
		return crc32.getValue();
	}

	private int countEqualHalfwordBytes(int baseAddress, int size) {
		int pairs = 0;
		for (int i = 0; i + 1 < size; i += 2) {
			int lo = memory.getByte(baseAddress + i) & 0xFF;
			int hi = memory.getByte(baseAddress + i + 1) & 0xFF;
			if (lo == hi) pairs++;
		}
		return pairs;
	}

	private static long crc32Frame(BufferedImage frame) {
		CRC32 crc32 = new CRC32();
		int width = frame.getWidth();
		int height = frame.getHeight();
		int[] pixels = frame.getRGB(0, 0, width, height, null, 0, width);
		for (int pixel : pixels) {
			crc32.update(pixel & 0xFF);
			crc32.update((pixel >>> 8) & 0xFF);
			crc32.update((pixel >>> 16) & 0xFF);
			crc32.update((pixel >>> 24) & 0xFF);
		}
		return crc32.getValue();
	}

	private static int dmaUnitBytes(boolean is32Bit) {
		return is32Bit ? 4 : 2;
	}

	private static int dmaByteCount(int unitCount, boolean is32Bit) {
		if (unitCount <= 0) return 0;
		long bytes = ((long) unitCount) * dmaUnitBytes(is32Bit);
		if (bytes <= 0) return 0;
		if (bytes > Integer.MAX_VALUE) return Integer.MAX_VALUE;
		return (int) bytes;
	}

	private String dmaLinearRangeCRC32(int startAddress, int unitCount, boolean is32Bit, int step) {
		int unitBytes = dmaUnitBytes(is32Bit);
		if (unitCount <= 0) return "00000000";
		if (step != unitBytes) return "NON_LINEAR";
		int byteCount = dmaByteCount(unitCount, is32Bit);
		if (byteCount <= 0) return "00000000";
		return hex8((int) crc32Range(startAddress, byteCount));
	}

	private void dumpDMAState(PrintWriter out) {
		out.println("[dma]");
		dumpWord(out, iorMem, "DMA0SAD", IORegMemory.REG_DMA0SAD);
		dumpWord(out, iorMem, "DMA0DAD", IORegMemory.REG_DMA0DAD);
		dumpHalfWord(out, iorMem, "DMA0CNT_L", IORegMemory.REG_DMA0CNT_L);
		dumpHalfWord(out, iorMem, "DMA0CNT_H", IORegMemory.REG_DMA0CNT_H);
		dumpWord(out, iorMem, "DMA1SAD", IORegMemory.REG_DMA1SAD);
		dumpWord(out, iorMem, "DMA1DAD", IORegMemory.REG_DMA1DAD);
		dumpHalfWord(out, iorMem, "DMA1CNT_L", IORegMemory.REG_DMA1CNT_L);
		dumpHalfWord(out, iorMem, "DMA1CNT_H", IORegMemory.REG_DMA1CNT_H);
		dumpWord(out, iorMem, "DMA2SAD", IORegMemory.REG_DMA2SAD);
		dumpWord(out, iorMem, "DMA2DAD", IORegMemory.REG_DMA2DAD);
		dumpHalfWord(out, iorMem, "DMA2CNT_L", IORegMemory.REG_DMA2CNT_L);
		dumpHalfWord(out, iorMem, "DMA2CNT_H", IORegMemory.REG_DMA2CNT_H);
		dumpWord(out, iorMem, "DMA3SAD", IORegMemory.REG_DMA3SAD);
		dumpWord(out, iorMem, "DMA3DAD", IORegMemory.REG_DMA3DAD);
		dumpHalfWord(out, iorMem, "DMA3CNT_L", IORegMemory.REG_DMA3CNT_L);
		dumpHalfWord(out, iorMem, "DMA3CNT_H", IORegMemory.REG_DMA3CNT_H);
		out.println();
	}

	private void dumpDMATelemetry(PrintWriter out) {
		DirectMemoryAccess directMemoryAccess = ygba.getDMA();
		out.println("[dma_counters]");
		for (int i = 0; i < 4; i++) {
			DMA dma = directMemoryAccess.getDMA(i);
			String prefix = "DMA" + i;
			out.println(prefix + "Triggers=" + dma.getTriggerCount());
				out.println(prefix + "UnitsTransferred=" + dma.getTotalUnitsTransferred());
				out.println(prefix + "VRAMHalfWordUnits=" + dma.getVRAMHalfWordUnits());
				out.println(prefix + "VRAMWordUnits=" + dma.getVRAMWordUnits());
				out.println(prefix + "EWRAMHalfWordUnits=" + dma.getEWRAMHalfWordUnits());
				out.println(prefix + "EWRAMWordUnits=" + dma.getEWRAMWordUnits());
				out.println(prefix + "LastControl=" + hex4(dma.getControlRegister() & 0xFFFF));
				out.println(prefix + "LastSourceStart=" + hex8(dma.getLastSourceStart()));
				out.println(prefix + "LastSourceEnd=" + hex8(dma.getLastSourceEnd()));
				out.println(prefix + "LastDestinationStart=" + hex8(dma.getLastDestinationStart()));
				out.println(prefix + "LastDestinationEnd=" + hex8(dma.getLastDestinationEnd()));
			out.println(prefix + "LastUnitCount=" + dma.getLastUnitCount());
			out.println(prefix + "LastSourceStep=" + dma.getLastSourceStep());
			out.println(prefix + "LastDestinationStep=" + dma.getLastDestinationStep());
			out.println(prefix + "LastTransfer32Bit=" + dma.wasLastTransfer32Bit());
			out.println(prefix + "LastTransferTouchedVRAM=" + dma.didLastTransferTouchVRAM());
			out.println(prefix + "LastVRAMControl=" + hex4(dma.getLastVRAMControl() & 0xFFFF));
			out.println(prefix + "LastVRAMSourceStart=" + hex8(dma.getLastVRAMSourceStart()));
			out.println(prefix + "LastVRAMSourceEnd=" + hex8(dma.getLastVRAMSourceEnd()));
			out.println(prefix + "LastVRAMDestinationStart=" + hex8(dma.getLastVRAMDestinationStart()));
			out.println(prefix + "LastVRAMDestinationEnd=" + hex8(dma.getLastVRAMDestinationEnd()));
			out.println(prefix + "LastVRAMUnitCount=" + dma.getLastVRAMUnitCount());
			out.println(prefix + "LastVRAMSourceStep=" + dma.getLastVRAMSourceStep());
			out.println(prefix + "LastVRAMDestinationStep=" + dma.getLastVRAMDestinationStep());
			out.println(prefix + "LastVRAMTransfer32Bit=" + dma.wasLastVRAMTransfer32Bit());
			out.println(prefix + "LastVRAMByteCount=" + dmaByteCount(dma.getLastVRAMUnitCount(), dma.wasLastVRAMTransfer32Bit()));
			out.println(prefix + "LastVRAMSourceCRC32=" + dmaLinearRangeCRC32(
					dma.getLastVRAMSourceStart(),
					dma.getLastVRAMUnitCount(),
					dma.wasLastVRAMTransfer32Bit(),
					dma.getLastVRAMSourceStep()));
			out.println(prefix + "LastVRAMDestinationCRC32=" + dmaLinearRangeCRC32(
					dma.getLastVRAMDestinationStart(),
					dma.getLastVRAMUnitCount(),
					dma.wasLastVRAMTransfer32Bit(),
					dma.getLastVRAMDestinationStep()));
			out.println(prefix + "LastEWRAMControl=" + hex4(dma.getLastEWRAMControl() & 0xFFFF));
			out.println(prefix + "LastEWRAMSourceStart=" + hex8(dma.getLastEWRAMSourceStart()));
			out.println(prefix + "LastEWRAMSourceEnd=" + hex8(dma.getLastEWRAMSourceEnd()));
			out.println(prefix + "LastEWRAMDestinationStart=" + hex8(dma.getLastEWRAMDestinationStart()));
			out.println(prefix + "LastEWRAMDestinationEnd=" + hex8(dma.getLastEWRAMDestinationEnd()));
			out.println(prefix + "LastEWRAMUnitCount=" + dma.getLastEWRAMUnitCount());
			out.println(prefix + "LastEWRAMSourceStep=" + dma.getLastEWRAMSourceStep());
			out.println(prefix + "LastEWRAMDestinationStep=" + dma.getLastEWRAMDestinationStep());
			out.println(prefix + "LastEWRAMTransfer32Bit=" + dma.wasLastEWRAMTransfer32Bit());
			out.println(prefix + "LastEWRAMByteCount=" + dmaByteCount(dma.getLastEWRAMUnitCount(), dma.wasLastEWRAMTransfer32Bit()));
			out.println(prefix + "LastEWRAMSourceCRC32=" + dmaLinearRangeCRC32(
					dma.getLastEWRAMSourceStart(),
					dma.getLastEWRAMUnitCount(),
					dma.wasLastEWRAMTransfer32Bit(),
					dma.getLastEWRAMSourceStep()));
			out.println(prefix + "LastEWRAMDestinationCRC32=" + dmaLinearRangeCRC32(
					dma.getLastEWRAMDestinationStart(),
					dma.getLastEWRAMUnitCount(),
					dma.wasLastEWRAMTransfer32Bit(),
					dma.getLastEWRAMDestinationStep()));
		}
		out.println();
	}

	private void dumpTimerAndKeyState(PrintWriter out) {
		out.println("[timers]");
		dumpHalfWord(out, iorMem, "TM0D", IORegMemory.REG_TM0D);
		dumpHalfWord(out, iorMem, "TM0CNT", IORegMemory.REG_TM0CNT);
		dumpHalfWord(out, iorMem, "TM1D", IORegMemory.REG_TM1D);
		dumpHalfWord(out, iorMem, "TM1CNT", IORegMemory.REG_TM1CNT);
		dumpHalfWord(out, iorMem, "TM2D", IORegMemory.REG_TM2D);
		dumpHalfWord(out, iorMem, "TM2CNT", IORegMemory.REG_TM2CNT);
		dumpHalfWord(out, iorMem, "TM3D", IORegMemory.REG_TM3D);
		dumpHalfWord(out, iorMem, "TM3CNT", IORegMemory.REG_TM3CNT);
		out.println();

		out.println("[keys]");
		dumpHalfWord(out, iorMem, "P1", IORegMemory.REG_P1);
		dumpHalfWord(out, iorMem, "P1CNT", IORegMemory.REG_P1CNT);
		out.println();
	}

	private void dumpMemoryHashes(PrintWriter out) {
		out.println("[memory_hashes]");
		out.println("paletteCRC32=" + hex8((int) crc32Range(PaletteBaseAddress, 0x0400)));
		out.println("bgPaletteCRC32=" + hex8((int) crc32Range(PaletteBaseAddress, 0x0200)));
		out.println("objPaletteCRC32=" + hex8((int) crc32Range(PaletteBaseAddress + 0x0200, 0x0200)));
		out.println("vramCRC32=" + hex8((int) crc32Range(VideoBaseAddress, 0x18000)));
		out.println("vramBGCRC32=" + hex8((int) crc32Range(VideoBaseAddress, 0x10000)));
		out.println("vramOBJCRC32=" + hex8((int) crc32Range(VideoBaseAddress + 0x10000, 0x8000)));
		out.println("oamCRC32=" + hex8((int) crc32Range(ObjectBaseAddress, 0x0400)));
		for (int bg = 0; bg < 4; bg++) {
			int charBase = iorMem.getCharacterBaseAddress(bg);
			int screenBase = iorMem.getScreenBaseAddress(bg);
			out.println("BG" + bg + "CharBase=" + hex8(VideoBaseAddress + charBase));
			out.println("BG" + bg + "ScreenBase=" + hex8(VideoBaseAddress + screenBase));
			out.println("BG" + bg + "CharCRC32=" + hex8((int) crc32Range(VideoBaseAddress + charBase, 0x4000)));
			out.println("BG" + bg + "CharEvenCRC32=" + hex8((int) crc32StridedRange(VideoBaseAddress + charBase, 0x4000, 0, 2)));
			out.println("BG" + bg + "CharOddCRC32=" + hex8((int) crc32StridedRange(VideoBaseAddress + charBase, 0x4000, 1, 2)));
			out.println("BG" + bg + "CharEqualPairs=" + countEqualHalfwordBytes(VideoBaseAddress + charBase, 0x4000));
			out.println("BG" + bg + "Map2KCRC32=" + hex8((int) crc32Range(VideoBaseAddress + screenBase, 0x0800)));
			out.println("BG" + bg + "Map8KCRC32=" + hex8((int) crc32Range(VideoBaseAddress + screenBase, 0x2000)));
		}
		out.println("OBJTiles0CRC32=" + hex8((int) crc32Range(VideoBaseAddress + 0x10000, 0x2000)));
		out.println("OBJTiles1CRC32=" + hex8((int) crc32Range(VideoBaseAddress + 0x12000, 0x2000)));
		out.println("OBJTiles2CRC32=" + hex8((int) crc32Range(VideoBaseAddress + 0x14000, 0x2000)));
		out.println("OBJTiles3CRC32=" + hex8((int) crc32Range(VideoBaseAddress + 0x16000, 0x2000)));
		out.println();
	}

	private void dumpMemoryWriteCounters(PrintWriter out) {
		VideoMemory videoMemory = (VideoMemory) memory.getBank(0x06);
		out.println("[memory_writes]");
		out.println("vramByteWrites=" + videoMemory.getByteWriteCount());
		out.println("vramByteWritesIgnored=" + videoMemory.getByteWriteIgnoredCount());
		out.println("vramHalfWordWrites=" + videoMemory.getHalfWordWriteCount());
		out.println("vramWordWrites=" + videoMemory.getWordWriteCount());
		out.println();
	}

	private void dumpObjectSummary(PrintWriter out) {
		ObjectMemory objMem = (ObjectMemory) memory.getBank(0x07);
		int scanline = iorMem.getCurrentScanline();
		int configured = 0;
		int renderable = 0;
		int onScanline = 0;
		int listed = 0;
		final int maxListed = 24;

		out.println("[objects]");
		out.println("scanline=" + scanline);
		for (int obj = 0; obj < 128; obj++) {
			int xSize = objMem.getXSize(obj);
			int ySize = objMem.getYSize(obj);
			if (xSize == 0 || ySize == 0) continue;
			configured++;

			boolean rot = objMem.isRotScalEnabled(obj);
			boolean dbl = objMem.isDoubleSizeEnabled(obj);
			boolean hidden = (!rot && !objMem.isDisplayable(obj));
			if (hidden) continue;
			renderable++;

			int y = objMem.getYCoordinate(obj);
			if (y >= 160) y -= 256;
			int displayHeight = (rot && dbl) ? (ySize * 2) : ySize;
			if (scanline < y || scanline >= (y + displayHeight)) continue;
			onScanline++;

			if (listed >= maxListed) continue;
			int x = objMem.getXCoordinate(obj);
			int mode = objMem.getOBJMode(obj);
			int pri = objMem.getPriority(obj);
			int tile = objMem.getTileNumber(obj);
			int pal = objMem.getPaletteNumber(obj);
			boolean pal256 = objMem.is256ColorPalette(obj);
			boolean hFlip = objMem.isHFlipEnabled(obj);
			boolean vFlip = objMem.isVFlipEnabled(obj);
			out.printf("OBJ%03d x=%4d y=%4d size=%dx%d pri=%d tile=%03X pal=%02X mode=%d rs=%b dbl=%b 256=%b h=%b v=%b%n",
					obj, x, y, xSize, ySize, pri, tile, pal, mode, rot, dbl, pal256, hFlip, vFlip);
			listed++;
		}
		out.println("configured=" + configured);
		out.println("renderable=" + renderable);
		out.println("onScanline=" + onScanline);
		if (onScanline > maxListed) {
			out.println("onScanlineTruncated=" + (onScanline - maxListed));
		}
		out.println();
	}

	private void dumpOAMEntries(PrintWriter out) {
		ObjectMemory objMem = (ObjectMemory) memory.getBank(0x07);
		out.println("[oam_entries]");
		for (int obj = 0; obj < 128; obj++) {
			int base = obj << 3;
			int attr0 = objMem.getHalfWord(base) & 0xFFFF;
			int attr1 = objMem.getHalfWord(base + 2) & 0xFFFF;
			int attr2 = objMem.getHalfWord(base + 4) & 0xFFFF;
			int xSize = objMem.getXSize(obj);
			int ySize = objMem.getYSize(obj);
			int x = objMem.getXCoordinate(obj);
			int y = objMem.getYCoordinate(obj);
			if (y >= 160) y -= 256;
			boolean rot = objMem.isRotScalEnabled(obj);
			boolean dbl = objMem.isDoubleSizeEnabled(obj);
			boolean hidden = (!rot && !objMem.isDisplayable(obj));
			int mode = objMem.getOBJMode(obj);
			int pri = objMem.getPriority(obj);
			int tile = objMem.getTileNumber(obj);
			int pal = objMem.getPaletteNumber(obj);
			boolean pal256 = objMem.is256ColorPalette(obj);
			out.printf("OBJ%03d attr0=%04X attr1=%04X attr2=%04X x=%4d y=%4d size=%dx%d pri=%d tile=%03X pal=%02X mode=%d rs=%b dbl=%b 256=%b hidden=%b%n",
					obj, attr0, attr1, attr2, x, y, xSize, ySize, pri, tile, pal, mode, rot, dbl, pal256, hidden);
		}
		out.println();
	}

	private synchronized void dumpFrame() {
		String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS").format(new Date());
		File dumpDir = new File(System.getProperty("ygba.dump.dir", "dumps"));
		if (!dumpDir.exists() && !dumpDir.mkdirs()) {
			System.out.println("[DUMP] failed creating directory: " + dumpDir.getAbsolutePath());
			return;
		}

		File pngFile = new File(dumpDir, "frame-" + stamp + ".png");
		File txtFile = new File(dumpDir, "frame-" + stamp + ".txt");
		boolean wasPaused = pauseMenuItem.isSelected();
		boolean resumeAfterDump = (!wasPaused && ygba.isReady());

		try {
			if (resumeAfterDump) ygba.stop();
			BufferedImage frame = new BufferedImage(
					ygba.getGraphics().XScreenSize, ygba.getGraphics().YScreenSize,
					BufferedImage.TYPE_INT_ARGB);
			frame.setRGB(0, 0, ygba.getGraphics().XScreenSize, ygba.getGraphics().YScreenSize,
					ygba.getGraphics().getPixels(), 0, ygba.getGraphics().XScreenSize);
			long frameCRC32 = crc32Frame(frame);
			ImageIO.write(frame, "png", pngFile);
			dumpState(txtFile, pngFile.getName(), frameCRC32);
			System.out.println("[DUMP] frame=" + pngFile.getAbsolutePath());
			System.out.println("[DUMP] state=" + txtFile.getAbsolutePath());
		} catch (IOException e) {
			System.out.println("[DUMP] failed: " + e.getMessage());
		} finally {
			if (resumeAfterDump) ygba.run();
		}
	}

	private void dumpState(File txtFile, String frameFileName, long frameCRC32) throws IOException {
		ARM7TDMI cpu = ygba.getCPU();
		try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(txtFile)))) {
			out.println("frameFile=" + frameFileName);
			out.println("frameARGBCRC32=" + hex8((int) frameCRC32));
			out.println("timestamp=" + new Date().toString());
			out.println("romSource=" + memory.getLoadedROMSource());
			out.println("romCRC32=" + hex8((int) memory.getLoadedROMCRC32()));
			out.println("biosSource=" + memory.getLoadedBIOSSource());
			out.println("biosCRC32=" + hex8((int) memory.getLoadedBIOSCRC32()));
			out.println();

			out.println("[cpu]");
			for (int i = 0; i <= 15; i++) {
				out.println("R" + i + "=" + hex8(cpu.getRegister(i)));
			}
			out.println("PC_CURRENT=" + hex8(cpu.getCurrentPC()));
			out.println("CPSR=" + hex8(cpu.getCPSR()));
			out.println("MODE=" + cpu.getModeName());
			out.println("T=" + cpu.getTFlag() + " I=" + cpu.getIFlag() + " F=" + cpu.getFFlag() +
					" N=" + cpu.getNFlag() + " Z=" + cpu.getZFlag() + " C=" + cpu.getCFlag() + " V=" + cpu.getVFlag());
			out.println();

			out.println("[video]");
			out.println("videoMode=" + iorMem.getVideoMode());
			dumpHalfWord(out, iorMem, "DISPCNT", IORegMemory.REG_DISPCNT);
			dumpHalfWord(out, iorMem, "DISPSTAT", IORegMemory.REG_DISPSTAT);
			dumpHalfWord(out, iorMem, "VCOUNT", IORegMemory.REG_VCOUNT);
			dumpHalfWord(out, iorMem, "BG0CNT", IORegMemory.REG_BG0CNT);
			dumpHalfWord(out, iorMem, "BG1CNT", IORegMemory.REG_BG1CNT);
			dumpHalfWord(out, iorMem, "BG2CNT", IORegMemory.REG_BG2CNT);
			dumpHalfWord(out, iorMem, "BG3CNT", IORegMemory.REG_BG3CNT);
			dumpHalfWord(out, iorMem, "BG0HOFS", IORegMemory.REG_BG0HOFS);
			dumpHalfWord(out, iorMem, "BG0VOFS", IORegMemory.REG_BG0VOFS);
				dumpHalfWord(out, iorMem, "BG1HOFS", IORegMemory.REG_BG1HOFS);
				dumpHalfWord(out, iorMem, "BG1VOFS", IORegMemory.REG_BG1VOFS);
				dumpHalfWord(out, iorMem, "BG2HOFS", IORegMemory.REG_BG2HOFS);
				dumpHalfWord(out, iorMem, "BG2VOFS", IORegMemory.REG_BG2VOFS);
				dumpHalfWord(out, iorMem, "BG3HOFS", IORegMemory.REG_BG3HOFS);
				dumpHalfWord(out, iorMem, "BG3VOFS", IORegMemory.REG_BG3VOFS);
				dumpHalfWord(out, iorMem, "BG2PA", IORegMemory.REG_BG2PA);
				dumpHalfWord(out, iorMem, "BG2PB", IORegMemory.REG_BG2PB);
				dumpHalfWord(out, iorMem, "BG2PC", IORegMemory.REG_BG2PC);
				dumpHalfWord(out, iorMem, "BG2PD", IORegMemory.REG_BG2PD);
				dumpWord(out, iorMem, "BG2X", IORegMemory.REG_BG2X);
				dumpWord(out, iorMem, "BG2Y", IORegMemory.REG_BG2Y);
				dumpHalfWord(out, iorMem, "BG3PA", IORegMemory.REG_BG3PA);
				dumpHalfWord(out, iorMem, "BG3PB", IORegMemory.REG_BG3PB);
				dumpHalfWord(out, iorMem, "BG3PC", IORegMemory.REG_BG3PC);
				dumpHalfWord(out, iorMem, "BG3PD", IORegMemory.REG_BG3PD);
				dumpWord(out, iorMem, "BG3X", IORegMemory.REG_BG3X);
				dumpWord(out, iorMem, "BG3Y", IORegMemory.REG_BG3Y);
				dumpHalfWord(out, iorMem, "WIN0H", IORegMemory.REG_WIN0H);
				dumpHalfWord(out, iorMem, "WIN1H", IORegMemory.REG_WIN1H);
				dumpHalfWord(out, iorMem, "WIN0V", IORegMemory.REG_WIN0V);
			dumpHalfWord(out, iorMem, "WIN1V", IORegMemory.REG_WIN1V);
			dumpHalfWord(out, iorMem, "WININ", IORegMemory.REG_WININ);
			dumpHalfWord(out, iorMem, "WINOUT", IORegMemory.REG_WINOUT);
			dumpHalfWord(out, iorMem, "MOSAIC", IORegMemory.REG_MOSAIC);
			dumpHalfWord(out, iorMem, "BLDCNT", IORegMemory.REG_BLDMOD);
				dumpHalfWord(out, iorMem, "BLDALPHA", IORegMemory.REG_COLEV);
				dumpHalfWord(out, iorMem, "BLDY", IORegMemory.REG_COLY);
				out.println();

				dumpMemoryHashes(out);
				dumpMemoryWriteCounters(out);
				dumpObjectSummary(out);
				dumpOAMEntries(out);
				out.println("[layers]");
				out.println("BG0LayerCRC32=" + hex8((int) ygba.getGraphics().getLayerCRC32(0)));
				out.println("BG1LayerCRC32=" + hex8((int) ygba.getGraphics().getLayerCRC32(1)));
				out.println("BG2LayerCRC32=" + hex8((int) ygba.getGraphics().getLayerCRC32(2)));
				out.println("BG3LayerCRC32=" + hex8((int) ygba.getGraphics().getLayerCRC32(3)));
				out.println("OBJLayerCRC32=" + hex8((int) ygba.getGraphics().getLayerCRC32(4)));
				out.println("BG0LayerNonZero=" + ygba.getGraphics().getLayerNonZeroCount(0));
				out.println("BG1LayerNonZero=" + ygba.getGraphics().getLayerNonZeroCount(1));
				out.println("BG2LayerNonZero=" + ygba.getGraphics().getLayerNonZeroCount(2));
				out.println("BG3LayerNonZero=" + ygba.getGraphics().getLayerNonZeroCount(3));
				out.println("OBJLayerNonZero=" + ygba.getGraphics().getLayerNonZeroCount(4));
				out.println();
				dumpDMAState(out);
				dumpDMATelemetry(out);
				dumpTimerAndKeyState(out);

				out.println("[interrupts]");
				dumpHalfWord(out, iorMem, "IME", IORegMemory.REG_IME);
				dumpHalfWord(out, iorMem, "IE", IORegMemory.REG_IE);
				dumpHalfWord(out, iorMem, "IF", IORegMemory.REG_IF);
		}
	}

	public void keyPressed(KeyEvent ke) {
		int keyCode = ke.getKeyCode();
		boolean isControlDown = ke.isControlDown();
		boolean isAltDown = ke.isAltDown();

		switch (keyCode) {
		case OpenBIOSKey:
			openBIOSMenuItem.doClick();
			break;
		case OpenROMKey:
			openROMMenuItem.doClick();
			break;
		case ResetKey:
			if (isControlDown)
				resetMenuItem.doClick();
			break;
		case PauseKey:
			if (isControlDown)
				pauseMenuItem.doClick();
			break;
		case LaunchDebuggerKey:
			if (isAltDown)
				debuggerMenuItem.doClick();
			break;
		case DumpFrameKey:
			dumpFrame();
			break;
		}

		int btn = keyToButton(ke.getKeyCode());
		if (btn != 0) iorMem.pressButton(btn);
	}

	public void keyReleased(KeyEvent ke) {
		int btn = keyToButton(ke.getKeyCode());
		if (btn != 0) iorMem.releaseButton(btn);
	}

	private static int keyToButton(int keyCode) {
		switch (keyCode) {
			case KeyEvent.VK_X:          return IORegMemory.BTN_A;
			case KeyEvent.VK_C:          return IORegMemory.BTN_B;
			case KeyEvent.VK_BACK_SPACE: return IORegMemory.BTN_SELECT;
			case KeyEvent.VK_SPACE:      return IORegMemory.BTN_SELECT;
			case KeyEvent.VK_ENTER:      return IORegMemory.BTN_START;
			case KeyEvent.VK_RIGHT:      return IORegMemory.BTN_RIGHT;
			case KeyEvent.VK_LEFT:       return IORegMemory.BTN_LEFT;
			case KeyEvent.VK_UP:         return IORegMemory.BTN_UP;
			case KeyEvent.VK_DOWN:       return IORegMemory.BTN_DOWN;
			case KeyEvent.VK_D:          return IORegMemory.BTN_R;
			case KeyEvent.VK_S:          return IORegMemory.BTN_L;
			default:                     return 0;
		}
	}

	public void keyTyped(KeyEvent ke) {
	}

	public void mouseClicked(MouseEvent me) {
		requestInputFocus();
	}

	public void mouseEntered(MouseEvent me) {
	}

	public void mouseExited(MouseEvent me) {
	}

	public void mousePressed(MouseEvent me) {
		requestInputFocus();
		if (me.isPopupTrigger())
			popupMenu.show(gfxScreen, me.getX(), me.getY());
	}

	public void mouseReleased(MouseEvent me) {
		requestInputFocus();
		if (me.isPopupTrigger())
			popupMenu.show(gfxScreen, me.getX(), me.getY());
	}

	private void setupPopupMenu(boolean isRunning) {
		resetMenuItem.setEnabled(isRunning);
		debuggerMenuItem.setEnabled(isRunning);
	}

}
