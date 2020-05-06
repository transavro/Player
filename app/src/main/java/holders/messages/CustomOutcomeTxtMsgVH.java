package holders.messages;

import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.stfalcon.chatkit.messages.MessageHolders;

import model.Message;
import tv.cloudwalker.player.R;

public class CustomOutcomeTxtMsgVH extends MessageHolders.OutcomingTextMessageViewHolder<Message> {

    private TextView username, textMsg ;
    private static final String TAG = "CustomOutcomeTxtMsgVH";

    public CustomOutcomeTxtMsgVH(View itemView, Object payload) {
        super(itemView, payload);
        username  = itemView.findViewById(R.id.messageUserName);
        textMsg  = itemView.findViewById(R.id.messageText);
    }

    @Override
    public void onBind(Message message) {
        super.onBind(message);

        if(username != null){
            username.setText(message.getUser().getName().substring(0, 1).toUpperCase().concat(message.getUser().getName().substring(1)));
        }
        if(textMsg != null){
            Log.i(TAG, "onBind: "+textMsg + message.getText());
            textMsg.setText(message.getText());
        }
        return;
    }
}
