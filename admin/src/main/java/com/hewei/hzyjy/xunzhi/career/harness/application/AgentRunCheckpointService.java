package com.hewei.hzyjy.xunzhi.career.harness.application;
import com.alibaba.fastjson2.JSON; import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hewei.hzyjy.xunzhi.career.harness.dao.entity.AgentRunCheckpointDO; import com.hewei.hzyjy.xunzhi.career.harness.dao.entity.AgentRunDO; import com.hewei.hzyjy.xunzhi.career.harness.dao.mapper.AgentRunCheckpointMapper; import com.hewei.hzyjy.xunzhi.career.harness.dao.mapper.AgentRunMapper;
import com.hewei.hzyjy.xunzhi.common.convention.exception.ClientException; import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.util.*;
/** 协作式检查点与取消标记服务：长任务仅在安全阶段边界检查取消，不中断正在进行的第三方网络调用。 */
@Service @RequiredArgsConstructor public class AgentRunCheckpointService {
 private final AgentRunCheckpointMapper checkpointMapper; private final AgentRunMapper runMapper;
 public void checkpoint(String runId,String code,Map<String,Object> metadata){ if(runId==null||code==null)throw new ClientException("检查点参数不能为空"); List<AgentRunCheckpointDO> old=checkpointMapper.selectList(Wrappers.<AgentRunCheckpointDO>lambdaQuery().eq(AgentRunCheckpointDO::getRunId,runId)); AgentRunCheckpointDO item=new AgentRunCheckpointDO(); item.setRunId(runId);item.setCheckpointCode(code);item.setSequenceNo(old.size()+1);item.setMetadataJson(JSON.toJSONString(safe(metadata)));checkpointMapper.insert(item); }
  /** 取消是协作式请求：只允许运行中的任务进入取消请求状态，终态不可被改写。 */
  public boolean requestCancel(String runId,String reason){
   if(runId==null||runId.isBlank())return false;
   AgentRunDO current=runMapper.selectList(Wrappers.<AgentRunDO>lambdaQuery().eq(AgentRunDO::getRunId,runId).last("LIMIT 1")).stream().findFirst().orElse(null);
   if(current==null||!"RUNNING".equals(current.getStatus()))return false;
   AgentRunDO update=new AgentRunDO();update.setStatus("CANCEL_REQUESTED");update.setErrorMessage(reason==null?"用户请求取消，等待安全检查点确认":reason);
   return runMapper.update(update,Wrappers.<AgentRunDO>lambdaUpdate().eq(AgentRunDO::getRunId,runId).eq(AgentRunDO::getStatus,"RUNNING"))>0;
  }
  public boolean isCancellationRequested(String runId){ return runMapper.selectList(Wrappers.<AgentRunDO>lambdaQuery().eq(AgentRunDO::getRunId,runId).last("LIMIT 1")).stream().anyMatch(item->"CANCEL_REQUESTED".equals(item.getStatus())||"CANCELLED".equals(item.getStatus())); }
  /** 安全检查点确认取消后才写入最终取消状态，并补齐结束时间与总耗时。 */
  public boolean confirmCancelled(String runId,String reason){
   AgentRunDO current=runMapper.selectList(Wrappers.<AgentRunDO>lambdaQuery().eq(AgentRunDO::getRunId,runId).last("LIMIT 1")).stream().findFirst().orElse(null);
   if(current==null||!"CANCEL_REQUESTED".equals(current.getStatus()))return false;
   Date now=new Date(); AgentRunDO update=new AgentRunDO(); update.setStatus("CANCELLED"); update.setErrorMessage(reason==null?"已在安全检查点取消":reason); update.setFinishedAt(now); update.setDurationMs(current.getStartedAt()==null?0L:Math.max(0L,now.getTime()-current.getStartedAt().getTime()));
   return runMapper.update(update,Wrappers.<AgentRunDO>lambdaUpdate().eq(AgentRunDO::getRunId,runId).eq(AgentRunDO::getStatus,"CANCEL_REQUESTED"))>0;
  }
  public boolean isCancelled(String runId){ return runMapper.selectList(Wrappers.<AgentRunDO>lambdaQuery().eq(AgentRunDO::getRunId,runId).last("LIMIT 1")).stream().anyMatch(item->"CANCELLED".equals(item.getStatus())); }
 private Map<String,Object> safe(Map<String,Object> values){Map<String,Object> result=new LinkedHashMap<>();if(values==null)return result;for(String key:List.of("resultCount","candidateCount","durationMs","fallbackStageCount","retryCount")){Object value=values.get(key);if(value instanceof Number||value instanceof Boolean)result.put(key,value);}return result;}
}
