package com.ruoyi.knowledge.service;

import com.ruoyi.knowledge.domain.KbOutline;
import com.ruoyi.knowledge.domain.KbOutlineNode;

import java.util.List;

public interface IKbOutlineService {
    com.ruoyi.knowledge.domain.dto.KbOutlineOptions options();

    List<KbOutline> list(KbOutline query);

    KbOutline get(Long id);

    KbOutline create(KbOutline data);

    KbOutline update(KbOutline data);

    void delete(Long[] ids);

    KbOutlineNode createNode(KbOutlineNode data);

    KbOutlineNode updateNode(KbOutlineNode data);

    void deleteNode(Long id);
}
