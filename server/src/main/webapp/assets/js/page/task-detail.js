new Vue({
  el: '#app',
  mixins: [CQ.pageMixin],
  data: function () {
    return {
      taskId: '',
      task: null,
      applicants: [],
      loading: true,
      errorText: '',
      remainSeconds: null,
      timer: null,
      reviewVisible: false
    };
  },
  computed: {
    catName: function () { return this.task ? CQ.dict.categoryName(this.task.category) : ''; },
    catColor: function () { return this.task ? CQ.dict.categoryColor(this.task.category) : '#7f8b99'; },
    statusName: function () { return this.task ? (CQ.dict.taskStatus[this.task.status] || this.task.status) : ''; },
    myApplicationText: function () {
      return this.task ? (CQ.dict.applicationStatus[this.task.myApplicationStatus] || this.task.myApplicationStatus) : '';
    },
    initial: function () {
      return this.task && this.task.publisherNickname ? this.task.publisherNickname.substring(0, 1) : '?';
    },
    isPublisher: function () {
      return !!(this.task && this.me.userId && this.task.publisherId === this.me.userId);
    },
    isAcceptor: function () {
      return !!(this.task && this.me.userId && this.task.acceptorId === this.me.userId);
    },
    hasAction: function () {
      if (!this.task) return false;
      return this.task.canApply || this.task.canCancel || this.task.canDeliver || this.task.canAbandon
        || this.task.canComplete || this.task.canReview || this.task.canConfirmApplication;
    },
    remainText: function () {
      return CQ.remainText(this.remainSeconds);
    }
  },
  watch: {
    'me.userId': function () {
      this.loadApplicants();
    }
  },
  created: function () {
    var match = /[?&]id=(\d+)/.exec(location.search);
    if (!match) {
      this.loading = false;
      this.errorText = '任务不存在或已删除';
      return;
    }
    this.taskId = match[1];
    this.load();
  },
  beforeDestroy: function () {
    if (this.timer) clearInterval(this.timer);
  },
  methods: {
    load: function () {
      var self = this;
      this.loading = true;
      CQ.api.get('/tasks/' + this.taskId).then(function (data) {
        self.task = data;
        self.errorText = '';
        self.remainSeconds = (data.remainingSeconds === null || typeof data.remainingSeconds === 'undefined')
          ? null : data.remainingSeconds;
        self.startCountdown();
        self.loadApplicants();
      }).catch(function (err) {
        self.errorText = (err && err.message) || '任务不存在或已删除';
      }).finally(function () {
        self.loading = false;
      });
    },
    loadApplicants: function () {
      var self = this;
      if (!this.isPublisher || !this.task) {
        this.applicants = [];
        return;
      }
      if (this.task.applicationCount === 0) {
        this.applicants = [];
        return;
      }
      CQ.api.get('/tasks/' + this.taskId + '/applications').then(function (data) {
        self.applicants = data || [];
      }).catch(function () {
        self.applicants = [];
      });
    },
    startCountdown: function () {
      var self = this;
      if (this.timer) clearInterval(this.timer);
      if (!this.task || this.task.status !== 'PENDING_CONFIRM' || this.remainSeconds === null) return;
      this.timer = setInterval(function () {
        if (self.remainSeconds > 0) {
          self.remainSeconds = self.remainSeconds - 1;
        } else {
          clearInterval(self.timer);
          self.timer = null;
        }
      }, 1000);
    },
    applyTask: function () {
      var self = this;
      this.$confirm('申请后需要发布者确认，确认后请按约定完成并送达。确定申请吗？', '申请接单', {
        confirmButtonText: '确定申请', cancelButtonText: '取消'
      }).then(function () {
        return CQ.api.post('/tasks/' + self.taskId + '/apply', {});
      }).then(function () {
        ElementUI.Message.success('申请已提交，等待发布者确认');
        self.load();
      }).catch(function () {});
    },
    confirmApplicant: function (applicant) {
      var self = this;
      this.$confirm('确认由「' + applicant.applicantNickname + '」接单？确认后其他申请将自动变为未选中。', '确认接单', {
        confirmButtonText: '确认 TA 接单', cancelButtonText: '取消'
      }).then(function () {
        return CQ.api.post('/tasks/' + self.taskId + '/confirm', { applicationId: applicant.id });
      }).then(function () {
        ElementUI.Message.success('已确认接单，任务进入进行中');
        self.load();
      }).catch(function () {});
    },
    scrollToApplicants: function () {
      var el = document.getElementById('applicant-panel');
      if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' });
    },
    completeTask: function () {
      var self = this;
      this.$confirm('确认接单同学已按约定完成？确认后 ' + this.task.points + ' 积分将结算给对方，且不可撤销。', '确认完成交付', {
        confirmButtonText: '确认完成', cancelButtonText: '再看看', type: 'warning'
      }).then(function () {
        return CQ.api.post('/tasks/' + self.taskId + '/complete', {});
      }).then(function () {
        ElementUI.Message.success('已确认完成，积分已结算给接单同学');
        self.refreshMe();
        self.load();
      }).catch(function () {});
    },
    openReview: function () {
      this.reviewVisible = true;
    },
    submitReview: function (payload) {
      var self = this;
      CQ.api.post('/tasks/' + this.taskId + '/review', payload).then(function (data) {
        self.reviewVisible = false;
        ElementUI.Message.success('评价已提交，对方信用分变动 ' + (data.change > 0 ? '+' : '') + data.change);
        self.load();
      }).catch(function () {});
    },
    cancelTask: function () {
      var self = this;
      this.$confirm('取消后任务将从大厅移除，预算 ' + this.task.points + ' 积分会全额退回给你。确定取消吗？', '取消任务', {
        confirmButtonText: '确定取消', cancelButtonText: '再想想', type: 'warning'
      }).then(function () {
        return CQ.api.post('/tasks/' + self.taskId + '/cancel', {});
      }).then(function (data) {
        ElementUI.Message.success('任务已取消，退回 ' + data.refund + ' 积分');
        self.refreshMe();
        self.load();
      }).catch(function () {});
    },
    deliverTask: function () {
      var self = this;
      this.$confirm('确认已经按约定完成任务并送达？发布者有 24 小时确认，超时系统会自动确认。', '标记已送达', {
        confirmButtonText: '已送达', cancelButtonText: '取消', type: 'warning'
      }).then(function () {
        return CQ.api.post('/tasks/' + self.taskId + '/deliver', {});
      }).then(function () {
        ElementUI.Message.success('已通知发布者确认');
        self.load();
      }).catch(function () {});
    },
    abandonTask: function () {
      var self = this;
      this.$confirm('放弃后任务会重新回到大厅，接单超过 30 分钟放弃会扣 2 点信用分。确定放弃吗？', '放弃任务', {
        confirmButtonText: '确定放弃', cancelButtonText: '继续完成', type: 'warning'
      }).then(function () {
        return CQ.api.post('/tasks/' + self.taskId + '/abandon', {});
      }).then(function (data) {
        if (data.penalty > 0) {
          ElementUI.Message.warning('已放弃任务，信用分扣 2 分，当前信用 ' + data.credit + ' 分');
        } else {
          ElementUI.Message.success('已放弃任务，任务已回到大厅，未扣信用分');
        }
        self.refreshMe();
        self.load();
      }).catch(function () {});
    }
  }
});
