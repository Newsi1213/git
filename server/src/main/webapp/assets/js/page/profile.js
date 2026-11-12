new Vue({
  el: '#app',
  mixins: [CQ.pageMixin],
  data: function () {
    return {
      initial: '',
      pointType: '',
      pointTypes: [
        { value: '', label: '全部' },
        { value: 'REGISTER', label: '注册赠送' },
        { value: 'PUBLISH', label: '发布任务' },
        { value: 'REFUND', label: '取消退回' },
        { value: 'INCOME', label: '任务收入' },
        { value: 'RECHARGE', label: '模拟充值' }
      ],
      pointLogs: [],
      pointPage: 1,
      pointSize: 10,
      pointTotal: 0,
      pointLoading: false,
      creditLogs: [],
      creditPage: 1,
      creditSize: 10,
      creditTotal: 0,
      creditLoading: false,
      rechargeVisible: false,
      rechargeAmount: 50,
      quickAmounts: [10, 50, 100, 500],
      appealVisible: false,
      appealLog: null,
      appealContent: '',
      appealFiles: [],
      appealFile: null,
      appealSubmitting: false
    };
  },
  watch: {
    'me.nickname': {
      immediate: true,
      handler: function (v) {
        this.initial = v ? v.substring(0, 1) : '?';
      }
    }
  },
  created: function () {
    this.loadPointLogs();
    this.loadCreditLogs();
  },
  methods: {
    pointTypeName: function (type) { return CQ.dict.pointType[type] || type; },
    creditReasonName: function (reason) { return CQ.dict.creditReason[reason] || reason; },
    selectPointType: function (type) {
      this.pointType = type;
      this.pointPage = 1;
      this.loadPointLogs();
    },
    changePointPage: function (p) {
      this.pointPage = p;
      this.loadPointLogs();
    },
    changeCreditPage: function (p) {
      this.creditPage = p;
      this.loadCreditLogs();
    },
    loadPointLogs: function () {
      var self = this;
      this.pointLoading = true;
      CQ.api.get('/points/logs', {
        params: { type: this.pointType, page: this.pointPage, size: this.pointSize }
      }).then(function (data) {
        self.pointLogs = data.list || [];
        self.pointTotal = data.total || 0;
      }).finally(function () {
        self.pointLoading = false;
      });
    },
    loadCreditLogs: function () {
      var self = this;
      this.creditLoading = true;
      CQ.api.get('/credit/logs', {
        params: { page: this.creditPage, size: this.creditSize }
      }).then(function (data) {
        self.creditLogs = data.list || [];
        self.creditTotal = data.total || 0;
      }).finally(function () {
        self.creditLoading = false;
      });
    },
    doRecharge: function () {
      var self = this;
      CQ.api.post('/points/recharge', { amount: this.rechargeAmount }).then(function (data) {
        ElementUI.Message.success('充值成功，到账 ' + data.added + ' 积分');
        self.rechargeVisible = false;
        self.refreshMe();
        self.pointPage = 1;
        self.loadPointLogs();
      }).catch(function () {});
    },
    openAppeal: function (log) {
      this.appealLog = log;
      this.appealContent = '';
      this.appealFiles = [];
      this.appealFile = null;
      this.appealVisible = true;
    },
    onAppealFile: function (file, fileList) {
      this.appealFile = file.raw || null;
      this.appealFiles = fileList.slice(-1);
    },
    onAppealRemove: function () {
      this.appealFile = null;
      this.appealFiles = [];
    },
    submitAppeal: function () {
      var self = this;
      if (!this.appealContent.trim()) {
        ElementUI.Message.warning('请填写申诉理由');
        return;
      }
      if (!this.appealFile) {
        ElementUI.Message.warning('请上传申诉截图');
        return;
      }
      if (this.appealFile.size > 5 * 1024 * 1024) {
        ElementUI.Message.warning('图片不能超过 5MB');
        return;
      }
      var form = new FormData();
      form.append('taskId', this.appealLog.taskId);
      form.append('content', this.appealContent.trim());
      form.append('screenshot', this.appealFile);
      this.appealSubmitting = true;
      CQ.api.post('/appeals', form).then(function () {
        ElementUI.Message.success('申诉已提交，等待管理员处理');
        self.appealVisible = false;
        self.creditPage = 1;
        self.loadCreditLogs();
      }).catch(function () {
      }).finally(function () {
        self.appealSubmitting = false;
      });
    }
  }
});
