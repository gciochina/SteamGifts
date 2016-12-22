package net.mabako.sgtools;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;

import net.mabako.Constants;
import net.mabako.steamgifts.data.Giveaway;
import net.mabako.steamgifts.fragments.GiveawayDetailFragment;
import net.mabako.steamgifts.fragments.GiveawayListFragment;
import net.mabako.steamgifts.fragments.interfaces.IHasEnterableGiveaways;
import net.mabako.steamgifts.persistentdata.SteamGiftsUserData;
import net.mabako.steamgifts.tasks.EnterLeaveGiveawayTask;
import net.mabako.steamgifts.tasks.Utils;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class AutoJoinRecommendedGiveawayTask extends AsyncTask<Void, Void, List<Giveaway>> {
    private static final String TAG = AutoJoinRecommendedGiveawayTask.class.getSimpleName();

    private final int page;
    private final GiveawayListFragment.Type type;
    private final String searchQuery;
    private final boolean showPinnedGiveaways;

    private String foundXsrfToken = null;
    private SteamGiftsUserData steamGiftsUserData = new SteamGiftsUserData();
    private Context context = null;
    private NotificationManager mNotificationManager;
    private int NOTIFICATION_ID = 1;
    private int joinedGiveawaysCount;

    private String errorMessage = "";

    public AutoJoinRecommendedGiveawayTask(int page, GiveawayListFragment.Type type, String searchQuery, boolean showPinnedGiveaways, Context context) {
        this.page = page;
        this.type = type;
        this.searchQuery = searchQuery;
        this.showPinnedGiveaways = showPinnedGiveaways && type == GiveawayListFragment.Type.ALL && TextUtils.isEmpty(searchQuery);
        this.context = context;
    }

    @Override
    protected List<Giveaway> doInBackground(Void... params) {
        Log.d(TAG, "Fetching giveaways for page " + page);

        try {
            // Fetch the Giveaway page
            List<Giveaway> giveaways = GetGiveaways(GiveawayListFragment.Type.RECOMMENDED);
            List<Giveaway> wgiveaways = GetGiveaways(GiveawayListFragment.Type.WISHLIST);
            giveaways.addAll(wgiveaways);

            //only consider not joined giveaways
            List<Giveaway> giveawaysToEnter = new ArrayList<>(giveaways.size());
            for (Giveaway giveaway : giveaways) {
                if (!giveaway.isEntered())
                {
                    giveawaysToEnter.add(giveaway);
                }
            }

            return giveawaysToEnter;
        } catch (Exception e) {
            Log.e(TAG, "Error fetching URL", e);
            return null;
        }
    }

    private List<Giveaway> GetGiveaways(GiveawayListFragment.Type type) throws IOException {
        Connection jsoup = Jsoup.connect("https://www.steamgifts.com/giveaways/search")
                .userAgent(Constants.JSOUP_USER_AGENT)
                .timeout(Constants.JSOUP_TIMEOUT);
        jsoup.data("page", Integer.toString(page));

        if (searchQuery != null)
            jsoup.data("q", searchQuery);

        jsoup.data("type", type.name().toLowerCase(Locale.ENGLISH));

        steamGiftsUserData.getCurrent(context);

        if (SteamGiftsUserData.getCurrent(context).isLoggedIn())
            jsoup.cookie("PHPSESSID", SteamGiftsUserData.getCurrent(context).getSessionId());

        Document document = jsoup.get();

        // Fetch the xsrf token
        Element xsrfToken = document.select("input[name=xsrf_token]").first();
        if (xsrfToken != null)
            foundXsrfToken = xsrfToken.attr("value");

        // Do away with pinned giveaways.
        if (!showPinnedGiveaways)
            document.select(".pinned-giveaways__outer-wrap").html("");

        // Parse all rows of giveaways
        return Utils.loadGiveawaysFromList(document);
    }

    @Override
    protected void onPostExecute(List<Giveaway> result) {
        this.errorMessage = "";
        super.onPostExecute(result);
        joinedGiveawaysCount = 0;
        doDelegation(result, 0);
    }

    private void doDelegation(final List<Giveaway> giveawaysList, final int index)
    {
        if (SteamGiftsUserData.getCurrent(context).getPoints() > 10) {
            try {

                if (giveawaysList.get(index).getPoints() > SteamGiftsUserData.getCurrent(context).getPoints()) { //skip to next if we dont have enough points

                    doDelegation(giveawaysList, index + 1);
                }
                else {
                    IHasEnterableGiveaways hasEnterableGiveaways = new IHasEnterableGiveaways() {
                        @Override
                        public void requestEnterLeave(String giveawayId, String what, String xsrfToken) {

                        }

                        @Override
                        public void onEnterLeaveResult(String giveawayId, String what, Boolean success, boolean propagate) {
                            if (success) {
                                Log.v(TAG, "entered giveaway " + giveawayId);
                            } else {
                                Log.v(TAG, "failure on giveaway " + giveawayId);
                            }
                            joinedGiveawaysCount++;
                            doDelegation(giveawaysList, index + 1);
                        }
                    };

                    EnterLeaveGiveawayTask enterLeaveTask = new EnterLeaveGiveawayTask(hasEnterableGiveaways,
                            null,
                            giveawaysList.get(index).getGiveawayId(),
                            this.foundXsrfToken,
                            GiveawayDetailFragment.ENTRY_INSERT);

                    enterLeaveTask.execute();
                }
            }
            catch (Exception ex) {
                this.errorMessage += ex.getMessage() + System.getProperty("line.separator");;
            }
        }
        else {
            String notificationText = "AutoJoin completed (" + joinedGiveawaysCount + "/" + giveawaysList.size() + " joined)";
            if (errorMessage != null && errorMessage != "") {
                notificationText += System.getProperty("line.separator") + "Messages: " + System.getProperty("line.separator") + errorMessage;
            }

            createNotification("SteamGifts", notificationText, context);
        }
    }

    private void createNotification(String contentTitle, String contentText,Context context) {

        mNotificationManager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        //Build the notification using Notification.Builder
        Notification.Builder builder = new Notification.Builder(context)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setAutoCancel(true)
                .setContentTitle(contentTitle)
                .setContentText(contentText);


        //Show the notification
        mNotificationManager.notify(NOTIFICATION_ID, builder.getNotification());

    }
}
