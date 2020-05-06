package tv.cloudwalker.player;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;
import android.view.Gravity;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import cloudwalker.Wewatch;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import io.nats.client.MessageHandler;
import io.nats.client.Nats;
import io.nats.client.Options;
import model.User;


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
    private String userName = "";
    private Connection nc = null;
    private boolean ignoreSelf = false;
    private Options options = new Options.Builder().server("nats://3.6.231.212:80")
            .connectionListener((conn, type) -> {
                if (nc == null) {
                    nc = conn;
                }
                Log.i(TAG, "connectionEvent: " + conn.toString() + "  " + type.toString());
            }).build();


    private Dispatcher mainDispatcher;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            connectToMessagingServer();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        inputName();
        mMediaPlayerGlue = new PlaybackTransportControlGlueSample<MediaPlayerAdapter>(getActivity(), new MediaPlayerAdapter(getActivity())) {

            @Override
            public void onActionClicked(Action action) {
                super.onActionClicked(action);
                if (action.getId() == 7777) {
                    if (!isRoomMade && !isJoinRoom) {
                        if (currentUrl.isEmpty()) {
                            showToast("Please play any video to make a room ");
                        } else {
                            makeRoom(action);
                        }
                    } else if (isJoinRoom) {
                        showToast("Already Join a room with id = " + roomId);

                    } else {
                        showToast("Already Made a room with id = " + roomId + " , Please inform others to join room");
                    }
                } else if (action.getId() == 8888) {
                    if (isRoomMade) {
                        showToast("Already Made a room with id = " + roomId + " , Please inform others to join room");
                    } else if (isJoinRoom) {
                        showToast("Already Join a room with id = " + roomId);
                    } else {
                        showDialogPopUp();
                    }
                } else if (action.getId() == 9999) {

                    if (roomId.isEmpty() || userName.isEmpty()) {
                        showToast("Set UserName and RoomID to open QRCode.");
                        return;
                    }

                    try {
                        String text = ((VideoSupportActivity) getActivity()).getEthMac() + "~" + userName + "~" + roomId;
//                        String text = "homeTv" + "~" + userName + "~" + roomId;
                        MultiFormatWriter multiFormatWriter = new MultiFormatWriter();
                        ImageView image = new ImageView(getActivity());
                        BitMatrix bitMatrix = multiFormatWriter.encode(text, BarcodeFormat.QR_CODE, 400, 400);
                        BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
                        Bitmap bitmap = barcodeEncoder.createBitmap(bitMatrix);
                        image.setPadding(20, 20, 20, 20);
                        image.setImageBitmap(bitmap);
                        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.Theme_AppCompat)
                                .setMessage("Scan QR code to chat.")
                                .setView(image)
                                .setCancelable(true);

                        AlertDialog dialog = builder.create();
                        Window window = dialog.getWindow();
                        if (window == null) return;
                        window.setLayout(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                        window.setGravity(Gravity.CENTER);
                        window.setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.transparent)));
                        dialog.show();


                        new Timer().schedule(new TimerTask() {
                            @Override
                            public void run() {
                                dialog.dismiss();
                            }
                        }, 10000);

                    } catch (WriterException e) {
                        e.printStackTrace();
                    }

                } else if (action.getId() == R.id.lb_control_more_actions) {
                    selectSource();
                } else {
                    if (nc != null && !roomId.isEmpty()) {
                        Log.i(TAG, "onActionClicked: PUBLISHING... " + roomId + "  " + ignoreSelf);
                        streamWriter(action);
                    }
                }
            }

            @Override
            protected void onUpdateBufferedProgress() {
                Log.d(TAG, "onUpdateBufferedProgress: ");
                super.onUpdateBufferedProgress();
            }

            @Override
            protected void onPlayStateChanged() {
                Log.d(TAG, "onPlayStateChanged: ");
                super.onPlayStateChanged();
            }

            @Override
            protected void onPreparedStateChanged() {
                Log.d(TAG, "onPreparedStateChanged: ");
                super.onPreparedStateChanged();
            }

            @Override
            protected void onUpdateDuration() {
                Log.d(TAG, "onUpdateDuration: ");
                super.onUpdateDuration();
            }

            @Override
            protected void onUpdateProgress() {
                Log.d(TAG, "onUpdateProgress: ");
                super.onUpdateProgress();
            }
        };
        MediaSessionCompat mMediaSessionCompat = new MediaSessionCompat(getActivity(), MEDIA_SESSION_COMPAT_TOKEN);
        mMediaPlayerGlue.connectToMediaSession(mMediaSessionCompat);
        mMediaPlayerGlue.setHost(mHost);
        mMediaPlayerGlue.setMode(PlaybackControlsRow.RepeatAction.INDEX_NONE);
    }


    private void inputName() {

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.Theme_AppCompat);
        builder.setTitle("Enter Name");

        final EditText input = new EditText(getActivity());
        input.setPadding(20, 20, 20, 20);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(400, 400);
        lp.setMarginStart(20);
        lp.setMarginEnd(20);
        input.setLayoutParams(lp);
        builder.setView(input);

        builder.setCancelable(false);
        builder.setPositiveButton("ok", (dialog, which) -> {
            userName = input.getText().toString();
            if (!userName.isEmpty()) {
                dialog.dismiss();
            } else {
                Toast.makeText(getActivity(), "Please enter your Name.", Toast.LENGTH_SHORT).show();
            }
        });
        AlertDialog dialog = builder.create();

        Window window = dialog.getWindow();
        if (window == null) return;
        window.setLayout(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        window.setGravity(Gravity.CENTER);
        window.setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.transparent)));

        dialog.show();
    }

    private void streamWriter(Action action) {
        Wewatch.PlayerStates playerStates = null;
        if (action.getId() == R.id.lb_control_play_pause) {
            if (mMediaPlayerGlue.isPlaying()) {
                playerStates = Wewatch.PlayerStates.PLAY;
            } else {
                playerStates = Wewatch.PlayerStates.PAUSE;
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
        builder.setNegativeButton("Back", (dialog, which) -> dialog.dismiss());
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

                mMediaPlayerGlue.setSubtitle("WeWatch Group Id = " + roomId);
                mMediaPlayerGlue.setTitle("Video Title" + which);
                currentUrl = animals[which];
                mMediaPlayerGlue.getPlayerAdapter().setDataSource(Uri.parse(currentUrl));
                mMediaPlayerGlue.setSeekEnabled(false);
                playWhenReady();
                if (!roomId.isEmpty() && nc != null) {

                    nc.publish(roomId, Wewatch.Messaging.newBuilder()
                            .setTitle(mMediaPlayerGlue.getTitle().toString())
                            .setSubtitle(mMediaPlayerGlue.getTitle().toString())
                            .setUrl(currentUrl)
                            .setCurrentPosition(mMediaPlayerGlue.getCurrentPosition())
                            .setTimeStamp(System.currentTimeMillis())
                            .setPlayerState(Wewatch.PlayerStates.PLAY).build().toByteArray());
                }
                dialog.dismiss();
            }
        });
        // create and show the alert dialog
        AlertDialog dialog = builder.create();

        Window window = dialog.getWindow();
        if (window == null) return;
        window.setLayout(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        window.setGravity(Gravity.CENTER);
        window.setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.transparent)));

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
            if (resultCode == Activity.RESULT_OK && data != null) {
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
        try {
            mMediaPlayerGlue.disconnectToMediaSession();
            mainDispatcher.unsubscribe(roomId);
            nc.closeDispatcher(mainDispatcher);
            nc.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        super.onStop();
    }

    private void makeRoom(final Action action) {
        Random random = new Random();
        roomId = String.format(Locale.ENGLISH, "%04d", random.nextInt(10000));
        showToast("RoomID is " + roomId);
        action.setIcon(getResources().getDrawable(R.drawable.broadcastblack, null));
        Log.i(TAG, "makeRoom: " + roomId);
        action.setLabel1(String.valueOf(roomId));
        mMediaPlayerGlue.getControlsRow().getPrimaryActionsAdapter().notifyItemRangeChanged(3, 1);
        mMediaPlayerGlue.setSubtitle("WeWatch Group Id = " + roomId);
        isRoomMade = true;
        startReading();
    }

    private void joinRoom() {
        ((PlaybackControlsRow.MoreActions) mMediaPlayerGlue.getControlsRow()
                .getPrimaryActionsAdapter()
                .get(4))
                .setIcon(getResources().getDrawable(R.drawable.add_in_room_black, null));

        mMediaPlayerGlue.getControlsRow().getPrimaryActionsAdapter().notifyItemRangeChanged(4, 1);
        isJoinRoom = true;
        Log.i(TAG, "joinRoom: ");
        ignoreSelf = true;
        Log.d(TAG, "joinRoom: JOIN REQUEST PUBLISHED..");
        nc.publish(roomId, userName, "sync".getBytes(StandardCharsets.UTF_8));

        Dispatcher d = nc.createDispatcher(new MessageHandler() {
            @Override
            public void onMessage(Message msg) {

                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {

                            Wewatch.Messaging value = Wewatch.Messaging.parseFrom(msg.getData());
                            mMediaPlayerGlue.setTitle(value.getTitle());
                            mMediaPlayerGlue.setSubtitle("WeWatch Group Id = " + roomId);
                            currentUrl = value.getUrl();
                            mMediaPlayerGlue.getPlayerAdapter().setDataSource(Uri.parse(currentUrl));
                            mMediaPlayerGlue.setSeekEnabled(false);
                            if (mMediaPlayerGlue.isPrepared()) {
                                mMediaPlayerGlue.seekTo((value.getCurrentPosition() + (System.currentTimeMillis() - value.getTimeStamp()) + 500)); //offset of 1000
                                if (value.getPlayerState() == Wewatch.PlayerStates.PAUSE) {
                                    mMediaPlayerGlue.pause();
                                } else {
                                    mMediaPlayerGlue.play();
                                }
                            } else {
                                mMediaPlayerGlue.addPlayerCallback(new PlaybackGlue.PlayerCallback() {
                                    @Override
                                    public void onPreparedStateChanged(PlaybackGlue glue) {
                                        if (glue.isPrepared()) {
                                            glue.removePlayerCallback(this);
                                            mMediaPlayerGlue.seekTo((value.getCurrentPosition() + (System.currentTimeMillis() - value.getTimeStamp()) + 500));
                                            if (value.getPlayerState() == Wewatch.PlayerStates.PAUSE) {
                                                glue.pause();
                                            } else {
                                                glue.play();
                                            }
                                        }
                                    }
                                });
                            }

                        } catch (InvalidProtocolBufferException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        });

        d.subscribe(userName);
        d.unsubscribe(userName, 5000);
        startReading();
    }

    private void startReading() {

        mainDispatcher = nc.createDispatcher(msg -> {

            if (isRoomMade && msg.getReplyTo() != null && !msg.getReplyTo().isEmpty()) {
                Log.d(TAG, "onMessage: HADELING SYNCE MSG " + isRoomMade + "  " + ignoreSelf);
                Wewatch.PlayerStates playerStates;
                if (mMediaPlayerGlue.isPlaying()) {
                    playerStates = Wewatch.PlayerStates.PLAY;
                } else {
                    playerStates = Wewatch.PlayerStates.PAUSE;
                }
                nc.publish(msg.getReplyTo(),
                        Wewatch.Messaging.newBuilder()
                                .setTitle(mMediaPlayerGlue.getTitle().toString())
                                .setSubtitle(mMediaPlayerGlue.getSubtitle().toString())
                                .setUrl(currentUrl)
                                .setPlayerState(playerStates)
                                .setCurrentPosition(mMediaPlayerGlue.getCurrentPosition())
                                .setTimeStamp(System.currentTimeMillis()).build().toByteArray());

            } else if (ignoreSelf) {
                Log.d(TAG, "onMessage: IGNORE NOOP " + ignoreSelf);
                ignoreSelf = false;
                //noop
            } else {

                getActivity().runOnUiThread(() -> {

                    try {
                        Wewatch.Chat chat = Wewatch.Chat.parseFrom(msg.getData());

                        if (chat.getId() == null ||
                                chat.getMessage() == null ||
                                chat.getUserName() == null ||
                                chat.getUserName().isEmpty() ||
                                chat.getId().isEmpty()) {
                            Log.d(TAG, "onMessage: PLAYER MESSAGE RECEIVE MSG ");
                            // not a chat message check of player msg
                            Wewatch.Messaging messaging = Wewatch.Messaging.parseFrom(msg.getData());
                            syncStream(messaging);

                        } else {
                            Log.d(TAG, "onMessage: CHAT RECEIVE MSG ");
                            User user = new User(chat.getId(), chat.getUserName(), "", true);
                            model.Message message = new model.Message(((VideoSupportActivity) getActivity()).getEthMac(), user, chat.getMessage());
//                                    model.Message message = new model.Message("homeTv", user, chat.getMessage());
                            ((VideoSupportActivity) getActivity()).chat(message);

                        }
                    } catch (InvalidProtocolBufferException e) {
                        e.printStackTrace();
                    }
                });
            }
        });

        mainDispatcher.subscribe(roomId);
    }


    private void syncStream(final Wewatch.Messaging value) {

        boolean isSourceDiff = false;
        if (value.getTitle() != null && value.getTitle() != mMediaPlayerGlue.getTitle()) {
            mMediaPlayerGlue.setTitle(value.getTitle());
        }
        if (value.getSubtitle() != null && value.getSubtitle() != mMediaPlayerGlue.getSubtitle()) {
            mMediaPlayerGlue.setSubtitle("WeWatch Group Id = " + roomId);
        }
        if (value.getUrl() != null &&  !value.getUrl().isEmpty()  &&  !currentUrl.equals(value.getUrl())) {
            isSourceDiff = true;
            currentUrl = value.getUrl();
        }
//        else {
//            mMediaPlayerGlue.seekTo((value.getCurrentPosition()) + (System.currentTimeMillis() - value.getTimeStamp()) + 500);
//        }
        switch (value.getPlayerState()) {
            case PLAY: {
                if (isSourceDiff) {
                    mMediaPlayerGlue.getPlayerAdapter().setDataSource(Uri.parse(currentUrl));
                    mMediaPlayerGlue.setSeekEnabled(false);
                    if (mMediaPlayerGlue.isPrepared()) {
                        mMediaPlayerGlue.seekTo((value.getCurrentPosition() + (System.currentTimeMillis() - value.getTimeStamp()) + 500));
                        mMediaPlayerGlue.play();
                    } else {
                        mMediaPlayerGlue.addPlayerCallback(new PlaybackGlue.PlayerCallback() {
                            @Override
                            public void onPreparedStateChanged(PlaybackGlue glue) {
                                if (glue.isPrepared()) {
                                    glue.removePlayerCallback(this);
                                    mMediaPlayerGlue.seekTo((value.getCurrentPosition() + (System.currentTimeMillis() - value.getTimeStamp()) + 500));
                                    glue.play();
                                }
                            }
                        });
                    }

                } else {
                    mMediaPlayerGlue.seekTo((value.getCurrentPosition()) + (System.currentTimeMillis() - value.getTimeStamp()) + 500);
                    mMediaPlayerGlue.play();
                }
            }
            break;
            case PAUSE: {
                if (isSourceDiff) {

                    mMediaPlayerGlue.getPlayerAdapter().setDataSource(Uri.parse(currentUrl));
                    mMediaPlayerGlue.setSeekEnabled(false);
                    if (mMediaPlayerGlue.isPrepared()) {
                        mMediaPlayerGlue.seekTo((value.getCurrentPosition() + (System.currentTimeMillis() - value.getTimeStamp()) + 500));
                        mMediaPlayerGlue.pause();
                    } else {
                        mMediaPlayerGlue.addPlayerCallback(new PlaybackGlue.PlayerCallback() {
                            @Override
                            public void onPreparedStateChanged(PlaybackGlue glue) {
                                if (glue.isPrepared()) {
                                    glue.removePlayerCallback(this);
                                    mMediaPlayerGlue.seekTo((value.getCurrentPosition() + (System.currentTimeMillis() - value.getTimeStamp()) + 500));
                                    glue.pause();
                                }
                            }
                        });
                    }

                } else {
                    mMediaPlayerGlue.seekTo((value.getCurrentPosition()) + (System.currentTimeMillis() - value.getTimeStamp()) + 500);
                    mMediaPlayerGlue.pause();
                }
            }
            break;
            case REWIND: {

                if (isSourceDiff) {

                    mMediaPlayerGlue.getPlayerAdapter().setDataSource(Uri.parse(currentUrl));
                    mMediaPlayerGlue.setSeekEnabled(false);
                    if (mMediaPlayerGlue.isPrepared()) {
                        mMediaPlayerGlue.seekTo((value.getCurrentPosition() + (System.currentTimeMillis() - value.getTimeStamp()) + 500));
                        mMediaPlayerGlue.play();
//                        mMediaPlayerGlue.rewind();

                    } else {
                        mMediaPlayerGlue.addPlayerCallback(new PlaybackGlue.PlayerCallback() {
                            @Override
                            public void onPreparedStateChanged(PlaybackGlue glue) {
                                if (glue.isPrepared()) {
                                    glue.removePlayerCallback(this);
                                    mMediaPlayerGlue.seekTo((value.getCurrentPosition() + (System.currentTimeMillis() - value.getTimeStamp()) + 500));
                                    mMediaPlayerGlue.play();
//                                    mMediaPlayerGlue.rewind();
                                }
                            }
                        });
                    }

                } else {
                    mMediaPlayerGlue.seekTo((value.getCurrentPosition()) + (System.currentTimeMillis() - value.getTimeStamp()) + 500);

//                    mMediaPlayerGlue.rewind();
                }
            }
            break;
            case FORWARD: {

                if (isSourceDiff) {

                    mMediaPlayerGlue.getPlayerAdapter().setDataSource(Uri.parse(currentUrl));
                    mMediaPlayerGlue.setSeekEnabled(false);
                    if (mMediaPlayerGlue.isPrepared()) {
                        mMediaPlayerGlue.seekTo((value.getCurrentPosition() + (System.currentTimeMillis() - value.getTimeStamp()) + 500));
                        mMediaPlayerGlue.play();
//                        mMediaPlayerGlue.fastForward();

                    } else {
                        mMediaPlayerGlue.addPlayerCallback(new PlaybackGlue.PlayerCallback() {
                            @Override
                            public void onPreparedStateChanged(PlaybackGlue glue) {
                                if (glue.isPrepared()) {
                                    glue.removePlayerCallback(this);
                                    mMediaPlayerGlue.seekTo((value.getCurrentPosition() + (System.currentTimeMillis() - value.getTimeStamp()) + 500));
                                    mMediaPlayerGlue.play();
//                                    mMediaPlayerGlue.fastForward();
                                }
                            }
                        });
                    }

                } else {
                    mMediaPlayerGlue.seekTo((value.getCurrentPosition()) + (System.currentTimeMillis() - value.getTimeStamp()) + 500);


//                    mMediaPlayerGlue.fastForward();
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
        getActivity().runOnUiThread(() -> Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show());
    }
}
