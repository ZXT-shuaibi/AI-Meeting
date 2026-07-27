package com.hewei.hzyjy.xunzhi.career.raglab.api.io;

import lombok.Data;
import java.util.List;

/** 替换一份简历的自由岗位与方向标签。 */
@Data
public class ReplaceResumeTagsReqDTO {
    private List<String> roles;
    private List<String> directions;
    private List<String> projects;
    private List<String> skills;
    private List<String> experiences;
}
