package ygba.time;

import ygba.memory.Memory;
import ygba.memory.IORegMemory;

public final class Timer {

    private int time, reload, rest, period;
    private boolean isEnabled, isIRQEnabled, isCascadeEnabled;
    private Timer nextTimer;

    private int timerNumber;
    private short timerInterruptBit;

    private IORegMemory iorMem;


    public Timer(Timer nextTimer, int timerNumber) {
        this.nextTimer = nextTimer;
        this.timerNumber = timerNumber;
        timerInterruptBit = (short) (0x0008 << timerNumber);
        reset();
    }

    public void connectToMemory(Memory memory) {
        iorMem = memory.getIORegMemory();
    }

    public void reset() {
        time = 0;
        reload = 0;
        rest = 0;
        period = 1;
        isEnabled = isIRQEnabled = isCascadeEnabled = false;
    }

    public void updateState(short control) {
        boolean wasEnabled = isEnabled;

        switch (control & 0x0003) {
            case 0: period = 1; break;
            case 1: period = 64; break;
            case 2: period = 256; break;
            case 3: period = 1024; break;
        }
        isEnabled = ((control & 0x0080) != 0);
        isIRQEnabled = ((control & 0x0040) != 0);
        isCascadeEnabled = ((control & 0x0004) != 0);

        // When timer transitions from disabled to enabled, load reload value
        if (!wasEnabled && isEnabled) {
            time = reload;
            rest = 0;
        }
    }

    public String getName() {
        return "Timer" + timerNumber;
    }

    public short getTime() {
        return (short) time;
    }

    public void setTime(int t) {
        reload = t & 0x0000FFFF;
    }

    public void addTime(int t) {
        if (isEnabled && !isCascadeEnabled) {
            rest += t;
            while (rest >= period) {
                time++;
                if (time > 0x0000FFFF) {
                    time = reload;
                    if (nextTimer != null) nextTimer.addOverflowTime(1);
                    if (isIRQEnabled) iorMem.generateInterrupt(timerInterruptBit);
                }
                rest -= period;
            }
        }
    }

    public void addOverflowTime(int t) {
        if (!isEnabled || !isCascadeEnabled) return;

        while (t > 0) {
            time++;
            if (time > 0x0000FFFF) {
                time = reload;
                if (nextTimer != null) nextTimer.addOverflowTime(1);
                if (isIRQEnabled) iorMem.generateInterrupt(timerInterruptBit);
            }
            t--;
        }
    }

}
