
package tv.cloudwalker.player;

import android.os.Bundle;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

import java.util.ArrayList;
import java.util.List;

public class VideoSupportActivity extends FragmentActivity {

    private List<PictureInPictureListener> mListeners = new ArrayList<>();
//    private TextView mTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        if (savedInstanceState == null) {
//            SampleVideoSupportFragment sampleVideoSupportFragment = new SampleVideoSupportFragment();
//            getSupportFragmentManager().beginTransaction()
//                    .replace(android.R.id.content, sampleVideoSupportFragment)
//                    .commit();
//        }
//        setContentView(R.layout.playback_activity);
//        mTextView = findViewById(R.id.loggingText);
        if (savedInstanceState == null) {
            CloudwalkerPlayerFragment sampleVideoSupportFragment = new CloudwalkerPlayerFragment();
            getSupportFragmentManager().beginTransaction()
                    .replace(android.R.id.content, sampleVideoSupportFragment)
                    .commit();
        }
    }


//    public void logging(long serverTS , long serverCurrentPosition , long myCurrentPosition){
//
//        mTextView.append("\n\n");
//        mTextView.append("TARGET TS => "+serverTS + "\n");
//        mTextView.append("MY TS => "+ System.currentTimeMillis() + "\n");
//        mTextView.append("TARGET SEEK => "+serverCurrentPosition + "\n");
//        mTextView.append("MY SEEK => "+myCurrentPosition + "\n");
//        mTextView.append("TS DIFF  => "+( System.currentTimeMillis() - serverTS )+ "\n");
//        mTextView.append("SEEKING TO => "+( serverCurrentPosition + (System.currentTimeMillis() - serverTS )) + "\n");
//
//        final int scrollAmount = mTextView.getLayout().getLineTop(mTextView.getLineCount()) - mTextView.getHeight();
//        if (scrollAmount > 0)
//            mTextView.scrollTo(0, scrollAmount);
//        else
//            mTextView.scrollTo(0, 0);
//    }


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
