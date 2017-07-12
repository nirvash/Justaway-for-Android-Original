package info.justaway.adapter;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.util.LongSparseArray;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.ArrayList;

import butterknife.Bind;
import butterknife.ButterKnife;
import de.greenrobot.event.EventBus;
import info.justaway.BuildConfig;
import info.justaway.ProfileActivity;
import info.justaway.R;
import info.justaway.event.AlertDialogEvent;
import info.justaway.model.AccessTokenManager;
import info.justaway.model.FavRetweetManager;
import info.justaway.model.Row;
import info.justaway.model.UserIconManager;
import info.justaway.settings.BasicSettings;
import info.justaway.settings.MuteSettings;
import info.justaway.util.ActionUtil;
import info.justaway.util.ImageUtil;
import info.justaway.util.MessageUtil;
import info.justaway.util.MutableLinkMovementMethod;
import info.justaway.util.StatusUtil;
import info.justaway.util.ThemeUtil;
import info.justaway.util.TimeUtil;
import twitter4j.DirectMessage;
import twitter4j.Status;
import twitter4j.User;

import static de.greenrobot.event.EventBus.TAG;

public class TwitterAdapter extends ArrayAdapter<Row> {
    static final String TAG = TwitterAdapter.class.getSimpleName();

    static class ViewHolder {
        @Bind(R.id.action_container) ViewGroup mActionContainer;
        @Bind(R.id.action_icon) TextView mActionIcon;
        @Bind(R.id.action_by_display_name) TextView mActionByDisplayName;
        @Bind(R.id.action_by_screen_name) TextView mActionByScreenName;
        @Bind(R.id.icon) ImageView mIcon;
        @Bind(R.id.display_name) TextView mDisplayName;
        @Bind(R.id.screen_name) TextView mScreenName;
        @Bind(R.id.lock) TextView mLock;
        @Bind(R.id.datetime_relative) TextView mDatetimeRelative;
        @Bind(R.id.status) TextView mStatus;
        @Bind(R.id.quoted_display_name) TextView mQuotedDisplayName;
        @Bind(R.id.quoted_screen_name) TextView mQuotedScreenName;
        @Bind(R.id.quoted_status) TextView mQuotedStatus;
        @Bind(R.id.quoted_tweet) RelativeLayout mQuotedTweet;
        @Bind(R.id.quoted_images_container_wrapper) ViewGroup mQuotedImagesContainerWrapper;
        @Bind(R.id.quoted_images_container) ViewGroup mQuotedImagesContainer;
        @Bind(R.id.quoted_play) TextView mQuotedPlay;
        @Bind(R.id.images_container_wrapper) ViewGroup mImagesContainerWrapper;
        @Bind(R.id.images_container) ViewGroup mImagesContainer;
        @Bind(R.id.play) TextView mPlay;
        @Bind(R.id.menu_and_via_container) ViewGroup mMenuAndViaContainer;
        @Bind(R.id.do_reply) TextView mDoReply;
        @Bind(R.id.do_retweet) TextView mDoRetweet;
        @Bind(R.id.retweet_count) TextView mRetweetCount;
        @Bind(R.id.do_fav) TextView mDoFav;
        @Bind(R.id.fav_count) TextView mFavCount;
        @Bind(R.id.via) TextView mVia;
        @Bind(R.id.datetime) TextView mDatetime;
        @Bind(R.id.retweet_container) View mRetweetContainer;
        @Bind(R.id.retweet_icon) ImageView mRetweetIcon;
        @Bind(R.id.retweet_by) TextView mRetweetBy;

        public ViewHolder(View view) {
            ButterKnife.bind(this, view);
        }
    }

    private Context mContext;
    private LayoutInflater mInflater;
    private int mLayout;
    private int mColorBlue = 0;
    private static final int LIMIT = 100;
    private int mLimit = LIMIT;
    private final LongSparseArray<Boolean> mIdMap = new LongSparseArray<>();

    public TwitterAdapter(Context context, int textViewResourceId) {
        super(context, textViewResourceId);
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mContext = context;
        mLayout = textViewResourceId;
    }

    public Context getContext() {
        return mContext;
    }

    public void extensionAdd(Row row) {
        if (MuteSettings.isMute(row)) {
            return;
        }
        if (exists(row)) {
            return;
        }
        if (filterRetweet(row)) {
            return;
        }

        super.add(row);
        if (row.isStatus()) {
            mIdMap.put(row.getStatus().getId(), true);
        }
        filter(row);
        mLimit++;
    }

    @Override
    public void add(Row row) {
        if (MuteSettings.isMute(row)) {
            return;
        }
        if (exists(row)) {
            return;
        }
        if (filterRetweet(row)) {
            return;
        }

        super.add(row);
        if (row.isStatus()) {
            mIdMap.put(row.getStatus().getId(), true);
        }
        filter(row);
        limitation();
    }

    @Override
    public void insert(Row row, int index) {
        if (MuteSettings.isMute(row)) {
            return;
        }
        if (exists(row)) {
            return;
        }
        if (filterRetweet(row)) {
            return;
        }
        super.insert(row, index);
        if (row.isStatus()) {
            mIdMap.put(row.getStatus().getId(), true);
        }
        filter(row);
        limitation();
    }

    @Override
    public void remove(Row row) {
        super.remove(row);
        if (row.isStatus()) {
            mIdMap.remove(row.getStatus().getId());
        }
    }

    public boolean exists(Row row) {
        return row.isStatus() && mIdMap.get(row.getStatus().getId(), false);
    }

    public boolean filterRetweet(Row row) {
        return false; // Disabled
    }

    public boolean filterRetweetTest(Row row) {
        if (row.isStatus()) {
            try {
                Status status = row.getStatus();
                if (status != null && status.isRetweet()) {
                    Status retweet = status.getRetweetedStatus();
                    if (retweet != null) {
                        for (int i = 0; i < getCount(); i++) {
                            Row item = getItem(i);
                            if (item.isStatus() && item.getStatus().isRetweet()) {
                                Status status2 = item.getStatus();
                                Status retweet2 = status2.getRetweetedStatus();
                                if (retweet.getId() == retweet2.getId()) {
                                    if (status.getCreatedAt().before(status2.getCreatedAt())) {
                                        return true;
                                    } else {
                                        remove(item);
                                        return false;
                                    }
                                }
                            }

                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    public boolean retweetExists(Row row) {
        if (row.isStatus()) {
            Status status = row.getStatus();
            if (status != null && status.isRetweet()) {
                Status retweet = status.getRetweetedStatus();
                if (retweet != null) {
                    for (int i=0; i<getCount(); i++) {
                        Row item = getItem(i);
                        if (item.isStatus() && item.getStatus().isRetweet()) {
                            Status retweet2 = item.getStatus().getRetweetedStatus();
                            if (retweet.getId() == retweet2.getId()) {
                                return true;
                            }
                        }

                    }
                }
            }
        }
        return false;
    }

    private void filter(Row row) {
        Status status = row.getStatus();
        if (status != null && status.isRetweeted()) {
            Status retweet = status.getRetweetedStatus();
            if (retweet != null && status.getUser().getId() == AccessTokenManager.getUserId()) {
                FavRetweetManager.setRtId(retweet.getId(), status.getId());
            }
        }
    }

    public void updateFavorite(long id, boolean isFavorited) {
        for (int i = 0; i < getCount(); i++) {
            Row row = getItem(i);
            Status target = row.getStatus();
            if (target.getId() == id ||
                target.isRetweet() && target.getRetweetedStatus().getId() == id) {
                row.setFavorite(isFavorited);
                notifyDataSetChanged();
                // break; RTの場合複数タイムラインに表示されているので
            }
        }
    }

    public void updateRetweet(long id, long rtId, boolean isRetweeted) {
        Log.d("updateRetweet", String.format("id %d, rtId %d, isRetweeted %s", id, rtId, isRetweeted));
        for (int i = 0; i < getCount(); i++) {
            Row row = getItem(i);
            Status target = row.getStatus();
            if (target.getId() == id ||
                    target.isRetweet() && target.getRetweetedStatus().getId() == id) {
                row.setRetweet(isRetweeted);
                row.setCurrentUserRetweetId(rtId);
                notifyDataSetChanged();
                // break; RTの場合複数タイムラインに表示されているので
            }
        }
    }


    @SuppressWarnings("unused")
    public void replaceStatus(Status status) {
        for (int i = 0; i < getCount(); i++) {
            Row row = getItem(i);
            if (!row.isDirectMessage() && row.getStatus().getId() == status.getId()) {
                row.setStatus(status);
                notifyDataSetChanged();
                break;
            }
        }
    }

    public ArrayList<Integer> removeStatus2(long statusId) {
        int position = 0;
        ArrayList<Integer> positions = new ArrayList<>();
        ArrayList<Row> rows = new ArrayList<>();
        for (int i = 0; i < getCount(); i++) {
            Row row = getItem(i);
            if (row.isDirectMessage()) {
                continue;
            }
            twitter4j.Status status = row.getStatus();
            twitter4j.Status retweet = status.getRetweetedStatus();
            if (row.getStatus().getId() == statusId || (retweet != null && retweet.getId() == statusId)) {
                if (row.getStatus().getUser().getId() == AccessTokenManager.getUserId()) {
                    rows.add(row);
                    positions.add(position);
                } else {
                    row.setDeleted(true);
                }
            }
            position++;
        }
        for (Row row : rows) {
            remove(row);
        }
        notifyDataSetChanged();
        return positions;
    }

    public ArrayList<Integer> removeStatus(long statusId) {
        int position = 0;
        ArrayList<Integer> positions = new ArrayList<>();
        ArrayList<Row> rows = new ArrayList<>();
        for (int i = 0; i < getCount(); i++) {
            Row row = getItem(i);
            if (row.isDirectMessage()) {
                continue;
            }
            twitter4j.Status status = row.getStatus();
            twitter4j.Status retweet = status.getRetweetedStatus();
            if (row.getStatus().getId() == statusId || (retweet != null && retweet.getId() == statusId)) {
                rows.add(row);
                positions.add(position);
            }
            position++;
        }
        for (Row row : rows) {
            remove(row);
        }
        return positions;
    }

    public void removeDirectMessage(long directMessageId) {
        for (int i = 0; i < getCount(); i++) {
            Row row = getItem(i);
            if (row.isDirectMessage() && row.getMessage().getId() == directMessageId) {
                remove(row);
                break;
            }
        }
    }

    public void limitation() {
        int size = getCount();
        if (size > mLimit) {
            int count = size - mLimit;
            for (int i = 0; i < count; i++) {
                super.remove(getItem(size - i - 1));
            }
        }
    }

    @Override
    public void clear() {
        super.clear();
        mIdMap.clear();
        mLimit = LIMIT;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        // ビューを受け取る
        View view = convertView;

        if (view == null) {

            // 受け取ったビューがnullなら新しくビューを生成
            view = mInflater.inflate(this.mLayout, null);
            if (view == null) {
                return null;
            }
            holder = new ViewHolder(view);
            holder.mStatus.setTag(12); // fontsize
            view.setTag(holder);
        } else {
            holder = (ViewHolder) view.getTag();
        }

        Integer fontSize = BasicSettings.getFontSize();
        if (!fontSize.equals(holder.mStatus.getTag())) {
            holder.mStatus.setTag(fontSize);
            holder.mStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize);
            holder.mDisplayName.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize);
            holder.mScreenName.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize - 2);
            holder.mDatetimeRelative.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize - 2);
        }

        holder.mStatus.setBackgroundColor(Color.TRANSPARENT);
        holder.mStatus.setVisibility(View.VISIBLE);

        ImageUtil.hideImageContainer(holder.mImagesContainer, holder.mImagesContainerWrapper);
        ImageUtil.hideImageContainer(holder.mQuotedImagesContainer, holder.mQuotedImagesContainerWrapper);

        // 表示すべきデータの取得
        Row row = getItem(position);


        if (row.isDirectMessage()) {
            DirectMessage message = row.getMessage();
            if (message == null) {
                return view;
            }

            Long id = message.getId();
            holder.mImagesContainer.setTag(id);
            holder.mQuotedImagesContainer.setTag(id);

            renderMessage(holder, message);
        } else {
            Status status = row.getStatus();
            if (status == null) {
                return view;
            }


            // Log.d(TAG, String.format("renderStatus: %s, %d", status.getId(), position ));

            Status retweet = status.getRetweetedStatus();
            if (row.isFavorite()) {
                Long id = status.getId();
                holder.mImagesContainer.setTag(id);
                holder.mQuotedImagesContainer.setTag(id);
                renderStatus(holder, row, status, null, row.getSource());
            } else if (retweet == null) {
                Long id = status.getId();
                holder.mImagesContainer.setTag(id);
                holder.mQuotedImagesContainer.setTag(id);
                renderStatus(holder, row, status, null, null);
            } else {
                Long id = retweet.getId();
                holder.mImagesContainer.setTag(id);
                holder.mQuotedImagesContainer.setTag(id);
                renderStatus(holder, row, retweet, status, null);
            }
        }

        return view;
    }

    @SuppressLint("SetTextI18n")
    private void renderMessage(ViewHolder holder, final DirectMessage message) {

        long userId = AccessTokenManager.getUserId();

        holder.mStatus.setVisibility(View.VISIBLE);
        holder.mDoRetweet.setVisibility(View.GONE);
        holder.mDoFav.setVisibility(View.GONE);
        holder.mRetweetCount.setVisibility(View.GONE);
        holder.mFavCount.setVisibility(View.GONE);
        holder.mMenuAndViaContainer.setVisibility(View.VISIBLE);

        if (message.getSender().getId() == userId) {
            holder.mDoReply.setVisibility(View.GONE);
        } else {
            holder.mDoReply.setVisibility(View.VISIBLE);
            holder.mDoReply.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ActionUtil.doReplyDirectMessage(message, mContext);
                }
            });
        }

        holder.mDisplayName.setText(message.getSender().getName());
        holder.mScreenName.setText("@"
                + message.getSender().getScreenName());
        holder.mStatus.setText("D " + message.getRecipientScreenName()
                + " " + message.getText());
        holder.mDatetime
                .setText(TimeUtil.getAbsoluteTime(message.getCreatedAt()));
        holder.mDatetimeRelative.setText(TimeUtil.getRelativeTime(message.getCreatedAt()));
        holder.mVia.setVisibility(View.GONE);
        holder.mQuotedTweet.setVisibility(View.GONE);
        holder.mRetweetContainer.setVisibility(View.GONE);
        holder.mImagesContainer.setVisibility(View.GONE);
        holder.mImagesContainerWrapper.setVisibility(View.GONE);
        if (BasicSettings.enableSimpleLayout()) {
            holder.mIcon.setImageBitmap(null);
            holder.mIcon.setVisibility(View.INVISIBLE);
        } else {
            UserIconManager.displayUserIcon(message.getSender(), holder.mIcon);
        }
        holder.mIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(v.getContext(), ProfileActivity.class);
                intent.putExtra("screenName", message.getSender().getScreenName());
                mContext.startActivity(intent);
            }
        });
        holder.mActionContainer.setVisibility(View.GONE);
        holder.mLock.setVisibility(View.INVISIBLE);
    }

    @SuppressLint("SetTextI18n")
    private void renderStatus(final ViewHolder holder, final Row row, final Status status, Status retweet,
                              User favorite) {
        long userId = AccessTokenManager.getUserId();


        if (status.getFavoriteCount() > 0) {
            holder.mFavCount.setText(String.valueOf(status.getFavoriteCount()));
            holder.mFavCount.setVisibility(View.VISIBLE);
        } else {
            holder.mFavCount.setText("0");
            holder.mFavCount.setVisibility(View.INVISIBLE);
        }

        if (status.getRetweetCount() > 0) {
            holder.mRetweetCount.setText(String.valueOf(status.getRetweetCount()));
            holder.mRetweetCount.setVisibility(View.VISIBLE);
        } else {
            holder.mRetweetCount.setText("0");
            holder.mRetweetCount.setVisibility(View.INVISIBLE);
        }

        holder.mDoReply.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ActionUtil.doReplyAll(status, mContext);
            }
        });

        holder.mDoRetweet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (status.getUser().isProtected()) {
                    MessageUtil.showToast(R.string.toast_protected_tweet_can_not_share);
                    return;
                }

                if (row.isRetweeted()) {
                    DialogFragment dialog = new DestroyRetweetDialogFragment();
                    Bundle args = new Bundle(2);
                    args.putSerializable("status", status);
                    args.putSerializable("currentUserRetweetId", row.getCurrentUserRetweetId());
                    dialog.setArguments(args);
                    EventBus.getDefault().post(new AlertDialogEvent(dialog));
                } else {
                    DialogFragment dialog = new RetweetDialogFragment();
                    Bundle args = new Bundle(1);
                    args.putSerializable("status", status);
                    dialog.setArguments(args);
                    EventBus.getDefault().post(new AlertDialogEvent(dialog));
                }
            }
        });

        holder.mDoFav.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (row.isFavorited()) {
                    holder.mDoFav.setTag("no_fav");
                    holder.mDoFav.setTextColor(Color.parseColor("#666666"));
                    ActionUtil.doDestroyFavorite(status.getId());
                } else {
                    holder.mDoFav.setTag("is_fav");
                    holder.mDoFav.setTextColor(ContextCompat.getColor(mContext, R.color.holo_orange_light));
                    ActionUtil.doFavorite(status.getId());
                }
            }
        });

        if (row.isRetweeted()) {
            holder.mDoRetweet.setTextColor(ContextCompat.getColor(mContext, R.color.holo_green_light));
        } else {
            holder.mDoRetweet.setTextColor(Color.parseColor("#666666"));
        }

        if (row.isFavorited()) {
            holder.mDoFav.setTag("is_fav");
            holder.mDoFav.setTextColor(ContextCompat.getColor(mContext, R.color.holo_orange_light));
        } else {
            holder.mDoFav.setTag("no_fav");
            holder.mDoFav.setTextColor(Color.parseColor("#666666"));
        }

        holder.mDisplayName.setText(status.getUser().getName());
        holder.mScreenName.setText("@" + status.getUser().getScreenName());
        holder.mDatetimeRelative.setText(TimeUtil.getRelativeTime(status.getCreatedAt()));
        holder.mDatetime.setText(TimeUtil.getAbsoluteTime(status.getCreatedAt()));

        String via = StatusUtil.getClientName(status.getSource());
        holder.mVia.setText("via " + via);
        holder.mVia.setVisibility(View.VISIBLE);

        /**
         * デバッグモードの時だけ Justaway for Android をハイライト
         */
        if (BuildConfig.DEBUG) {
            if (via.equals("Justaway for Android")) {
                if (mColorBlue == 0) {
                    mColorBlue = ThemeUtil.getThemeTextColor(R.attr.holo_blue);
                }
                holder.mVia.setTextColor(mColorBlue);
            } else {
                holder.mVia.setTextColor(Color.parseColor("#666666"));
            }
        }

        // 自分以外からの favの場合
        if (favorite != null && favorite.getId() != userId) {
            holder.mActionIcon.setText(R.string.fontello_star);
            holder.mActionIcon.setTextColor(ContextCompat.getColor(mContext, R.color.holo_orange_light));
            holder.mActionByDisplayName.setText(favorite.getName());
            holder.mActionByScreenName.setText("@" + favorite.getScreenName());
            holder.mRetweetContainer.setVisibility(View.GONE);
            holder.mMenuAndViaContainer.setVisibility(View.VISIBLE);
            holder.mActionContainer.setVisibility(View.VISIBLE);
        }

        // RTの場合
        else if (retweet != null) {

            // 自分のツイート
            if (userId == status.getUser().getId()) {
                holder.mActionIcon.setText(R.string.fontello_retweet);
                holder.mActionIcon.setTextColor(ContextCompat.getColor(mContext, R.color.holo_green_light));
                holder.mActionByDisplayName.setText(retweet.getUser().getName());
                holder.mActionByScreenName.setText("@" + retweet.getUser().getScreenName());
                holder.mRetweetContainer.setVisibility(View.GONE);
                holder.mMenuAndViaContainer.setVisibility(View.VISIBLE);
                holder.mActionContainer.setVisibility(View.VISIBLE);
            } else {
                if (BasicSettings.getUserIconSize().equals("none")) {
                    holder.mRetweetIcon.setVisibility(View.GONE);
                } else {
                    holder.mRetweetIcon.setVisibility(View.VISIBLE);
//                    ImageUtil.displayRoundedImage(retweet.getUser().getProfileImageURL(), holder.mRetweetIcon);
                    ImageUtil.displayImage(retweet.getUser().getProfileImageURL(), holder.mRetweetIcon);
                }
                holder.mRetweetBy.setText(retweet.getUser().getName() + " @" + retweet.getUser().getScreenName());
                holder.mActionContainer.setVisibility(View.GONE);
                holder.mMenuAndViaContainer.setVisibility(View.VISIBLE);
                holder.mRetweetContainer.setVisibility(View.VISIBLE);
            }
        } else {

            // 自分へのリプ
            if (StatusUtil.isMentionForMe(status)) { // この表示要らない
                holder.mActionIcon.setText(R.string.fontello_at);
                holder.mActionIcon.setTextColor(ContextCompat.getColor(mContext, R.color.holo_red_light));
                holder.mActionByDisplayName.setText(status.getUser().getName());
                holder.mActionByScreenName.setText("@" + status.getUser().getScreenName());
                holder.mActionContainer.setVisibility(View.GONE); // いらない
            } else {
                holder.mActionContainer.setVisibility(View.GONE);
            }
            holder.mRetweetContainer.setVisibility(View.GONE);
            holder.mMenuAndViaContainer.setVisibility(View.VISIBLE);
        }

        if (status.getUser().isProtected()) {
            holder.mLock.setVisibility(View.VISIBLE);
        } else {
            holder.mLock.setVisibility(View.INVISIBLE);
        }

        if (BasicSettings.enableSimpleLayout()) {
            holder.mIcon.setImageBitmap(null);
            holder.mIcon.setVisibility(View.INVISIBLE);
        } else {
            UserIconManager.displayUserIcon(status.getUser(), holder.mIcon);
        }
        holder.mIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(v.getContext(), ProfileActivity.class);
                intent.putExtra("screenName", status.getUser().getScreenName());
                mContext.startActivity(intent);
            }
        });

        // RTの場合はRT元
        String statusString = "";
        if (retweet != null) {
            if (retweet.getRetweetedStatus() != null) {
                statusString = StatusUtil.getExpandedText(retweet.getRetweetedStatus());
            } else {
                statusString = StatusUtil.getExpandedText(status);
            }
        } else {
            statusString = StatusUtil.getExpandedText(status);
        }
        if (TextUtils.isEmpty(statusString)) {
            holder.mStatus.setVisibility(View.GONE);
        } else {
            holder.mStatus.setText(StatusUtil.generateUnderline(statusString, getContext()));
            holder.mStatus.setOnTouchListener(new MutableLinkMovementMethod.OnTouchListener());
        }

        // 引用ツイート
        Status quotedStatus = status.getQuotedStatus();
        if (quotedStatus != null) {
            holder.mQuotedDisplayName.setText(quotedStatus.getUser().getName());
            holder.mQuotedScreenName.setText(quotedStatus.getUser().getScreenName());
            holder.mQuotedStatus.setText(StatusUtil.generateUnderline(quotedStatus.getText(), getContext()));
            holder.mQuotedStatus.setOnTouchListener(new MutableLinkMovementMethod.OnTouchListener());

            // プレビュー表示On
            if (BasicSettings.getDisplayThumbnailOn()) {
                ImageUtil.displayThumbnailImages(mContext, holder.mQuotedImagesContainer, holder.mImagesContainerWrapper, holder.mQuotedPlay, quotedStatus);
                if (BasicSettings.enableSimpleLayout()) {
                    holder.mQuotedImagesContainer.setAlpha(0.5f);
                } else {
                    holder.mQuotedImagesContainer.setAlpha(1.0f);
                }
            } else {
                ImageUtil.hideImageContainer(holder.mQuotedImagesContainer, holder.mQuotedImagesContainerWrapper);
            }
            holder.mQuotedTweet.setVisibility(View.VISIBLE);
        } else {
            holder.mQuotedTweet.setVisibility(View.GONE);
        }

        // プレビュー表示On
        if (BasicSettings.getDisplayThumbnailOn()) {
            ImageUtil.displayThumbnailImages(mContext, holder.mImagesContainer, holder.mImagesContainerWrapper, holder.mPlay, status);
            if (BasicSettings.enableSimpleLayout()) {
                holder.mImagesContainer.setAlpha(0.5f);
            } else {
                holder.mImagesContainer.setAlpha(1.0f);
            }
        } else {
            ImageUtil.hideImageContainer(holder.mImagesContainer, holder.mImagesContainerWrapper);
        }


        // ツイ消し表示
        if (row.isDeleted()) {
            holder.mStatus.setBackgroundColor(Color.GRAY);
        }
    }

    public static final class RetweetDialogFragment extends DialogFragment {
        @SuppressWarnings("ConstantConditions")
        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Status status = (Status) getArguments().getSerializable("status");
            if (status == null) {
                return null;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(R.string.confirm_retweet);
            builder.setMessage(status.getText());
            builder.setNeutralButton(getString(R.string.button_fav_and_retweet),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ActionUtil.doRetweet(status.getId()); // 順序大事
                            if (!status.isFavorited()) {
                                ActionUtil.doFavorite(status.getId(), false);
                            }
                            dismiss();
                        }
                    }
            );
            builder.setPositiveButton(getString(R.string.button_retweet),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ActionUtil.doRetweet(status.getId());
                            dismiss();
                        }
                    }
            );
            builder.setNegativeButton(getString(R.string.button_cancel),
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

    public static final class DestroyRetweetDialogFragment extends DialogFragment {
        @SuppressWarnings("ConstantConditions")
        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Status status = (Status) getArguments().getSerializable("status");
            final long currentUserRetweetId = (long) getArguments().getSerializable("currentUserRetweetId");
            if (status == null) {
                return null;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(R.string.confirm_destroy_retweet);
            builder.setMessage(status.getText());
            builder.setPositiveButton(getString(R.string.button_destroy_retweet),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ActionUtil.doDestroyRetweet(status, currentUserRetweetId);
                            dismiss();
                        }
                    }
            );
            builder.setNegativeButton(getString(R.string.button_cancel),
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
}
