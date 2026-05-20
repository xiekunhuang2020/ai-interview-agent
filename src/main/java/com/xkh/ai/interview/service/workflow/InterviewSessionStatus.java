package com.xkh.ai.interview.service.workflow;

public enum InterviewSessionStatus {
    UPLOADED("已上传"),
    ANALYZED("已完成简历诊断"),
    JD_MATCHED("已完成岗位匹配"),
    QUESTIONS_GENERATED("已生成面试题"),
    ANSWER_SUBMITTED("已提交答案"),
    EVALUATED("已完成面试复盘"),
    FAILED("流程失败");

    private final String text;

    /**
     * 绑定状态码对应的中文展示文案。
     */
    InterviewSessionStatus(String text) {
        this.text = text;
    }

    /**
     * 返回状态在页面和接口中展示的中文名称。
     */
    public String getText() {
        return text;
    }
}
