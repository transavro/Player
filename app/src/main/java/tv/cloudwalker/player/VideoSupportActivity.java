
package tv.cloudwalker.player;

import android.os.Bundle;

import androidx.fragment.app.FragmentActivity;

import java.util.ArrayList;
import java.util.List;

public class VideoSupportActivity extends FragmentActivity {

    private List<PictureInPictureListener> mListeners = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            SampleVideoSupportFragment sampleVideoSupportFragment = new SampleVideoSupportFragment();
            getSupportFragmentManager().beginTransaction()
                    .replace(android.R.id.content, sampleVideoSupportFragment)
                    .commit();
        }
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
