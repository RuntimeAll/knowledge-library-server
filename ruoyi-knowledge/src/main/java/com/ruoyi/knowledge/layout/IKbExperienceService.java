package com.ruoyi.knowledge.layout;

import java.util.List;

public interface IKbExperienceService {
    List<KbExperience> list(KbExperience query);

    KbExperience get(Long id);

    KbExperience create(KbExperience data);

    KbExperience update(KbExperience data);

    void delete(Long[] ids);

    List<KbExperienceVersion> versions(Long id, String status);

    List<KbExperienceVersion> options();

    KbExperienceVersion version(Long id);

    KbExperienceVersion usableVersion(Long id);

    KbExperienceVersion createVersion(Long id, KbExperienceVersion data);

    KbExperienceVersion updateVersion(Long id, KbExperienceVersion data);

    KbExperienceVersion publish(Long id, Long rowVersion, String actor);

    void deleteVersion(Long id);
}
