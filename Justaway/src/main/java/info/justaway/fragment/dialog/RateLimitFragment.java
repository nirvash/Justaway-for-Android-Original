package info.justaway.fragment.dialog;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;

import info.justaway.R;
import info.justaway.util.ActionUtil;
import twitter4j.Status;

public class RateLimitFragment extends DialogFragment {
    @SuppressWarnings("ConstantConditions")
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
/*
        final Status status = (Status) getArguments().getSerializable("status");
        final long currentUserRetweetId = (long) getArguments().getSerializable("currentUserRetweetId");
        if (status == null) {
            return null;
        }
*/
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.menu_rate_limit);
        builder.setMessage("hoge");
        builder.setNegativeButton(getString(R.string.button_ok),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dismiss();
                    }
                }
        );
        return builder.create();
    }
}
