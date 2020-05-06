package holders.messages;

import android.view.View;
import android.widget.TextView;

import com.stfalcon.chatkit.messages.MessageHolders;

import cloudwalker.Wewatch;
import model.Message;
import tv.cloudwalker.player.R;

public class CustomIncomeTxtMsgVH extends MessageHolders.IncomingTextMessageViewHolder<Message> {

    private TextView username, textMsg ;

    public CustomIncomeTxtMsgVH(View itemView, Object payload) {
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
            textMsg.setText(message.getText());
        }
        return;
    }

}


