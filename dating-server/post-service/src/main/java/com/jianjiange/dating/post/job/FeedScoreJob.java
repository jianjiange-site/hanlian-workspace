package com.jianjiange.dating.post.job;

import com.jianjiange.dating.post.client.UserClient;
import com.jianjiange.dating.post.entity.PostEntity;
import com.jianjiange.dating.post.entity.PostStatEntity;
import com.jianjiange.dating.post.manager.PostManager;
import com.jianjiange.dating.post.service.FeedRedisService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class FeedScoreJob {

    private static final int CANDIDATE_LIMIT = 3000;
    private static final Duration RECENT_WINDOW = Duration.ofDays(3);

    private final PostManager postManager;
    private final FeedRedisService feedRedisService;
    private final UserClient userClient;

    /**
     * 推荐池生成
     *
     * 查询{since}天内的limit条数据
     * 计算热度分（创建时间，点赞，评论）
     * 加入male/female推荐池
     */
    @Scheduled(fixedDelay = 300000)
    public void rebuildRecommendPool() {
        OffsetDateTime since = OffsetDateTime.now().minus(RECENT_WINDOW);
        List<PostEntity> posts = postManager.listRecentNormalPosts(since, CANDIDATE_LIMIT);

        List<FeedRedisService.FeedCandidate> maleCandidates = new ArrayList<>();
        List<FeedRedisService.FeedCandidate> femaleCandidates = new ArrayList<>();

        for (PostEntity post : posts) {
            PostStatEntity stat = postManager.findStatByPostId(post.getPostId()).orElse(null);

            int likeCount = stat == null ? 0 : stat.getLikeCount();
            int commentCount = stat == null ? 0 : stat.getCommentCount();

            double score = calculateScore(post.getCreatedAt(), likeCount, commentCount);

            FeedRedisService.FeedCandidate candidate =
                    new FeedRedisService.FeedCandidate(post.getPostId(), score);

            boolean male = userClient.isMale(post.getUserId());
            if (male) {
                maleCandidates.add(candidate);
            } else {
                femaleCandidates.add(candidate);
            }
        }

        feedRedisService.rebuildRecommendPool(true, maleCandidates);
        feedRedisService.rebuildRecommendPool(false, femaleCandidates);
    }

    private double calculateScore(OffsetDateTime createdAt, int likeCount, int commentCount) {
        double ageHours = Duration.between(createdAt, OffsetDateTime.now()).toMinutes() / 60.0;
        double interactionScore = 10.0 + likeCount + commentCount * 3.0;
        return interactionScore / Math.pow(ageHours + 2.0, 1.5);
    }
}