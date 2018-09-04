package com.brentvatne.react;

import android.annotation.SuppressLint;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.events.RCTEventEmitter;
import com.brentvatne.react.ReactVideoViewManager;
import com.brentvatne.react.ScalableType;
import com.brentvatne.react.ScaleManager;
import com.brentvatne.react.Size;

import java.math.BigDecimal;

import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

public class ReactVideoView extends TextureView implements
        IMediaPlayer.OnPreparedListener,
        IMediaPlayer.OnErrorListener,
        IMediaPlayer.OnBufferingUpdateListener,
        IMediaPlayer.OnCompletionListener,
        IMediaPlayer.OnInfoListener,
        IMediaPlayer.OnVideoSizeChangedListener,
        TextureView.SurfaceTextureListener,
        LifecycleEventListener {

    @Override
    public void onVideoSizeChanged(IMediaPlayer iMediaPlayer, int i, int i1, int i2, int i3) {
        if (!mMediaPlayerValid) {
            return;
        }
        scaleVideoSize();
    }

    private void scaleVideoSize() {
        int videoWidth = plMediaPlayer.getVideoWidth();
        int videoHeight = plMediaPlayer.getVideoHeight();

        if (videoWidth == 0 || videoHeight == 0) {
            return;
        }

        Size viewSize = new Size(getWidth(), getHeight());
        Size videoSize = new Size(videoWidth, videoHeight);
        ScaleManager scaleManager = new ScaleManager(viewSize, videoSize);
        Matrix matrix = scaleManager.getScaleMatrix(mResizeMode);
        if (matrix != null) {
            setTransform(matrix);
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
        Surface surface = new Surface(surfaceTexture);
        if (plMediaPlayer != null) {
            plMediaPlayer.setSurface(surface);
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

    }

    public enum Events {
        EVENT_LOAD_START("onVideoLoadStart"),
        EVENT_LOAD("onVideoLoad"),
        EVENT_ERROR("onVideoError"),
        EVENT_PROGRESS("onVideoProgress"),
        EVENT_TIMED_METADATA("onTimedMetadata"),
        EVENT_SEEK("onVideoSeek"),
        EVENT_END("onVideoEnd"),
        EVENT_STALLED("onPlaybackStalled"),
        EVENT_RESUME("onPlaybackResume"),
        EVENT_READY_FOR_DISPLAY("onReadyForDisplay"),
        EVENT_FULLSCREEN_WILL_PRESENT("onVideoFullscreenPlayerWillPresent"),
        EVENT_FULLSCREEN_DID_PRESENT("onVideoFullscreenPlayerDidPresent"),
        EVENT_FULLSCREEN_WILL_DISMISS("onVideoFullscreenPlayerWillDismiss"),
        EVENT_FULLSCREEN_DID_DISMISS("onVideoFullscreenPlayerDidDismiss"),
        EVENT_IS_PLAYING("isPlaying");

        private final String mName;

        Events(final String name) {
            mName = name;
        }

        @Override
        public String toString() {
            return mName;
        }
    }

    public static final String EVENT_PROP_FAST_FORWARD = "canPlayFastForward";
    public static final String EVENT_PROP_SLOW_FORWARD = "canPlaySlowForward";
    public static final String EVENT_PROP_SLOW_REVERSE = "canPlaySlowReverse";
    public static final String EVENT_PROP_REVERSE = "canPlayReverse";
    public static final String EVENT_PROP_STEP_FORWARD = "canStepForward";
    public static final String EVENT_PROP_STEP_BACKWARD = "canStepBackward";

    public static final String EVENT_PROP_DURATION = "duration";
    public static final String EVENT_PROP_PLAYABLE_DURATION = "playableDuration";
    public static final String EVENT_PROP_SEEKABLE_DURATION = "seekableDuration";
    public static final String EVENT_PROP_CURRENT_TIME = "currentTime";
    public static final String EVENT_PROP_SEEK_TIME = "seekTime";
    public static final String EVENT_PROP_NATURALSIZE = "naturalSize";
    public static final String EVENT_PROP_WIDTH = "width";
    public static final String EVENT_PROP_HEIGHT = "height";
    public static final String EVENT_PROP_ORIENTATION = "orientation";
    public static final String EVENT_PROP_METADATA = "metadata";
    public static final String EVENT_PROP_TARGET = "target";
    public static final String EVENT_PROP_METADATA_IDENTIFIER = "identifier";
    public static final String EVENT_PROP_METADATA_VALUE = "value";

    public static final String EVENT_PROP_ERROR = "error";
    public static final String EVENT_PROP_WHAT = "what";
    public static final String EVENT_PROP_EXTRA = "extra";
    public static final String EVENT_PROP_IS_PLAYING = "isPlaying";

    private ThemedReactContext mThemedReactContext;
    private RCTEventEmitter mEventEmitter;

    private Handler mProgressUpdateHandler = new Handler();
    private Runnable mProgressUpdateRunnable = null;
    private IjkMediaPlayer plMediaPlayer;
    private int mVideoRotation;

    private String mSrcUriString = null;
    private ScalableType mResizeMode = ScalableType.LEFT_TOP;
    private boolean mRepeat = false;
    private boolean mPaused = false;
    private boolean mMuted = false;
    private float mVolume = 1.0f;
    private float mProgressUpdateInterval = 250.0f;
    private float mRate = 1.0f;
    private float mStereoPan = 0.0f;
    private boolean mPlayInBackground = false;
    private boolean mActiveStatePauseStatus = false;
    private boolean mActiveStatePauseStatusInitialized = false;
    private boolean mBackgroundPaused = false;

    private boolean mMediaPlayerValid = false; // True if mMediaPlayer is in prepared, started, paused or completed state.

    private int mVideoDuration = 0;
    private int mVideoBufferedDuration = 0;
    private boolean isCompleted = false;

    public ReactVideoView(ThemedReactContext themedReactContext) {
        super(themedReactContext);
        mThemedReactContext = themedReactContext;
        mEventEmitter = themedReactContext.getJSModule(RCTEventEmitter.class);
        themedReactContext.addLifecycleEventListener(this);
        IjkMediaPlayer.loadLibrariesOnce(null);
        IjkMediaPlayer.native_profileBegin("libijkplayer.so");
        mProgressUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                if (mMediaPlayerValid && !isCompleted && !mPaused && !mBackgroundPaused) {
                    WritableMap event = Arguments.createMap();
                    event.putDouble(EVENT_PROP_CURRENT_TIME, plMediaPlayer.getCurrentPosition() / 1000.0);
                    event.putDouble(EVENT_PROP_PLAYABLE_DURATION, mVideoBufferedDuration / 1000.0); //TODO:mBufferUpdateRunnable
                    event.putDouble(EVENT_PROP_SEEKABLE_DURATION, mVideoDuration / 1000.0);
                    mEventEmitter.receiveEvent(getId(), ReactVideoView.Events.EVENT_PROGRESS.toString(), event);

                    WritableMap ev = Arguments.createMap();
                    ev.putBoolean(EVENT_PROP_IS_PLAYING, plMediaPlayer.isPlaying());
                    mEventEmitter.receiveEvent(getId(), ReactVideoView.Events.EVENT_IS_PLAYING.toString(), ev);

                    // Check for update after an interval
                    mProgressUpdateHandler.postDelayed(mProgressUpdateRunnable, Math.round(mProgressUpdateInterval));
                }
            }
        };
    }

    @SuppressLint("DrawAllocation")
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        if (!changed || !mMediaPlayerValid) {
            return;
        }

        int videoWidth = plMediaPlayer.getVideoWidth();
        int videoHeight = plMediaPlayer.getVideoHeight();

        if (videoWidth == 0 || videoHeight == 0) {
            return;
        }

        Size viewSize = new Size(getWidth(), getHeight());
        Size videoSize = new Size(videoWidth, videoHeight);
        ScaleManager scaleManager = new ScaleManager(viewSize, videoSize);
        Matrix matrix = scaleManager.getScaleMatrix(mResizeMode);
        if (matrix != null) {
            setTransform(matrix);
        }
    }

    public void cleanupMediaPlayerResources() {
        mMediaPlayerValid = false;
        if (plMediaPlayer != null) {
            mMediaPlayerValid = false;
            plMediaPlayer.release();
        }
        this.mProgressUpdateHandler.removeCallbacks(mProgressUpdateRunnable);
        // stopPlayback();
    }

    public void setSrc(final String uriString) {

        mSrcUriString = uriString;
        mMediaPlayerValid = false;
        mVideoDuration = 0;
        mVideoBufferedDuration = 0;

        if (plMediaPlayer == null) {
            plMediaPlayer = new IjkMediaPlayer();

            plMediaPlayer.setOnErrorListener(this);
            plMediaPlayer.setOnPreparedListener(this);
            plMediaPlayer.setOnBufferingUpdateListener(this);
            plMediaPlayer.setOnCompletionListener(this);
            plMediaPlayer.setOnInfoListener(this);
            plMediaPlayer.setOnVideoSizeChangedListener(this);
            plMediaPlayer.setScreenOnWhilePlaying(true);
            setSurfaceTextureListener(this);
        }
        plMediaPlayer.reset();

        //  关闭播放器缓冲，这个必须关闭，否则会出现播放一段时间后，一直卡主，控制台打印 FFP_MSG_BUFFERING_START
        plMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", 0L);
        plMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 1L);

        try {
            plMediaPlayer.setDataSource(uriString);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        WritableMap src = Arguments.createMap();
        src.putString(ReactVideoViewManager.PROP_SRC_URI, uriString);
        WritableMap event = Arguments.createMap();
        event.putMap(ReactVideoViewManager.PROP_SRC, src);
        mEventEmitter.receiveEvent(getId(), ReactVideoView.Events.EVENT_LOAD_START.toString(), event);
        try {
            plMediaPlayer.prepareAsync();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setResizeModeModifier(final ScalableType resizeMode) {
        mResizeMode = resizeMode;
        if (mMediaPlayerValid) {
            scaleVideoSize();
            invalidate();
        }
    }

    public void setRepeatModifier(final boolean repeat) {

        mRepeat = repeat;

        if (mMediaPlayerValid) {
            plMediaPlayer.setLooping(repeat);
        }
    }

    public void setPausedModifier(final boolean paused) {

        mPaused = paused;
        if (!mMediaPlayerValid) {
            return;
        }

        if (mPaused) {
            if (plMediaPlayer.isPlaying()) {
                plMediaPlayer.pause();
            }
        } else {
            if (!plMediaPlayer.isPlaying()) {
                plMediaPlayer.start();
                setRateModifier(mRate);
            }
            mProgressUpdateHandler.removeCallbacks(mProgressUpdateRunnable);
            mProgressUpdateHandler.post(mProgressUpdateRunnable);
        }
        setKeepScreenOn(!mPaused);
    }

    // reduces the volume based on stereoPan
    private float calulateRelativeVolume() {
        float relativeVolume = (mVolume * (1 - Math.abs(mStereoPan)));
        // only one decimal allowed
        BigDecimal roundRelativeVolume = new BigDecimal(relativeVolume).setScale(1, BigDecimal.ROUND_HALF_UP);
        return roundRelativeVolume.floatValue();
    }

    public void setMutedModifier(final boolean muted) {
        mMuted = muted;

        if (!mMediaPlayerValid) {
            return;
        }
        if (mMuted) {
            plMediaPlayer.setVolume(0, 0);
        } else if (mStereoPan < 0) {
            // louder on the left channel
            plMediaPlayer.setVolume(mVolume, calulateRelativeVolume());
        } else if (mStereoPan > 0) {
            // louder on the right channel
            plMediaPlayer.setVolume(calulateRelativeVolume(), mVolume);
        } else {
            plMediaPlayer.setVolume(mVolume, mVolume);
        }
    }

    public void setVolumeModifier(final float volume) {
        mVolume = volume;
        setMutedModifier(mMuted);
    }

    public void setStereoPan(final float stereoPan) {
        mStereoPan = stereoPan;
        setMutedModifier(mMuted);
    }

    public void setProgressUpdateInterval(final float progressUpdateInterval) {
        mProgressUpdateInterval = progressUpdateInterval;
    }

    public void setRateModifier(final float rate) {
        mRate = rate;

        if (mMediaPlayerValid) {
            // TODO: Implement this.
        }
    }

    public void applyModifiers() {
        setResizeModeModifier(mResizeMode);
        setRepeatModifier(mRepeat);
        setPausedModifier(mPaused);
        setMutedModifier(mMuted);
        setProgressUpdateInterval(mProgressUpdateInterval);
        setRateModifier(mRate);
    }

    public void setPlayInBackground(final boolean playInBackground) {
        mPlayInBackground = playInBackground;
    }

    @Override
    public void onHostResume() {
        mBackgroundPaused = false;
        if (!mPlayInBackground && !mPlayInBackground && !mPaused) {
            new Handler().post(new Runnable() {
                @Override
                public void run() {
                    setPausedModifier(false);
                }
            });
        }
    }

    @Override
    public void onHostPause() {
        if (!mPlayInBackground && !mPaused && !mPlayInBackground) {
            mBackgroundPaused = true;
            setPausedModifier(true);
        }
    }

    @Override
    public void onHostDestroy() {
        IjkMediaPlayer.native_profileEnd();
    }

    public void seekTo(int msec) {

        if (mMediaPlayerValid) {
            WritableMap event = Arguments.createMap();
            event.putDouble(EVENT_PROP_CURRENT_TIME, plMediaPlayer.getCurrentPosition() / 1000.0);
            event.putDouble(EVENT_PROP_SEEK_TIME, msec / 1000.0);
            mEventEmitter.receiveEvent(getId(), ReactVideoView.Events.EVENT_SEEK.toString(), event);

            plMediaPlayer.seekTo(msec);
            if (isCompleted && mVideoDuration != 0 && msec < mVideoDuration) {
                isCompleted = false;
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mMediaPlayerValid = false;
        setKeepScreenOn(false);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        setSrc(mSrcUriString);
    }

    @Override
    public void onBufferingUpdate(IMediaPlayer iMediaPlayer, int percent) {
        mVideoBufferedDuration = (int) Math.round((double) (mVideoDuration * percent) / 100.0);
    }

    @Override
    public void onCompletion(IMediaPlayer iMediaPlayer) {
        isCompleted = true;
        mEventEmitter.receiveEvent(getId(), ReactVideoView.Events.EVENT_END.toString(), null);
        if (!mRepeat) {
            setKeepScreenOn(false);
        }
    }

    @Override
    public boolean onError(IMediaPlayer iMediaPlayer, int i, int i1) {
        WritableMap error = Arguments.createMap();
        error.putInt(EVENT_PROP_WHAT, i);
        WritableMap event = Arguments.createMap();
        event.putMap(EVENT_PROP_ERROR, error);
        mEventEmitter.receiveEvent(getId(), ReactVideoView.Events.EVENT_ERROR.toString(), event);

        WritableMap ev = Arguments.createMap();
        ev.putBoolean(EVENT_PROP_IS_PLAYING, plMediaPlayer.isPlaying());
        mEventEmitter.receiveEvent(getId(), ReactVideoView.Events.EVENT_IS_PLAYING.toString(), ev);

        return true;
    }

    @Override
    public boolean onInfo(IMediaPlayer iMediaPlayer, int what, int extra) {
        switch (what) {
            case IMediaPlayer.MEDIA_INFO_BUFFERING_START:
                mEventEmitter.receiveEvent(getId(), Events.EVENT_STALLED.toString(), Arguments.createMap());
                break;
            case IMediaPlayer.MEDIA_INFO_BUFFERING_END:
                mEventEmitter.receiveEvent(getId(), Events.EVENT_RESUME.toString(), Arguments.createMap());
                break;
            case IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START:
                mEventEmitter.receiveEvent(getId(), Events.EVENT_READY_FOR_DISPLAY.toString(), Arguments.createMap());
                break;
            case IMediaPlayer.MEDIA_INFO_VIDEO_ROTATION_CHANGED:
                mVideoRotation = extra;
                break;
            default:
        }
        return false;
    }

    @Override
    public void onPrepared(IMediaPlayer iMediaPlayer) {
        mMediaPlayerValid = true;
        mVideoDuration = (int) plMediaPlayer.getDuration();
        WritableMap ev = Arguments.createMap();
        ev.putBoolean(EVENT_PROP_IS_PLAYING, plMediaPlayer.isPlaying());
        mEventEmitter.receiveEvent(getId(), ReactVideoView.Events.EVENT_IS_PLAYING.toString(), ev);
        setKeepScreenOn(true);
        WritableMap naturalSize = Arguments.createMap();
        naturalSize.putInt(EVENT_PROP_WIDTH, plMediaPlayer.getVideoWidth());
        naturalSize.putInt(EVENT_PROP_HEIGHT, plMediaPlayer.getVideoHeight());
        if (plMediaPlayer.getVideoWidth() > plMediaPlayer.getVideoHeight())
            naturalSize.putString(EVENT_PROP_ORIENTATION, "landscape");
        else
            naturalSize.putString(EVENT_PROP_ORIENTATION, "portrait");

        WritableMap event = Arguments.createMap();
        event.putDouble(EVENT_PROP_DURATION, mVideoDuration / 1000.0);
        event.putDouble(EVENT_PROP_CURRENT_TIME, plMediaPlayer.getCurrentPosition() / 1000.0);
        event.putMap(EVENT_PROP_NATURALSIZE, naturalSize);
        // TODO: Actually check if you can.
        event.putBoolean(EVENT_PROP_FAST_FORWARD, true);
        event.putBoolean(EVENT_PROP_SLOW_FORWARD, true);
        event.putBoolean(EVENT_PROP_SLOW_REVERSE, true);
        event.putBoolean(EVENT_PROP_REVERSE, true);
        event.putBoolean(EVENT_PROP_FAST_FORWARD, true);
        event.putBoolean(EVENT_PROP_STEP_BACKWARD, true);
        event.putBoolean(EVENT_PROP_STEP_FORWARD, true);
        mEventEmitter.receiveEvent(getId(), ReactVideoView.Events.EVENT_LOAD.toString(), event);
        applyModifiers();
    }
}
