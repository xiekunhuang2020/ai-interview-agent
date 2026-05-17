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

createApp({
    data() {
        return {
            activeView: 'workspace',
            navItems: [
                { id: 'workspace', index: '01', label: '候选人导入' },
                { id: 'analysis', index: '02', label: '简历诊断' },
                { id: 'match', index: '03', label: '岗位匹配' },
                { id: 'interview', index: '04', label: '模拟面试' },
                { id: 'assistant', index: '05', label: 'AI 顾问' },
                { id: 'ops', index: '06', label: '运营看板' }
            ],
            titleMap: {
                workspace: '候选人工作台',
                analysis: '简历诊断报告',
                match: '岗位匹配分析',
                interview: '模拟面试',
                result: '面试复盘报告',
                assistant: 'AI 面试顾问',
                ops: '运营与稳定性'
            },
            emptyText: {
                analysis: '暂无简历诊断数据。',
                result: '暂无面试评估数据。'
            },
            loading: {
                upload: false,
                workspace: false,
                questions: false,
                evaluation: false,
                match: false,
                chat: false,
                ops: false
            },
            selectedFile: null,
            dragOver: false,
            resumeId: '',
            scoreResult: null,
            jdText: '',
            matchResult: null,
            questionMode: 'rag',
            topK: 5,
            questions: [],
            answers: {},
            evaluation: null,
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
            globalError: '',
            globalMessage: ''
        };
    },
    computed: {
        currentTitle() {
            return this.titleMap[this.activeView] || 'AI Interview Agent';
        },
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
                percent: this.normalizeDetailScore(item.value, item.max)
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
        }
    },
    mounted() {
        this.conversationId = localStorage.getItem('interviewAgentConversationId') || '';
        this.bootFromRoute();
    },
    methods: {
        bootFromRoute() {
            const path = window.location.pathname;
            const resumeRoute = path.match(/^\/(analysis|interview|result)\/([^/]+)/);
            if (resumeRoute) {
                this.resumeId = decodeURIComponent(resumeRoute[2]);
                this.activeView = resumeRoute[1] === 'result' ? 'result' : resumeRoute[1];
                this.loadWorkspace();
                return;
            }
            if (path === '/audit/prompt-dashboard') {
                this.activeView = 'ops';
                this.refreshOps();
                return;
            }
            if (path === '/upload') {
                this.activeView = 'workspace';
            }
        },
        activate(view) {
            this.globalError = '';
            this.globalMessage = '';
            this.activeView = view;
            if ((view === 'analysis' || view === 'interview' || view === 'result') && this.resumeId) {
                this.pushRoute(view);
            }
            if (view === 'ops') {
                window.history.pushState({}, '', '/audit/prompt-dashboard');
                this.refreshOps();
            }
        },
        pushRoute(view) {
            const route = view === 'result'
                ? `/result/${encodeURIComponent(this.resumeId)}`
                : `/${view}/${encodeURIComponent(this.resumeId)}`;
            window.history.pushState({}, '', route);
        },
        handleFileChange(event) {
            this.selectedFile = event.target.files[0] || null;
            this.globalError = '';
        },
        handleDrop(event) {
            this.dragOver = false;
            this.selectedFile = event.dataTransfer.files[0] || null;
            this.globalError = '';
        },
        async uploadResume() {
            if (!this.selectedFile) {
                return;
            }
            if (this.selectedFile.size > 10 * 1024 * 1024) {
                this.globalError = '文件大小不能超过 10MB。';
                return;
            }
            this.loading.upload = true;
            this.globalError = '';
            this.globalMessage = '';
            try {
                const formData = new FormData();
                formData.append('file', this.selectedFile);
                const payload = await fetchJson('/api/resume/upload', {
                    method: 'POST',
                    body: formData
                });
                this.resumeId = payload.resumeId || '';
                this.scoreResult = payload.scoreResult || null;
                this.questions = [];
                this.answers = {};
                this.evaluation = null;
                this.matchResult = null;
                this.globalMessage = '简历分析完成。';
                this.activeView = 'analysis';
                this.pushRoute('analysis');
            } catch (error) {
                this.globalError = error.message;
            } finally {
                this.loading.upload = false;
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
        async matchJob() {
            if (!this.resumeId || !this.jdText.trim()) {
                return;
            }
            this.loading.match = true;
            this.globalError = '';
            this.globalMessage = '';
            try {
                this.matchResult = await fetchJson(`/api/jd/${encodeURIComponent(this.resumeId)}/match`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ jobDescription: this.jdText })
                });
                this.globalMessage = '岗位匹配完成。';
            } catch (error) {
                this.globalError = error.message;
            } finally {
                this.loading.match = false;
            }
        },
        async generateQuestions(mode) {
            if (!this.resumeId) {
                this.globalError = '请先导入候选人简历。';
                return;
            }
            if (mode === 'rag' && !this.jdText.trim()) {
                this.activeView = 'match';
                this.globalError = '请先填写目标岗位 JD。';
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
                this.questionMode = mode;
                this.globalMessage = '面试题已生成。';
                this.activeView = 'interview';
                this.pushRoute('interview');
            } catch (error) {
                this.globalError = error.message;
            } finally {
                this.loading.questions = false;
            }
        },
        async submitAnswers() {
            if (!this.resumeId || !this.questions.length) {
                return;
            }
            this.loading.evaluation = true;
            this.globalError = '';
            this.globalMessage = '';
            try {
                this.evaluation = await fetchJson(`/api/interview/${encodeURIComponent(this.resumeId)}/submit`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(this.answers)
                });
                this.globalMessage = '面试评估完成。';
                this.activeView = 'result';
                this.pushRoute('result');
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
            const userMessage = {
                id: `u-${Date.now()}`,
                role: 'user',
                content
            };
            this.chatMessages.push(userMessage);
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
        clearCandidate() {
            this.selectedFile = null;
            this.resumeId = '';
            this.scoreResult = null;
            this.matchResult = null;
            this.questions = [];
            this.answers = {};
            this.evaluation = null;
            this.globalError = '';
            this.globalMessage = '';
            window.history.pushState({}, '', '/upload');
        },
        safeList(value) {
            return Array.isArray(value) ? value : [];
        },
        normalizeDetailScore(value, max) {
            const numeric = Number(value || 0);
            const denominator = Number(max || 100);
            return this.clamp(numeric * 100 / denominator);
        },
        clamp(value) {
            const numeric = Number(value || 0);
            return Math.max(0, Math.min(100, numeric));
        },
        round(value) {
            return Math.round(Number(value || 0) * 100) / 100;
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
}).mount('#app');
