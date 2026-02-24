package ygba.gfx;

import ygba.memory.Memory;
import ygba.memory.IORegMemory;
import ygba.memory.PaletteMemory;
import ygba.memory.VideoMemory;
import ygba.memory.ObjectMemory;

import java.awt.*;
import java.awt.image.*;
import java.util.zip.CRC32;

public final class GFX {

    public final static int
            XScreenSize = 240,
            YScreenSize = 160;

    private int[] pixels;
    private int[][] layerFrames = new int[5][XScreenSize * YScreenSize];

    // Per-layer scanline buffers (0 = transparent / not drawn)
    private int[][] bgPixels = new int[4][XScreenSize];
    private int[] objPixels = new int[XScreenSize];
    private int[] objPri = new int[XScreenSize];
    private boolean[] objSemiTrans = new boolean[XScreenSize];
    private boolean[] objWindowMask = new boolean[XScreenSize];

    // Pre-computed per-pixel window flags (6 bits: BG0-3, OBJ, SFX)
    private int[] windowFlags = new int[XScreenSize];

    private static final int LAYER_OBJ = 4;
    private static final int LAYER_BD = 5;
    private static final int WIN_SFX_BIT = 0x20;

    private MemoryImageSource imageSource;
    private Image image;

    private IORegMemory iorMem;
    private PaletteMemory palMem;
    private VideoMemory vidMem;
    private ObjectMemory objMem;


    public GFX() {
        pixels = new int[XScreenSize * YScreenSize];
        imageSource = new MemoryImageSource(XScreenSize, YScreenSize,
            new DirectColorModel(32, 0x00FF0000, 0x0000FF00, 0x000000FF), pixels, 0, XScreenSize);
        imageSource.setAnimated(true);
        image = Toolkit.getDefaultToolkit().createImage(imageSource);
    }

    public void connectToMemory(Memory memory) {
        iorMem = memory.getIORegMemory();
        palMem = (PaletteMemory) memory.getBank(0x05);
        vidMem = (VideoMemory) memory.getBank(0x06);
        objMem = (ObjectMemory) memory.getBank(0x07);
    }

    public void reset() {
        for (int i = 0; i < pixels.length; i++) pixels[i] = 0;
        for (int layer = 0; layer < layerFrames.length; layer++) {
            for (int i = 0; i < layerFrames[layer].length; i++) {
                layerFrames[layer][i] = 0;
            }
        }
        imageSource.newPixels();
        image.flush();
    }

    public Image getImage() { return image; }

    public BufferedImage getFrameImage() {
        BufferedImage frame = new BufferedImage(XScreenSize, YScreenSize, BufferedImage.TYPE_INT_ARGB);
        frame.setRGB(0, 0, XScreenSize, YScreenSize, pixels, 0, XScreenSize);
        return frame;
    }

    private static long crc32Pixels(int[] data) {
        CRC32 crc32 = new CRC32();
        for (int pixel : data) {
            crc32.update(pixel & 0xFF);
            crc32.update((pixel >>> 8) & 0xFF);
            crc32.update((pixel >>> 16) & 0xFF);
            crc32.update((pixel >>> 24) & 0xFF);
        }
        return crc32.getValue();
    }

    public long getLayerCRC32(int layer) {
        if (layer < 0 || layer >= layerFrames.length) return 0;
        return crc32Pixels(layerFrames[layer]);
    }

    public int getLayerNonZeroCount(int layer) {
        if (layer < 0 || layer >= layerFrames.length) return 0;
        int nonZero = 0;
        int[] frame = layerFrames[layer];
        for (int color : frame) {
            if (color != 0) nonZero++;
        }
        return nonZero;
    }


    public void drawLine(int y) {
        if (y < YScreenSize) {
            switch (iorMem.getVideoMode()) {
                case 0: drawMode0Line(y); break;
                case 1: drawMode1Line(y); break;
                case 2: drawMode2Line(y); break;
                case 3: drawMode3Line(y); break;
                case 4: drawMode4Line(y); break;
                case 5: drawMode5Line(y); break;
            }
        } else if (y == YScreenSize) {
            imageSource.newPixels();
        }
    }

    // ===== Scanline buffer management =====

    private void initScanlineBuffers(int yScreen) {
        computeWindowFlags(yScreen);
        for (int x = 0; x < XScreenSize; x++) {
            bgPixels[0][x] = 0;
            bgPixels[1][x] = 0;
            bgPixels[2][x] = 0;
            bgPixels[3][x] = 0;
            objPixels[x] = 0;
            objPri[x] = 4;
            objSemiTrans[x] = false;
        }
    }

    private static int clipWindowStart(int start, int max) {
        return (start > max) ? max : start;
    }

    private static int clipWindowEnd(int start, int end, int max) {
        // Hardware treats invalid ranges (end > max or start > end) as end=max.
        if (end > max || start > end) return max;
        return end;
    }

    private void computeWindowFlags(int yScreen) {
        boolean win0Enabled = iorMem.isWinEnabled(0);
        boolean win1Enabled = iorMem.isWinEnabled(1);
        boolean objWinEnabled = iorMem.isOBJWinEnabled();

        if (!win0Enabled && !win1Enabled && !objWinEnabled) {
            for (int x = 0; x < XScreenSize; x++) windowFlags[x] = 0x3F;
            return;
        }

        int outsideFlags = iorMem.getWinOutside();
        for (int x = 0; x < XScreenSize; x++) windowFlags[x] = outsideFlags;

        if (objWinEnabled) {
            drawOBJWindowMaskLine(yScreen);
            int objWinFlags = iorMem.getWinOBJ();
            for (int x = 0; x < XScreenSize; x++) {
                if (objWindowMask[x]) windowFlags[x] = objWinFlags;
            }
        }

        if (win1Enabled) {
            int left = clipWindowStart(iorMem.getWin1Left(), XScreenSize);
            int top = clipWindowStart(iorMem.getWin1Top(), YScreenSize);
            int right = clipWindowEnd(left, iorMem.getWin1Right(), XScreenSize);
            int bottom = clipWindowEnd(top, iorMem.getWin1Bottom(), YScreenSize);
            boolean inV = (yScreen >= top && yScreen < bottom);
            if (inV) {
                int flags = iorMem.getWinInside(1);
                for (int x = left; x < right; x++) windowFlags[x] = flags;
            }
        }

        if (win0Enabled) {
            int left = clipWindowStart(iorMem.getWin0Left(), XScreenSize);
            int top = clipWindowStart(iorMem.getWin0Top(), YScreenSize);
            int right = clipWindowEnd(left, iorMem.getWin0Right(), XScreenSize);
            int bottom = clipWindowEnd(top, iorMem.getWin0Bottom(), YScreenSize);
            boolean inV = (yScreen >= top && yScreen < bottom);
            if (inV) {
                int flags = iorMem.getWinInside(0);
                for (int x = left; x < right; x++) windowFlags[x] = flags;
            }
        }
    }

    // ===== Priority-sorted compositing (matches GBA hardware) =====

    private void composeScanline(int yScreen) {
        int blendMode = iorMem.getBlendMode();
        int eva = iorMem.getEVA();
        int evb = iorMem.getEVB();
        int evy = iorMem.getEVY();

        boolean[] firstTarget = new boolean[6];
        boolean[] secondTarget = new boolean[6];
        for (int i = 0; i < 5; i++) {
            firstTarget[i] = iorMem.isFirstTarget(i);
            secondTarget[i] = iorMem.isSecondTarget(i);
        }
        firstTarget[LAYER_BD] = iorMem.isFirstTargetBD();
        secondTarget[LAYER_BD] = iorMem.isSecondTargetBD();

        int[] bgPri = new int[4];
        for (int i = 0; i < 4; i++) bgPri[i] = iorMem.getPriority(i);

        int backdrop = toRGBA(palMem.getHalfWord(0));
        int lineOffset = yScreen * XScreenSize;

        for (int x = 0; x < XScreenSize; x++) {
            int wf = windowFlags[x];
            layerFrames[0][lineOffset + x] = bgPixels[0][x];
            layerFrames[1][lineOffset + x] = bgPixels[1][x];
            layerFrames[2][lineOffset + x] = bgPixels[2][x];
            layerFrames[3][lineOffset + x] = bgPixels[3][x];
            layerFrames[4][lineOffset + x] = objPixels[x];

            // Find top two non-transparent, window-visible pixels by priority
            int topColor = backdrop, botColor = backdrop;
            int topLayer = LAYER_BD, botLayer = LAYER_BD;
            boolean forceAlpha = false;
            boolean foundTop = false, foundBot = false;

            for (int p = 0; p <= 3; p++) {
                // OBJ wins over BG at same priority
                if (!foundBot && objPixels[x] != 0 && objPri[x] == p && (wf & 0x10) != 0) {
                    if (!foundTop) {
                        topColor = objPixels[x]; topLayer = LAYER_OBJ;
                        forceAlpha = objSemiTrans[x];
                        foundTop = true;
                    } else {
                        botColor = objPixels[x]; botLayer = LAYER_OBJ;
                        foundBot = true;
                    }
                }
                // BG0 has highest priority among BGs, BG3 lowest
                for (int bg = 0; bg <= 3 && !foundBot; bg++) {
                    if (bgPixels[bg][x] != 0 && bgPri[bg] == p && (wf & (1 << bg)) != 0) {
                        if (!foundTop) {
                            topColor = bgPixels[bg][x]; topLayer = bg;
                            foundTop = true;
                        } else {
                            botColor = bgPixels[bg][x]; botLayer = bg;
                            foundBot = true;
                        }
                    }
                }
                if (foundBot) break;
            }

            // Apply color special effects
            int color = topColor;

            if (forceAlpha && secondTarget[botLayer]) {
                // Semi-transparent OBJ: force alpha blend, ignores BLDCNT mode & window SFX bit
                color = alphaBlend(topColor, botColor, eva, evb);
            } else if (blendMode != 0 && (wf & WIN_SFX_BIT) != 0) {
                if (blendMode == 1 && firstTarget[topLayer] && secondTarget[botLayer]) {
                    color = alphaBlend(topColor, botColor, eva, evb);
                } else if (blendMode == 2 && firstTarget[topLayer]) {
                    color = brighten(topColor, evy);
                } else if (blendMode == 3 && firstTarget[topLayer]) {
                    color = darken(topColor, evy);
                }
            }

            pixels[lineOffset + x] = color;
        }
    }

    private static int alphaBlend(int a, int b, int eva, int evb) {
        int r = ((((a >>> 19) & 0x1F) * eva) + (((b >>> 19) & 0x1F) * evb)) >> 4;
        int g = ((((a >>> 11) & 0x1F) * eva) + (((b >>> 11) & 0x1F) * evb)) >> 4;
        int bl = ((((a >>> 3) & 0x1F) * eva) + (((b >>> 3) & 0x1F) * evb)) >> 4;
        if (r > 31) r = 31; if (g > 31) g = 31; if (bl > 31) bl = 31;
        return 0xFF000000 | (r << 19) | (g << 11) | (bl << 3);
    }

    private static int brighten(int c, int evy) {
        int r = (c >>> 19) & 0x1F, g = (c >>> 11) & 0x1F, b = (c >>> 3) & 0x1F;
        r += (31 - r) * evy >> 4; g += (31 - g) * evy >> 4; b += (31 - b) * evy >> 4;
        return 0xFF000000 | (r << 19) | (g << 11) | (b << 3);
    }

    private static int darken(int c, int evy) {
        int r = (c >>> 19) & 0x1F, g = (c >>> 11) & 0x1F, b = (c >>> 3) & 0x1F;
        r -= r * evy >> 4; g -= g * evy >> 4; b -= b * evy >> 4;
        return 0xFF000000 | (r << 19) | (g << 11) | (b << 3);
    }

    // ===== Mode draw methods =====

    private void drawMode0Line(int yScreen) {
        initScanlineBuffers(yScreen);
        if (iorMem.isBGEnabled(0)) drawBGTextModeLine(yScreen, 0);
        if (iorMem.isBGEnabled(1)) drawBGTextModeLine(yScreen, 1);
        if (iorMem.isBGEnabled(2)) drawBGTextModeLine(yScreen, 2);
        if (iorMem.isBGEnabled(3)) drawBGTextModeLine(yScreen, 3);
        if (iorMem.isOBJEnabled()) drawOBJLine(yScreen);
        composeScanline(yScreen);
    }

    private void drawMode1Line(int yScreen) {
        initScanlineBuffers(yScreen);
        if (iorMem.isBGEnabled(0)) drawBGTextModeLine(yScreen, 0);
        if (iorMem.isBGEnabled(1)) drawBGTextModeLine(yScreen, 1);
        if (iorMem.isBGEnabled(2)) drawBGRotScalModeLine(yScreen, 2);
        if (iorMem.isOBJEnabled()) drawOBJLine(yScreen);
        composeScanline(yScreen);
    }

    private void drawMode2Line(int yScreen) {
        initScanlineBuffers(yScreen);
        if (iorMem.isBGEnabled(2)) drawBGRotScalModeLine(yScreen, 2);
        if (iorMem.isBGEnabled(3)) drawBGRotScalModeLine(yScreen, 3);
        if (iorMem.isOBJEnabled()) drawOBJLine(yScreen);
        composeScanline(yScreen);
    }

    private void drawMode3Line(int yScreen) {
        initScanlineBuffers(yScreen);
        if (iorMem.isBGEnabled(2)) {
            boolean isMosaicEnabled = iorMem.isMosaicEnabled(2);
            int xMosaic = iorMem.getBGMosaicXSize();
            int yMosaic = iorMem.getBGMosaicYSize();
            int y = (isMosaicEnabled ? (yScreen - (yScreen % yMosaic)) : yScreen);
            for (int xScreen = 0; xScreen < XScreenSize; xScreen++) {
                int x = (isMosaicEnabled ? (xScreen - (xScreen % xMosaic)) : xScreen);
                short rgb15 = vidMem.getHalfWord(((y * XScreenSize) + x) * 2);
                bgPixels[2][xScreen] = toRGBA(rgb15);
            }
        }
        if (iorMem.isOBJEnabled()) drawOBJLine(yScreen);
        composeScanline(yScreen);
    }

    private void drawMode4Line(int yScreen) {
        initScanlineBuffers(yScreen);
        if (iorMem.isBGEnabled(2)) {
            int frameAddress = (iorMem.isFrame1Selected() ? 0xA000 : 0x0000);
            boolean isMosaicEnabled = iorMem.isMosaicEnabled(2);
            int xMosaic = iorMem.getBGMosaicXSize();
            int yMosaic = iorMem.getBGMosaicYSize();
            int y = (isMosaicEnabled ? (yScreen - (yScreen % yMosaic)) : yScreen);
            for (int xScreen = 0; xScreen < XScreenSize; xScreen++) {
                int x = (isMosaicEnabled ? (xScreen - (xScreen % xMosaic)) : xScreen);
                int colorIndex = vidMem.getByte(frameAddress + ((y * XScreenSize) + x)) & 0xFF;
                short rgb15 = palMem.getHalfWord(colorIndex * 2);
                bgPixels[2][xScreen] = toRGBA(rgb15);
            }
        }
        if (iorMem.isOBJEnabled()) drawOBJLine(yScreen);
        composeScanline(yScreen);
    }

    private void drawMode5Line(int yScreen) {
        initScanlineBuffers(yScreen);
        if (iorMem.isBGEnabled(2) && yScreen < 128) {
            int frameAddress = (iorMem.isFrame1Selected() ? 0xA000 : 0x0000);
            for (int xScreen = 0; xScreen < 160; xScreen++) {
                short rgb15 = vidMem.getHalfWord(frameAddress + ((yScreen * 160) + xScreen) * 2);
                bgPixels[2][xScreen] = toRGBA(rgb15);
            }
        }
        if (iorMem.isOBJEnabled()) drawOBJLine(yScreen);
        composeScanline(yScreen);
    }

    // ===== Background layer rendering =====

    private void drawBGTextModeLine(int yScreen, int bgNumber) {
        int characterBase = iorMem.getCharacterBaseAddress(bgNumber);
        int screenBase = iorMem.getScreenBaseAddress(bgNumber);

        int xSize = iorMem.getTextModeXSize(bgNumber);
        int ySize = iorMem.getTextModeYSize(bgNumber);
        int xMask = xSize - 1;
        int yMask = ySize - 1;

        int xOffset = iorMem.getXOffset(bgNumber);
        int yOffset = iorMem.getYOffset(bgNumber);

        boolean is256ColorPalette = iorMem.is256ColorPalette(bgNumber);
        int screenBlocksPerRow = (xSize >>> 8); // 1 (256px) or 2 (512px)

        boolean isMosaicEnabled = iorMem.isMosaicEnabled(bgNumber);
        int xMosaic = iorMem.getBGMosaicXSize();
        int yMosaic = iorMem.getBGMosaicYSize();

        int y = (isMosaicEnabled ? (yScreen - (yScreen % yMosaic)) : yScreen);
        y = (y + yOffset) & yMask;
        int mapY = y >>> 8;
        int localY = y & 0xFF;
        int yTileDataOffset = (localY >>> 3) * 32;
        int tileY = localY & 0x07;

        int[] buf = bgPixels[bgNumber];

        for (int xScreen = 0; xScreen < XScreenSize; xScreen++) {
            int x = (isMosaicEnabled ? (xScreen - (xScreen % xMosaic)) : xScreen);
            x = (x + xOffset) & xMask;

            int mapX = x >>> 8;
            int localX = x & 0xFF;
            int xTileDataOffset = (localX >>> 3);
            int tileX = localX & 0x07;

            int screenBlockOffset = ((mapY * screenBlocksPerRow) + mapX) * 0x0800;
            int tileDataOffset = ((yTileDataOffset + xTileDataOffset) * 2) + screenBlockOffset;
            short tileData = vidMem.getHalfWord(screenBase + tileDataOffset);

            int tileNumber = tileData & 0x03FF;
            int localTileY = tileY;
            if ((tileData & 0x0400) != 0) tileX = 7 - tileX;
            if ((tileData & 0x0800) != 0) localTileY = 7 - tileY;

            if (is256ColorPalette) {
                int colorIndex = vidMem.getByte(characterBase + (tileNumber * 64) + (localTileY * 8) + tileX) & 0xFF;
                if (colorIndex != 0) {
                    buf[xScreen] = toRGBA(palMem.getHalfWord(colorIndex * 2));
                }
            } else {
                int colorIndex = vidMem.getByte(characterBase + (tileNumber * 32) + (localTileY * 4) + (tileX / 2)) & 0xFF;
                if ((tileX & 0x01) != 0) colorIndex >>>= 4;
                else colorIndex &= 0x0F;
                if (colorIndex != 0) {
                    int paletteNumber = (tileData >>> 12) & 0x0F;
                    buf[xScreen] = toRGBA(palMem.getHalfWord(((paletteNumber * 16) + colorIndex) * 2));
                }
            }
        }
    }

    private void drawBGRotScalModeLine(int yScreen, int bgNumber) {
        int characterBase = iorMem.getCharacterBaseAddress(bgNumber);
        int screenBase = iorMem.getScreenBaseAddress(bgNumber);

        int xySize = iorMem.getRotScalModeXYSize(bgNumber);
        int xyMask = xySize - 1;

        int xCoordinate = iorMem.getXCoordinate(bgNumber);
        int yCoordinate = iorMem.getYCoordinate(bgNumber);

        int pa = iorMem.getPA(bgNumber);
        int pb = iorMem.getPB(bgNumber);
        int pc = iorMem.getPC(bgNumber);
        int pd = iorMem.getPD(bgNumber);

        boolean wraparoundEnabled = iorMem.isWraparoundOverflow(bgNumber);

        int xCur = (yScreen * pb) + xCoordinate;
        int yCur = (yScreen * pd) + yCoordinate;

        int[] buf = bgPixels[bgNumber];

        for (int xScreen = 0; xScreen < XScreenSize; xScreen++) {
            int x = xCur >> 8;
            int y = yCur >> 8;

            if (wraparoundEnabled) { x &= xyMask; y &= xyMask; }

            if (x >= 0 && x < xySize && y >= 0 && y < xySize) {
                int tileNumber = vidMem.getByte(screenBase + ((y >>> 3) * (xySize / 8)) + (x >>> 3)) & 0xFF;
                int colorIndex = vidMem.getByte(characterBase + (tileNumber * 64) + ((y & 7) * 8) + (x & 7)) & 0xFF;
                if (colorIndex != 0) {
                    buf[xScreen] = toRGBA(palMem.getHalfWord(colorIndex * 2));
                }
            }

            xCur += pa;
            yCur += pc;
        }
    }

    // ===== OBJ (sprite) rendering =====

    private void drawOBJWindowMaskLine(int yScreen) {
        for (int x = 0; x < XScreenSize; x++) objWindowMask[x] = false;

        int vidBase = 0x00010000;
        boolean is1DMapping = iorMem.isOBJ1DMapping();

        for (int objNumber = 127; objNumber >= 0; objNumber--) {
            if (!objMem.isOBJWindowMode(objNumber)) continue;

            boolean isRotScalEnabled = objMem.isRotScalEnabled(objNumber);
            int xSize = objMem.getXSize(objNumber);
            int ySize = objMem.getYSize(objNumber);
            if (xSize == 0 || ySize == 0) continue;

            int xCoordinate = objMem.getXCoordinate(objNumber);
            int yCoordinate = objMem.getYCoordinate(objNumber);

            boolean is256ColorPalette = objMem.is256ColorPalette(objNumber);
            int xTiles = xSize >>> 3;

            int firstTileNumber = objMem.getTileNumber(objNumber);
            int tileNumberIncrement;
            if (is1DMapping) {
                tileNumberIncrement = (is256ColorPalette ? xTiles * 2 : xTiles);
            } else {
                tileNumberIncrement = 32;
                if (is256ColorPalette) firstTileNumber &= 0xFFFE;
            }

            if (!isRotScalEnabled) {
                if (!objMem.isDisplayable(objNumber)) continue;

                boolean isHFlip = objMem.isHFlipEnabled(objNumber);
                boolean isVFlip = objMem.isVFlipEnabled(objNumber);

                int yC = yCoordinate;
                if (yC >= YScreenSize) yC -= 256;

                if (yScreen >= yC && yScreen < yC + ySize) {
                    int ySprite = yScreen - yC;

                    for (int xSprite = 0; xSprite < xSize; xSprite++) {
                        int xScreen = xCoordinate + xSprite;
                        if (xScreen < 0 || xScreen >= XScreenSize) continue;

                        int sx = isHFlip ? xSize - 1 - xSprite : xSprite;
                        int sy = isVFlip ? ySize - 1 - ySprite : ySprite;

                        if (isOBJPixelOpaque(vidBase, firstTileNumber, tileNumberIncrement,
                                is256ColorPalette, sx, sy)) {
                            objWindowMask[xScreen] = true;
                        }
                    }
                }
            } else {
                boolean isDoubleSize = objMem.isDoubleSizeEnabled(objNumber);

                int displayWidth = isDoubleSize ? xSize * 2 : xSize;
                int displayHeight = isDoubleSize ? ySize * 2 : ySize;

                int yC = yCoordinate;
                if (yC >= YScreenSize) yC -= 256;

                if (yScreen >= yC && yScreen < yC + displayHeight) {
                    int groupNumber = objMem.getRotScalGroupNumber(objNumber);
                    int pa = objMem.getPA(groupNumber);
                    int pb = objMem.getPB(groupNumber);
                    int pc = objMem.getPC(groupNumber);
                    int pd = objMem.getPD(groupNumber);

                    int halfW = xSize >> 1;
                    int halfH = ySize >> 1;
                    int iy = yScreen - yC - (displayHeight >> 1);

                    for (int ix0 = 0; ix0 < displayWidth; ix0++) {
                        int xScreen = xCoordinate + ix0;
                        if (xScreen < 0 || xScreen >= XScreenSize) continue;

                        int ix = ix0 - (displayWidth >> 1);
                        int texX = ((pa * ix + pb * iy) >> 8) + halfW;
                        int texY = ((pc * ix + pd * iy) >> 8) + halfH;

                        if (texX >= 0 && texX < xSize && texY >= 0 && texY < ySize &&
                                isOBJPixelOpaque(vidBase, firstTileNumber, tileNumberIncrement,
                                    is256ColorPalette, texX, texY)) {
                            objWindowMask[xScreen] = true;
                        }
                    }
                }
            }
        }
    }

    private void drawOBJLine(int yScreen) {
        int vidBase = 0x00010000;
        int palBase = 0x00000200;
        boolean is1DMapping = iorMem.isOBJ1DMapping();

        // Iterate 127→0 so lower OBJ numbers overwrite higher (at same priority)
        for (int objNumber = 127; objNumber >= 0; objNumber--) {
            boolean isRotScalEnabled = objMem.isRotScalEnabled(objNumber);

            int xSize = objMem.getXSize(objNumber);
            int ySize = objMem.getYSize(objNumber);
            if (xSize == 0 || ySize == 0) continue;

            if (objMem.isOBJWindowMode(objNumber)) continue;

            int xCoordinate = objMem.getXCoordinate(objNumber);
            int yCoordinate = objMem.getYCoordinate(objNumber);

            boolean is256ColorPalette = objMem.is256ColorPalette(objNumber);
            int paletteNumber = objMem.getPaletteNumber(objNumber);
            int xTiles = xSize >>> 3;

            int firstTileNumber = objMem.getTileNumber(objNumber);
            int tileNumberIncrement;
            if (is1DMapping) {
                tileNumberIncrement = (is256ColorPalette ? xTiles * 2 : xTiles);
            } else {
                tileNumberIncrement = 32;
                if (is256ColorPalette) firstTileNumber &= 0xFFFE;
            }

            int objPriority = objMem.getPriority(objNumber);
            boolean isSemiTransparent = objMem.isOBJMode1(objNumber);

            if (!isRotScalEnabled) {
                if (!objMem.isDisplayable(objNumber)) continue;

                boolean isHFlip = objMem.isHFlipEnabled(objNumber);
                boolean isVFlip = objMem.isVFlipEnabled(objNumber);

                int yC = yCoordinate;
                if (yC >= YScreenSize) yC -= 256;

                if (yScreen >= yC && yScreen < yC + ySize) {
                    int ySprite = yScreen - yC;

                    for (int xSprite = 0; xSprite < xSize; xSprite++) {
                        int xScreen = xCoordinate + xSprite;
                        if (xScreen < 0 || xScreen >= XScreenSize) continue;

                        int sx = isHFlip ? xSize - 1 - xSprite : xSprite;
                        int sy = isVFlip ? ySize - 1 - ySprite : ySprite;

                        int color = getOBJPixelColor(vidBase, palBase, firstTileNumber,
                            tileNumberIncrement, is256ColorPalette, paletteNumber, sx, sy);
                        if (color != 0 && (objPixels[xScreen] == 0 || objPriority <= objPri[xScreen])) {
                            objPixels[xScreen] = color;
                            objPri[xScreen] = objPriority;
                            objSemiTrans[xScreen] = isSemiTransparent;
                        }
                    }
                }
            } else {
                // Affine sprite
                boolean isDoubleSize = objMem.isDoubleSizeEnabled(objNumber);

                int displayWidth = isDoubleSize ? xSize * 2 : xSize;
                int displayHeight = isDoubleSize ? ySize * 2 : ySize;

                int yC = yCoordinate;
                if (yC >= YScreenSize) yC -= 256;

                if (yScreen >= yC && yScreen < yC + displayHeight) {
                    int groupNumber = objMem.getRotScalGroupNumber(objNumber);
                    int pa = objMem.getPA(groupNumber);
                    int pb = objMem.getPB(groupNumber);
                    int pc = objMem.getPC(groupNumber);
                    int pd = objMem.getPD(groupNumber);

                    int halfW = xSize >> 1;
                    int halfH = ySize >> 1;
                    int iy = yScreen - yC - (displayHeight >> 1);

                    for (int ix0 = 0; ix0 < displayWidth; ix0++) {
                        int xScreen = xCoordinate + ix0;
                        if (xScreen < 0 || xScreen >= XScreenSize) continue;

                        int ix = ix0 - (displayWidth >> 1);
                        int texX = ((pa * ix + pb * iy) >> 8) + halfW;
                        int texY = ((pc * ix + pd * iy) >> 8) + halfH;

                        if (texX >= 0 && texX < xSize && texY >= 0 && texY < ySize) {
                            int color = getOBJPixelColor(vidBase, palBase, firstTileNumber,
                                tileNumberIncrement, is256ColorPalette, paletteNumber, texX, texY);
                            if (color != 0 && (objPixels[xScreen] == 0 || objPriority <= objPri[xScreen])) {
                                objPixels[xScreen] = color;
                                objPri[xScreen] = objPriority;
                                objSemiTrans[xScreen] = isSemiTransparent;
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isOBJPixelOpaque(int vidBase, int firstTileNumber, int tileNumberIncrement,
                                     boolean is256ColorPalette, int x, int y) {
        int xTile = x >>> 3, yTile = y >>> 3;
        int tileX = x & 7, tileY = y & 7;

        if (is256ColorPalette) {
            int tileNumber = firstTileNumber + (yTile * tileNumberIncrement) + (xTile * 2);
            if (iorMem.getVideoMode() >= 3 && tileNumber < 512) return false;
            return ((vidMem.getByte(vidBase + (tileNumber * 32) + (tileY * 8) + tileX) & 0xFF) != 0);
        }

        int tileNumber = firstTileNumber + (yTile * tileNumberIncrement) + xTile;
        if (iorMem.getVideoMode() >= 3 && tileNumber < 512) return false;
        int colorIndex = vidMem.getByte(vidBase + (tileNumber * 32) + (tileY * 4) + (tileX / 2)) & 0xFF;
        if ((tileX & 1) != 0) colorIndex >>>= 4;
        else colorIndex &= 0x0F;
        return (colorIndex != 0);
    }

    private int getOBJPixelColor(int vidBase, int palBase, int firstTileNumber, int tileNumberIncrement,
                                  boolean is256ColorPalette, int paletteNumber, int x, int y) {
        int xTile = x >>> 3, yTile = y >>> 3;
        int tileX = x & 7, tileY = y & 7;

        if (is256ColorPalette) {
            int tileNumber = firstTileNumber + (yTile * tileNumberIncrement) + (xTile * 2);
            if (iorMem.getVideoMode() >= 3 && tileNumber < 512) return 0;
            int colorIndex = vidMem.getByte(vidBase + (tileNumber * 32) + (tileY * 8) + tileX) & 0xFF;
            if (colorIndex != 0) return toRGBA(palMem.getHalfWord(palBase + (colorIndex * 2)));
        } else {
            int tileNumber = firstTileNumber + (yTile * tileNumberIncrement) + xTile;
            if (iorMem.getVideoMode() >= 3 && tileNumber < 512) return 0;
            int colorIndex = vidMem.getByte(vidBase + (tileNumber * 32) + (tileY * 4) + (tileX / 2)) & 0xFF;
            if ((tileX & 1) != 0) colorIndex >>>= 4; else colorIndex &= 0x0F;
            if (colorIndex != 0) return toRGBA(palMem.getHalfWord(palBase + (((paletteNumber * 16) + colorIndex) * 2)));
        }
        return 0;
    }

    // ===== Color conversion =====

    private static int toRGBA(short rgb15) {
        int red   = (rgb15 & 0x001F) << 19;
        int green = (rgb15 & 0x03E0) <<  6;
        int blue  = (rgb15 & 0x7C00) >>> 7;
        return 0xFF000000 | red | green | blue;
    }

}
