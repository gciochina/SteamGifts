package net.mabako.steamgifts.fragments;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import net.mabako.steam.store.StoreAppFragment;
import net.mabako.steam.store.StoreSubFragment;
import net.mabako.steamgifts.R;
import net.mabako.steamgifts.activities.DetailActivity;
import net.mabako.steamgifts.activities.WebViewActivity;
import net.mabako.steamgifts.activities.WriteCommentActivity;
import net.mabako.steamgifts.adapters.CommentAdapter;
import net.mabako.steamgifts.adapters.EndlessAdapter;
import net.mabako.steamgifts.adapters.IEndlessAdaptable;
import net.mabako.steamgifts.data.BasicGiveaway;
import net.mabako.steamgifts.data.Comment;
import net.mabako.steamgifts.data.Game;
import net.mabako.steamgifts.data.Giveaway;
import net.mabako.steamgifts.data.GiveawayExtras;
import net.mabako.steamgifts.fragments.interfaces.ICommentableFragment;
import net.mabako.steamgifts.fragments.interfaces.IHasEnterableGiveaways;
import net.mabako.steamgifts.fragments.interfaces.IHasHideableGiveaways;
import net.mabako.steamgifts.fragments.util.GiveawayDetailsCard;
import net.mabako.steamgifts.fragments.util.GiveawayListFragmentStack;
import net.mabako.steamgifts.persistentdata.SavedGiveaways;
import net.mabako.steamgifts.tasks.EnterLeaveGiveawayTask;
import net.mabako.steamgifts.tasks.LoadGiveawayDetailsTask;
import net.mabako.steamgifts.tasks.UpdateGiveawayFilterTask;

import java.io.Serializable;
import java.util.List;

public class GiveawayDetailFragment extends ListFragment<CommentAdapter> implements ICommentableFragment, IHasEnterableGiveaways, IHasHideableGiveaways {
    public static final String ARG_GIVEAWAY = "giveaway";
    public static final String ENTRY_INSERT = "entry_insert";
    public static final String ENTRY_DELETE = "entry_delete";
    private static final String TAG = GiveawayDetailFragment.class.getSimpleName();

    private boolean fragmentAdded = false;

    /**
     * Content to show for the giveaway details.
     */
    private BasicGiveaway giveaway;
    private GiveawayDetailsCard giveawayCard;
    private EnterLeaveGiveawayTask enterLeaveTask;
    private Activity activity;
    private SavedGiveaways savedGiveaways;

    public static Fragment newInstance(BasicGiveaway giveaway) {
        GiveawayDetailFragment fragment = new GiveawayDetailFragment();
        fragment.giveaway = giveaway;
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            giveawayCard = new GiveawayDetailsCard();


            // Add the cardview for the Giveaway details
            adapter.setStickyItem(giveawayCard);
        }
    }

    @Override
    protected CommentAdapter createAdapter() {
        return new CommentAdapter<>(this, new EndlessAdapter.OnLoadListener() {
            @Override
            public void onLoad(int page) {
                fetchItems(page);
            }
        });
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof Activity)
            this.activity = (Activity) context;

        savedGiveaways = new SavedGiveaways(getContext());
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View layout = super.onCreateView(inflater, container, savedInstanceState);

        if (giveaway instanceof Giveaway) {
            onPostGiveawayLoaded((Giveaway) giveaway, true);
        } else {
            Log.d(TAG, "Loading activity for basic giveaway " + giveaway.getGiveawayId());
        }

        setHasOptionsMenu(true);

        return layout;
    }

    public void reload() {
        fetchItems(1);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (enterLeaveTask != null)
            enterLeaveTask.cancel(true);
    }

    @Override
    protected AsyncTask<Void, Void, ?> getFetchItemsTask(int page) {
        String url = giveaway.getGiveawayId();
        if (giveaway instanceof Giveaway)
            url += "/" + ((Giveaway) giveaway).getName();

        return new LoadGiveawayDetailsTask(this, url, page, !(giveaway instanceof Giveaway));
    }

    @Override
    protected Serializable getType() {
        return null;
    }

    public void addItems(GiveawayExtras extras, int page) {
        if (extras == null)
            return;

        // We should always have a giveaway instance at this point of time, as
        // #onPostGiveawayLoaded is called prior to this method.
        if (!(giveaway instanceof Giveaway))
            throw new IllegalStateException("#onPostGiveawayLoaded was probably not called");
        ((Giveaway) giveaway).setTimeRemaining(extras.getTimeRemaining());

        giveawayCard.setExtras(extras);
        getActivity().supportInvalidateOptionsMenu();
        adapter.setStickyItem(giveawayCard);

        super.addItems(extras.getComments(), page == 1);
    }

    @Override
    public void addItems(List<? extends IEndlessAdaptable> items, boolean clearExistingItems) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addItems(List<? extends IEndlessAdaptable> items, boolean clearExistingItems, String xsrfToken) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void requestEnterLeave(String giveawayId, String enterOrDelete, String xsrfToken) {
        if (enterLeaveTask != null)
            enterLeaveTask.cancel(true);

        enterLeaveTask = new EnterLeaveGiveawayTask(this, giveawayId, xsrfToken, enterOrDelete);
        enterLeaveTask.execute();
    }

    /**
     * This is called for the current game, but not for other games up the stack - only list fragments.
     *
     * @param giveawayId ID of the giveaway
     * @param what       what kind of action was executed
     * @param success    whether or not the action was successful
     * @param propagate
     */
    @Override
    public void onEnterLeaveResult(String giveawayId, String what, Boolean success, boolean propagate) {
        Log.v(TAG, "Enter Leave Result -> " + what + ", " + success);
        if (success == Boolean.TRUE) {

            GiveawayExtras extras = giveawayCard.getExtras();
            extras.setEntered(ENTRY_INSERT.equals(what));
            ((Giveaway) giveaway).setEntered(extras.isEntered());

            giveawayCard.setExtras(extras);
            adapter.setStickyItem(giveawayCard);
        } else {
            Log.e(TAG, "Probably an error catching the result...");
        }

        if (propagate)
            GiveawayListFragmentStack.onEnterLeaveResult(giveawayId, what, success);
    }

    /**
     * Set the details from the task started by {@link #fetchItems(int)}.
     *
     * @param giveaway giveaway this is for
     */
    private void onPostGiveawayLoaded(Giveaway giveaway, boolean ignoreExisting) {
        // Called this twice, eh...
        if (this.giveaway instanceof Giveaway && !ignoreExisting)
            return;

        this.giveaway = giveaway;
        giveawayCard.setGiveaway(giveaway);

        final CollapsingToolbarLayout appBarLayout = (CollapsingToolbarLayout) getActivity().findViewById(R.id.toolbar_layout);
        if (appBarLayout != null) {
            appBarLayout.setTitle(giveaway.getTitle());

            ImageView toolbarImage = (ImageView) getActivity().findViewById(R.id.toolbar_image);
            if (toolbarImage != null) {
                Picasso.with(getContext()).load("http://cdn.akamai.steamstatic.com/steam/" + giveaway.getType().name().toLowerCase() + "s/" + giveaway.getGameId() + "/header.jpg").into(toolbarImage, new Callback() {
                    @Override
                    public void onSuccess() {
                        appBarLayout.setExpandedTitleTextAppearance(R.style.TransparentText);
                    }

                    @Override
                    public void onError() {

                    }
                });
            }
        } else {
            ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
            if (actionBar != null)
                actionBar.setTitle(giveaway.getTitle());
        }


        // Re-build the options menu, which may not be created if no giveaway was present.
        getActivity().supportInvalidateOptionsMenu();

        if (getActivity() instanceof DetailActivity && giveaway.getGameId() != Game.NO_APP_ID && !fragmentAdded) {
            ((DetailActivity) getActivity()).addFragment(giveaway.getType() == Game.Type.APP ? StoreAppFragment.newInstance(giveaway.getGameId(), this) : StoreSubFragment.newInstance(giveaway.getGameId(), this));
            fragmentAdded = true;
        }
    }

    public void onPostGiveawayLoaded(Giveaway giveaway) {
        onPostGiveawayLoaded(giveaway, false);
    }

    @Override
    public void showProfile(String user) {
        Intent intent = new Intent(getActivity(), DetailActivity.class);
        intent.putExtra(UserDetailFragment.ARG_USER, user);
        getActivity().startActivity(intent);
    }

    @Override
    public void requestComment(Comment parentComment) {
        if (giveaway instanceof Giveaway) {
            Intent intent = new Intent(getActivity(), WriteCommentActivity.class);
            intent.putExtra(WriteCommentActivity.XSRF_TOKEN, giveawayCard.getExtras().getXsrfToken());
            intent.putExtra(WriteCommentActivity.PATH, "giveaway/" + giveaway.getGiveawayId() + "/" + ((Giveaway) giveaway).getName());
            intent.putExtra(WriteCommentActivity.PARENT, parentComment);
            intent.putExtra(WriteCommentActivity.TITLE, ((Giveaway) giveaway).getTitle());
            getActivity().startActivityForResult(intent, WriteCommentActivity.REQUEST_COMMENT);
        } else
            throw new IllegalStateException("Commenting on a not fully loaded Giveaway");
    }

    @Override
    public void onCreateOptionsMenu(
            Menu menu, MenuInflater inflater) {
        if (giveaway instanceof Giveaway) {
            inflater.inflate(R.menu.giveaway_menu, menu);
            menu.findItem(R.id.open_steam_store).setVisible(((Giveaway) giveaway).getGameId() > 0);
            menu.findItem(R.id.hide_game).setVisible(((Giveaway) giveaway).getInternalGameId() > 0 && giveawayCard.getExtras() != null && giveawayCard.getExtras().getXsrfToken() != null);

            if (savedGiveaways != null) {
                boolean isSaved = savedGiveaways.isSaved(giveaway.getGiveawayId());
                menu.findItem(R.id.add_saved_element).setVisible(!isSaved);
                menu.findItem(R.id.remove_saved_element).setVisible(isSaved);
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.open_steam_store:
                if (this.giveaway instanceof Giveaway) {
                    Giveaway giveaway = (Giveaway) this.giveaway;
                    Log.i(TAG, "Opening Steam Store entry for game " + giveaway.getGameId());

                    Intent intent = new Intent(activity, WebViewActivity.class);
                    intent.putExtra(WebViewActivity.ARG_URL, "http://store.steampowered.com/" + giveaway.getType().name().toLowerCase() + "/" + giveaway.getGameId() + "/");
                    activity.startActivity(intent);
                }
                return true;

            case R.id.hide_game:
                new UpdateGiveawayFilterTask<>(this, giveawayCard.getExtras().getXsrfToken(), UpdateGiveawayFilterTask.HIDE, ((Giveaway) giveaway).getInternalGameId(), ((Giveaway) giveaway).getTitle()).execute();
                return true;

            case R.id.add_saved_element:
                if (giveaway instanceof Giveaway && savedGiveaways.add((Giveaway) giveaway, giveaway.getGiveawayId())) {
                    getActivity().supportInvalidateOptionsMenu();
                    Toast.makeText(getContext(), R.string.added_saved_giveaway, Toast.LENGTH_SHORT).show();
                }
                return true;

            case R.id.remove_saved_element:
                if (giveaway instanceof Giveaway && savedGiveaways.remove(giveaway.getGiveawayId())) {
                    getActivity().supportInvalidateOptionsMenu();
                    Toast.makeText(getContext(), R.string.removed_saved_giveaway, Toast.LENGTH_SHORT).show();

                    GiveawayListFragmentStack.onRemoveSavedGiveaway(giveaway.getGiveawayId());
                }
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * This is called for the current game, but not for other games up the stack - only list fragments.
     *
     * @param internalGameId
     * @param propagate
     * @param title
     */
    @Override
    public void onHideGame(int internalGameId, boolean propagate, String title) {
        activity.finish();

        if (propagate)
            GiveawayListFragmentStack.onHideGame(internalGameId);
    }
}
