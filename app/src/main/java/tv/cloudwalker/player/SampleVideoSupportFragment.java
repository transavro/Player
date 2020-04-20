package tv.cloudwalker.player;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.leanback.app.VideoSupportFragmentGlueHost;
import androidx.leanback.media.MediaPlayerAdapter;
import androidx.leanback.media.PlaybackGlue;
import androidx.leanback.widget.Action;
import androidx.leanback.widget.PlaybackControlsRow;

import java.util.concurrent.Executor;

import cloudwalker.WeWatchGrpc;
import cloudwalker.Wewatch;
import io.grpc.CallCredentials;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

public class SampleVideoSupportFragment extends androidx.leanback.app.VideoSupportFragment implements VideoSupportActivity.PictureInPictureListener {

    private static final String TAG = "NayanMakasare";
    // Media Session Token
    private static final String MEDIA_SESSION_COMPAT_TOKEN = "media session support video";
    private static final int DIALOG_REQUEST_CODE = 1234;
    private static final String DIALOG_TAG = "dialog";

    private PlaybackTransportControlGlueSample<MediaPlayerAdapter> mMediaPlayerGlue;

    private MediaSessionCompat mMediaSessionCompat;
    private ManagedChannel managedChannel;
    private WeWatchGrpc.WeWatchStub weWatchStub;

    final VideoSupportFragmentGlueHost mHost = new VideoSupportFragmentGlueHost(SampleVideoSupportFragment.this);

    private String currentDataSource = "";
    private String token = "";
    private int roomId = 0;

    private boolean isTV = false;
    private boolean isRoomMade , isJointRoom;

    private StreamObserver<Wewatch.StreamRequest> streamRequestStreamObserver;


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

    private void initServer() {
        Log.i(TAG, "setServer: ");
        managedChannel = ManagedChannelBuilder.forAddress("192.168.0.106", 5000).usePlaintext().build();
        weWatchStub = WeWatchGrpc.newStub(managedChannel).withCallCredentials(new CallCredentials() {
            @Override
            public void applyRequestMetadata(RequestInfo requestInfo, Executor appExecutor, final MetadataApplier applier) {
                appExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Metadata headers = new Metadata();
                            Metadata.Key<String> clientIdKey = Metadata.Key.of("x-chat-token", Metadata.ASCII_STRING_MARSHALLER);
                            headers.put(clientIdKey, token);
                            applier.apply(headers);
                        } catch (Throwable ex) {
                            applier.fail(Status.UNAUTHENTICATED.withCause(ex));
                        }
                    }
                });
            }

            @Override
            public void thisUsesUnstableApi() {
            }
        });
    }

    private void login() {
        if (weWatchStub == null) {
            initServer();
        }
        weWatchStub.login(Wewatch.LoginRequest.newBuilder().setName("tv").setPassword("cloudwalker").build(), new StreamObserver<Wewatch.LoginResponse>() {
            @Override
            public void onNext(Wewatch.LoginResponse value) {
                Log.i(TAG, "onNext: login ");
                token = value.getToken();
            }

            @Override
            public void onError(Throwable t) {
                Log.e(TAG, "onError: login ", t.getCause());
            }

            @Override
            public void onCompleted() {
                Log.i(TAG, "onCompleted: login ");
            }
        });
    }

    private void logout() {
        if (weWatchStub == null) {
            return;
        }
        weWatchStub.logout(Wewatch.LogoutRequest.newBuilder().setToken(token).build(), new StreamObserver<Wewatch.LogoutResponse>() {
            @Override
            public void onNext(Wewatch.LogoutResponse value) {
                Log.i(TAG, "onNext: logout");
            }

            @Override
            public void onError(Throwable t) {
                Log.e(TAG, "onError: logout", t.getCause());
            }

            @Override
            public void onCompleted() {
                Log.i(TAG, "onCompleted: logout ");
                // very important !! the stream was not getting closed and the app got into bg, after trail n error, this is the soulution.
                managedChannel.shutdownNow();
            }
        });
    }

    private void makeRoom() {
        if (weWatchStub == null) {
            return;
        }
        weWatchStub.makeRoom(Wewatch.MakeRoomRequest.newBuilder().setToken(token).build(), new StreamObserver<Wewatch.MakeRoomResponse>() {
            @Override
            public void onNext(Wewatch.MakeRoomResponse value) {
                Log.i(TAG, "onNext: Make room ");
                roomId = value.getRoomId();
            }

            @Override
            public void onError(Throwable t) {
                Log.e(TAG, "onError: ", t.getCause());
            }

            @Override
            public void onCompleted() {
                Log.i(TAG, "onCompleted: ");
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getContext(), "Group Created with Id = " + roomId, Toast.LENGTH_SHORT).show();

                    }
                });
                startStreaming();
                isRoomMade = true;
            }
        });
    }


    private void synceMe(Action action) {
        if (action.getId() == R.id.lb_control_play_pause) {
            if (mMediaPlayerGlue.isPlaying()) {
                streamRequestStreamObserver.onNext(Wewatch.StreamRequest.newBuilder().setPlayerState(Wewatch.PlayerStates.PLAY).build());
            } else {
                streamRequestStreamObserver.onNext(Wewatch.StreamRequest.newBuilder().setPlayerState(Wewatch.PlayerStates.PAUSE).build());
            }
        } else if (action.getId() == R.id.lb_control_fast_forward) {
            streamRequestStreamObserver.onNext(Wewatch.StreamRequest.newBuilder().setPlayerState(Wewatch.PlayerStates.FORWARD).build());
        } else if (action.getId() == R.id.lb_control_fast_rewind) {
            streamRequestStreamObserver.onNext(Wewatch.StreamRequest.newBuilder().setPlayerState(Wewatch.PlayerStates.REWIND).build());
        }
        return;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mMediaPlayerGlue = new PlaybackTransportControlGlueSample<MediaPlayerAdapter>(getActivity(), new MediaPlayerAdapter(getActivity())) {
            @Override
            public void onActionClicked(Action action) {
                if (streamRequestStreamObserver != null) {
                    synceMe(action);
                }

                if (action.getId() == R.id.lb_control_picture_in_picture) {
                    if (Build.VERSION.SDK_INT >= 24) {
                        getActivity().enterPictureInPictureMode();
                    }
                    return;
                } else if (action.getId() == 7777) {

                    if(!isJointRoom){
                        Toast.makeText(getActivity(), "You have already joined a room with Id "+roomId, Toast.LENGTH_SHORT).show();
                        return;
                    }else if(!isRoomMade){
                        makeRoom();
                        action.setIcon(getResources().getDrawable(R.drawable.broadcastblack));
                        action.setLabel1(String.valueOf(roomId));
                        mMediaPlayerGlue.getControlsRow().getPrimaryActionsAdapter().notifyItemRangeChanged(3,1);
                        return;
                    }else {
                        Toast.makeText(getActivity(), "You have already made a room with Id "+roomId, Toast.LENGTH_SHORT).show();
                        return;
                    }
                } else if (action.getId() == 8888) {
                    if(isRoomMade){
                        Toast.makeText(getActivity(), "You have already made a room with id "+roomId+ " .Please inform others to join your room.", Toast.LENGTH_SHORT).show();
                        return;
                    }else if(!isJointRoom) {
                        action.setIcon(getResources().getDrawable(R.drawable.add_in_room_black));
                        showDialogPopUp();
                        return;
                    }
                }
                super.onActionClicked(action);
            }
        };

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

                    if (!isTV) {

                        mMediaPlayerGlue.setSubtitle("Dummy Video SubTitle");
                        mMediaPlayerGlue.setTitle("Bunny Honey.");
                        currentDataSource = "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4";

                    } else {
                        mMediaPlayerGlue.setSubtitle("Leanback artist Changed!");
                        mMediaPlayerGlue.setTitle("Leanback team at work");
                        currentDataSource = "https://storage.googleapis.com/android-tv/Sample videos/April Fool's 2013/Explore Treasure Mode with Google Maps.mp4";
                    }

                    mMediaPlayerGlue.getPlayerAdapter().setDataSource(Uri.parse(currentDataSource));
                    playWhenReady(mMediaPlayerGlue);
                } else {
                    mMediaPlayerGlue.removePlayerCallback(this);
                    switchAnotherGlue();
                }
            }
        });


        if (!isTV) {
            mMediaPlayerGlue.setSubtitle("Leanback artist Changed!");
            mMediaPlayerGlue.setTitle("Leanback team at work");
            currentDataSource = "https://storage.googleapis.com/android-tv/Sample videos/April Fool's 2013/Explore Treasure Mode with Google Maps.mp4";
        } else {

            mMediaPlayerGlue.setSubtitle("Dummy Video SubTitle");
            mMediaPlayerGlue.setTitle("Bunny Honey.");
            currentDataSource = "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4";
        }

        mMediaPlayerGlue.getPlayerAdapter().setDataSource(Uri.parse(currentDataSource));
        mMediaPlayerGlue.setSeekEnabled(false);
        playWhenReady(mMediaPlayerGlue);

        login();
    }

    private void showDialogPopUp() {
        KidsPasswordDialog dialogFragment = new KidsPasswordDialog(weWatchStub, token);
        dialogFragment.setTargetFragment(this, DIALOG_REQUEST_CODE);
        FragmentTransaction ft = getActivity().getSupportFragmentManager().beginTransaction();
        Fragment prev = getActivity().getSupportFragmentManager().findFragmentByTag(DIALOG_TAG);
        if (prev != null) {
            ft.remove(prev);
        }
        ft.addToBackStack(null);
        dialogFragment.show(ft, DIALOG_TAG);
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == DIALOG_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                if (data.getExtras().containsKey("roomId")) {
                    roomId = data.getExtras().getInt("roomId");
                    Log.i(TAG, "addedToRoom: !!! ");
                    ((PlaybackControlsRow.MoreActions)mMediaPlayerGlue.getControlsRow().getPrimaryActionsAdapter().get(4))
                            .setIcon(getResources().getDrawable(R.drawable.add_in_room_black));
                    mMediaPlayerGlue.getControlsRow().getPrimaryActionsAdapter().notifyItemRangeChanged(4,1);
                    isJointRoom = true;
                    startStreaming();
                }
            }
        }
    }

    private void startStreaming(){
        streamRequestStreamObserver = weWatchStub.stream(new StreamObserver<Wewatch.StreamResponse>() {
            @Override
            public void onNext(Wewatch.StreamResponse value) {
                if(value.getClientMessage() != null){
                    if(value.getClientMessage().getPlayerState() == Wewatch.PlayerStates.PLAY){
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mMediaPlayerGlue.play();
                            }
                        });

                    }else if(value.getClientMessage().getPlayerState() == Wewatch.PlayerStates.PAUSE){
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mMediaPlayerGlue.pause();
                            }
                        });

                    }else if(value.getClientMessage().getPlayerState() == Wewatch.PlayerStates.FORWARD){
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mMediaPlayerGlue.fastForward();
                            }
                        });

                    }else if(value.getClientMessage().getPlayerState() == Wewatch.PlayerStates.REWIND){
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mMediaPlayerGlue.rewind();
                            }
                        });

                    }else {
                        Log.i(TAG, "onNext: " + value.toString());
                    }
                }
            }

            @Override
            public void onError(Throwable t) {
                Log.e(TAG, "onError: stream..", t);
            }

            @Override
            public void onCompleted() {
                Log.i(TAG, "onCompleted: completed..");
            }
        });
    }


    @Override
    public void onPause() {
        if (mMediaPlayerGlue != null) {
            mMediaPlayerGlue.pause();
        }
        super.onPause();
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.i(TAG, "onStart: ");

        ((VideoSupportActivity) getActivity()).registerPictureInPictureListener(this);
    }

    @Override
    public void onStop() {
        Log.d(TAG, "onStop: ");


        ((VideoSupportActivity) getActivity()).unregisterPictureInPictureListener(this);
        super.onStop();
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
        if(streamRequestStreamObserver != null){
            streamRequestStreamObserver.onCompleted();
        }
        logout();
        mMediaPlayerGlue.disconnectToMediaSession();
        super.onDestroy();
    }

    private void switchAnotherGlue() {
        mMediaPlayerGlue = new PlaybackTransportControlGlueSample<MediaPlayerAdapter>(getActivity(), new MediaPlayerAdapter(getActivity()));

        // If the glue is switched, re-register the media session
        mMediaPlayerGlue.connectToMediaSession(mMediaSessionCompat);

        mMediaPlayerGlue.setMode(PlaybackControlsRow.RepeatAction.INDEX_ONE);
        mMediaPlayerGlue.setSubtitle("A Googler");
        mMediaPlayerGlue.setTitle("Swimming with the fishes");
        currentDataSource = "http://techslides.com/demos/sample-videos/small.mp4";
        mMediaPlayerGlue.getPlayerAdapter().setDataSource(Uri.parse(currentDataSource));
        mMediaPlayerGlue.setHost(mHost);
        playWhenReady(mMediaPlayerGlue);
    }

}
