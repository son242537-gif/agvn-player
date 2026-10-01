package com.winlator.cmod.widget;

import android.content.Context;
import android.view.SurfaceView;

import com.winlator.cmod.renderer.GPUImage;
import com.winlator.cmod.renderer.ViewTransformation;
import com.winlator.cmod.xserver.Drawable;
import com.winlator.cmod.xserver.Window;
import com.winlator.cmod.xserver.XServer;

public abstract class XServerRendererView extends SurfaceView {

    public final ViewTransformation viewTransformation = new ViewTransformation();
    protected final XServer xServer;

    protected XServerRendererView(Context context, XServer xServer) {
        super(context);
        this.xServer = xServer;
    }

    public abstract void onPause();
    public abstract void onResume();
    public abstract void onDestroy();

    public abstract void toggleFullscreen();
    public abstract boolean isFullscreen();

    public abstract void setCursorVisible(boolean visible);
    public abstract void setScreenOffsetYRelativeToCursor(boolean relative);
    public abstract void setMagnifierZoom(float zoom);
    public abstract float getMagnifierZoom();
    public abstract void setUnviewableWMClasses(String... classes);

    public abstract void addDirectContent(int windowId, Drawable drawable, GPUImage gpuImage);
    public abstract void nativeRemoveDirectContent(int windowId, int pixmapId);
    public abstract void nativeSetCompositeRedirected(int windowId, boolean redirected);
    public abstract void nativeCompositeRedirect(int srcDrawableId, int dstDrawableId, short dstX, short dstY);

    public abstract void onUpdateWindowContentDirect(Window window, Drawable drawable);
    public abstract void onUpdateWindowContentDirect(Window window, Drawable drawable, short xOff, short yOff);
    public abstract void onPointerMove(short x, short y);
    public abstract void requestRender();
    public abstract void queueEvent(Runnable action);
    public abstract void onSurfaceChanged(int width, int height);
    public abstract void forceCleanup();
    public abstract void setFpsWindowId(int windowId);
    public abstract void setPipMode(boolean pipMode);
    public abstract void setFrameRating(Object frameRating);

    public abstract void setFpsLimit(int fps);
    public abstract int getFpsLimit();

    private volatile boolean vsyncPacing;

    /** AGVN: true when this game's FPS limit is set to follow the screen's vsync (AgvnVsyncLimiter); off by default. */
    public boolean isVsyncPacing() { return vsyncPacing; }
    public void setVsyncPacing(boolean on) { vsyncPacing = on; }

    private volatile boolean cpuBoost;

    /** AGVN: true when this game sends Android performance hints for its busiest threads (AgvnCpuBoost); off by default. */
    public boolean isCpuBoost() { return cpuBoost; }
    public void setCpuBoost(boolean on) { cpuBoost = on; }

    /** AGVN: under an FPS limit, draw only when {@link #requestPacedFrame} asks (renderers that can; others ignore it). */
    public void setPacedPresentation(boolean paced) {}

    /** AGVN: draw the newest content now, to be shown no earlier than {@code desiredPresentNs} (0 = as soon as it can). */
    public void requestPacedFrame(long desiredPresentNs) {}
}
