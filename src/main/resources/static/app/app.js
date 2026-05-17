const { createApp } = Vue;

async function fetchJson(url, options = {}) {
    const response = await fetch(url, options);
    const text = await response.text();
    const payload = text ? JSON.parse(text) : null;
    if (!response.ok) {
        throw new Error((payload && payload.error) || `请求失败：${response.status}`);
    }
    return payload;
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
                jdText: '',
                matchResult: null,
                topK: 5,
                assistantMessage: '',
                conversationId: '',
                chatMessages: [],
                audit: {
                    operationName: '',
                    promptVersion: '',
                    limit: 1000,
                    metrics: [],
                    failures: [],
                    modelCalls: [],
                    agentMessages: []
                },
                loading: {
                    upload: false,
                    workspace: false,
                    match: false,
                    questions: false,
                    evaluation: false,
                    chat: false,
                    ops: false
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
            }
        },
        mounted() {
            if (this.resumeId) {
                this.loadWorkspace();
                this.loadPersistedMatch();
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
                    this.uploadStage = '正在调用岗位匹配智能体...';
                    this.matchResult = await this.matchJobByResumeId(uploadedResumeId, targetJd);
                    this.jdText = targetJd;
                    this.persistMatch();
                    this.globalMessage = '简历诊断和岗位匹配已完成。';
                    window.location.href = `/match/${encodeURIComponent(uploadedResumeId)}`;
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
1. 基于 Spring Boot、Spring AI、检索增强生成和工具调用开发企业级智能体应用；
2. 负责简历解析、向量检索、面试问答、评估报告等业务链路；
3. 建设模型调用审计、提示词版本管理、稳定性监控和降级策略。
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
                    this.persistMatch();
                    this.globalMessage = '岗位匹配完成，可以继续生成定向面试题。';
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
                    this.globalError = '请先填写目标岗位说明，再生成定向面试题。';
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
                    window.location.href = `/result/${encodeURIComponent(this.resumeId)}`;
                } catch (error) {
                    this.globalError = error.message;
                } finally {
                    this.loading.evaluation = false;
                }
            },
            async sendAssistantMessage() {
                const content = this.assistantMessage.trim();
                if (!content) {
                    return;
                }
                const outbound = this.resumeId && !content.includes(this.resumeId)
                    ? `resumeId=${this.resumeId}\n${content}`
                    : content;
                this.chatMessages.push({
                    id: `u-${Date.now()}`,
                    role: 'user',
                    content
                });
                this.assistantMessage = '';
                this.loading.chat = true;
                this.globalError = '';
                try {
                    const payload = await fetchJson('/api/agent/interview-assistant/chat', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({
                            message: outbound,
                            conversationId: this.conversationId || null
                        })
                    });
                    this.conversationId = payload.conversationId || this.conversationId;
                    if (this.conversationId) {
                        localStorage.setItem('interviewAgentConversationId', this.conversationId);
                    }
                    this.chatMessages.push({
                        id: payload.turnId || `a-${Date.now()}`,
                        role: 'assistant',
                        content: payload.answer || ''
                    });
                } catch (error) {
                    this.globalError = error.message;
                } finally {
                    this.loading.chat = false;
                }
            },
            resetConversation() {
                this.conversationId = '';
                this.chatMessages = [];
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
                    const [metrics, failures, modelCalls, agentMessages] = await Promise.all([
                        fetchJson(`/api/audit/prompt-metrics?${params.toString()}`),
                        fetchJson(`/api/audit/failure-reasons?${params.toString()}`),
                        fetchJson('/api/audit/model-calls?limit=20'),
                        fetchJson('/api/audit/agent-messages?limit=20')
                    ]);
                    this.audit.metrics = this.safeList(metrics);
                    this.audit.failures = this.safeList(failures);
                    this.audit.modelCalls = this.safeList(modelCalls);
                    this.audit.agentMessages = this.safeList(agentMessages);
                } catch (error) {
                    this.globalError = error.message;
                } finally {
                    this.loading.ops = false;
                }
            },
            persistMatch() {
                if (!this.resumeId) {
                    return;
                }
                localStorage.setItem(this.matchStorageKey(), JSON.stringify({
                    jdText: this.jdText,
                    matchResult: this.matchResult
                }));
            },
            loadPersistedMatch() {
                const raw = localStorage.getItem(this.matchStorageKey());
                if (!raw) {
                    return;
                }
                try {
                    const payload = JSON.parse(raw);
                    this.jdText = payload.jdText || '';
                    this.matchResult = payload.matchResult || null;
                } catch (error) {
                    localStorage.removeItem(this.matchStorageKey());
                }
            },
            matchStorageKey() {
                return `aiInterviewMatch:${this.resumeId}`;
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
            operationNameText(operationName) {
                const map = {
                    'resume-analysis': '简历诊断',
                    'jd-match': '岗位匹配',
                    'interview-question-generation': '面试题生成',
                    'rag-interview-question-generation': '岗位定向出题',
                    'answer-evaluation': '回答评估'
                };
                return map[operationName] || '其他调用';
            },
            agentNameText(agentName) {
                const map = {
                    'interview-assistant': '面试顾问',
                    'InterviewAssistantAgentService': '面试顾问'
                };
                return map[agentName] || '智能体';
            },
            roleText(role) {
                const map = {
                    user: '用户',
                    assistant: '助手',
                    system: '系统',
                    tool: '工具'
                };
                return map[role] || '其他';
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
