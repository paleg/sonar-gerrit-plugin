package org.jenkinsci.plugins.sonargerrit.review;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import org.jenkinsci.plugins.sonargerrit.config.NotificationConfig;
import org.jenkinsci.plugins.sonargerrit.config.ReviewConfig;
import org.jenkinsci.plugins.sonargerrit.config.ScoreConfig;
import org.jenkinsci.plugins.sonargerrit.inspection.entity.Issue;
import org.jenkinsci.plugins.sonargerrit.review.formatter.CustomIssueFormatter;
import org.jenkinsci.plugins.sonargerrit.review.formatter.CustomReportFormatter;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Project: Sonar-Gerrit Plugin
 * Author:  Tatiana Didik
 * Created: 28.11.2017 14:03
 * <p/>
 * $Id$
 */
public class GerritReviewBuilder {
    private Multimap<String, Issue> finalIssuesToComment;
    private Multimap<String, Issue> finalIssuesToScore;
    private ReviewConfig reviewConfig;
    private ScoreConfig scoreConfig;
    private NotificationConfig notificationConfig;
    private String sonarURL;

    public GerritReviewBuilder(Multimap<String, Issue> finalIssuesToComment, Multimap<String, Issue> finalIssuesToScore, ReviewConfig reviewConfig, ScoreConfig scoreConfig, NotificationConfig notificationConfig, String sonarURL) {
        this.finalIssuesToComment = finalIssuesToComment;
        this.finalIssuesToScore = finalIssuesToScore;
        this.reviewConfig = reviewConfig;
        this.scoreConfig = scoreConfig;
        this.notificationConfig = notificationConfig;
        this.sonarURL = sonarURL;
    }

    public ReviewInput buildReview() {
        // review
        String reviewMessage = getReviewMessage(finalIssuesToComment);
        ReviewInput reviewInput = new ReviewInput().message(reviewMessage);
        reviewInput.comments = generateComments();

        // score
        int score = 0;
        if (postScore()) {
            score = getReviewMark(finalIssuesToScore.size());
            String category = scoreConfig.getCategory();

            reviewInput.label(category, score);
        }

        // notification
        reviewInput.notify = getNotificationSettings(finalIssuesToComment.size(), score);
        return reviewInput;
    }

    private boolean postScore() {
        return scoreConfig != null;
    }

    private String getReviewMessage(Multimap<String, Issue> finalIssues) {
        return new CustomReportFormatter(finalIssues.values(), reviewConfig.getSomeIssuesTitleTemplate(), reviewConfig.getNoIssuesTitleTemplate()).getMessage();
    }

    private int getReviewMark(int finalIssuesCount) {
        return finalIssuesCount > 0 ? scoreConfig.getIssuesScore() : scoreConfig.getNoIssuesScore();
    }

    private NotifyHandling getNotificationSettings(int commentsCount, int score) {
        if (score < 0) {
            return NotifyHandling.valueOf(notificationConfig.getNegativeScoreNotificationRecipient());
        } else if (commentsCount > 0) {
            return NotifyHandling.valueOf(notificationConfig.getCommentedIssuesNotificationRecipient());
        } else {
            return NotifyHandling.valueOf(notificationConfig.getNoIssuesNotificationRecipient());
        }
    }

    private Map<String, List<ReviewInput.CommentInput>> generateComments() {
        Map<String, List<ReviewInput.CommentInput>> file2comments = new HashMap<String, List<ReviewInput.CommentInput>>();
        for (String file : finalIssuesToComment.keySet()) {
            Collection<Issue> issues = finalIssuesToComment.get(file);
            Collection<ReviewInput.CommentInput> comments = Collections2.transform(issues, new IssueToCommentTransformation());
            ArrayList<ReviewInput.CommentInput> commentList = Lists.newArrayList(comments);
            file2comments.put(file, commentList);
        }
        return file2comments;
    }

    protected ReviewInput.CommentInput createComment(@Nullable Issue input) {
        if (input == null) {
            return null;
        }

        String commentTemplate = reviewConfig.getIssueCommentTemplate();
        String message = new CustomIssueFormatter(input, commentTemplate, sonarURL).getMessage();

        ReviewInput.CommentInput commentInput = new ReviewInput.CommentInput();
        commentInput.id = input.getKey();
        commentInput.line = input.getLine();
        commentInput.message = message;
        return commentInput;
    }

    private class IssueToCommentTransformation implements Function<Issue, ReviewInput.CommentInput> {
        @Nullable
        @Override
        public ReviewInput.CommentInput apply(@Nullable Issue input) {
            return createComment(input);
        }
    }

}
