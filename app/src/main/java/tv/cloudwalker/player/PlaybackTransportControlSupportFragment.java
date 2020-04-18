// CHECKSTYLE:OFF Generated code
/* This file is auto-generated from PlaybackTransportControlFragment.java.  DO NOT MODIFY. */

/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tv.cloudwalker.player;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;

import androidx.leanback.app.PlaybackSupportFragmentGlueHost;
import androidx.leanback.media.MediaPlayerAdapter;
import androidx.leanback.media.PlaybackGlue;
import androidx.leanback.widget.Action;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ClassPresenterSelector;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.PlaybackControlsRow;
import androidx.leanback.widget.SparseArrayObjectAdapter;

import presenter.StringPresenter;

/**
 * Example of PlaybackSupportFragment working with a PlaybackControlGlue.
 */
public class PlaybackTransportControlSupportFragment extends androidx.leanback.app.PlaybackSupportFragment implements PlaybackTransportControlSupportActivity.PictureInPictureListener {
    private static final String TAG = "TransportFragment";

    /**
     * Change this to choose a different overlay background.
     */
    private static final int BACKGROUND_TYPE = BG_DARK;


    // Media Session Token
    private static final String MEDIA_SESSION_COMPAT_TOKEN = "media session support video";

    private MediaSessionCompat mMediaSessionCompat;



    /**
     * Change the number of related content rows.
     */
    private static final int RELATED_CONTENT_ROWS = 3;

    private static final int ROW_CONTROLS = 0;

    private PlaybackTransportControlGlueSample<MediaPlayerAdapter> mGlue;

    public SparseArrayObjectAdapter getAdapter() {
        return (SparseArrayObjectAdapter) super.getAdapter();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        setBackgroundType(BACKGROUND_TYPE);

        createComponents(getActivity());
    }

    private void createComponents(Context context) {


        // create a media session inside of a fragment, and app developer can determine if connect
        // this media session to glue or not
        // as requested in b/64935838
        mMediaSessionCompat = new MediaSessionCompat(getActivity(), MEDIA_SESSION_COMPAT_TOKEN);

        mGlue = new PlaybackTransportControlGlueSample<MediaPlayerAdapter>(context, new MediaPlayerAdapter(getActivity())) {
            @Override
            public void onActionClicked(Action action) {
                if (action.getId() == R.id.lb_control_picture_in_picture) {
                    if (Build.VERSION.SDK_INT >= 24) {
                        getActivity().enterPictureInPictureMode();
                    }
                    return;
                }
                super.onActionClicked(action);
            }

        };

        bindGlue();

        ClassPresenterSelector selector = new ClassPresenterSelector();
        selector.addClassPresenter(ListRow.class, new ListRowPresenter());
        setAdapter(new SparseArrayObjectAdapter(selector));

        // Add related content rows
        for (int i = 0; i < RELATED_CONTENT_ROWS; ++i) {
            ArrayObjectAdapter listRowAdapter = new ArrayObjectAdapter(new StringPresenter());
            listRowAdapter.add("Some related content");
            listRowAdapter.add("Other related content");
            HeaderItem header = new HeaderItem(i, "Row " + i);
            getAdapter().set(ROW_CONTROLS + 1 + i, new ListRow(header, listRowAdapter));
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        ((PlaybackTransportControlSupportActivity) getActivity()).registerPictureInPictureListener(this);
    }



    private void bindGlue() {

        // If the glue is switched, re-register the media session
        mGlue.connectToMediaSession(mMediaSessionCompat);
        mGlue.setMode(PlaybackControlsRow.RepeatAction.INDEX_ONE);
        mGlue.setTitle("Title");
        mGlue.setSubtitle("Android developer");
        mGlue.setHost(new PlaybackSupportFragmentGlueHost(this));
        mGlue.setSeekProvider(new PlaybackSeekDataProviderSample(PlayerAdapter.FAUX_DURATION, 100));

        String uriPath = "https://storage.googleapis.com/android-tv/Sample videos/"
                + "April Fool's 2013/Explore Treasure Mode with Google Maps.mp4";

        mGlue.getPlayerAdapter().setDataSource(Uri.parse(uriPath));

        loadSeekData(mGlue);
        playWhenReady(mGlue);
    }



    static void playWhenReady(PlaybackGlue glue) {
        if (glue.isPrepared()) {
            glue.play();
        } else {
            glue.addPlayerCallback(new PlaybackGlue.PlayerCallback() {
                @Override
                public void onPreparedStateChanged(PlaybackGlue glue) {
                    if (glue.isPrepared()) {
                        glue.removePlayerCallback(this);
                        glue.play();
                    }
                }
            });
        }
    }

    static void loadSeekData(final PlaybackTransportControlGlueSample glue) {
        if (glue.isPrepared()) {
            glue.setSeekEnabled(true);
            glue.setSeekProvider(new PlaybackSeekDataProviderSample(glue.getDuration(), 1000));
        } else {
            glue.addPlayerCallback(new PlaybackGlue.PlayerCallback() {
                @Override
                public void onPreparedStateChanged(PlaybackGlue glue) {
                    if (glue.isPrepared()) {
                        glue.removePlayerCallback(this);
                        PlaybackTransportControlGlueSample transportControlGlue = (PlaybackTransportControlGlueSample) glue;
                        transportControlGlue.setSeekEnabled(true);
                        transportControlGlue.setSeekProvider(new PlaybackSeekDataProviderSample(transportControlGlue.getDuration(), 1000));
                    }
                }
            });
        }
    }

    @Override
    public void onStop() {
        ((PlaybackTransportControlSupportActivity) getActivity()).unregisterPictureInPictureListener(this);
        super.onStop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mGlue.disconnectToMediaSession();
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        if (isInPictureInPictureMode) {
            // Hide the controls in picture-in-picture mode.
            setControlsOverlayAutoHideEnabled(true);
            hideControlsOverlay(true);
        } else {
            setControlsOverlayAutoHideEnabled(mGlue.isPlaying());
        }
    }
}
