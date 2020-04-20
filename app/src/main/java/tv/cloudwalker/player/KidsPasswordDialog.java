package tv.cloudwalker.player;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
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

import cloudwalker.WeWatchGrpc;
import cloudwalker.Wewatch;
import io.grpc.stub.StreamObserver;

public class KidsPasswordDialog extends DialogFragment implements View.OnClickListener {
    private static final String TAG = "KidsPasswordDialog";

    private TextView pass1, pass2, pass3, pass4;
    private int count = 0;
    private LinearLayout passParent;
    private WeWatchGrpc.WeWatchStub weWatchStub;
    private String token;


    public KidsPasswordDialog() {

    }

    public KidsPasswordDialog(WeWatchGrpc.WeWatchStub weWatchStub, String token) {
        this.weWatchStub = weWatchStub;
        this.token = token;
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
        Log.d(TAG, "initView: " + pass1);
        pass2 = v.findViewById(R.id.pass2);
        pass3 = v.findViewById(R.id.pass3);
        pass4 = v.findViewById(R.id.pass4);
        Button passButton = v.findViewById(R.id.passwordButton);
        Button clearButton = v.findViewById(R.id.clearButton);
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
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
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
        Log.d(TAG, "setPattern: " + pass1);
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

    private String mergingCode() {
        return pass1.getText().toString() + pass2.getText().toString() + pass3.getText().toString() + pass4.getText().toString();
    }

    @Override
    public void onClick(final View view) {
        switch (view.getId()) {
            case R.id.passwordButton: {

                final String groupId = mergingCode();

                Log.d(TAG, "onClick: target id " + groupId);

                weWatchStub.joinRoom(Wewatch.JoinRoomRequest.newBuilder().setToken(token).setRoomId(Integer.parseInt(groupId)).build()
                        , new StreamObserver<Wewatch.JoinRoomResponse>() {

                    @Override
                    public void onNext(final Wewatch.JoinRoomResponse value) {
                        Log.i(TAG, "onNext: join room ");
                        switch (value.getJointState()) {
                            case ADDED: {
                                getActivity().runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(getContext(), "Added to group !!", Toast.LENGTH_SHORT).show();
                                        Bundle bundle = new Bundle();
                                        bundle.putInt("roomId", Integer.parseInt(groupId));
                                        bundle.putByteArray(Wewatch.RoomMeta.class.getSimpleName(), value.getRoomMeta().toByteArray());
                                        Intent intent = new Intent().putExtras(bundle);
                                        getTargetFragment().onActivityResult(getTargetRequestCode(), Activity.RESULT_OK, intent);
                                        KidsPasswordDialog.this.dismiss();
                                    }
                                });
                                return;
                            }
                            case ALREADY:
                                getActivity().runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(getContext(), "Already in the group !!", Toast.LENGTH_SHORT).show();
                                        KidsPasswordDialog.this.dismiss();
                                    }
                                });
                                return;
                            case UNKNOWN:
                                getActivity().runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(getContext(), "Group Doesnt exits !!", Toast.LENGTH_SHORT).show();
                                        KidsPasswordDialog.this.dismiss();
                                    }
                                });
                                return;
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        Log.e(TAG, "onError: join room ",t.getCause() );
                    }

                    @Override
                    public void onCompleted() {
                        Log.i(TAG, "onCompleted: join room ");
                    }
                });
            }
            break;
            case R.id.clearButton: {
                reset();
            }
            break;
        }
    }
}