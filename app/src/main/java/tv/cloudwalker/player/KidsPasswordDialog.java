package tv.cloudwalker.player;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import java.lang.reflect.Method;

import io.grpc.stub.StreamObserver;
import main.MediaChatServiceGrpc;
import main.Watch;

public class KidsPasswordDialog extends DialogFragment implements View.OnClickListener {
    private static final String TAG = "KidsPasswordDialog";

    private TextView pass1, pass2, pass3, pass4;
    private int count = 0;
    private Button passButton;
    private Button clearButton;
    private LinearLayout passParent;
    private MediaChatServiceGrpc.MediaChatServiceStub mediaChatServiceStub;
    private int sourceId;


    public KidsPasswordDialog(){

    }

    public KidsPasswordDialog(MediaChatServiceGrpc.MediaChatServiceStub mediaChatServiceStub, int sourceId) {
        this.mediaChatServiceStub = mediaChatServiceStub;
        this.sourceId = sourceId;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.i(TAG, "onCreateView: ");
        View v = inflater.inflate(R.layout.kids_password, container, false);
        initView(v);
        return v;
    }


    private void initView(View v) {

        pass1 = v.findViewById(R.id.pass1);
        Log.d(TAG, "initView: "+pass1);
        pass2 = v.findViewById(R.id.pass2);
        pass3 = v.findViewById(R.id.pass3);
        pass4 = v.findViewById(R.id.pass4);
        passButton = v.findViewById(R.id.passwordButton);
        clearButton = v.findViewById(R.id.clearButton);
        passParent = v.findViewById(R.id.passparent);

        passButton.setOnClickListener(this);
        clearButton.setOnClickListener(this);

    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Dialog myDialog = getDialog();
        myDialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface dialogInterface, int i, KeyEvent keyEvent) {

                if (keyEvent.getAction() == KeyEvent.ACTION_UP && keyEvent.getKeyCode() > 6 && keyEvent.getKeyCode() < 17) {
                    setPattern(keyEvent.getKeyCode());
                } else if (keyEvent.getAction() == KeyEvent.ACTION_DOWN && keyEvent.getKeyCode() == 4) {
                    dialogInterface.dismiss();
                }
                return false;
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        reset();
    }


    private void reset() {
        passParent.setFocusable(true);
        passParent.requestFocus();
        passParent.requestFocusFromTouch();
        pass1.setText("");
        pass2.setText("");
        pass3.setText("");
        pass4.setText("");
        count = 0;
//        passButton.setFocusable(false);
//        clearButton.setFocusable(false);
    }

    private int getKeyValueFromCode(int code) {
        switch (code) {
            case 7:
                return 0;
            case 8:
                return 1;
            case 9:
                return 2;
            case 10:
                return 3;
            case 11:
                return 4;
            case 12:
                return 5;
            case 13:
                return 6;
            case 14:
                return 7;
            case 15:
                return 8;
            case 16:
                return 9;
        }
        return -1;
    }

    private void setPattern(int keycode) {
        count++;
        Log.d(TAG, "setPattern: "+pass1);
        if (count == 1) {
            pass1.setText(String.valueOf(getKeyValueFromCode(keycode)));
        } else if (count == 2) {
            pass2.setText(String.valueOf(getKeyValueFromCode(keycode)));
        } else if (count == 3) {
            pass3.setText(String.valueOf(getKeyValueFromCode(keycode)));
        } else if (count == 4) {
            pass4.setText(String.valueOf(getKeyValueFromCode(keycode)));
        } else {
            Log.i(TAG, "setPattern: returned");
            return;
        }
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

    private String mergingCode() {
        return pass1.getText().toString() + pass2.getText().toString() + pass3.getText().toString() + pass4.getText().toString();
    }

    @Override
    public void onClick(final View view) {
        switch (view.getId()) {
            case R.id.passwordButton: {
                final String targetId = mergingCode();
                Log.d(TAG, "onClick: target id "+targetId);
                setSystemProperty("playerTargetCode", targetId);
                if(mediaChatServiceStub != null){
                    mediaChatServiceStub.player(Watch.MediaChat.newBuilder()
                            .setPlayerMeta(Watch.PlayerMeta.newBuilder().setPlayerState(Watch.PlayerState.SYNC).build())
                            .setSourceId(sourceId).setTargetId(Integer.valueOf(mergingCode())).build(), new StreamObserver<Watch.None>() {
                        @Override
                        public void onNext(Watch.None value) {
                            Log.i(TAG, "onNext: "+value);
                        }

                        @Override
                        public void onError(Throwable t) {
                            Log.e(TAG, "onError: ",t.getCause());
                        }

                        @Override
                        public void onCompleted()
                        {
                            Log.i(TAG, "onCompleted: "+targetId);
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(view.getContext(), "Player Sync "+targetId, Toast.LENGTH_SHORT).show();
                                    KidsPasswordDialog.this.dismiss();
                                }
                            });
                        }
                    });
                }
            }
            break;
            case R.id.clearButton: {
                reset();
            }
            break;
        }
    }
}