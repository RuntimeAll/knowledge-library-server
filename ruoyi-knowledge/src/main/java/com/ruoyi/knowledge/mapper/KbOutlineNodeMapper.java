package com.ruoyi.knowledge.mapper;

import com.ruoyi.knowledge.domain.KbOutlineNode;
import com.ruoyi.knowledge.dto.KbOutlineNodeKp;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface KbOutlineNodeMapper {
    KbOutlineNode selectById(Long id);

    KbOutlineNode lockById(Long id);

    List<KbOutlineNode> selectList(KbOutlineNode query);

    int insert(KbOutlineNode entity);

    int update(KbOutlineNode entity);

    int deleteById(Long id);

    List<Long> selectKpIds(Long id);

    List<KbOutlineNodeKp> selectKpsForNodes(@Param("ids") List<Long> ids);

    int deleteKps(Long id);

    int insertKp(@Param("id") Long id, @Param("kpId") Long kpId);

    long countChildren(Long id);
}
