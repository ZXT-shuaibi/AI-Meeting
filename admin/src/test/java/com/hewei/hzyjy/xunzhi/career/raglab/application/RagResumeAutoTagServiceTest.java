package com.hewei.hzyjy.xunzhi.career.raglab.application;

import com.hewei.hzyjy.xunzhi.career.ai.AiGateway;
import com.hewei.hzyjy.xunzhi.career.ai.AiGatewayResult;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.entity.RagResumeTagDO;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.mapper.RagResumeTagMapper;
import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RagResumeAutoTagServiceTest {
    @Test
    void evaluatesTagsOnDemandAndReturnsTheGeneratedLabels() {
        AiGateway gateway = mock(AiGateway.class); RagResumeTagMapper mapper = mock(RagResumeTagMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of());
        when(gateway.chat(any())).thenReturn(AiGatewayResult.builder().content("{\"roles\":[\"Java后端开发\"],\"directions\":[\"RAG\"],\"projects\":[\"智能面试平台\"],\"skills\":[\"Spring Boot\"],\"experiences\":[\"教育科技\"]}").build());
        CvBO cv = CvBO.builder().id(8L).userId(9L).name("resume.pdf").build();

        List<RagResumeTagDO> evaluated = new RagResumeAutoTagService(gateway, mapper).evaluateOnDemand(cv);

        assertEquals(5, evaluated.size());
        verify(mapper, times(5)).insert(any(RagResumeTagDO.class));
    }

    @Test
    void onDemandEvaluationCallsTheModelAndReplacesPriorTagsOnlyAfterANewResultIsAvailable() {
        AiGateway gateway = mock(AiGateway.class); RagResumeTagMapper mapper = mock(RagResumeTagMapper.class);
        RagResumeTagDO existing = new RagResumeTagDO(); existing.setTagValue("旧标签");
        when(mapper.selectList(any())).thenReturn(List.of(existing));
        when(gateway.chat(any())).thenReturn(AiGatewayResult.builder().content("{\"roles\":[\"产品经理\"],\"directions\":[],\"projects\":[],\"skills\":[],\"experiences\":[]}").build());
        CvBO cv = CvBO.builder().id(8L).userId(9L).name("resume.pdf").build();

        List<RagResumeTagDO> evaluated = new RagResumeAutoTagService(gateway, mapper).evaluateOnDemand(cv);

        assertEquals("产品经理", evaluated.get(0).getTagValue());
        verify(gateway).chat(any());
        verify(mapper).delete(any());
        verify(mapper).insert(any(RagResumeTagDO.class));
    }

    @Test
    void savesAllFiveTypesFromStrictJsonResponse() {
        AiGateway gateway = mock(AiGateway.class); RagResumeTagMapper mapper = mock(RagResumeTagMapper.class);
        when(gateway.chat(any())).thenReturn(AiGatewayResult.builder().content("{\"roles\":[\"Java后端开发\"],\"directions\":[\"RAG\"],\"projects\":[\"智能面试平台\"],\"skills\":[\"Spring Boot\"],\"experiences\":[\"教育科技\"]}").build());
        CvBO cv = CvBO.builder().id(8L).userId(9L).name("resume.pdf").build();
        new RagResumeAutoTagService(gateway, mapper).generateAndReplace(cv);
        ArgumentCaptor<RagResumeTagDO> captor = ArgumentCaptor.forClass(RagResumeTagDO.class);
        verify(mapper, times(5)).insert(captor.capture());
        assertEquals(List.of("ROLE", "DIRECTION", "PROJECT", "SKILL", "EXPERIENCE"), captor.getAllValues().stream().map(item -> item.getTagType().getValue()).toList());
    }

    @Test
    void keepsExistingTagsWhenModelReturnsNoUsableTags() {
        AiGateway gateway = mock(AiGateway.class); RagResumeTagMapper mapper = mock(RagResumeTagMapper.class);
        when(gateway.chat(any())).thenReturn(AiGatewayResult.builder().content("{\"roles\":[],\"directions\":[],\"projects\":[],\"skills\":[],\"experiences\":[]}").build());
        CvBO cv = CvBO.builder().id(8L).userId(9L).name("resume.pdf").build();

        new RagResumeAutoTagService(gateway, mapper).generateAndReplace(cv);

        verify(mapper, never()).delete(any());
        verify(mapper, never()).insert(any(RagResumeTagDO.class));
    }

    @Test
    void doesNotOverwriteTagsAlreadyConfirmedByUser() {
        AiGateway gateway = mock(AiGateway.class); RagResumeTagMapper mapper = mock(RagResumeTagMapper.class);
        when(mapper.selectCount(any())).thenReturn(1L);
        CvBO cv = CvBO.builder().id(8L).userId(9L).name("resume.pdf").build();

        new RagResumeAutoTagService(gateway, mapper).generateAndReplace(cv);

        verify(gateway, never()).chat(any());
        verify(mapper, never()).delete(any());
        verify(mapper, never()).insert(any(RagResumeTagDO.class));
    }
}
