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

import com.google.protobuf.InvalidProtocolBufferException;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Random;

import cloudwalker.Wewatch;
import io.nats.client.Connection;
import io.nats.client.ConnectionListener;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import io.nats.client.MessageHandler;
import io.nats.client.Nats;
import io.nats.client.Options;


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

    //NATS VARIABLES
    private String roomId = "";
    private boolean isRoomMade = false;
    private boolean isJoinRoom = false;
    private Connection nc = null;
    private boolean ignoreSelf = false;
    private Options options = new Options.Builder().server("nats://192.168.0.106:4222")
            .connectionListener(new ConnectionListener() {
                @Override
                public void connectionEvent(Connection conn, Events type) {
                    if (nc == null) {
                        nc = conn;
                    }
                    Log.i(TAG, "connectionEvent: " + conn.toString() + "  " + type.toString());
                }
            }).build();


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            connectToMessagingServer();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mMediaPlayerGlue = new PlaybackTransportControlGlueSample<MediaPlayerAdapter>(getActivity(), new MediaPlayerAdapter(getActivity())) {

            @Override
            public void onActionClicked(Action action) {
                if (action.getId() == 7777) {
                    if (!isRoomMade && !isJoinRoom) {
                        makeRoom(action);
                    } else if (isJoinRoom) {
                        Toast.makeText(getActivity(), "Already Join a room with id = " + roomId, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getActivity(), "Already Made a room with id = " + roomId + " , Please inform others to join room", Toast.LENGTH_SHORT).show();
                    }
                } else if (action.getId() == 8888) {
                    if (isRoomMade) {
                        Toast.makeText(getActivity(), "Already Made a room with id = " + roomId + " , Please inform others to join room", Toast.LENGTH_SHORT).show();
                    } else if (isJoinRoom) {
                        Toast.makeText(getActivity(), "Already Join a room with id = " + roomId, Toast.LENGTH_SHORT).show();
                    } else {
                        showDialogPopUp();
                    }
                } else {
                    if (nc != null && !roomId.isEmpty()) {
                        Log.i(TAG, "onActionClicked: PUBLISHING... " + roomId + "  " + ignoreSelf);
                        streamWriter(action);
                    }
                }
                super.onActionClicked(action);
            }
        };
        MediaSessionCompat mMediaSessionCompat = new MediaSessionCompat(getActivity(), MEDIA_SESSION_COMPAT_TOKEN);
        mMediaPlayerGlue.connectToMediaSession(mMediaSessionCompat);
        mMediaPlayerGlue.setHost(mHost);
        mMediaPlayerGlue.setMode(PlaybackControlsRow.RepeatAction.INDEX_NONE);
        selectSource();
    }

    private void streamWriter(Action action) {
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
        ignoreSelf = true;
        nc.publish(roomId, Wewatch.Messaging.newBuilder()
                .setCurrentPosition(mMediaPlayerGlue.getCurrentPosition())
                .setTitle(mMediaPlayerGlue.getTitle().toString())
                .setSubtitle(mMediaPlayerGlue.getSubtitle().toString())
                .setPlayerState(playerStates)
                .setTimeStamp(System.currentTimeMillis())
                .setUrl(currentUrl).build().toByteArray());
        return;
    }

    private void connectToMessagingServer() throws InterruptedException {
        Nats.connectAsynchronously(options, true);
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
                roomId = data.getStringExtra("roomId");
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
        try {
            nc.close();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        super.onStop();
    }

    private void makeRoom(final Action action) {
        Random random = new Random();
        roomId = String.format("%04d", random.nextInt(10000));
        showToast("RoomID is " + roomId);
        action.setIcon(getResources().getDrawable(R.drawable.broadcastblack));
        Log.i(TAG, "makeRoom: " + roomId);
        action.setLabel1(String.valueOf(roomId));
        mMediaPlayerGlue.getControlsRow().getPrimaryActionsAdapter().notifyItemRangeChanged(3, 1);
        isRoomMade = true;
        startReading();
    }

    private void joinRoom() {
        ((PlaybackControlsRow.MoreActions) mMediaPlayerGlue.getControlsRow().getPrimaryActionsAdapter().get(4)).setIcon(getResources().getDrawable(R.drawable.add_in_room_black));
        mMediaPlayerGlue.getControlsRow().getPrimaryActionsAdapter().notifyItemRangeChanged(4, 1);
        isJoinRoom = true;
        Log.i(TAG, "joinRoom: ");
        startReading();
    }

    private void startReading() {
        Dispatcher d = nc.createDispatcher(new MessageHandler() {
            @Override
            public void onMessage(Message msg) throws InterruptedException {
                if (ignoreSelf) {
                    ignoreSelf = false;
                    //noop
                } else {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                syncStream(Wewatch.Messaging.parseFrom(msg.getData()));
                            } catch (InvalidProtocolBufferException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                }
            }
        });

        d.subscribe(roomId);
    }


    private void syncStream(final Wewatch.Messaging value) {

        boolean isSourceDiff = false;
        if (value.getTitle() != mMediaPlayerGlue.getTitle()) {
            mMediaPlayerGlue.setTitle(value.getTitle());
        }
        if (value.getSubtitle() != mMediaPlayerGlue.getSubtitle()) {
            mMediaPlayerGlue.setSubtitle(value.getSubtitle());
        }
        if (!currentUrl.equals(value.getUrl())) {
            isSourceDiff = true;
            currentUrl = value.getUrl();
        } else {

            mMediaPlayerGlue.seekTo((value.getCurrentPosition()) + (System.currentTimeMillis() - value.getTimeStamp()));
        }
        switch (value.getPlayerState()) {
            case PLAY: {
                if (isSourceDiff) {
                    mMediaPlayerGlue.getPlayerAdapter().setDataSource(Uri.parse(currentUrl));
                    mMediaPlayerGlue.setSeekEnabled(false);
                    if (mMediaPlayerGlue.isPrepared()) {
                        mMediaPlayerGlue.seekTo((value.getCurrentPosition() + (System.currentTimeMillis() - value.getTimeStamp())));
                        mMediaPlayerGlue.play();
                    } else {
                        mMediaPlayerGlue.addPlayerCallback(new PlaybackGlue.PlayerCallback() {
                            @Override
                            public void onPreparedStateChanged(PlaybackGlue glue) {
                                if (glue.isPrepared()) {
                                    glue.removePlayerCallback(this);
                                    mMediaPlayerGlue.seekTo((value.getCurrentPosition() + (System.currentTimeMillis() - value.getTimeStamp())));
                                    glue.play();
                                }
                            }
                        });
                    }

                } else {
                    mMediaPlayerGlue.play();
                }
            }
            break;
            case PAUSE: {
                if (isSourceDiff) {

                    mMediaPlayerGlue.getPlayerAdapter().setDataSource(Uri.parse(currentUrl));
                    mMediaPlayerGlue.setSeekEnabled(false);
                    if (mMediaPlayerGlue.isPrepared()) {
                        mMediaPlayerGlue.seekTo((value.getCurrentPosition() + (System.currentTimeMillis() - value.getTimeStamp())));
                        mMediaPlayerGlue.pause();
                    } else {
                        mMediaPlayerGlue.addPlayerCallback(new PlaybackGlue.PlayerCallback() {
                            @Override
                            public void onPreparedStateChanged(PlaybackGlue glue) {
                                if (glue.isPrepared()) {
                                    glue.removePlayerCallback(this);
                                    mMediaPlayerGlue.seekTo((value.getCurrentPosition() + (System.currentTimeMillis() - value.getTimeStamp())));
                                    glue.pause();
                                }
                            }
                        });
                    }

                } else {
                    mMediaPlayerGlue.pause();
                }
            }
            break;
            case REWIND: {

                if (isSourceDiff) {

                    mMediaPlayerGlue.getPlayerAdapter().setDataSource(Uri.parse(currentUrl));
                    mMediaPlayerGlue.setSeekEnabled(false);
                    if (mMediaPlayerGlue.isPrepared()) {
                        mMediaPlayerGlue.seekTo((value.getCurrentPosition() + (System.currentTimeMillis() - value.getTimeStamp())));
                        mMediaPlayerGlue.rewind();

                    } else {
                        mMediaPlayerGlue.addPlayerCallback(new PlaybackGlue.PlayerCallback() {
                            @Override
                            public void onPreparedStateChanged(PlaybackGlue glue) {
                                if (glue.isPrepared()) {
                                    glue.removePlayerCallback(this);
                                    mMediaPlayerGlue.seekTo((value.getCurrentPosition() + (System.currentTimeMillis() - value.getTimeStamp())));
                                    mMediaPlayerGlue.rewind();
                                }
                            }
                        });
                    }

                } else {
                    mMediaPlayerGlue.rewind();
                }
            }
            break;
            case FORWARD: {

                if (isSourceDiff) {

                    mMediaPlayerGlue.getPlayerAdapter().setDataSource(Uri.parse(currentUrl));
                    mMediaPlayerGlue.setSeekEnabled(false);
                    if (mMediaPlayerGlue.isPrepared()) {
                        mMediaPlayerGlue.seekTo((value.getCurrentPosition() + (System.currentTimeMillis() - value.getTimeStamp())));
                        mMediaPlayerGlue.fastForward();

                    } else {
                        mMediaPlayerGlue.addPlayerCallback(new PlaybackGlue.PlayerCallback() {
                            @Override
                            public void onPreparedStateChanged(PlaybackGlue glue) {
                                if (glue.isPrepared()) {
                                    glue.removePlayerCallback(this);
                                    mMediaPlayerGlue.seekTo((value.getCurrentPosition() + (System.currentTimeMillis() - value.getTimeStamp())));
                                    mMediaPlayerGlue.fastForward();
                                }
                            }
                        });
                    }

                } else {
                    mMediaPlayerGlue.fastForward();
                }
            }
            break;
            case SYNC: {

                if (nc == null) {
                    return;
                }

            }
            break;
            case BUFFER: {

            }
            break;
        }
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
