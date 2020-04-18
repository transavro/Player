package tv.cloudwalker.player;

import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.leanback.app.VideoSupportFragmentGlueHost;
import androidx.leanback.media.MediaPlayerAdapter;
import androidx.leanback.media.PlaybackGlue;
import androidx.leanback.widget.Action;
import androidx.leanback.widget.PlaybackControlsRow;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Method;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import main.MediaChatServiceGrpc;
import main.Watch;

import static main.Watch.PlayerState.PLAY;

public class SampleVideoSupportFragment extends androidx.leanback.app.VideoSupportFragment implements VideoSupportActivity.PictureInPictureListener {

    private static final String TAG = "##SVSF##";
    // Media Session Token
    private static final String MEDIA_SESSION_COMPAT_TOKEN = "media session support video";

    private PlaybackTransportControlGlueSample<MediaPlayerAdapter> mMediaPlayerGlue;

    private MediaSessionCompat mMediaSessionCompat;
    private ManagedChannel managedChannel;

    final VideoSupportFragmentGlueHost mHost = new VideoSupportFragmentGlueHost(SampleVideoSupportFragment.this);

    private String currentDataSource = "";

    private boolean isTV = false;

    //grpc
    private int sourceId, targetId;
    private MediaChatServiceGrpc.MediaChatServiceStub mediaChatServiceStub;


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
            glue.setSeekProvider(new PlaybackSeekDiskDataProvider(glue.getDuration(), glue.getDuration() / 100, "/data/user/0/tv.cloudwalker.player/files/seek/frame_%04d.jpg"));
        } else {
            glue.addPlayerCallback(new PlaybackGlue.PlayerCallback() {
                @Override
                public void onPreparedStateChanged(PlaybackGlue glue) {
                    if (glue.isPrepared()) {
                        glue.removePlayerCallback(this);
                        PlaybackTransportControlGlueSample transportControlGlue = (PlaybackTransportControlGlueSample) glue;
                        transportControlGlue.setSeekProvider(new PlaybackSeekDiskDataProvider(transportControlGlue.getDuration(), transportControlGlue.getDuration() / 100, "/data/user/0/tv.cloudwalker.player/files/seek/frame_%04d.jpg"));
                    }
                }
            });
        }
    }


    private void setServer() {
        Log.i(TAG, "setServer: ");

        managedChannel = ManagedChannelBuilder.forAddress("192.168.1.9", 5000).usePlaintext().build();
//        managedChannel  = ManagedChannelBuilder.forTarget("dev.cloudwalker.tv:80").usePlaintext().build();
        mediaChatServiceStub = MediaChatServiceGrpc.newStub(managedChannel);
        authMe();
    }

    private String getEthMacAddress() {
        try {
            return loadFileAsString("/sys/class/net/eth0/address").toUpperCase().substring(0, 17);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private String loadFileAsString(String filePath) throws java.io.IOException {
        StringBuffer fileData = new StringBuffer(1000);
        BufferedReader reader = new BufferedReader(new FileReader(filePath));
        char[] buf = new char[1024];
        int numRead;
        while ((numRead = reader.read(buf)) != -1) {
            String readData = String.valueOf(buf, 0, numRead);
            fileData.append(readData);
        }
        reader.close();
        return fileData.toString();
    }

    private void authMe() {
        // get Authorized with ChatServer

        String device = "5510TV";
//        if(isTV){
//            device = getEthMacAddress();
//        }


        mediaChatServiceStub.authorize(Watch.RequestAuthorize.newBuilder().setName(device).build(), new StreamObserver<Watch.ResponseAuthorize>() {
            @Override
            public void onNext(Watch.ResponseAuthorize value) {
                sourceId = value.getSessionId();
                Log.d(TAG, "onNext: authorize " + sourceId);
            }

            @Override
            public void onError(Throwable t) {
                Log.e(TAG, "onError: authorize ", t.getCause());
            }

            @Override
            public void onCompleted() {
                Log.i(TAG, "onCompleted: authorize MYID ===>" + sourceId);
                connectMe();
                mMediaPlayerGlue.setSubtitle(String.valueOf(sourceId));
            }
        });
    }

    private void syncingPlayer(final Watch.PlayerMeta playerMeta) {

        if (playerMeta.getTitle() != null && !playerMeta.getTitle().isEmpty() && !mMediaPlayerGlue.getTitle().equals(playerMeta.getTitle())) {
            Log.d(TAG, "syncingPlayer: setting new title");
            mMediaPlayerGlue.setTitle(playerMeta.getTitle());
        }
        if (playerMeta.getSubtitle() != null && !playerMeta.getSubtitle().isEmpty() && !mMediaPlayerGlue.getSubtitle().equals(playerMeta.getSubtitle())) {
            Log.d(TAG, "syncingPlayer: setting new subtitle");
            mMediaPlayerGlue.setSubtitle(playerMeta.getSubtitle());
        }
        if (playerMeta.getUrl() != null && !playerMeta.getUrl().isEmpty() && !currentDataSource.equals(playerMeta.getUrl())) {
            Log.d(TAG, "syncingPlayer: setting new URL ");
            currentDataSource = playerMeta.getUrl();
            mMediaPlayerGlue.getPlayerAdapter().setDataSource(Uri.parse(currentDataSource));
            playWhenReady(mMediaPlayerGlue);
        }
        Log.d(TAG, "syncingPlayer: cp= "+mMediaPlayerGlue.getCurrentPosition()+" sp = "+playerMeta.getCurrentPosition()+" diff = "+(playerMeta.getCurrentPosition() - mMediaPlayerGlue.getCurrentPosition() ));
        if (playerMeta.getCurrentPosition() != 0) {


            if(playerMeta.getCurrentPosition() != mMediaPlayerGlue.getCurrentPosition()) {
                mMediaPlayerGlue.getPlayerAdapter().seekTo(playerMeta.getCurrentPosition());
            }

//            if(playerMeta.getCurrentPosition() - mMediaPlayerGlue.getCurrentPosition() < 0 ){
//                Toast.makeText(getActivity(), "Parent Player seems to be lagy !! Matching parent seekbar...", Toast.LENGTH_SHORT).show();
//                mMediaPlayerGlue.seekTo(playerMeta.getCurrentPosition());
//
//            }else {
//
//                if((playerMeta.getCurrentPosition() - mMediaPlayerGlue.getCurrentPosition() > 1000)){
//                    Log.d(TAG, "syncingPlayer: re seeking...");
//                    mMediaPlayerGlue.addPlayerCallback(new PlaybackGlue.PlayerCallback() {
//                        @Override
//                        public void onPreparedStateChanged(PlaybackGlue glue) {
//                            if (glue.isPrepared()) {
//                                glue.removePlayerCallback(this);
//                                mMediaPlayerGlue.getPlayerAdapter().seekTo(playerMeta.getCurrentPosition());
//                            }
//                        }
//                    });
//                }
//            }
        }
    }

    private void connectMe() {
        mediaChatServiceStub.connect(Watch.RequestConnect.newBuilder().setSessionId(sourceId).build(), new StreamObserver<Watch.Event>() {
            @Override
            public void onNext(final Watch.Event value) {
                if (value.getLog() != null && value.getLog().hasPlayerMeta() && !value.getLog().getPlayerMeta().getPlayerState().equals(Watch.PlayerState.UNKNOWN)) {
                    Log.i(TAG, "onNext: connect " + value.getLog());
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            syncingPlayer(value.getLog().getPlayerMeta());
                        }
                    });

                    switch (value.getLog().getPlayerMeta().getPlayerState()) {
                        case PLAY: {
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mMediaPlayerGlue.play();
                                }
                            });
                        }
                        break;
                        case REWIND: {
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mMediaPlayerGlue.rewind();
                                }
                            });
                        }
                        break;
                        case PAUSE: {
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mMediaPlayerGlue.pause();
                                }
                            });
                        }
                        break;
                        case FORWARD: {
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mMediaPlayerGlue.fastForward();
                                }
                            });
                        }
                        break;
                        case VOLUMN_UP: {
                            Log.i(TAG, "onNext:connect volumn up");
                            mMediaSessionCompat.getController().adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
                        }
                        break;
                        case VOLUMN_DOWN: {
                            Log.i(TAG, "onNext:  connect volumn down");
                            mMediaSessionCompat.getController().adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
                        }
                        case SYNC: {


                            targetId = value.getLog().getSouceId();
                            Log.d(TAG, "onNext: ************************* connect SYNc "+value.getLog().getSouceId());


                            // trigger for the content to be player in other player.
                            mediaChatServiceStub.player(Watch.MediaChat.newBuilder()
                                    .setSourceId(sourceId)
                                    .setTargetId(targetId)
                                    .setPlayerMeta(Watch.PlayerMeta.newBuilder()
                                            .setPlayerState(PLAY)
                                            .setTitle(mMediaPlayerGlue.getTitle().toString())
                                            .setSubtitle(mMediaPlayerGlue.getSubtitle().toString())
                                            .setUrl(currentDataSource)
                                            .setCurrentPosition(mMediaPlayerGlue.getCurrentPosition()).build()
                                    ).build(), null);

                        }
                        break;
                    }
                }
            }

            @Override
            public void onError(Throwable t) {
                Log.e(TAG, "onError: connect ", t.getCause());
            }

            @Override
            public void onCompleted() {
                Log.i(TAG, "onCompleted: connect ");
            }
        });
    }

    private void syncMe(Action action) {

        Watch.PlayerState playerState = null;

        if (action.getId() == R.id.lb_control_thumbs_down) {

        } else if (action.getId() == R.id.lb_control_thumbs_up) {

        } else if (action.getId() == R.id.lb_control_play_pause) {
            if (mMediaPlayerGlue.isPlaying()) {
                playerState = Watch.PlayerState.PAUSE;
            } else {
                playerState = PLAY;
            }

        } else if (action.getId() == R.id.lb_control_fast_forward) {
            playerState = Watch.PlayerState.FORWARD;

        } else if (action.getId() == R.id.lb_control_fast_rewind) {
            playerState = Watch.PlayerState.REWIND;
        }


        if (playerState == null || targetId == 0) return;

        Log.d(TAG, "syncMe: sourceId " + sourceId + "   targetId  " + targetId);

        mediaChatServiceStub.player(Watch.MediaChat.newBuilder()
                        .setSourceId(sourceId)
                        .setTargetId(targetId)
                        .setPlayerMeta(Watch.PlayerMeta.newBuilder()
                                .setPlayerState(playerState)
                                .setTitle(mMediaPlayerGlue.getTitle().toString())
                                .setSubtitle(mMediaPlayerGlue.getSubtitle().toString())
                                .setUrl(currentDataSource)
                                .setCurrentPosition(mMediaPlayerGlue.getCurrentPosition()).build()
                        ).build(),
                new StreamObserver<Watch.None>() {
                    @Override
                    public void onNext(Watch.None value) {
                        Log.i(TAG, "onNext: player " + value);
                    }

                    @Override
                    public void onError(Throwable t) {
                        Log.e(TAG, "onError: player ", t.getCause());
                    }

                    @Override
                    public void onCompleted() {
                        Log.i(TAG, "onCompleted: player ");
                    }
                });
    }


    private String getSystemProperty(String key) {
        String value = null;
        try {
            value = (String) Class.forName("android.os.SystemProperties")
                    .getMethod("get", String.class).invoke(null, key);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return value;
    }

    private volatile Method set = null;

    private void setSystemProperty(String prop, String value) {
        try {
            if (null == set) {
                synchronized (KidsPasswordDialog.class) {
                    if (null == set) {
                        Class<?> cls = Class.forName("android.os.SystemProperties");
                        set = cls.getDeclaredMethod("set", new Class<?>[]{String.class, String.class});
                    }
                }
            }
            set.invoke(null, new Object[]{prop, value});
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private void showPasswordPopUp() {
        KidsPasswordDialog dialogFragment = new KidsPasswordDialog(mediaChatServiceStub, sourceId);
        FragmentTransaction ft = getActivity().getSupportFragmentManager().beginTransaction();
        Fragment prev = getActivity().getSupportFragmentManager().findFragmentByTag("dialog");
        if (prev != null) {
            ft.remove(prev);
        }
        ft.addToBackStack(null);
        dialogFragment.show(ft, "dialog");
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mMediaPlayerGlue = new PlaybackTransportControlGlueSample<MediaPlayerAdapter>(getActivity(), new MediaPlayerAdapter(getActivity())) {
            @Override
            public void onActionClicked(Action action) {
                syncMe(action);
                if (action.getId() == R.id.lb_control_picture_in_picture) {
                    if (Build.VERSION.SDK_INT >= 24) {
                        getActivity().enterPictureInPictureMode();
                    }
                    return;
                } else if (action.getId() == R.id.lb_control_more_actions) {
                    showPasswordPopUp();
                }
                super.onActionClicked(action);
            }
        };

        // create a media session inside of a fragment, and app developer can determine if connect
        // this media session to glue or not
        // as requested in b/64935838
        mMediaSessionCompat = new MediaSessionCompat(getActivity(), MEDIA_SESSION_COMPAT_TOKEN);
        mMediaPlayerGlue.connectToMediaSession(mMediaSessionCompat);

        mMediaPlayerGlue.setHost(mHost);
        mMediaPlayerGlue.setMode(PlaybackControlsRow.RepeatAction.INDEX_NONE);
        mMediaPlayerGlue.addPlayerCallback(new PlaybackGlue.PlayerCallback() {
            boolean mSecondCompleted = false;

            @Override
            public void onPlayCompleted(PlaybackGlue glue) {
                if (!mSecondCompleted) {
                    mSecondCompleted = true;


                    if(!isTV){

                        mMediaPlayerGlue.setSubtitle("Dummy Video SubTitle");
                        mMediaPlayerGlue.setTitle("Bunny Honey.");
                        currentDataSource = "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4";

                    }else {
                        mMediaPlayerGlue.setSubtitle("Leanback artist Changed!");
                        mMediaPlayerGlue.setTitle("Leanback team at work");
                        currentDataSource = "https://storage.googleapis.com/android-tv/Sample videos/April Fool's 2013/Explore Treasure Mode with Google Maps.mp4";
                    }


                    mMediaPlayerGlue.getPlayerAdapter().setDataSource(Uri.parse(currentDataSource));
//                    loadSeekData(mMediaPlayerGlue);
                    playWhenReady(mMediaPlayerGlue);
                } else {
                    mMediaPlayerGlue.removePlayerCallback(this);
                    switchAnotherGlue();
                }
            }
        });


        if(!isTV){

            mMediaPlayerGlue.setSubtitle("Leanback artist Changed!");
            mMediaPlayerGlue.setTitle("Leanback team at work");
            currentDataSource = "https://storage.googleapis.com/android-tv/Sample videos/April Fool's 2013/Explore Treasure Mode with Google Maps.mp4";

        }else {

            mMediaPlayerGlue.setSubtitle("Dummy Video SubTitle");
            mMediaPlayerGlue.setTitle("Bunny Honey.");
            currentDataSource = "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4";
        }


        mMediaPlayerGlue.getPlayerAdapter().setDataSource(Uri.parse(currentDataSource));
        mMediaPlayerGlue.setSeekEnabled(false);

//        loadSeekData(mMediaPlayerGlue);


        playWhenReady(mMediaPlayerGlue);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setServer();
    }

    @Override
    public void onPause() {
        if (mMediaPlayerGlue != null) {
            mMediaPlayerGlue.pause();
        }
        Log.d(TAG, "onPause: ");
        super.onPause();
    }


    @Override
    public void onStart() {
        super.onStart();
        ((VideoSupportActivity) getActivity()).registerPictureInPictureListener(this);
    }

    @Override
    public void onStop() {
        Log.d(TAG, "onStop: ");
        managedChannel.shutdownNow();
        ((VideoSupportActivity) getActivity()).unregisterPictureInPictureListener(this);
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d(TAG, "onDestroyView: ");
    }

    @Override
    public void onDetach() {
        super.onDetach();
        Log.d(TAG, "onDetach: ");
    }


    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        if (isInPictureInPictureMode) {
            // Hide the controls in picture-in-picture mode.
            setFadingEnabled(true);
            fadeOut();
        } else {
            setFadingEnabled(mMediaPlayerGlue.isPlaying());
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy: ");
        super.onDestroy();
        mMediaPlayerGlue.disconnectToMediaSession();
    }

    void switchAnotherGlue() {
        mMediaPlayerGlue = new PlaybackTransportControlGlueSample<MediaPlayerAdapter>(getActivity(), new MediaPlayerAdapter(getActivity()));

        // If the glue is switched, re-register the media session
        mMediaPlayerGlue.connectToMediaSession(mMediaSessionCompat);

        mMediaPlayerGlue.setMode(PlaybackControlsRow.RepeatAction.INDEX_ONE);
        mMediaPlayerGlue.setSubtitle("A Googler");
        mMediaPlayerGlue.setTitle("Swimming with the fishes");
        currentDataSource = "http://techslides.com/demos/sample-videos/small.mp4";
        mMediaPlayerGlue.getPlayerAdapter().setDataSource(Uri.parse(currentDataSource));
        mMediaPlayerGlue.setHost(mHost);
        loadSeekData(mMediaPlayerGlue);
        playWhenReady(mMediaPlayerGlue);
    }
}
