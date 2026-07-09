<#function h n>
    <#assign level = (n + headingOffset)!n>
    <#assign hashes = "" />
    <#list 1..level as i>
        <#assign hashes = hashes + "#" />
    </#list>
    <#return hashes>
</#function>

<#function fmtDate d="">
    <#if d?has_content>
        <#assign pattern = meta.datePattern!'yyyy.MM'>
        <#if d?is_date_like>
            <#return d?string(pattern)>
        <#else>
            <#return d?string>
        </#if>
    <#else>
        <#return "至今">
    </#if>
</#function>

<#if includeHeaderBlock!true>
${h(1)} ${name}<#if title?has_content> · ${title}</#if>

<#assign contactParts = []>
<#if contact.phone?has_content><#assign contactParts = contactParts + ["电话：" + contact.phone]></#if>
<#if contact.email?has_content><#assign contactParts = contactParts + ["邮箱：" + contact.email]></#if>
<#if contact.website?has_content><#assign contactParts = contactParts + ["主页：" + contact.website]></#if>
<#if contact.location?has_content><#assign contactParts = contactParts + ["地点：" + contact.location]></#if>
<#if contactParts?has_content>
联系方式：${contactParts?join(" / ")}
</#if>

<#if socialLinks?has_content>
社交链接：
<#list socialLinks as s>
- [${s.label}](${s.url})
</#list>
</#if>

</#if>

<#if summary?has_content>
${h(2)} 个人摘要

${summary}
</#if>

<#if educations?has_content>
${h(2)} 教育经历

<#list educations as edu>
${h(3)} ${edu.school}<#if edu.major?has_content> · ${edu.major}</#if><#if edu.degree?has_content> · ${edu.degree}</#if>（${fmtDate(edu.startDate!)} - ${fmtDate(edu.endDate!)}）
<#if edu.description?has_content>
${edu.description}
</#if>

</#list>
</#if>

<#if experiences?has_content>
${h(2)} 实习/工作经历

<#list experiences as exp>
${h(3)} ${exp.company}<#if exp.role?has_content> · ${exp.role}</#if>（${fmtDate(exp.startDate!)} - ${fmtDate(exp.endDate!)}）
<#if exp.description?has_content>
${exp.description}
</#if>
<#if exp.highlights?has_content>
<#list exp.highlights as hl>
- ${hl}
</#list>
</#if>

</#list>
</#if>

<#if projects?has_content>
${h(2)} 项目经验

<#list projects as p>
${h(3)} ${p.name}<#if p.role?has_content> · ${p.role}</#if><#if p.startDate?has_content || p.endDate?has_content>（${fmtDate(p.startDate!)} - ${fmtDate(p.endDate!)}）</#if>
<#if p.description?has_content>
${p.description}
</#if>
<#if p.highlights?has_content>
<#list p.highlights as hl>
- ${hl}
</#list>
</#if>

</#list>
</#if>

<#if skills?has_content>
${h(2)} 技能与亮点

<#list skills as s>
- **${s.name}**<#if s.level?has_content>（${s.level}）</#if><#if s.highlights?has_content>：${s.highlights?join("；")}</#if>
</#list>
</#if>

<#if certificates?has_content>
${h(2)} 证书与获奖

<#list certificates as c>
- ${c.name}<#if c.issuer?has_content> · ${c.issuer}</#if><#if c.date?has_content>（${fmtDate(c.date!)}）</#if><#if c.description?has_content>：${c.description}</#if>
</#list>
</#if>
