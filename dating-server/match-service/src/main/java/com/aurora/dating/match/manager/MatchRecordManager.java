package com.aurora.dating.match.manager;

import com.aurora.dating.match.entity.LikeRecordEntity;
import com.aurora.dating.match.entity.MatchPairEntity;
import com.aurora.dating.match.entity.UserSwipeHistoryEntity;
import com.aurora.dating.match.entity.VisitRecordEntity;
import com.aurora.dating.match.mapper.LikeRecordMapper;
import com.aurora.dating.match.mapper.MatchPairMapper;
import com.aurora.dating.match.mapper.UserSwipeHistoryMapper;
import com.aurora.dating.match.mapper.VisitRecordMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class MatchRecordManager {

    private final UserSwipeHistoryMapper swipeHistoryMapper;
    private final MatchPairMapper matchPairMapper;
    private final LikeRecordMapper likeRecordMapper;
    private final VisitRecordMapper visitRecordMapper;

    public MatchRecordManager(UserSwipeHistoryMapper swipeHistoryMapper,
                              MatchPairMapper matchPairMapper,
                              LikeRecordMapper likeRecordMapper,
                              VisitRecordMapper visitRecordMapper) {
        this.swipeHistoryMapper = swipeHistoryMapper;
        this.matchPairMapper = matchPairMapper;
        this.likeRecordMapper = likeRecordMapper;
        this.visitRecordMapper = visitRecordMapper;
    }

    public Optional<UserSwipeHistoryEntity> findSwipe(Long userId, Long targetUserId) {
        return Optional.ofNullable(swipeHistoryMapper.findByUserAndTarget(userId, targetUserId));
    }

    public void createSwipe(UserSwipeHistoryEntity swipeHistory) {
        swipeHistoryMapper.insert(swipeHistory);
    }

    public Optional<MatchPairEntity> findMatchPair(Long userIdA, Long userIdB) {
        long userIdLow = Math.min(userIdA, userIdB);
        long userIdHigh = Math.max(userIdA, userIdB);
        return Optional.ofNullable(matchPairMapper.findByUserPair(userIdLow, userIdHigh));
    }

    public void createMatchPair(MatchPairEntity matchPair) {
        matchPairMapper.insert(matchPair);
    }

    public Optional<LikeRecordEntity> findLike(Long fromUserId, Long toUserId) {
        return Optional.ofNullable(likeRecordMapper.findByFromAndTo(fromUserId, toUserId));
    }

    public void createLike(LikeRecordEntity likeRecord) {
        likeRecordMapper.insert(likeRecord);
    }

    public List<LikeRecordEntity> listLikesOfMe(Long userId, Integer limit) {
        return likeRecordMapper.listLikesOfMe(userId, limit);
    }

    public void recordVisit(Long fromUserId, Long toUserId) {
        visitRecordMapper.upsertVisit(fromUserId, toUserId);
    }

    public List<VisitRecordEntity> listVisitsOfMe(Long userId, Integer limit) {
        return visitRecordMapper.listVisitsOfMe(userId, limit);
    }
}
