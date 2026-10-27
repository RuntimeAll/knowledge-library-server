package com.ruoyi.knowledge.mapper;

import com.ruoyi.knowledge.review.ReviewModels;

import java.util.List;
import org.apache.ibatis.annotations.Param;

/** Review-specific bounded persistence, including dependency invalidation and current facts. */
public interface KbReviewMapper {
    List<ReviewModels.Fact> facts(Long questionId);
    Long questionVersion(Long id);
    List<Long> relatedKpIds(Long id);
    List<Long> readyIds(@Param("ids") List<Long> ids);
    ReviewModels.Impact impact(Long questionId);
    int changeState(@Param("id") Long id, @Param("version") Long version,
            @Param("status") String status, @Param("actor") String actor);
    int validOrigin(@Param("id") Long id, @Param("kind") String kind);
    int invalidBindings(Long id);
    int invalidAssets(Long id);
    List<Long> impacted(@Param("kind") String kind, @Param("id") Long id, @Param("limit") int limit);
    List<Long> usableIds(@Param("ids") List<Long> ids, @Param("approvalJson") String approvalJson);
    int importIdentityMatches(ReviewModels.ImportBinding binding);
    int frozenParentMatches(@Param("questionId") Long questionId, @Param("parentId") Long parentId);
    List<com.ruoyi.knowledge.domain.KbOccurrence> candidateOccurrences(@Param("questionId") Long questionId,
            @Param("sourceId") String sourceId);
    int draft(@Param("ids") List<Long> ids, @Param("actor") String actor);
}
