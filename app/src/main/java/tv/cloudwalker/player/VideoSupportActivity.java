
package tv.cloudwalker.player;

import android.os.Bundle;
import android.os.Handler;
import android.view.View;

import androidx.fragment.app.FragmentActivity;

import com.stfalcon.chatkit.messages.MessageHolders;
import com.stfalcon.chatkit.messages.MessagesList;
import com.stfalcon.chatkit.messages.MessagesListAdapter;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import cloudwalker.Wewatch;
import holders.messages.CustomIncomeTxtMsgVH;
import holders.messages.CustomOutcomeTxtMsgVH;
import model.Message;

public class VideoSupportActivity extends FragmentActivity {

    private List<PictureInPictureListener> mListeners = new ArrayList<>();
    private MessagesList messagesList;
    private Handler inviViewHandler;

    private Runnable invisibleRunner = new Runnable() {
        @Override
        public void run() {
            messagesList.setVisibility(View.INVISIBLE);
        }
    };

    protected MessagesListAdapter<Message> messagesAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.playback_activity);
        messagesList = findViewById(R.id.messagesList);
        inviViewHandler = new Handler();
        messagesList.bringToFront();
        messagesList.setFocusable(false);
        messagesList.setFocusableInTouchMode(false);
        messagesList.setSelected(false);
        initAdapter();
    }


    private void initAdapter() {

        MessageHolders holdersConfig = new MessageHolders()
                .setIncomingTextConfig(CustomIncomeTxtMsgVH.class, R.layout.item_custom_incoming_text_message, Wewatch.Chat.getDefaultInstance())
                .setOutcomingTextConfig(CustomOutcomeTxtMsgVH.class, R.layout.item_custom_outcoming_text_message, Wewatch.Chat.getDefaultInstance());

        messagesAdapter = new MessagesListAdapter<>(getEthMac(), holdersConfig,null);

        this.messagesList.setAdapter(messagesAdapter);
    }

    public boolean chat(Message message) {
        messagesList.setVisibility(View.VISIBLE);
        inviViewHandler.removeCallbacks(invisibleRunner);
        messagesAdapter.addToStart(message, true);
        return inviViewHandler.postDelayed(invisibleRunner, 5000);
    }


    protected String getEthMac() {
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
