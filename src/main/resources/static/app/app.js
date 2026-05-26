const { createApp } = Vue;

const speechAudioState = {
    stream: null,
    audioContext: null,
    source: null,
    processor: null,
    buffers: [],
    inputSampleRate: 16000,
    outputSampleRate: 16000
};

async function fetchJson(url, options = {}) {
    const response = await fetch(url, options);
    const text = await response.text();
    const payload = text ? JSON.parse(text) : null;
    if (!response.ok) {
        throw new Error(formatApiError(payload, response.status));
    }
    return payload;
}

// 将后端结构化错误字段拼成用户可读文案。
function formatApiError(payload, status) {
    if (!payload) {
        return `请求失败：${status}`;
    }
    const details = [];
    if (payload.schemaName) {
        details.push(`结构：${payload.schemaName}`);
    }
    if (payload.fieldPath) {
        details.push(`字段：${payload.fieldPath}`);
    }
    if (payload.failureReason) {
        details.push(`原因：${payload.failureReason}`);
    }
    if (payload.traceId) {
        details.push(`Trace：${payload.traceId}`);
    }
    return details.length ? `${payload.error}\n${details.join('；')}` : (payload.error || `请求失败：${status}`);
}

function createInterviewApp() {
    const root = document.getElementById('app');
    const page = root.dataset.page || 'home';
    const resumeId = root.dataset.resumeId || '';

    return createApp({
        data() {
            return {
                page,
                resumeId,
                selectedFile: null,
                dragOver: false,
                scoreResult: null,
                questions: [],
                answers: {},
                voiceAnswerIndexes: [],
                evaluation: null,
                session: null,
                jdText: '',
                matchResult: null,
                topK: 5,
                assistantMessage: '',
                conversationId: '',
                chatMessages: [],
                assistantTypingQueue: '',
                assistantTypingTarget: null,
                assistantTypingTimer: null,
                assistantInputComposing: false,
                assistantSummaryCompressed: false,
                recordingQuestionIndex: null,
                transcribingQuestionIndex: null,
                pendingAudioReviewIndex: null,
                recordingStartedAt: null,
                recordingElapsedSeconds: 0,
                recordingTimer: null,
                recordingAssistant: false,
                transcribingAssistant: false,
                audit: {
                    operationName: '',
                    promptVersion: '',
                    limit: 1000,
                    ragTopK: 5,
                    metrics: [],
                    failures: [],
                    structuredFailures: [],
                    answerEvaluationFailures: [],
                    modelCalls: [],
                    agentMessages: [],
                    ragRecall: null,
                    ragError: ''
                },
                harness: {
                    topK: 5,
                    report: null
                },
                loading: {
                    upload: false,
                    workspace: false,
                    match: false,
                    questions: false,
                    evaluation: false,
                    transcription: false,
                    chat: false,
                    ops: false,
                    harness: false,
                    jdOcr: false
                },
                uploadStage: '',
                evaluationStage: '',
                globalError: '',
                globalMessage: ''
            };
        },
        computed: {
            canUseResume() {
                return Boolean(this.resumeId);
            },
            scoreItems() {
                const detail = this.scoreResult && this.scoreResult.scoreDetail;
                if (!detail) {
                    return [];
                }
                return [
                    { label: '项目深度', value: detail.projectScore, max: 40 },
                    { label: '技能匹配', value: detail.skillMatchScore, max: 20 },
                    { label: '内容完整', value: detail.contentScore, max: 15 },
                    { label: '结构清晰', value: detail.structureScore, max: 15 },
                    { label: '表达质量', value: detail.expressionScore, max: 10 }
                ].map((item) => ({
                    ...item,
                    percent: this.clamp(Number(item.value || 0) * 100 / item.max)
                }));
            },
            strengthItems() {
                return this.safeList(this.scoreResult && this.scoreResult.strengths);
            },
            suggestionItems() {
                return this.safeList(this.scoreResult && this.scoreResult.suggestions);
            },
            scoreRingStyle() {
                return { '--score': this.clamp(this.scoreResult && this.scoreResult.overallScore) };
            },
            matchRingStyle() {
                return { '--score': this.clamp(this.matchResult && this.matchResult.overallScore) };
            },
            evaluationRingStyle() {
                return { '--score': this.clamp(this.evaluation && this.evaluation.overallScore) };
            },
            answeredCount() {
                return Object.values(this.answers).filter((answer) => String(answer || '').trim()).length;
            },
            unansweredCount() {
                return Math.max(0, this.questions.length - this.answeredCount);
            },
            voiceEvaluationSummary() {
                const details = this.safeList(this.evaluation && this.evaluation.questionDetails);
                const voiceDetails = details.filter((item) => item.answerMode === 'VOICE_TRANSCRIPT');
                const total = details.length;
                const count = voiceDetails.length;
                if (!count) {
                    return {
                        count: 0,
                        total,
                        avgScore: null,
                        lowestQuestion: null,
                        issueItems: [],
                        suggestionItems: []
                    };
                }
                const avgScore = voiceDetails.reduce((sum, item) => sum + Number(item.score || 0), 0) / count;
                const lowestQuestion = voiceDetails
                    .slice()
                    .sort((a, b) => Number(a.score || 0) - Number(b.score || 0))[0];
                const issueItems = this.compactUniqueTextList(
                    voiceDetails.map((item) => item.voiceExpressionIssue),
                    '非语音作答'
                ).slice(0, 3);
                const suggestionItems = this.compactUniqueTextList(
                    voiceDetails.map((item) => item.voiceExpressionSuggestion),
                    '非语音作答'
                ).slice(0, 3);
                return {
                    count,
                    total,
                    avgScore: this.round(avgScore),
                    lowestQuestion,
                    issueItems,
                    suggestionItems
                };
            },
            opsEffectCards() {
                const metrics = this.safeList(this.audit.metrics);
                const failures = this.safeList(this.audit.failures);
                const totalCalls = metrics.reduce((sum, item) => sum + Number(item.totalCalls || 0), 0);
                const successCalls = metrics.reduce((sum, item) => sum + Number(item.successCalls || 0), 0);
                const failedCalls = metrics.reduce((sum, item) => sum + Number(item.failedCalls || 0), 0);
                const totalLatency = metrics.reduce((sum, item) => {
                    return sum + Number(item.avgLatencyMs || 0) * Number(item.totalCalls || 0);
                }, 0);
                const tokenSampleCalls = metrics.reduce((sum, item) => sum + Number(item.tokenSampleCalls || 0), 0);
                const totalInputTokens = metrics.reduce((sum, item) => sum + Number(item.totalInputTokens || 0), 0);
                const totalOutputTokens = metrics.reduce((sum, item) => sum + Number(item.totalOutputTokens || 0), 0);
                const totalTokens = metrics.reduce((sum, item) => sum + Number(item.totalTokens || 0), 0);
                const estimatedTextCost = metrics.reduce((sum, item) => sum + this.estimateTextCost(item), 0);
                const contextSampleCalls = metrics.reduce((sum, item) => sum + Number(item.contextSampleCalls || 0), 0);
                const clippedCalls = metrics.reduce((sum, item) => sum + Number(item.clippedCalls || 0), 0);
                const totalPromptChars = metrics.reduce((sum, item) => sum + Number(item.totalPromptChars || 0), 0);
                const totalClippedChars = metrics.reduce((sum, item) => sum + Number(item.totalClippedChars || 0), 0);
                const budgetSampleCalls = metrics.reduce((sum, item) => sum + Number(item.budgetSampleCalls || 0), 0);
                const budgetExceededCalls = metrics.reduce((sum, item) => sum + Number(item.budgetExceededCalls || 0), 0);
                const budgetUncoveredCalls = metrics.reduce((sum, item) => sum + Number(item.budgetUncoveredCalls || 0), 0);
                const totalInputTokenOverBudget = metrics.reduce((sum, item) => sum + Number(item.totalInputTokenOverBudget || 0), 0);
                const avgLatency = totalCalls ? totalLatency / totalCalls : null;
                const avgTotalTokens = tokenSampleCalls ? totalTokens / tokenSampleCalls : null;
                const avgPromptChars = contextSampleCalls ? totalPromptChars / contextSampleCalls : null;
                const successRate = totalCalls ? successCalls * 100 / totalCalls : null;
                const structuredFailures = failures
                    .filter((item) => item.errorType === 'STRUCTURED_OUTPUT_ERROR' || item.errorType === 'VALIDATION_ERROR')
                    .reduce((sum, item) => sum + Number(item.count || 0), 0);
                const resumeMetric = metrics.find((item) => item.operationName === 'resume-analysis');
                const jdMetric = metrics.find((item) => item.operationName === 'jd-match');
                const questionMetric = metrics.find((item) => item.operationName === 'rag-interview-question-generation')
                    || metrics.find((item) => item.operationName === 'interview-question-generation');
                const summaryMetric = metrics.find((item) => item.operationName === 'interview-assistant-summary');
                const audioMetrics = metrics.filter((item) => [
                    'answer-audio-transcription',
                    'assistant-audio-transcription'
                ].includes(item.operationName));
                const audioCalls = audioMetrics.reduce((sum, item) => sum + Number(item.totalCalls || 0), 0);
                const audioSuccessCalls = audioMetrics.reduce((sum, item) => sum + Number(item.successCalls || 0), 0);
                const audioTotalLatency = audioMetrics.reduce((sum, item) => {
                    return sum + Number(item.avgLatencyMs || 0) * Number(item.totalCalls || 0);
                }, 0);
                const audioTotalDurationMs = audioMetrics.reduce((sum, item) => {
                    return sum + Number(item.totalAudioDurationMs || 0);
                }, 0);
                const estimatedAudioCost = this.estimateAudioCost(audioTotalDurationMs);
                const estimatedTotalCost = estimatedTextCost + estimatedAudioCost;
                const ragRecall = this.audit.ragRecall;

                return [
                    {
                        label: '模型调用样本',
                        value: `${totalCalls} 次`,
                        note: `最近 ${this.audit.limit || 1000} 条审计`
                    },
                    {
                        label: '模型调用成功率',
                        value: successRate === null ? '--' : `${this.round(successRate)}%`,
                        note: `${successCalls} 次成功 / ${failedCalls} 次失败`
                    },
                    {
                        label: '平均模型耗时',
                        value: this.formatLatency(avgLatency),
                        note: '按调用次数加权统计'
                    },
                    {
                        label: '模型失败次数',
                        value: `${failedCalls} 次`,
                        note: structuredFailures ? `结构化相关 ${structuredFailures} 次` : '暂无结构化失败记录'
                    },
                    {
                        label: '总 Token 消耗',
                        value: this.formatTokenCount(totalTokens),
                        note: tokenSampleCalls ? `输入 ${this.formatTokenCount(totalInputTokens)} / 输出 ${this.formatTokenCount(totalOutputTokens)}` : '等待新调用产生用量'
                    },
                    {
                        label: '平均 Token',
                        value: this.formatTokenCount(avgTotalTokens),
                        note: tokenSampleCalls ? `有用量样本 ${tokenSampleCalls} 次` : '旧审计记录无用量'
                    },
                    {
                        label: '估算调用成本',
                        value: this.formatCny(estimatedTotalCost),
                        note: `文本 ${this.formatCny(estimatedTextCost)} / 语音 ${this.formatCny(estimatedAudioCost)}`
                    },
                    {
                        label: '平均输入长度',
                        value: this.formatCharCount(avgPromptChars),
                        note: contextSampleCalls ? `上下文样本 ${contextSampleCalls} 次` : '等待新调用产生统计'
                    },
                    {
                        label: '上下文裁剪',
                        value: `${clippedCalls} 次`,
                        note: clippedCalls ? `累计减少 ${this.formatCharCount(totalClippedChars)}` : '暂无裁剪记录'
                    },
                    {
                        label: '输入预算超出',
                        value: `${budgetExceededCalls} 次`,
                        note: budgetSampleCalls ? `累计超出 ${this.formatTokenCount(totalInputTokenOverBudget)} Token` : '等待新调用产生预算数据'
                    },
                    {
                        label: '策略未覆盖',
                        value: `${budgetUncoveredCalls} 次`,
                        note: budgetUncoveredCalls ? '超预算但没有发生裁剪' : '暂无未覆盖记录'
                    },
                    {
                        label: '摘要压缩次数',
                        value: `${summaryMetric ? Number(summaryMetric.totalCalls || 0) : 0} 次`,
                        note: summaryMetric ? `均耗时 ${this.formatLatency(summaryMetric.avgLatencyMs)}` : '暂无摘要压缩记录'
                    },
                    {
                        label: '语音转写调用',
                        value: `${audioCalls} 次`,
                        note: audioCalls ? `成功率 ${this.round(audioSuccessCalls * 100 / audioCalls)}% / 总音频 ${this.formatAudioDuration(audioTotalDurationMs)}` : '暂无语音样本'
                    },
                    {
                        label: '向量召回命中率',
                        value: ragRecall ? `${this.round(Number(ragRecall.hitRate || 0) * 100)}%` : '--',
                        note: ragRecall ? `评估样例 ${ragRecall.totalCases || 0} 条` : (this.audit.ragError || '暂无召回评估')
                    },
                    {
                        label: '简历解析耗时',
                        value: resumeMetric ? this.formatLatency(resumeMetric.avgLatencyMs) : '--',
                        note: resumeMetric ? `成功率 ${this.round(resumeMetric.successRate)}%` : '暂无样本'
                    },
                    {
                        label: '岗位匹配耗时',
                        value: jdMetric ? this.formatLatency(jdMetric.avgLatencyMs) : '--',
                        note: jdMetric ? `成功率 ${this.round(jdMetric.successRate)}%` : '暂无样本'
                    },
                    {
                        label: '出题成功率',
                        value: questionMetric ? `${this.round(questionMetric.successRate)}%` : '--',
                        note: questionMetric ? `均耗时 ${this.formatLatency(questionMetric.avgLatencyMs)}` : '暂无样本'
                    }
                ];
            },
            harnessSummaryCards() {
                const report = this.harness.report;
                if (!report) {
                    return [];
                }
                return [
                    { label: '评测样例', value: `${report.totalCases || 0} 个`, note: `${report.totalChecks || 0} 个检查点` },
                    { label: '总通过率', value: `${this.round(report.passRate)}%`, note: `${report.passedChecks || 0} 通过 / ${report.failedCheckCount || 0} 失败` },
                    { label: '结构化成功率', value: `${this.round(report.structuredOutputSuccessRate)}%`, note: 'DTO 转换与校验' },
                    { label: '上下文相关性', value: `${this.round(report.contextRelevancePassRate)}%`, note: '官方相关性评估' },
                    { label: '事实一致性', value: `${this.round(report.factConsistencyPassRate)}%`, note: '官方事实核验' },
                    { label: '平均耗时', value: this.formatLatency(report.avgLatencyMs), note: report.generatedAt || '--' }
                ];
            }
        },
        mounted() {
            if (this.resumeId) {
                this.loadWorkspace();
            }
            if (this.page === 'ops') {
                this.refreshOps();
            }
            if (this.page === 'assistant') {
                this.conversationId = localStorage.getItem('interviewAgentConversationId') || '';
                this.loadPendingAssistantPrompt();
            }
        },
        methods: {
            handleFileChange(event) {
                this.selectedFile = event.target.files[0] || null;
                this.globalError = '';
            },
            handleDrop(event) {
                this.dragOver = false;
                this.selectedFile = event.dataTransfer.files[0] || null;
                this.globalError = '';
            },
            openJdImagePicker() {
                const input = this.$refs.jdImageFileInput;
                if (input) {
                    input.click();
                }
            },
            async handleJdImageChange(event) {
                const file = event.target.files[0] || null;
                if (!file) {
                    return;
                }
                await this.extractJobDescriptionFromImage(file);
                event.target.value = '';
            },
            async extractJobDescriptionFromImage(file) {
                if (file.size > 10 * 1024 * 1024) {
                    this.globalError = '截图大小不能超过 10MB。';
                    return;
                }
                this.loading.jdOcr = true;
                this.globalError = '';
                this.globalMessage = '';
                try {
                    const formData = new FormData();
                    formData.append('file', file);
                    const payload = await fetchJson('/api/jd/ocr-image', {
                        method: 'POST',
                        body: formData
                    });
                    this.jdText = payload.jobDescription || '';
                    this.globalMessage = '岗位截图识别完成，请检查内容后再继续。';
                } catch (error) {
                    this.globalError = error.message;
                } finally {
                    this.loading.jdOcr = false;
                }
            },
            clearCandidate() {
                this.selectedFile = null;
                this.globalError = '';
                this.globalMessage = '';
                const input = document.getElementById('resumeFile');
                if (input) {
                    input.value = '';
                }
            },
            async uploadResume() {
                if (!this.selectedFile) {
                    this.globalError = '请先选择简历文件。';
                    return;
                }
                const targetJd = this.jdText.trim();
                if (!targetJd) {
                    this.globalError = '请先填写目标岗位说明，系统需要根据岗位要求判断简历是否匹配。';
                    return;
                }
                if (this.selectedFile.size > 10 * 1024 * 1024) {
                    this.globalError = '文件大小不能超过 10MB。';
                    return;
                }
                this.loading.upload = true;
                this.uploadStage = '正在解析简历并生成诊断...';
                this.globalError = '';
                this.globalMessage = '';
                let uploadedResumeId = '';
                try {
                    const formData = new FormData();
                    formData.append('file', this.selectedFile);
                    const payload = await fetchJson('/api/resume/upload', {
                        method: 'POST',
                        body: formData
                    });
                    uploadedResumeId = payload.resumeId;
                    this.resumeId = uploadedResumeId;
                    this.scoreResult = payload.scoreResult || null;
                    this.uploadStage = '正在分析简历与岗位匹配度...';
                    this.matchResult = await this.matchJobByResumeId(uploadedResumeId, targetJd);
                    this.jdText = targetJd;
                    this.globalMessage = '简历诊断和岗位匹配已完成，可继续查看岗位匹配结果。';
                    window.location.href = `/analysis/${encodeURIComponent(uploadedResumeId)}`;
                } catch (error) {
                    this.globalError = uploadedResumeId
                        ? `简历已导入，但岗位匹配失败：${error.message}`
                        : error.message;
                } finally {
                    this.loading.upload = false;
                    this.uploadStage = '';
                }
            },
            async loadWorkspace() {
                if (!this.resumeId) {
                    return;
                }
                this.loading.workspace = true;
                this.globalError = '';
                try {
                    const payload = await fetchJson(`/api/resume/${encodeURIComponent(this.resumeId)}`);
                    this.scoreResult = payload.scoreResult || null;
                    this.questions = this.safeList(payload.questions && payload.questions.questions);
                    this.evaluation = payload.evaluation || null;
                    this.matchResult = payload.matchResult || this.matchResult;
                    this.jdText = payload.jobDescription || this.jdText;
                    this.session = payload.session || null;
                    this.answers = Object.fromEntries(this.questions.map((_, index) => [index, this.answers[index] || '']));
                    if (this.page === 'interview') {
                        this.restoreAnswerDraft();
                    }
                } catch (error) {
                    this.globalError = error.message;
                } finally {
                    this.loading.workspace = false;
                }
            },
            fillSampleJd() {
                this.jdText = `岗位：Java 智能应用开发工程师
职责：
1. 基于 Spring Boot、Spring AI、检索增强生成和工具调用开发企业级 AI 应用；
2. 负责简历解析、向量检索、面试问答、评估报告等业务链路；
3. 建设模型调用审计、提示词版本管理、稳定性监控和错误追踪。
要求：
1. 熟悉 Java 21、Spring Boot、MyBatis-Plus、MySQL、Redis；
2. 有 LangChain、Spring AI、Milvus 或向量数据库经验；
3. 能把 AI 能力产品化，关注用户体验和交付闭环。`;
            },
            async matchJob() {
                if (!this.resumeId || !this.jdText.trim()) {
                    this.globalError = '请先填写目标岗位说明。';
                    return;
                }
                this.loading.match = true;
                this.globalError = '';
                this.globalMessage = '';
                try {
                    this.matchResult = await this.matchJobByResumeId(this.resumeId, this.jdText.trim());
                    await this.loadWorkspace();
                    this.globalMessage = '岗位匹配完成，可以继续根据岗位生成面试题。';
                } catch (error) {
                    this.globalError = error.message;
                } finally {
                    this.loading.match = false;
                }
            },
            async matchJobByResumeId(resumeId, jobDescription) {
                return fetchJson(`/api/jd/${encodeURIComponent(resumeId)}/match`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ jobDescription })
                });
            },
            async generateStandardQuestions() {
                await this.generateQuestions('standard');
            },
            async generateRagQuestions() {
                await this.generateQuestions('rag');
            },
            async generateQuestions(mode) {
                if (!this.resumeId) {
                    this.globalError = '请先导入候选人简历。';
                    return;
                }
                if (mode === 'rag' && !this.jdText.trim()) {
                    this.globalError = '请先填写目标岗位说明，再根据岗位生成面试题。';
                    return;
                }
                this.loading.questions = true;
                this.globalError = '';
                this.globalMessage = '';
                try {
                    const url = mode === 'rag'
                        ? `/api/interview/${encodeURIComponent(this.resumeId)}/rag-questions`
                        : `/api/interview/${encodeURIComponent(this.resumeId)}/questions`;
                    const options = mode === 'rag'
                        ? {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ jobDescription: this.jdText, topK: this.topK })
                        }
                        : { method: 'POST' };
                    const payload = await fetchJson(url, options);
                    this.questions = this.safeList(payload.questions);
                    this.answers = Object.fromEntries(this.questions.map((_, index) => [index, '']));
                    this.voiceAnswerIndexes = [];
                    this.evaluation = null;
                    window.location.href = `/interview/${encodeURIComponent(this.resumeId)}`;
                } catch (error) {
                    this.globalError = error.message;
                } finally {
                    this.loading.questions = false;
                }
            },
            async submitAnswers() {
                if (!this.resumeId || !this.questions.length) {
                    this.globalError = '请先生成面试问题。';
                    return;
                }
                if (this.isRecordingSpeech()) {
                    this.globalError = '请先停止当前录音，再提交评估。';
                    return;
                }
                if (this.loading.transcription) {
                    this.globalError = '语音正在转写，请等待转写完成后再提交评估。';
                    return;
                }
                if (this.unansweredCount > 0 && !window.confirm(`还有 ${this.unansweredCount} 题未回答，确定提交评估吗？`)) {
                    return;
                }
                this.loading.evaluation = true;
                this.evaluationStage = '正在生成逐题反馈、参考答案和语音复盘，通常需要几十秒。';
                this.globalError = '';
                this.globalMessage = '';
                try {
                    this.saveAnswerDraft();
                    await fetchJson(`/api/interview/${encodeURIComponent(this.resumeId)}/submit`, {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({
                            answers: this.answers,
                            voiceAnswerIndexes: this.voiceAnswerIndexes
                        })
                    });
                    this.pendingAudioReviewIndex = null;
                    this.clearAnswerDraft();
                    window.location.href = `/result/${encodeURIComponent(this.resumeId)}`;
                } catch (error) {
                    this.globalError = error.message;
                    this.globalMessage = '答案已保留在当前页面，可以调整后重新提交评估。';
                } finally {
                    this.loading.evaluation = false;
                    this.evaluationStage = '';
                }
            },
            scrollToFirstUnanswered() {
                const firstIndex = this.questions.findIndex((_, index) => !String(this.answers[index] || '').trim());
                if (firstIndex < 0) {
                    this.globalMessage = '所有问题都已作答。';
                    return;
                }
                const target = document.querySelector(`[data-question-index="${firstIndex}"]`);
                if (target) {
                    target.scrollIntoView({ behavior: 'smooth', block: 'start' });
                    const textarea = target.querySelector('textarea');
                    if (textarea) {
                        window.setTimeout(() => textarea.focus(), 350);
                    }
                }
            },
            async toggleAnswerRecording(index) {
                if (this.recordingQuestionIndex === index) {
                    await this.stopAnswerRecording();
                    return;
                }
                await this.startAnswerRecording(index);
            },
            async startAnswerRecording(index) {
                this.globalError = '';
                this.globalMessage = '';
                if (await this.startSpeechRecording()) {
                    this.pendingAudioReviewIndex = null;
                    this.recordingQuestionIndex = index;
                    this.globalMessage = `第 ${index + 1} 题正在录音，回答完后点击“停止录音”。`;
                }
            },
            async stopAnswerRecording() {
                const index = this.recordingQuestionIndex;
                const blob = await this.finishSpeechRecording();
                this.recordingQuestionIndex = null;
                if (index === null || !blob) {
                    return;
                }
                await this.transcribeAnswerAudio(index, blob);
            },
            async startSpeechRecording() {
                if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
                    this.globalError = '当前浏览器不支持录音，请改用文字输入。';
                    return false;
                }
                if (this.isRecordingSpeech()) {
                    this.globalError = '请先停止当前录音。';
                    return false;
                }
                try {
                    const AudioContextClass = window.AudioContext || window.webkitAudioContext;
                    if (!AudioContextClass) {
                        this.globalError = '当前浏览器不支持音频处理，请改用文字输入。';
                        return false;
                    }
                    speechAudioState.stream = await navigator.mediaDevices.getUserMedia({ audio: true });
                    speechAudioState.audioContext = new AudioContextClass();
                    speechAudioState.inputSampleRate = speechAudioState.audioContext.sampleRate || 16000;
                    speechAudioState.outputSampleRate = 16000;
                    speechAudioState.buffers = [];
                    speechAudioState.source = speechAudioState.audioContext.createMediaStreamSource(speechAudioState.stream);
                    speechAudioState.processor = speechAudioState.audioContext.createScriptProcessor(4096, 1, 1);
                    speechAudioState.processor.onaudioprocess = (event) => {
                        const input = event.inputBuffer.getChannelData(0);
                        speechAudioState.buffers.push(new Float32Array(input));
                        const output = event.outputBuffer.getChannelData(0);
                        output.fill(0);
                    };
                    speechAudioState.source.connect(speechAudioState.processor);
                    speechAudioState.processor.connect(speechAudioState.audioContext.destination);
                    this.startRecordingTimer();
                    return true;
                } catch (error) {
                    await this.releaseSpeechAudioResources();
                    this.globalError = error.message || '录音启动失败，请检查麦克风权限。';
                    return false;
                }
            },
            async finishSpeechRecording() {
                const buffers = speechAudioState.buffers.slice();
                const inputSampleRate = speechAudioState.inputSampleRate;
                const outputSampleRate = speechAudioState.outputSampleRate;
                await this.releaseSpeechAudioResources();
                if (!buffers.length) {
                    this.globalError = '没有录到语音，请重新录制。';
                    return null;
                }
                const samples = this.flattenAudioBuffers(buffers);
                const resampled = this.downsampleAudioBuffer(samples, inputSampleRate, outputSampleRate);
                return this.encodeWavBlob(resampled, outputSampleRate);
            },
            async releaseSpeechAudioResources() {
                this.stopRecordingTimer();
                if (speechAudioState.processor) {
                    speechAudioState.processor.disconnect();
                    speechAudioState.processor.onaudioprocess = null;
                    speechAudioState.processor = null;
                }
                if (speechAudioState.source) {
                    speechAudioState.source.disconnect();
                    speechAudioState.source = null;
                }
                if (speechAudioState.stream) {
                    speechAudioState.stream.getTracks().forEach((track) => track.stop());
                    speechAudioState.stream = null;
                }
                if (speechAudioState.audioContext) {
                    await speechAudioState.audioContext.close();
                    speechAudioState.audioContext = null;
                }
            },
            async transcribeAnswerAudio(index, blob) {
                this.loading.transcription = true;
                this.transcribingQuestionIndex = index;
                this.globalError = '';
                this.globalMessage = '';
                try {
                    const formData = new FormData();
                    formData.append('file', blob, `answer-${index + 1}.wav`);
                    formData.append('sampleRate', String(speechAudioState.outputSampleRate));
                    const payload = await fetchJson(`/api/interview/${encodeURIComponent(this.resumeId)}/answer-audio/transcribe`, {
                        method: 'POST',
                        body: formData
                    });
                    this.answers[index] = this.mergeAnswerText(this.answers[index], payload.text);
                    if (!this.voiceAnswerIndexes.includes(index)) {
                        this.voiceAnswerIndexes.push(index);
                    }
                    this.saveAnswerDraft();
                    this.pendingAudioReviewIndex = index;
                    this.globalMessage = `第 ${index + 1} 题语音已转成文字，请检查答案框。全部题目答完后，再点击右上角“提交评估”。`;
                } catch (error) {
                    this.globalError = error.message;
                } finally {
                    this.loading.transcription = false;
                    this.transcribingQuestionIndex = null;
                }
            },
            async rerecordAnswerAudio(index) {
                if (this.isRecordingSpeech() || this.loading.transcription) {
                    this.globalError = '请先完成当前录音或转写。';
                    return;
                }
                this.globalError = '';
                this.globalMessage = '';
                if (await this.startSpeechRecording()) {
                    this.answers[index] = '';
                    this.clearVoiceAnswerMark(index);
                    this.recordingQuestionIndex = index;
                    this.globalMessage = `第 ${index + 1} 题已清空旧答案，正在重新录音。`;
                }
            },
            clearVoiceAnswerMark(index) {
                this.voiceAnswerIndexes = this.voiceAnswerIndexes.filter((item) => item !== index);
                if (this.pendingAudioReviewIndex === index) {
                    this.pendingAudioReviewIndex = null;
                }
                this.saveAnswerDraft();
            },
            answerDraftKey() {
                return `interviewAgentAnswerDraft:${this.resumeId || 'global'}`;
            },
            saveAnswerDraft() {
                if (!this.resumeId || this.page !== 'interview') {
                    return;
                }
                localStorage.setItem(this.answerDraftKey(), JSON.stringify({
                    questionCount: this.questions.length,
                    answers: this.answers,
                    voiceAnswerIndexes: this.voiceAnswerIndexes,
                    savedAt: new Date().toISOString()
                }));
            },
            restoreAnswerDraft() {
                const raw = localStorage.getItem(this.answerDraftKey());
                if (!raw || !this.questions.length) {
                    return;
                }
                try {
                    const draft = JSON.parse(raw);
                    if (Number(draft.questionCount || 0) !== this.questions.length) {
                        return;
                    }
                    const draftAnswers = draft.answers || {};
                    let restored = 0;
                    this.questions.forEach((_, index) => {
                        const value = String(draftAnswers[index] || '').trim();
                        if (value && !String(this.answers[index] || '').trim()) {
                            this.answers[index] = draftAnswers[index];
                            restored += 1;
                        }
                    });
                    this.voiceAnswerIndexes = this.safeList(draft.voiceAnswerIndexes)
                        .map((item) => Number(item))
                        .filter((item) => Number.isInteger(item) && item >= 0 && item < this.questions.length);
                    if (restored) {
                        this.globalMessage = `已恢复 ${restored} 题未提交答案，可继续修改后提交评估。`;
                    }
                } catch (error) {
                    localStorage.removeItem(this.answerDraftKey());
                }
            },
            clearAnswerDraft() {
                if (this.resumeId) {
                    localStorage.removeItem(this.answerDraftKey());
                }
            },
            isRecordingSpeech() {
                return this.recordingQuestionIndex !== null || this.recordingAssistant;
            },
            startRecordingTimer() {
                this.stopRecordingTimer();
                this.recordingStartedAt = Date.now();
                this.recordingElapsedSeconds = 0;
                this.recordingTimer = window.setInterval(() => {
                    this.recordingElapsedSeconds = Math.floor((Date.now() - this.recordingStartedAt) / 1000);
                }, 500);
            },
            stopRecordingTimer() {
                if (this.recordingTimer) {
                    window.clearInterval(this.recordingTimer);
                    this.recordingTimer = null;
                }
                this.recordingStartedAt = null;
                this.recordingElapsedSeconds = 0;
            },
            flattenAudioBuffers(buffers) {
                const totalLength = buffers.reduce((sum, buffer) => sum + buffer.length, 0);
                const result = new Float32Array(totalLength);
                let offset = 0;
                buffers.forEach((buffer) => {
                    result.set(buffer, offset);
                    offset += buffer.length;
                });
                return result;
            },
            downsampleAudioBuffer(buffer, inputSampleRate, outputSampleRate) {
                if (outputSampleRate === inputSampleRate) {
                    return buffer;
                }
                const ratio = inputSampleRate / outputSampleRate;
                const length = Math.round(buffer.length / ratio);
                const result = new Float32Array(length);
                let sourceOffset = 0;
                for (let i = 0; i < length; i += 1) {
                    const nextOffset = Math.round((i + 1) * ratio);
                    let sum = 0;
                    let count = 0;
                    for (let j = sourceOffset; j < nextOffset && j < buffer.length; j += 1) {
                        sum += buffer[j];
                        count += 1;
                    }
                    result[i] = count ? sum / count : 0;
                    sourceOffset = nextOffset;
                }
                return result;
            },
            encodeWavBlob(samples, sampleRate) {
                const bytesPerSample = 2;
                const buffer = new ArrayBuffer(44 + samples.length * bytesPerSample);
                const view = new DataView(buffer);
                this.writeAscii(view, 0, 'RIFF');
                view.setUint32(4, 36 + samples.length * bytesPerSample, true);
                this.writeAscii(view, 8, 'WAVE');
                this.writeAscii(view, 12, 'fmt ');
                view.setUint32(16, 16, true);
                view.setUint16(20, 1, true);
                view.setUint16(22, 1, true);
                view.setUint32(24, sampleRate, true);
                view.setUint32(28, sampleRate * bytesPerSample, true);
                view.setUint16(32, bytesPerSample, true);
                view.setUint16(34, 16, true);
                this.writeAscii(view, 36, 'data');
                view.setUint32(40, samples.length * bytesPerSample, true);
                let offset = 44;
                samples.forEach((sample) => {
                    const value = Math.max(-1, Math.min(1, sample));
                    view.setInt16(offset, value < 0 ? value * 0x8000 : value * 0x7fff, true);
                    offset += bytesPerSample;
                });
                return new Blob([view], { type: 'audio/wav' });
            },
            writeAscii(view, offset, text) {
                for (let i = 0; i < text.length; i += 1) {
                    view.setUint8(offset + i, text.charCodeAt(i));
                }
            },
            mergeAnswerText(currentAnswer, transcriptionText) {
                const current = String(currentAnswer || '').trim();
                const transcription = String(transcriptionText || '').trim();
                if (!current) {
                    return transcription;
                }
                if (!transcription || current.includes(transcription)) {
                    return current;
                }
                return `${current}\n${transcription}`;
            },
            answerAudioButtonText(index) {
                if (this.recordingQuestionIndex === index) {
                    return '停止录音';
                }
                if (this.transcribingQuestionIndex === index) {
                    return '转写中...';
                }
                if (this.voiceAnswerIndexes.includes(index)) {
                    return '继续录音补充';
                }
                return '录音回答';
            },
            async toggleAssistantRecording() {
                if (this.recordingAssistant) {
                    await this.stopAssistantRecording();
                    return;
                }
                await this.startAssistantRecording();
            },
            async startAssistantRecording() {
                if (this.loading.chat) {
                    this.globalError = 'AI 顾问正在回复，请稍后再录音提问。';
                    return;
                }
                this.globalError = '';
                this.globalMessage = '';
                if (await this.startSpeechRecording()) {
                    this.recordingAssistant = true;
                    this.globalMessage = '正在录音提问，说完后点击“停止录音”。';
                }
            },
            async stopAssistantRecording() {
                const blob = await this.finishSpeechRecording();
                this.recordingAssistant = false;
                if (!blob) {
                    return;
                }
                await this.transcribeAssistantAudio(blob);
            },
            async transcribeAssistantAudio(blob) {
                this.transcribingAssistant = true;
                this.globalError = '';
                this.globalMessage = '';
                try {
                    const formData = new FormData();
                    formData.append('file', blob, 'assistant-question.wav');
                    formData.append('sampleRate', String(speechAudioState.outputSampleRate));
                    const payload = await fetchJson('/api/audio/transcribe', {
                        method: 'POST',
                        body: formData
                    });
                    this.assistantMessage = this.mergeAnswerText(this.assistantMessage, payload.text);
                    this.globalMessage = '语音提问已转成文字，请检查输入框后发送。';
                } catch (error) {
                    this.globalError = error.message;
                } finally {
                    this.transcribingAssistant = false;
                }
            },
            assistantAudioButtonText() {
                if (this.recordingAssistant) {
                    return '停止录音';
                }
                if (this.transcribingAssistant) {
                    return '转写中...';
                }
                return '语音提问';
            },
            async sendAssistantMessage() {
                if (this.loading.chat) {
                    return;
                }
                if (this.recordingAssistant || this.transcribingAssistant) {
                    this.globalError = '请先完成语音转写，再发送顾问问题。';
                    return;
                }
                const content = this.assistantMessage.trim();
                if (!content) {
                    return;
                }
                this.stopAssistantTyping(true);
                const outbound = this.resumeId && !content.includes(this.resumeId)
                    ? `resumeId=${this.resumeId}\n${content}`
                    : content;
                this.chatMessages.push({
                    id: `u-${Date.now()}`,
                    role: 'user',
                    content
                });
                const assistantMessage = {
                    id: `a-${Date.now()}`,
                    role: 'assistant',
                    content: ''
                };
                this.chatMessages.push(assistantMessage);
                this.assistantMessage = '';
                this.loading.chat = true;
                this.globalError = '';
                try {
                    await this.consumeAssistantEventSource(outbound, assistantMessage);
                } catch (error) {
                    this.globalError = error.message;
                    if (!assistantMessage.content) {
                        this.chatMessages = this.chatMessages.filter((message) => message.id !== assistantMessage.id);
                    }
                } finally {
                    this.loading.chat = false;
                }
            },
            submitAssistantMessageByEnter(event) {
                if (this.assistantInputComposing || event.isComposing) {
                    return;
                }
                event.preventDefault();
                this.sendAssistantMessage();
            },
            loadPendingAssistantPrompt() {
                const key = this.pendingAssistantPromptKey();
                const payloadText = sessionStorage.getItem(key) || sessionStorage.getItem('interviewAgentPendingAssistantPrompt');
                if (!payloadText) {
                    return;
                }
                try {
                    const payload = JSON.parse(payloadText);
                    this.assistantMessage = payload.prompt || '';
                    this.globalMessage = payload.source === 'evaluation-summary'
                        ? '已带入面试复盘摘要，请确认后发送给 AI 顾问。'
                        : '已带入待发送内容，请确认后发送。';
                } catch (error) {
                    this.assistantMessage = payloadText;
                } finally {
                    sessionStorage.removeItem(key);
                    sessionStorage.removeItem('interviewAgentPendingAssistantPrompt');
                }
            },
            fillAssistantQuickPrompt(type) {
                const prompts = {
                    'practice-plan': '请基于当前简历、岗位匹配结果和面试复盘，帮我制定下一轮 3 天练习计划，按优先级列出每天要练什么、怎么练、验收标准是什么。',
                    'improve-answer': '请找出我面试复盘里最应该优先优化的低分回答，帮我按“结论 -> 背景 -> 动作 -> 结果 -> 反思”重写一版。',
                    'follow-up': '请基于我当前简历和面试复盘，模拟面试官连续追问 5 个问题，并说明每个问题想考察什么。'
                };
                this.assistantMessage = prompts[type] || '';
                this.globalMessage = '快捷问题已填入输入框，请确认后发送。';
            },
            consumeAssistantEventSource(message, assistantMessage) {
                return new Promise((resolve, reject) => {
                    if (!window.EventSource) {
                        reject(new Error('当前浏览器不支持流式对话。'));
                        return;
                    }

                    const params = new URLSearchParams({ message });
                    if (this.conversationId) {
                        params.set('conversationId', this.conversationId);
                    }
                    const source = new EventSource(`/api/agent/interview-assistant/stream?${params.toString()}`);
                    let completed = false;

                    source.addEventListener('meta', (event) => {
                        this.handleAssistantStreamPayload('meta', event.data, assistantMessage);
                    });
                    source.addEventListener('delta', (event) => {
                        this.handleAssistantStreamPayload('delta', event.data, assistantMessage);
                    });
                    source.addEventListener('done', async (event) => {
                        completed = true;
                        source.close();
                        this.handleAssistantStreamPayload('done', event.data, assistantMessage);
                        await this.waitAssistantTypingDone();
                        resolve();
                    });
                    source.addEventListener('error', (event) => {
                        if (completed) {
                            return;
                        }
                        source.close();
                        const message = event.data
                            ? (JSON.parse(event.data).message || '顾问回答失败。')
                            : '流式连接中断，请重试。';
                        reject(new Error(message));
                    });
                });
            },
            handleAssistantStreamPayload(eventName, rawData, assistantMessage) {
                const payload = rawData ? JSON.parse(rawData) : {};
                if (eventName === 'meta') {
                    this.conversationId = payload.conversationId || this.conversationId;
                    if (this.conversationId) {
                        localStorage.setItem('interviewAgentConversationId', this.conversationId);
                    }
                    if (Object.prototype.hasOwnProperty.call(payload, 'summaryCompressed')) {
                        this.assistantSummaryCompressed = Boolean(payload.summaryCompressed);
                    }
                    assistantMessage.turnId = payload.turnId || '';
                } else if (eventName === 'delta') {
                    this.enqueueAssistantDelta(assistantMessage, payload.content || '');
                } else if (eventName === 'done' && payload.summaryCompressed) {
                    this.assistantSummaryCompressed = true;
                    this.appendAssistantSystemNotice('较早的对话已经压缩成摘要，后续回答会结合摘要和最近消息继续推进。');
                }
            },
            consumeSseBuffer(buffer, assistantMessage, flush = false) {
                const parts = buffer.split(/\r?\n\r?\n/);
                const pending = flush ? '' : parts.pop();
                parts.forEach((part) => this.handleAssistantStreamEvent(part, assistantMessage));
                if (flush && parts.length === 0 && buffer.trim()) {
                    this.handleAssistantStreamEvent(buffer, assistantMessage);
                }
                return pending || '';
            },
            handleAssistantStreamEvent(rawEvent, assistantMessage) {
                if (!rawEvent.trim()) {
                    return;
                }
                let eventName = 'message';
                const dataLines = [];
                rawEvent.split(/\r?\n/).forEach((line) => {
                    if (line.startsWith('event:')) {
                        eventName = line.slice(6).trim();
                    } else if (line.startsWith('data:')) {
                        dataLines.push(line.slice(5).trimStart());
                    }
                });
                const rawData = dataLines.join('\n');
                if (eventName === 'meta' || eventName === 'delta') {
                    this.handleAssistantStreamPayload(eventName, rawData, assistantMessage);
                } else if (eventName === 'done') {
                    this.handleAssistantStreamPayload(eventName, rawData, assistantMessage);
                } else if (eventName === 'error') {
                    const payload = rawData ? JSON.parse(rawData) : {};
                    throw new Error(payload.message || '顾问回答失败。');
                }
            },
            appendAssistantSystemNotice(content) {
                this.chatMessages.push({
                    id: `notice-${Date.now()}-${Math.random()}`,
                    role: 'system',
                    content
                });
                this.scrollChatToBottom();
            },
            enqueueAssistantDelta(assistantMessage, content) {
                if (!content) {
                    return;
                }
                this.assistantTypingTarget = assistantMessage;
                this.assistantTypingQueue += content;
                this.startAssistantTyping();
            },
            startAssistantTyping() {
                if (this.assistantTypingTimer) {
                    return;
                }
                this.assistantTypingTimer = window.setInterval(() => {
                    if (!this.assistantTypingTarget || !this.assistantTypingQueue) {
                        this.stopAssistantTyping(false);
                        return;
                    }
                    const batchSize = this.assistantTypingQueue.length > 80 ? 4 : 2;
                    const chunk = this.assistantTypingQueue.slice(0, batchSize);
                    this.assistantTypingQueue = this.assistantTypingQueue.slice(batchSize);
                    this.assistantTypingTarget.content += chunk;
                    this.scrollChatToBottom();
                }, 24);
            },
            stopAssistantTyping(flush) {
                if (flush && this.assistantTypingTarget && this.assistantTypingQueue) {
                    this.assistantTypingTarget.content += this.assistantTypingQueue;
                }
                this.assistantTypingQueue = '';
                this.assistantTypingTarget = null;
                if (this.assistantTypingTimer) {
                    window.clearInterval(this.assistantTypingTimer);
                    this.assistantTypingTimer = null;
                }
            },
            waitAssistantTypingDone() {
                return new Promise((resolve) => {
                    const check = () => {
                        if (!this.assistantTypingQueue && !this.assistantTypingTimer) {
                            resolve();
                            return;
                        }
                        window.setTimeout(check, 30);
                    };
                    check();
                });
            },
            scrollChatToBottom() {
                this.$nextTick(() => {
                    const messageList = this.$refs.messageList;
                    if (messageList) {
                        messageList.scrollTop = messageList.scrollHeight;
                    }
                });
            },
            resetConversation() {
                this.stopAssistantTyping(false);
                this.conversationId = '';
                this.chatMessages = [];
                this.assistantSummaryCompressed = false;
                localStorage.removeItem('interviewAgentConversationId');
            },
            async refreshOps() {
                this.loading.ops = true;
                this.globalError = '';
                try {
                    const params = new URLSearchParams({ limit: String(this.audit.limit || 1000) });
                    if (this.audit.operationName.trim()) {
                        params.set('operationName', this.audit.operationName.trim());
                    }
                    if (this.audit.promptVersion.trim()) {
                        params.set('promptVersion', this.audit.promptVersion.trim());
                    }
                    const structuredParams = new URLSearchParams(params);
                    structuredParams.set('limit', '10');
                    const evaluationStructuredParams = new URLSearchParams({ operationName: 'answer-evaluation', limit: '10' });
                    if (this.audit.promptVersion.trim()) {
                        evaluationStructuredParams.set('promptVersion', this.audit.promptVersion.trim());
                    }
                    const [metrics, failures, structuredFailures, answerEvaluationFailures, modelCalls, agentMessages] = await Promise.all([
                        fetchJson(`/api/audit/prompt-metrics?${params.toString()}`),
                        fetchJson(`/api/audit/failure-reasons?${params.toString()}`),
                        fetchJson(`/api/audit/structured-output-failures?${structuredParams.toString()}`),
                        fetchJson(`/api/audit/structured-output-failures?${evaluationStructuredParams.toString()}`),
                        fetchJson('/api/audit/model-calls?limit=20'),
                        fetchJson('/api/audit/agent-messages?limit=20')
                    ]);
                    this.audit.metrics = this.safeList(metrics);
                    this.audit.failures = this.safeList(failures);
                    this.audit.structuredFailures = this.safeList(structuredFailures);
                    this.audit.answerEvaluationFailures = this.safeList(answerEvaluationFailures);
                    this.audit.modelCalls = this.safeList(modelCalls);
                    this.audit.agentMessages = this.safeList(agentMessages);
                    this.audit.ragError = '';
                    try {
                        const topK = Math.max(1, Math.min(Number(this.audit.ragTopK || 5), 20));
                        this.audit.ragTopK = topK;
                        this.audit.ragRecall = await fetchJson(`/api/evaluation/rag-recall?topK=${topK}`);
                    } catch (ragError) {
                        this.audit.ragRecall = null;
                        this.audit.ragError = ragError.message;
                    }
                } catch (error) {
                    this.globalError = error.message;
                } finally {
                    this.loading.ops = false;
                }
            },
            async runPromptRagEvaluation() {
                this.loading.harness = true;
                this.globalError = '';
                try {
                    const topK = Math.max(1, Math.min(Number(this.harness.topK || 5), 20));
                    this.harness.topK = topK;
                    this.harness.report = await fetchJson(`/api/evaluation/prompt-rag?topK=${topK}`, {
                        method: 'POST'
                    });
                } catch (error) {
                    this.globalError = error.message;
                } finally {
                    this.loading.harness = false;
                }
            },
            safeList(value) {
                return Array.isArray(value) ? value : [];
            },
            async copyEvaluationSummary() {
                if (!this.evaluation) {
                    this.globalError = '暂无复盘报告可复制。';
                    return;
                }
                this.globalError = '';
                try {
                    await this.copyText(this.buildEvaluationSummary());
                    this.globalMessage = '复盘摘要已复制，可直接发送给朋友或粘贴给 AI 顾问继续分析。';
                } catch (error) {
                    this.globalError = '复制失败，请手动选择页面内容复制。';
                }
            },
            openAssistantWithEvaluationSummary() {
                if (!this.evaluation) {
                    this.globalError = '暂无复盘报告可解读。';
                    return;
                }
                const prompt = `请根据这份面试复盘，帮我制定下一轮练习计划。要求：先指出最优先解决的 3 个问题，再给出 3 天练习安排，最后给一版低分题重答模板。\n\n${this.buildEvaluationSummary()}`;
                sessionStorage.setItem(this.pendingAssistantPromptKey(), JSON.stringify({
                    source: 'evaluation-summary',
                    prompt
                }));
                window.location.href = `/assistant/${encodeURIComponent(this.resumeId)}`;
            },
            pendingAssistantPromptKey() {
                return `interviewAgentPendingAssistantPrompt:${this.resumeId || 'global'}`;
            },
            // 从已有评估结果拼出轻量摘要，不额外调用模型。
            buildEvaluationSummary() {
                const evaluation = this.evaluation || {};
                const details = this.safeList(evaluation.questionDetails);
                const weakestQuestions = details
                    .slice()
                    .sort((a, b) => Number(a.score || 0) - Number(b.score || 0))
                    .slice(0, 2);
                const lines = [
                    'AI 求职顾问｜面试复盘摘要',
                    '',
                    `总分：${evaluation.overallScore ?? '--'}`,
                    `整体反馈：${String(evaluation.overallFeedback || '暂无').trim()}`
                ];

                this.appendSummarySection(lines, '主要优势', this.safeList(evaluation.strengths).slice(0, 3));
                this.appendSummarySection(lines, '主要问题与建议', this.safeList(evaluation.improvements).slice(0, 3));

                if (this.voiceEvaluationSummary.count) {
                    const voice = this.voiceEvaluationSummary;
                    lines.push('', '语音复盘');
                    lines.push(`- 语音作答：${voice.count}/${voice.total} 题，平均分 ${voice.avgScore}`);
                    if (voice.lowestQuestion) {
                        lines.push(`- 优先复盘：Q${Number(voice.lowestQuestion.questionIndex || 0) + 1}`);
                    }
                    this.safeList(voice.issueItems).slice(0, 2).forEach((item) => lines.push(`- 表达问题：${item}`));
                    this.safeList(voice.suggestionItems).slice(0, 2).forEach((item) => lines.push(`- 复述建议：${item}`));
                    lines.push('- 推荐节奏：先给结论，再按背景、动作、结果、反思展开。');
                }

                if (weakestQuestions.length) {
                    lines.push('', '优先复盘题');
                    weakestQuestions.forEach((item, index) => {
                        lines.push(`${index + 1}. Q${Number(item.questionIndex ?? index) + 1}｜${item.category || '综合问题'}｜${item.score ?? '--'} 分`);
                        if (item.contentIssue) {
                            lines.push(`   问题：${item.contentIssue}`);
                        }
                        if (item.structureSuggestion) {
                            lines.push(`   重答：${item.structureSuggestion}`);
                        }
                    });
                }

                return lines.join('\n');
            },
            appendSummarySection(lines, title, items) {
                const values = this.safeList(items).map((item) => String(item || '').trim()).filter(Boolean);
                if (!values.length) {
                    return;
                }
                lines.push('', title);
                values.forEach((item, index) => lines.push(`${index + 1}. ${item}`));
            },
            async copyText(text) {
                if (navigator.clipboard && window.isSecureContext) {
                    await navigator.clipboard.writeText(text);
                    return;
                }
                this.fallbackCopyText(text);
            },
            fallbackCopyText(text) {
                const textarea = document.createElement('textarea');
                textarea.value = text;
                textarea.setAttribute('readonly', 'readonly');
                textarea.style.position = 'fixed';
                textarea.style.left = '-9999px';
                document.body.appendChild(textarea);
                textarea.select();
                const copied = document.execCommand('copy');
                document.body.removeChild(textarea);
                if (!copied) {
                    throw new Error('copy failed');
                }
            },
            // 去掉空文本和占位文案，保留顺序生成复盘总览。
            compactUniqueTextList(values, ignoredKeyword) {
                const result = [];
                this.safeList(values).forEach((value) => {
                    const text = String(value || '').trim();
                    if (!text || (ignoredKeyword && text.includes(ignoredKeyword))) {
                        return;
                    }
                    if (!result.includes(text)) {
                        result.push(text);
                    }
                });
                return result;
            },
            // 根据题目序号找到对应参考答案，兼容旧数据里 questionIndex 从 1 开始的情况。
            referenceAnswerOf(questionDetail, fallbackIndex) {
                const references = this.safeList(this.evaluation && this.evaluation.referenceAnswers);
                if (!references.length) {
                    return null;
                }
                const questionIndex = Number(questionDetail && questionDetail.questionIndex);
                return references.find((item) => Number(item.questionIndex) === questionIndex)
                    || references.find((item) => Number(item.questionIndex) === fallbackIndex)
                    || references.find((item) => Number(item.questionIndex) === fallbackIndex + 1)
                    || references[fallbackIndex]
                    || null;
            },
            clamp(value) {
                const numeric = Number(value || 0);
                return Math.max(0, Math.min(100, numeric));
            },
            round(value) {
                return Math.round(Number(value || 0) * 100) / 100;
            },
            formatLatency(value) {
                if (value === null || value === undefined || value === '') {
                    return '--';
                }
                return `${this.round(value)} 毫秒`;
            },
            // 将 Token 数量格式化为看板上的简短数字。
            formatTokenCount(value) {
                if (value === null || value === undefined || value === '') {
                    return '--';
                }
                const numeric = Number(value || 0);
                if (!numeric) {
                    return '--';
                }
                if (numeric >= 10000) {
                    return `${this.round(numeric / 10000)} 万`;
                }
                return `${this.round(numeric)}`;
            },
            // 按当前百炼中国内地实时推理价格估算文本模型费用。
            estimateTextCost(item) {
                if (!item) {
                    return 0;
                }
                const pricing = this.textModelPricing(item.modelNames || '');
                const inputCost = Number(item.totalInputTokens || 0) / 1000000 * pricing.inputPerMillion;
                const outputCost = Number(item.totalOutputTokens || 0) / 1000000 * pricing.outputPerMillion;
                return inputCost + outputCost;
            },
            // 按当前 Paraformer 实时语音识别价格估算 ASR 费用。
            estimateAudioCost(durationMs) {
                return Number(durationMs || 0) / 1000 * 0.00024;
            },
            textModelPricing(modelNames) {
                const text = String(modelNames || '').toLowerCase();
                if (text.includes('qwen-plus')) {
                    return { inputPerMillion: 0.8, outputPerMillion: 2 };
                }
                return { inputPerMillion: 2.4, outputPerMillion: 9.6 };
            },
            estimateMetricCost(item) {
                return this.formatCny(this.estimateTextCost(item) + this.estimateAudioCost(item && item.totalAudioDurationMs));
            },
            estimateCallCost(item) {
                if (!item) {
                    return '';
                }
                const textCost = this.estimateTextCost({
                    modelNames: item.modelName,
                    totalInputTokens: item.inputTokens,
                    totalOutputTokens: item.outputTokens
                });
                const audioCost = this.estimateAudioCost(item.audioDurationMs);
                const total = textCost + audioCost;
                return total > 0 ? `估算 ${this.formatCny(total)}` : '';
            },
            formatCny(value) {
                const numeric = Number(value || 0);
                if (numeric <= 0) {
                    return '--';
                }
                if (numeric < 0.01) {
                    return `¥${numeric.toFixed(4)}`;
                }
                return `¥${numeric.toFixed(2)}`;
            },
            // 将单次调用的输入、输出和总 Token 拼成审计表展示文案。
            formatTokenBreakdown(item) {
                if (!item || (!item.inputTokens && !item.outputTokens && !item.totalTokens)) {
                    return '--';
                }
                return `入 ${this.formatTokenCount(item.inputTokens)} / 出 ${this.formatTokenCount(item.outputTokens)} / 总 ${this.formatTokenCount(item.totalTokens)}`;
            },
            // 将 ASR 音频输入元信息格式化为审计表展示文案。
            formatAudioMeta(item) {
                if (!item || (!item.audioFileSizeBytes && !item.audioSampleRate && !item.audioDurationMs)) {
                    return '--';
                }
                const parts = [];
                if (item.audioFileSizeBytes) {
                    parts.push(this.formatBytes(item.audioFileSizeBytes));
                }
                if (item.audioSampleRate) {
                    parts.push(this.formatSampleRate(item.audioSampleRate));
                }
                if (item.audioDurationMs) {
                    parts.push(this.formatAudioDuration(item.audioDurationMs));
                }
                return parts.join(' / ');
            },
            // 将字符数格式化为看板上的简短数字。
            formatCharCount(value) {
                if (value === null || value === undefined || value === '') {
                    return '--';
                }
                const numeric = Number(value || 0);
                if (!numeric) {
                    return '--';
                }
                if (numeric >= 10000) {
                    return `${this.round(numeric / 10000)} 万字`;
                }
                return `${this.round(numeric)} 字`;
            },
            // 将上下文裁剪状态转换为最近调用列表中的短文案。
            formatContextClipping(item) {
                if (!item || item.contextClipped !== 1) {
                    return '未裁剪';
                }
                return item.clippedChars ? `已裁 ${this.formatCharCount(item.clippedChars)}` : '已裁剪';
            },
            // 将单次模型调用的输入预算状态转换为看板文案。
            formatInputBudget(item) {
                if (!item || !item.inputTokenBudget) {
                    return '--';
                }
                if (!item.inputTokens) {
                    return `预算 ${this.formatTokenCount(item.inputTokenBudget)}`;
                }
                if (item.budgetExceeded === 1) {
                    return `超出 ${this.formatTokenCount(item.inputTokenOverBudget)}`;
                }
                return `未超 / ${this.formatTokenCount(item.inputTokenBudget)}`;
            },
            // 将输入预算和裁剪组合成策略状态，方便识别是否需要优化 Prompt。
            formatBudgetStrategy(item) {
                if (!item || !item.inputTokenBudget) {
                    return '--';
                }
                if (item.budgetUncovered === 1) {
                    return '需优化';
                }
                if (item.contextClipped === 1) {
                    return '已裁剪';
                }
                return item.budgetExceeded === 1 ? '已超' : '正常';
            },
            sessionSummary() {
                if (!this.session) {
                    return '暂无流程状态';
                }
                const status = this.session.statusText || this.session.status || '--';
                const stage = this.session.currentStageText || this.session.currentStage || '--';
                return `${status} / ${stage}`;
            },
            operationNameText(operationName) {
                const map = {
                    'resume-analysis': '简历诊断',
                    'jd-match': '岗位匹配',
                    'interview-question-generation': '面试题生成',
                    'rag-interview-question-generation': '岗位定制出题',
                    'answer-evaluation': '回答评估',
                    'interview-assistant-stream': 'AI 顾问流式对话',
                    'interview-assistant-summary': '顾问摘要压缩',
                    'jd-image-ocr': '岗位截图识别',
                    'answer-audio-transcription': '语音回答转写',
                    'assistant-audio-transcription': '顾问语音提问转写'
                };
                return map[operationName] || '其他调用';
            },
            errorTypeText(errorType) {
                const map = {
                    TIMEOUT: '调用超时',
                    RATE_LIMIT: '限流或额度不足',
                    MODEL_ERROR: '模型服务错误',
                    STRUCTURED_OUTPUT_ERROR: '结构化输出错误',
                    VALIDATION_ERROR: '业务校验错误',
                    EMPTY_RESPONSE: '模型空响应',
                    UNKNOWN: '未知错误'
                };
                return map[errorType] || '未知错误';
            },
            agentNameText(agentName) {
                const map = {
                    'interview-assistant': '面试顾问',
                    'InterviewAssistantAgentService': '面试顾问',
                    'interview_assistant_stream_agent': 'AI 顾问',
                    'interview_assistant_summary_agent': '摘要压缩'
                };
                return map[agentName] || '顾问';
            },
            roleText(role) {
                const map = {
                    user: '用户',
                    assistant: '助手',
                    system: '系统',
                    tool: '工具',
                    USER: '用户',
                    ASSISTANT: '助手',
                    SYSTEM: '系统',
                    TOOL: '工具'
                };
                return map[role] || '其他';
            },
            chatRoleName(role) {
                const map = {
                    user: '客户',
                    assistant: '顾问',
                    system: '系统'
                };
                return map[role] || '消息';
            },
            priorityClass(priority) {
                if (priority === '高') {
                    return 'high';
                }
                if (priority === '中') {
                    return 'medium';
                }
                return 'low';
            },
            typeName(type) {
                const map = {
                    PROJECT: '项目',
                    JAVA_BASIC: 'Java 基础',
                    JAVA_COLLECTION: '集合',
                    JAVA_CONCURRENT: '并发',
                    MYSQL: 'MySQL',
                    REDIS: 'Redis',
                    SPRING: 'Spring',
                    SPRING_BOOT: 'Spring Boot',
                    AI: 'AI'
                };
                return map[type] || type || '问题';
            },
            sourceName(source) {
                const map = {
                    CURRENT_RESUME_FACT: '当前简历事实',
                    SIMILAR_RESUME_REFERENCE: '相似简历参考'
                };
                return map[source] || '当前简历事实';
            },
            sourceClass(source) {
                return {
                    'source-badge': true,
                    'reference': source === 'SIMILAR_RESUME_REFERENCE'
                };
            },
            shortTime(value) {
                if (!value) {
                    return '--';
                }
                return String(value).replace('T', ' ').slice(0, 19);
            },
            formatBytes(value) {
                if (!value) {
                    return '0KB';
                }
                if (value < 1024 * 1024) {
                    return `${Math.round(value / 1024)}KB`;
                }
                return `${Math.round(value / 1024 / 1024 * 10) / 10}MB`;
            },
            // 将采样率转换成常见音频标注，例如 16000Hz 展示为 16kHz。
            formatSampleRate(value) {
                const numeric = Number(value || 0);
                if (!numeric) {
                    return '--';
                }
                if (numeric >= 1000 && numeric % 1000 === 0) {
                    return `${numeric / 1000}kHz`;
                }
                return `${numeric}Hz`;
            },
            // 将音频时长毫秒数格式化为“3.2秒”或“1分05秒”。
            formatAudioDuration(value) {
                const numeric = Number(value || 0);
                if (!numeric) {
                    return '--';
                }
                if (numeric < 60000) {
                    return `${this.round(numeric / 1000)}秒`;
                }
                const minutes = Math.floor(numeric / 60000);
                const seconds = Math.round((numeric % 60000) / 1000);
                return `${minutes}分${String(seconds).padStart(2, '0')}秒`;
            },
            formatRecordingDuration(value) {
                const numeric = Math.max(0, Number(value || 0));
                const minutes = Math.floor(numeric / 60);
                const seconds = numeric % 60;
                return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
            }
        }
    });
}

createInterviewApp().mount('#app');
