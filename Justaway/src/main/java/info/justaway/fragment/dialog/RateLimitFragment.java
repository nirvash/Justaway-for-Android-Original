package info.justaway.fragment.dialog;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;

import java.util.Map;

import info.justaway.R;
import info.justaway.model.TwitterManager;
import info.justaway.util.ActionUtil;
import twitter4j.RateLimitStatus;
import twitter4j.Status;
import twitter4j.TwitterException;

public class RateLimitFragment extends DialogFragment {
    @SuppressWarnings("ConstantConditions")
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final String text = (String) getArguments().getSerializable("text");

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.menu_rate_limit);
        builder.setMessage(text);
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
