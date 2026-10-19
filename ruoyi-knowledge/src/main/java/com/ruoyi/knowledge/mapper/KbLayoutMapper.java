package com.ruoyi.knowledge.mapper;

import com.ruoyi.knowledge.layout.*;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface KbLayoutMapper {
    List<KbExperience> experiences(KbExperience query);

    KbExperience experience(Long id);

    KbExperience lockExperience(Long id);

    int insertExperience(KbExperience row);

    int updateExperience(KbExperience row);

    int deleteExperience(Long id);

    List<KbExperienceVersion> versions(
            @Param("experienceId") Long id, @Param("status") String status);

    List<KbExperienceVersion> options();

    KbExperienceVersion version(Long id);

    KbExperienceVersion lockVersion(Long id);

    int nextVersion(Long id);

    int insertVersion(KbExperienceVersion row);

    int updateVersion(KbExperienceVersion row);

    int publishVersion(KbExperienceVersion row);

    int deleteVersion(Long id);

    List<KbLayoutAsset> versionAssets(Long id);

    int insertVersionAsset(KbLayoutAsset row);

    int deleteVersionAssets(Long id);

    long versionJobs(Long id);

    List<KbRenderJob> jobs(KbRenderJob query);

    KbRenderJob job(Long id);

    KbRenderJob lockJob(Long id);

    KbRenderJob lockOwnedJob(@Param("id") Long id, @Param("token") String token);

    KbRenderJob ownKey(@Param("actorUserId") Long actor, @Param("key") String key);

    KbRenderJob lockOwnKey(@Param("actorUserId") Long actor, @Param("key") String key);

    List<Long> questionSourceIds(@Param("questionIds") List<Long> questionIds);

    Long lockSource(Long id);

    int insertJob(KbRenderJob row);

    List<KbRenderItem> items(Long id);

    int insertItem(KbRenderItem row);

    List<KbLayoutAsset> jobAssets(Long id);

    int insertJobAsset(KbLayoutAsset row);

    List<KbRenderOutput> outputs(Long id);

    Long nextJob();

    int claim(KbRenderJob row);

    int heartbeat(KbRenderJob row);

    int finish(KbRenderJob row);

    int retry(KbRenderJob row);

    int deleteItems(Long id);

    int deleteJobAssets(Long id);

    int deleteJob(Long id);

    List<KbRenderJob> assetJobs(Long assetId);
}
