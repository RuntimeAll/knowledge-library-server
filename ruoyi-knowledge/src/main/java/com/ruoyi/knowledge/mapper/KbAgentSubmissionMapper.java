package com.ruoyi.knowledge.mapper;

import com.ruoyi.knowledge.domain.KbAgentSubmission;
import org.apache.ibatis.annotations.Param;

public interface KbAgentSubmissionMapper {
    int claim(@Param("actor") String actor, @Param("operation") String operation,
              @Param("key") String key, @Param("hash") String hash);
    KbAgentSubmission lock(@Param("actor") String actor, @Param("operation") String operation,
                           @Param("key") String key);
    int complete(@Param("actor") String actor, @Param("operation") String operation,
                 @Param("key") String key, @Param("resourceId") Long resourceId);
    Long findResource(@Param("actor") String actor, @Param("operation") String operation, @Param("key") String key);
}
