package ygba.gfx;

import java.awt.*;
import java.awt.image.*;
import javax.swing.*;

public final class GFXScreen
        extends JComponent
        implements GFX.FrameListener {

    private GFX gfx;
    private MemoryImageSource imageSource;
    private Image image;

    private static final int NATIVE_W = GFX.XScreenSize;  // 240
    private static final int NATIVE_H = GFX.YScreenSize;  // 160


    public GFXScreen(GFX gfx) {
        this.gfx = gfx;

        int[] pixels = gfx.getPixels();
        imageSource = new MemoryImageSource(NATIVE_W, NATIVE_H,
            new DirectColorModel(32, 0x00FF0000, 0x0000FF00, 0x000000FF), pixels, 0, NATIVE_W);
        imageSource.setAnimated(true);
        image = Toolkit.getDefaultToolkit().createImage(imageSource);

        gfx.setFrameListener(this);

        setPreferredSize(new Dimension(NATIVE_W * 2, NATIVE_H * 2));
        setMinimumSize(new Dimension(NATIVE_W, NATIVE_H));
        setBackground(Color.BLACK);
        setOpaque(true);
    }

    public void onFrameReady(int[] pixels) {
        imageSource.newPixels();
    }

    protected void paintComponent(Graphics g) {
        int panelW = getWidth();
        int panelH = getHeight();

        // Fill background with black (letterbox/pillarbox)
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, panelW, panelH);

        // Compute scaled size maintaining 3:2 aspect ratio
        int scaledW, scaledH;
        if (panelW * NATIVE_H > panelH * NATIVE_W) {
            // Height is the limiting dimension
            scaledH = panelH;
            scaledW = panelH * NATIVE_W / NATIVE_H;
        } else {
            // Width is the limiting dimension
            scaledW = panelW;
            scaledH = panelW * NATIVE_H / NATIVE_W;
        }

        int x = (panelW - scaledW) / 2;
        int y = (panelH - scaledH) / 2;

        g.drawImage(image, x, y, scaledW, scaledH, this);
    }

    public void clear() {
        gfx.reset();
    }

}
