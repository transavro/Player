package tv.cloudwalker.player;

import android.os.Bundle;

import androidx.fragment.app.FragmentActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Host PlaybackFragment and provide PIP events.
 */
public class PlaybackSupportActivity extends FragmentActivity {
    private List<PictureInPictureListener> mListeners = new ArrayList<>();

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.playback_activity_support);
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        for (PictureInPictureListener listener : mListeners) {
            listener.onPictureInPictureModeChanged(isInPictureInPictureMode);
        }
    }

    /**
     * Register a PIP listener.
     */
    public void registerPictureInPictureListener(PictureInPictureListener listener) {
        mListeners.add(listener);
    }

    /**
     * Unregister a PIP listener.
     */
    public void unregisterPictureInPictureListener(PictureInPictureListener listener) {
        mListeners.remove(listener);
    }

    /**
     * Interface of PIP event on Activity.
     */
    public interface PictureInPictureListener {
        /**
         * Called when Activity's PIP mode is changed.
         */
        void onPictureInPictureModeChanged(boolean isInPictureInPictureMode);
    }
}
