const { createApp } = Vue;

const answerAudioState = {
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
                audit: {
                    operationName: '',
                    promptVersion: '',
                    limit: 1000,
                    ragTopK: 5,
                    metrics: [],
                    failures: [],
                    structuredFailures: [],
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
                if (this.recordingQuestionIndex !== null) {
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
                this.globalError = '';
                this.globalMessage = '';
                try {
                    await fetchJson(`/api/interview/${encodeURIComponent(this.resumeId)}/submit`, {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify(this.answers)
                    });
                    this.pendingAudioReviewIndex = null;
                    window.location.href = `/result/${encodeURIComponent(this.resumeId)}`;
                } catch (error) {
                    this.globalError = error.message;
                } finally {
                    this.loading.evaluation = false;
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
                if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
                    this.globalError = '当前浏览器不支持录音，请改用文字输入。';
                    return;
                }
                if (this.recordingQuestionIndex !== null) {
                    this.globalError = '请先停止当前录音。';
                    return;
                }
                this.globalError = '';
                this.globalMessage = '';
                this.pendingAudioReviewIndex = null;
                try {
                    const AudioContextClass = window.AudioContext || window.webkitAudioContext;
                    if (!AudioContextClass) {
                        this.globalError = '当前浏览器不支持音频处理，请改用文字输入。';
                        return;
                    }
                    answerAudioState.stream = await navigator.mediaDevices.getUserMedia({ audio: true });
                    answerAudioState.audioContext = new AudioContextClass();
                    answerAudioState.inputSampleRate = answerAudioState.audioContext.sampleRate || 16000;
                    answerAudioState.outputSampleRate = 16000;
                    answerAudioState.buffers = [];
                    answerAudioState.source = answerAudioState.audioContext.createMediaStreamSource(answerAudioState.stream);
                    answerAudioState.processor = answerAudioState.audioContext.createScriptProcessor(4096, 1, 1);
                    answerAudioState.processor.onaudioprocess = (event) => {
                        const input = event.inputBuffer.getChannelData(0);
                        answerAudioState.buffers.push(new Float32Array(input));
                        const output = event.outputBuffer.getChannelData(0);
                        output.fill(0);
                    };
                    answerAudioState.source.connect(answerAudioState.processor);
                    answerAudioState.processor.connect(answerAudioState.audioContext.destination);
                    this.recordingQuestionIndex = index;
                    this.globalMessage = `第 ${index + 1} 题正在录音，回答完后点击“停止录音”。`;
                } catch (error) {
                    this.releaseAnswerAudioResources();
                    this.globalError = error.message || '录音启动失败，请检查麦克风权限。';
                }
            },
            async stopAnswerRecording() {
                const index = this.recordingQuestionIndex;
                const blob = await this.finishAnswerRecording();
                this.recordingQuestionIndex = null;
                if (index === null || !blob) {
                    return;
                }
                await this.transcribeAnswerAudio(index, blob);
            },
            async finishAnswerRecording() {
                const buffers = answerAudioState.buffers.slice();
                const inputSampleRate = answerAudioState.inputSampleRate;
                const outputSampleRate = answerAudioState.outputSampleRate;
                await this.releaseAnswerAudioResources();
                if (!buffers.length) {
                    this.globalError = '没有录到语音，请重新录制。';
                    return null;
                }
                const samples = this.flattenAudioBuffers(buffers);
                const resampled = this.downsampleAudioBuffer(samples, inputSampleRate, outputSampleRate);
                return this.encodeWavBlob(resampled, outputSampleRate);
            },
            async releaseAnswerAudioResources() {
                if (answerAudioState.processor) {
                    answerAudioState.processor.disconnect();
                    answerAudioState.processor.onaudioprocess = null;
                    answerAudioState.processor = null;
                }
                if (answerAudioState.source) {
                    answerAudioState.source.disconnect();
                    answerAudioState.source = null;
                }
                if (answerAudioState.stream) {
                    answerAudioState.stream.getTracks().forEach((track) => track.stop());
                    answerAudioState.stream = null;
                }
                if (answerAudioState.audioContext) {
                    await answerAudioState.audioContext.close();
                    answerAudioState.audioContext = null;
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
                    formData.append('sampleRate', String(answerAudioState.outputSampleRate));
                    const payload = await fetchJson(`/api/interview/${encodeURIComponent(this.resumeId)}/answer-audio/transcribe`, {
                        method: 'POST',
                        body: formData
                    });
                    this.answers[index] = this.mergeAnswerText(this.answers[index], payload.text);
                    this.pendingAudioReviewIndex = index;
                    this.globalMessage = `第 ${index + 1} 题语音已转成文字，请检查答案框。全部题目答完后，再点击右上角“提交评估”。`;
                } catch (error) {
                    this.globalError = error.message;
                } finally {
                    this.loading.transcription = false;
                    this.transcribingQuestionIndex = null;
                }
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
                return '录音回答';
            },
            async sendAssistantMessage() {
                if (this.loading.chat) {
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
                    const [metrics, failures, structuredFailures, modelCalls, agentMessages] = await Promise.all([
                        fetchJson(`/api/audit/prompt-metrics?${params.toString()}`),
                        fetchJson(`/api/audit/failure-reasons?${params.toString()}`),
                        fetchJson(`/api/audit/structured-output-failures?${structuredParams.toString()}`),
                        fetchJson('/api/audit/model-calls?limit=20'),
                        fetchJson('/api/audit/agent-messages?limit=20')
                    ]);
                    this.audit.metrics = this.safeList(metrics);
                    this.audit.failures = this.safeList(failures);
                    this.audit.structuredFailures = this.safeList(structuredFailures);
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
            // 将单次调用的输入、输出和总 Token 拼成审计表展示文案。
            formatTokenBreakdown(item) {
                if (!item || (!item.inputTokens && !item.outputTokens && !item.totalTokens)) {
                    return '--';
                }
                return `入 ${this.formatTokenCount(item.inputTokens)} / 出 ${this.formatTokenCount(item.outputTokens)} / 总 ${this.formatTokenCount(item.totalTokens)}`;
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
                    'answer-audio-transcription': '语音回答转写'
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
            }
        }
    });
}

createInterviewApp().mount('#app');
