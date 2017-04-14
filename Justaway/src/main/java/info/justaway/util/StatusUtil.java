package info.justaway.util;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.text.style.UnderlineSpan;
import android.view.View;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import info.justaway.R;
import info.justaway.model.AccessTokenManager;
import twitter4j.ExtendedMediaEntity;
import twitter4j.MediaEntity;
import twitter4j.Status;
import twitter4j.URLEntity;
import twitter4j.UserMentionEntity;

public class StatusUtil {
    private static final boolean ENABLE_EXPAND_URL = false;

    private static final Pattern TWITTER_PATTERN = Pattern.compile("(\\n)?https?://twitter\\.com/[\\w\\.\\-/:#\\?=&;%~\\+]+");
    private static final Pattern TWITPIC_PATTERN = Pattern.compile("^http://twitpic\\.com/(\\w+)$");
    private static final Pattern TWIPPLE_PATTERN = Pattern.compile("^http://p\\.twipple\\.jp/(\\w+)$");
    private static final Pattern INSTAGRAM_PATTERN = Pattern.compile("^https?://(?:www\\.)?instagram\\.com/p/([^/]+)/$");
    private static final Pattern PHOTOZOU_PATTERN = Pattern.compile("^http://photozou\\.jp/photo/show/\\d+/(\\d+)$");
    private static final Pattern IMAGES_PATTERN = Pattern.compile("^https?://.*\\.(png|gif|jpeg|jpg)$");
    private static final Pattern YOUTUBE_PATTERN = Pattern.compile("^https?://(?:www\\.youtube\\.com/watch\\?.*v=|youtu\\.be/)([\\w-]+)");
    private static final Pattern NICONICO_PATTERN = Pattern.compile("^http://(?:www\\.nicovideo\\.jp/watch|nico\\.ms)/sm(\\d+)$");
    private static final Pattern PIXIV_PATTERN = Pattern.compile("^http://www\\.pixiv\\.net/member_illust\\.php.*illust_id=(\\d+)");
    private static final Pattern GYAZO_PATTERN = Pattern.compile("^https?://gyazo\\.com/(\\w+)");

    private static final Pattern URL_PATTERN = Pattern.compile("(http://|https://)[\\w\\.\\-/:#\\?=&;%~\\+]+");
    private static final Pattern MENTION_PATTERN = Pattern.compile("@[a-zA-Z0-9_]+");
    private static final Pattern HASHTAG_PATTERN = Pattern.compile("#\\S+");

    // (?!)   否定先読み Foo(?!Bar) は Foo に Bar が続かないパターンのみマッチし、Bar は抽出されない (グループ括弧とみなされない)
    // (?<!)  否定後読み (?<!Bar)Foo は Foo に Bar が先行しないパターンのみマッチし、Bar は抽出されない (グループ括弧とみなされない)
    private static final Pattern GRANBLUE_FANTASY_ID_PATTERN = Pattern.compile("(?<![0-9a-zA-Z\\.\\-/#\\?=&;%~\\+])([0-9a-fA-F]{6}|[0-9a-fA-F]{8})(?![0-9a-zA-Z\\.\\-/#\\?=&;%~\\+])");

    /**
     * source(via)からクライアント名を抜き出す
     *
     * @param source <a href="クライアントURL">クライアント名</a>という文字列
     * @return クライアント名
     */
    public static String getClientName(String source) {
        String[] tokens = source.split("[<>]");
        if (tokens.length > 1) {
            return tokens[2];
        } else {
            return tokens[0];
        }
    }

    /**
     * 自分宛てのメンションかどうかを判定する
     *
     * @param status ツイート
     * @return true ... 自分宛てのメンション
     */
    public static boolean isMentionForMe(Status status) {
        long userId = AccessTokenManager.getUserId();
        if (status.getInReplyToUserId() == userId) {
            return true;
        }
        UserMentionEntity[] mentions = status.getUserMentionEntities();
        for (UserMentionEntity mention : mentions) {
            if (mention.getId() == userId) {
                return true;
            }
        }
        return false;
    }

    /**
     * 短縮URLを表示用URLに置換する
     *
     * @param status ツイート
     * @return 短縮URLを展開したツイート本文
     */
    public static String getExpandedText(Status status) {
        String text = status.getText();

        if (ENABLE_EXPAND_URL) {
            for (URLEntity url : status.getURLEntities()) {
                Pattern p = Pattern.compile(url.getURL());
                Matcher m = p.matcher(text);
                text = m.replaceAll(url.getExpandedURL());
            }

            for (MediaEntity media : status.getMediaEntities()) {
                Pattern p = Pattern.compile(media.getURL());
                Matcher m = p.matcher(text);
                text = m.replaceAll(media.getExpandedURL());
            }
        }

        Matcher m = TWITTER_PATTERN.matcher(text);
        text = m.replaceAll("");

        return text;
    }

    /**
     * ツイートに含まれる画像のURLをすべて取得する
     *
     * @param status ツイート
     * @return 画像のURL
     */
    public static ArrayList<String> getImageUrls(Status status) {
        ArrayList<String> imageUrls = new ArrayList<String>();
        for (URLEntity url : status.getURLEntities()) {
            Matcher twitpic_matcher = TWITPIC_PATTERN.matcher(url.getExpandedURL());
            if (twitpic_matcher.find()) {
                imageUrls.add("http://twitpic.com/show/full/" + twitpic_matcher.group(1));
                continue;
            }
            Matcher twipple_matcher = TWIPPLE_PATTERN.matcher(url.getExpandedURL());
            if (twipple_matcher.find()) {
                imageUrls.add("http://p.twpl.jp/show/orig/" + twipple_matcher.group(1));
                continue;
            }
            Matcher instagram_matcher = INSTAGRAM_PATTERN.matcher(url.getExpandedURL());
            if (instagram_matcher.find()) {
                imageUrls.add(url.getExpandedURL() + "media?size=l");
                continue;
            }
            Matcher photozou_matcher = PHOTOZOU_PATTERN.matcher(url.getExpandedURL());
            if (photozou_matcher.find()) {
                imageUrls.add("http://photozou.jp/p/img/" + photozou_matcher.group(1));
                continue;
            }
            Matcher youtube_matcher = YOUTUBE_PATTERN.matcher(url.getExpandedURL());
            if (youtube_matcher.find()) {
                imageUrls.add("http://i.ytimg.com/vi/" + youtube_matcher.group(1) + "/hqdefault.jpg");
                continue;
            }
            Matcher niconico_matcher = NICONICO_PATTERN.matcher(url.getExpandedURL());
            if (niconico_matcher.find()) {
                int id = Integer.valueOf(niconico_matcher.group(1));
                int host = id % 4 + 1;
                imageUrls.add("http://tn-skr" + host + ".smilevideo.jp/smile?i=" + id + ".L");
                continue;
            }
            Matcher pixiv_matcher = PIXIV_PATTERN.matcher(url.getExpandedURL());
            if (pixiv_matcher.find()) {
                imageUrls.add("http://embed.pixiv.net/decorate.php?illust_id=" + pixiv_matcher.group(1));
                continue;
            }
            Matcher gyazo_matcher = GYAZO_PATTERN.matcher(url.getExpandedURL());
            if (gyazo_matcher.find()) {
                imageUrls.add("https://i.gyazo.com/" + gyazo_matcher.group(1) + ".png");
                continue;
            }
            Matcher images_matcher = IMAGES_PATTERN.matcher(url.getExpandedURL());
            if (images_matcher.find()) {
                imageUrls.add(url.getExpandedURL());
            }
        }

        for (MediaEntity media : status.getMediaEntities()) {
            imageUrls.add(media.getMediaURL());
        }

        return imageUrls;
    }

    public static String getVideoUrl(Status status) {
        for (final MediaEntity mediaEntity : status.getMediaEntities()) {
            for (final MediaEntity.Variant videoVariant : mediaEntity.getVideoVariants()) {
                if (videoVariant.getUrl().lastIndexOf("mp4") != -1) {
                    return videoVariant.getUrl();
                }
            }
        }
        return "";
    }

    public static SpannableStringBuilder generateUnderline(String str, final Context context) {
        // URL、メンション、ハッシュタグ が含まれていたら下線を付ける
        SpannableStringBuilder sb = new SpannableStringBuilder();
        sb.append(str);
        UnderlineSpan us;

        Matcher urlMatcher = URL_PATTERN.matcher(str);
        while (urlMatcher.find()) {
/*            us = new UnderlineSpan();
            sb.setSpan(us, urlMatcher.start(), urlMatcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
*/
            final Uri uri = Uri.parse(urlMatcher.group());
            ClickableSpan cs = new ClickableSpan() {
                @Override
                public void onClick(View widget) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                        context.startActivity(intent);
                    } catch (Exception e) {
                    }
                }
            };

            sb.setSpan(cs, urlMatcher.start(), urlMatcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        String tmpStr = Normalizer.normalize(str, Normalizer.Form.NFKC).toUpperCase();
        final Matcher idMatcher = GRANBLUE_FANTASY_ID_PATTERN.matcher(tmpStr);
        while (idMatcher.find()) {
            final String id = idMatcher.group();
            ClickableSpan cs = new ClickableSpan() {
                @Override
                public void onClick(View widget) {
                    ClipboardManager cm = (ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("label", id));
                    String message = String.format(context.getString(R.string.copy_id_success), id);
                    MessageUtil.showToast(message);

                    PackageManager pm = context.getPackageManager();
                    Intent intent = pm.getLaunchIntentForPackage("jp.mbga.a12016007.lite");
                    try {
                        context.startActivity(intent);
                    } catch (Exception e) {
                    }
                }
            };

            sb.setSpan(cs, idMatcher.start(), idMatcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

/*
        Matcher mentionMatcher = MENTION_PATTERN.matcher(str);
        while (mentionMatcher.find()) {
            us = new UnderlineSpan();
            sb.setSpan(us, mentionMatcher.start(), mentionMatcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        Matcher hashtagMatcher = HASHTAG_PATTERN.matcher(str);
        while (hashtagMatcher.find()) {
            us = new UnderlineSpan();
            sb.setSpan(us, hashtagMatcher.start(), hashtagMatcher.end(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        }
*/
        return sb;
    }

    /*
     * 最初に見つかった ID を返す
     */
    public static String getGranbluefantasyId(String text) {
        String textFiltered = Normalizer.normalize(text, Normalizer.Form.NFKC);
        textFiltered = textFiltered.toLowerCase();

        Matcher m = GRANBLUE_FANTASY_ID_PATTERN.matcher(textFiltered);
        if (m.find()) {
            text = m.group();
        }
        return text;
    }

    public static boolean hasGranblueFantasyId(String text) {
        String textFiltered = Normalizer.normalize(text, Normalizer.Form.NFKC);
        textFiltered = textFiltered.toLowerCase();

        Matcher m = GRANBLUE_FANTASY_ID_PATTERN.matcher(textFiltered);
        return m.find();
    }
}
