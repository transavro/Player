package tv.cloudwalker.player;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.leanback.app.VideoSupportFragment;
import androidx.leanback.app.VideoSupportFragmentGlueHost;
import androidx.leanback.media.MediaPlayerAdapter;
import androidx.leanback.media.PlaybackGlue;
import androidx.leanback.widget.Action;
import androidx.leanback.widget.PlaybackControlsRow;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.Executor;

import cloudwalker.WeWatchGrpc;
import cloudwalker.Wewatch;
import io.grpc.CallCredentials;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

public class CloudwalkerPlayerFragment extends VideoSupportFragment {
    private static final String TAG = "NayanMakasare";

    // room id dialog
    private static final int DIALOG_REQUEST_CODE = 1234;
    private static final String DIALOG_TAG = "dialog";


    // Media Player Variables
    private PlaybackTransportControlGlueSample<MediaPlayerAdapter> mMediaPlayerGlue;
    private static final String MEDIA_SESSION_COMPAT_TOKEN = "media session support video";
    private VideoSupportFragmentGlueHost mHost = new VideoSupportFragmentGlueHost(CloudwalkerPlayerFragment.this);
    private String currentUrl = "";

    //GRPC VARIABLES
    private ManagedChannel managedChannel;
    private String token = "";
    private int roomId = 0;
    private boolean isRoomMade = false;
    private boolean isJoinRoom = false;
    private StreamObserver<Wewatch.StreamRequest> streamObserver;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mMediaPlayerGlue = new PlaybackTransportControlGlueSample<MediaPlayerAdapter>(getActivity(), new MediaPlayerAdapter(getActivity())) {

            @Override
            public void onActionClicked(Action action) {
                if (action.getId() == 7777) {
                    if(isRoomMade == false && isJoinRoom == false){
                        makeRoom(action);
                    }else if(isJoinRoom == true){
                        Toast.makeText(getActivity(), "Already Join a room with id = "+roomId, Toast.LENGTH_SHORT).show();
                    }else if(isRoomMade == true){
                        Toast.makeText(getActivity(), "Already Made a room with id = "+roomId+" , Please inform others to join room", Toast.LENGTH_SHORT).show();
                    }
                } else if (action.getId() == 8888) {
                    if(isRoomMade == true){
                        Toast.makeText(getActivity(), "Already Made a room with id = "+roomId+" , Please inform others to join room", Toast.LENGTH_SHORT).show();
                    }else if(isJoinRoom == true){
                        Toast.makeText(getActivity(), "Already Join a room with id = "+roomId, Toast.LENGTH_SHORT).show();
                    }else {
                        showDialogPopUp();
                    }
                } else {
                    streamWriter(action);
                }
                super.onActionClicked(action);
            }
        };


        initServer();

        MediaSessionCompat mMediaSessionCompat = new MediaSessionCompat(getActivity(), MEDIA_SESSION_COMPAT_TOKEN);
        mMediaPlayerGlue.connectToMediaSession(mMediaSessionCompat);
        mMediaPlayerGlue.setHost(mHost);
        mMediaPlayerGlue.setMode(PlaybackControlsRow.RepeatAction.INDEX_NONE);
        selectSource();
    }

    private void playWhenReady() {
        if (mMediaPlayerGlue.isPrepared()) {
            mMediaPlayerGlue.play();
        } else {
            mMediaPlayerGlue.addPlayerCallback(new PlaybackGlue.PlayerCallback() {
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

    private void selectSource() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.Theme_AppCompat);
        builder.setNegativeButton("Back", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.setTitle("Select Source to play.");
        builder.setCancelable(true);


        final String[] animals = {
                "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/VolkswagenGTIReview.mp4",
        };


        builder.setItems(animals, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

                mMediaPlayerGlue.setSubtitle("Video Subtitle " + which);
                mMediaPlayerGlue.setTitle("Video Title" + which);
                currentUrl = animals[which];
                mMediaPlayerGlue.getPlayerAdapter().setDataSource(Uri.parse(currentUrl));
                mMediaPlayerGlue.setSeekEnabled(false);
                playWhenReady();
                dialog.dismiss();
            }
        });
        // create and show the alert dialog
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void showDialogPopUp() {
        KidsPasswordDialog dialogFragment = new KidsPasswordDialog();
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
                roomId = data.getIntExtra("roomId", 0);
                joinRoom();
            }
        }
    }

    @Override
    public void onPause() {
        if (mMediaPlayerGlue != null) {
            mMediaPlayerGlue.pause();
        }
        super.onPause();
    }

    @Override
    public void onStop() {
        mMediaPlayerGlue.disconnectToMediaSession();
        if (streamObserver != null) {
            streamObserver.onCompleted();
        }
        if (!token.isEmpty()) {
            logout();
        }
        super.onStop();
    }


    //server methods
    private void initServer() {
        managedChannel = ManagedChannelBuilder.forAddress("3.6.231.212", 5000).usePlaintext().build();
//        managedChannel = ManagedChannelBuilder.forTarget("dev.cloudwalker.tv:80").usePlaintext().build();
        login();
    }

    private void login() {

        WeWatchGrpc.WeWatchStub weWatchStub = WeWatchGrpc.newStub(managedChannel);
        weWatchStub.login(Wewatch.LoginRequest.newBuilder()
                .setName("nayan box")
                .setPassword("cloudwalker").build(), new StreamObserver<Wewatch.LoginResponse>() {
            @Override
            public void onNext(Wewatch.LoginResponse value) {
                Log.i(TAG, "onNext: login ");
                token = value.getToken();
            }

            @Override
            public void onError(Throwable t) {
                Log.e(TAG, "onError: login ", t);
            }

            @Override
            public void onCompleted() {
                Log.i(TAG, "onCompleted: login");
            }
        });
    }

    private void logout() {
        WeWatchGrpc.WeWatchStub weWatchStub = WeWatchGrpc.newStub(managedChannel);
        weWatchStub.logout(Wewatch.LogoutRequest.newBuilder().setToken(token).build(), new StreamObserver<Wewatch.LogoutResponse>() {
            @Override
            public void onNext(Wewatch.LogoutResponse value) {
                Log.i(TAG, "onNext: logout ");
            }

            @Override
            public void onError(Throwable t) {
                Log.e(TAG, "onError: logout", t);
            }

            @Override
            public void onCompleted() {
                Log.i(TAG, "onCompleted: logout ");
                // important
                managedChannel.shutdownNow();
            }
        });
    }

    private void makeRoom(final Action action) {

        if (token.isEmpty()) {
            return;
        }

        WeWatchGrpc.WeWatchStub weWatchStub = WeWatchGrpc.newStub(managedChannel);

        weWatchStub.makeRoom(Wewatch.MakeRoomRequest.newBuilder()
                .setTkn(token)
                .build(), new StreamObserver<Wewatch.MakeRoomResponse>() {
            @Override
            public void onNext(Wewatch.MakeRoomResponse value) {
                Log.i(TAG, "onNext: makeRoom " + value);
                roomId = value.getRoomId();
                showToast("Room id " + roomId);
            }

            @Override
            public void onError(Throwable t) {
                Log.e(TAG, "onError: makeRoom ", t);
            }

            @Override
            public void onCompleted() {
                Log.i(TAG, "onCompleted: ");
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        action.setIcon(getResources().getDrawable(R.drawable.broadcastblack));
                        action.setLabel1(String.valueOf(roomId));
                        mMediaPlayerGlue.getControlsRow().getPrimaryActionsAdapter().notifyItemRangeChanged(3 , 1);
                    }
                });
                isRoomMade = true;
                streamReader();
            }
        });

    }

    private void joinRoom() {

        if (token.isEmpty() || roomId == 0) {
            return;
        }

        WeWatchGrpc.WeWatchStub weWatchStub = WeWatchGrpc.newStub(managedChannel);
        weWatchStub.joinRoom(Wewatch.JoinRoomRequest.newBuilder().setToken(token).setRoomId(roomId).build(), new StreamObserver<Wewatch.JoinRoomResponse>() {
            @Override
            public void onNext(Wewatch.JoinRoomResponse value) {
                Log.i(TAG, "onNext: joinRoom " + value.toString());
                showToast(value.getJointState().name());
            }

            @Override
            public void onError(Throwable t) {
                Log.e(TAG, "onError: join room ", t);
            }

            @Override
            public void onCompleted() {
                Log.i(TAG, "onCompleted: join room ");
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ((PlaybackControlsRow.MoreActions)mMediaPlayerGlue.getControlsRow().getPrimaryActionsAdapter().get(4)).setIcon(getResources().getDrawable(R.drawable.add_in_room_black));
                        mMediaPlayerGlue.getControlsRow().getPrimaryActionsAdapter().notifyItemRangeChanged(4,1);
                        isJoinRoom = true;
                    }
                });
                streamReader();
            }
        });
    }

    private void streamReader() {

        if (token.isEmpty()) {
            return;
        }

        WeWatchGrpc.WeWatchStub streamStub = WeWatchGrpc.newStub(managedChannel).withCallCredentials(new CallCredentials() {
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

        streamObserver = streamStub.stream(new StreamObserver<Wewatch.StreamResponse>() {
            @Override
            public void onNext(final Wewatch.StreamResponse value) {

                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        syncStream(value);
                    }
                });

            }

            @Override
            public void onError(Throwable t) {
                Log.d(TAG, "onError: KNOWN BUG " + t.getMessage());
            }

            @Override
            public void onCompleted() {
                Log.i(TAG, "onCompleted: stream ");
            }
        });
    }


    private void syncStream(final Wewatch.StreamResponse value) {

        if (value.getClientMessage() != null) {

            boolean isSourceDiff = false;

            if (value.getClientMessage().getRoomMeta().getTitle() != mMediaPlayerGlue.getTitle()) {
                mMediaPlayerGlue.setTitle(value.getClientMessage().getRoomMeta().getTitle());
            }
            if (value.getClientMessage().getRoomMeta().getSubtitle() != mMediaPlayerGlue.getSubtitle()) {
                mMediaPlayerGlue.setSubtitle(value.getClientMessage().getRoomMeta().getSubtitle());
            }

            if(!currentUrl.equals(value.getClientMessage().getRoomMeta().getUrl())){
                // diffrent content.
                isSourceDiff = true;
                currentUrl = value.getClientMessage().getRoomMeta().getUrl();
                Log.i(TAG, "syncStream: NEW URL ="+currentUrl);

            }else {
                // adding offset of 1 sec == 1000 millisec
                mMediaPlayerGlue.seekTo((value.getClientMessage().getRoomMeta().getCurrentPosition() + 1500));
            }


            switch (value.getClientMessage().getRoomMeta().getPlayerState()) {
                case SYNC: {

                }
                break;
                case PLAY: {
                    if(isSourceDiff){

                        mMediaPlayerGlue.getPlayerAdapter().setDataSource(Uri.parse(currentUrl));
                        mMediaPlayerGlue.setSeekEnabled(false);
                        if (mMediaPlayerGlue.isPrepared()) {
                            mMediaPlayerGlue.seekTo((value.getClientMessage().getRoomMeta().getCurrentPosition()  + 1500 ));
                            mMediaPlayerGlue.play();
                        } else {
                            mMediaPlayerGlue.addPlayerCallback(new PlaybackGlue.PlayerCallback() {
                                @Override
                                public void onPreparedStateChanged(PlaybackGlue glue) {
                                    if (glue.isPrepared()) {
                                        glue.removePlayerCallback(this);
                                        mMediaPlayerGlue.seekTo((value.getClientMessage().getRoomMeta().getCurrentPosition() + 1500));
                                        glue.play();
                                    }
                                }
                            });
                        }

                    }else {
                        mMediaPlayerGlue.play();
                    }
                }
                break;
                case PAUSE: {
                    if(isSourceDiff){

                        mMediaPlayerGlue.getPlayerAdapter().setDataSource(Uri.parse(currentUrl));
                        mMediaPlayerGlue.setSeekEnabled(false);
                        if (mMediaPlayerGlue.isPrepared()) {
                            mMediaPlayerGlue.seekTo((value.getClientMessage().getRoomMeta().getCurrentPosition() + 1500 ));
                            mMediaPlayerGlue.pause();
                        } else {
                            mMediaPlayerGlue.addPlayerCallback(new PlaybackGlue.PlayerCallback() {
                                @Override
                                public void onPreparedStateChanged(PlaybackGlue glue) {
                                    if (glue.isPrepared()) {
                                        glue.removePlayerCallback(this);
                                        mMediaPlayerGlue.seekTo((value.getClientMessage().getRoomMeta().getCurrentPosition() + 1500));
                                        glue.pause();
                                    }
                                }
                            });
                        }

                    }else {
                        mMediaPlayerGlue.pause();
                    }
                }
                break;
                case REWIND: {

                    if(isSourceDiff){

                        mMediaPlayerGlue.getPlayerAdapter().setDataSource(Uri.parse(currentUrl));
                        mMediaPlayerGlue.setSeekEnabled(false);
                        if (mMediaPlayerGlue.isPrepared()) {
                            mMediaPlayerGlue.seekTo((value.getClientMessage().getRoomMeta().getCurrentPosition() + 1500));
                            mMediaPlayerGlue.rewind();

                        } else {
                            mMediaPlayerGlue.addPlayerCallback(new PlaybackGlue.PlayerCallback() {
                                @Override
                                public void onPreparedStateChanged(PlaybackGlue glue) {
                                    if (glue.isPrepared()) {
                                        glue.removePlayerCallback(this);
                                        mMediaPlayerGlue.seekTo((value.getClientMessage().getRoomMeta().getCurrentPosition() + 1500));
                                        mMediaPlayerGlue.rewind();
                                    }
                                }
                            });
                        }

                    }else {
                        mMediaPlayerGlue.rewind();
                    }
                }
                break;
                case FORWARD: {

                    if(isSourceDiff){

                        mMediaPlayerGlue.getPlayerAdapter().setDataSource(Uri.parse(currentUrl));
                        mMediaPlayerGlue.setSeekEnabled(false);
                        if (mMediaPlayerGlue.isPrepared()) {
                            mMediaPlayerGlue.seekTo((value.getClientMessage().getRoomMeta().getCurrentPosition() + 1500));
                            mMediaPlayerGlue.fastForward();

                        } else {
                            mMediaPlayerGlue.addPlayerCallback(new PlaybackGlue.PlayerCallback() {
                                @Override
                                public void onPreparedStateChanged(PlaybackGlue glue) {
                                    if (glue.isPrepared()) {
                                        glue.removePlayerCallback(this);
                                        mMediaPlayerGlue.seekTo((value.getClientMessage().getRoomMeta().getCurrentPosition() + 1500));
                                        mMediaPlayerGlue.fastForward();
                                    }
                                }
                            });
                        }

                    }else {
                        mMediaPlayerGlue.fastForward();
                    }
                }
                break;
                case BUFFER: {

                }
                break;
            }
        }
    }

    private void streamWriter(Action action) {

        if (streamObserver == null) {
            return;
        }
        Wewatch.PlayerStates playerStates = null;
        if (action.getId() == R.id.lb_control_play_pause) {
            if (mMediaPlayerGlue.isPlaying()) {
                playerStates = Wewatch.PlayerStates.PAUSE;
            } else {
                playerStates = Wewatch.PlayerStates.PLAY;
            }
        } else if (action.getId() == R.id.lb_control_fast_rewind) {
            playerStates = Wewatch.PlayerStates.REWIND;
        } else if (action.getId() == R.id.lb_control_fast_forward) {
            playerStates = Wewatch.PlayerStates.FORWARD;
        }

        if (playerStates == null) {
            return;
        }

        streamObserver.onNext(Wewatch.StreamRequest.newBuilder()
                .setRoomMeta(Wewatch.RoomMeta.newBuilder()
                        .setTitle(mMediaPlayerGlue.getTitle().toString())
                        .setSubtitle(mMediaPlayerGlue.getSubtitle().toString())
                        .setUrl(currentUrl)
                        .setCurrentPosition(mMediaPlayerGlue.getCurrentPosition())
                        .setPlayerState(playerStates)
                        .build())
                .build());
    }

    // utility methods
    private void showToast(final String message) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }


    public String getEthMacAddress() {
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

}
