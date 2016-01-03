package net.mabako.steamgifts.tasks;

import android.util.Log;

import net.mabako.steamgifts.data.Comment;
import net.mabako.steamgifts.data.ICommentHolder;

import org.jsoup.nodes.Element;

public final class Utils {
    /**
     * Extract comments recursively.
     *
     * @param commentNode Jsoup-Node of the parent node.
     * @param parent
     */
    public static void loadComments(Element commentNode, ICommentHolder parent) {
        loadComments(commentNode, parent, 0);
    }

    private static void loadComments(Element commentNode, ICommentHolder parent, int depth) {
        for(Element c : commentNode.children()) {
            Element thisComment = c.child(0);

            // Remove "Save Changes" & "Cancel"
            thisComment.select(".comment__edit-state").html("");

            Element authorNode = thisComment.select(".comment__username").first();
            String author = authorNode.text();

            String avatar = null;
            Element avatarNode = thisComment.select(".global__image-inner-wrap").first();
            if(avatarNode != null)
                avatar = extractAvatar(avatarNode.attr("style"));

            Element desc = thisComment.select(".comment__description").first();
            desc.select(".comment__toggle-attached").html("");
            String content = desc.html();

            Element timeRemaining = thisComment.select(".comment__actions > div span").first();

            // public Comment(int id, String author, String timeAgo, String timeAgoLong, String content) {
            Comment comment = new Comment(Integer.parseInt(c.attr("data-comment-id")), author, timeRemaining.text(), timeRemaining.attr("title"), content, depth, avatar);

            // add this
            parent.addComment(comment);

            // Add all children
            loadComments(c.select(".comment__children").first(), parent, depth + 1);
        }
    }

    public static String extractAvatar(String style) {
        return style.replace("background-image:url(", "").replace(");", "").replace("_medium", "_full");
    }
}