new Vue({
  el: '#app',
  mixins: [CQ.pageMixin],
  data: function () {
    return {
      tab: 'published',
      status: '',
      statusOptions: [
        { value: '', label: '全部' },
        { value: 'WAITING', label: '待接单' },
        { value: 'IN_PROGRESS', label: '进行中' },
        { value: 'PENDING_CONFIRM', label: '待确认完成' },
        { value: 'COMPLETED', label: '已完成' },
        { value: 'CANCELLED', label: '已取消' }
      ],
      counts: { published: 0, accepted: 0 },
      page: 1,
      size: 10,
      total: 0,
      list: [],
      loading: false,
      timer: null,
      pollTimer: null,
      reviewVisible: false,
      currentTask: null
    };
  },
  computed: {
    emptyText: function () {
      return this.tab === 'published' ? '你还没有发布过符合条件的任务' : '你还没有接过符合条件的任务';
    }
  },
  created: function () {
    this.loadCounts();
    this.load();
  },
  beforeDestroy: function () {
    if (this.timer) clearInterval(this.timer);
    if (this.pollTimer) clearInterval(this.pollTimer);
  },
  methods: {
    categoryName: function (code) { return CQ.dict.categoryName(code); },
    remainText: function (seconds) { return CQ.remainText(seconds); },
    switchTab: function (tab) {
      if (this.tab === tab) return;
      this.tab = tab;
      this.status = '';
      this.page = 1;
      this.load();
    },
    selectStatus: function (status) {
      this.status = status;
      this.page = 1;
      this.load();
    },
    changePage: function (p) {
      this.page = p;
      this.load();
    },
    openTask: function (id) {
      location.href = 'task-detail.html?id=' + id;
    },
    loadCounts: function () {
      var self = this;
      CQ.api.get('/tasks/mine', { params: { type: 'published', page: 1, size: 1 } }).then(function (data) {
        self.counts.published = data.total || 0;
      });
      CQ.api.get('/tasks/mine', { params: { type: 'accepted', page: 1, size: 1 } }).then(function (data) {
        self.counts.accepted = data.total || 0;
      });
    },
    load: function () {
      var self = this;
      this.loading = true;
      CQ.api.get('/tasks/mine', {
        params: { type: this.tab, status: this.status, page: this.page, size: this.size }
      }).then(function (data) {
        self.list = data.list || [];
        self.total = data.total || 0;
        self.watchCountdown();
      }).finally(function () {
        self.loading = false;
      });
    },
    refresh: function () {
      this.loadCounts();
      this.load();
    },
    watchCountdown: function () {
      var self = this;
      if (this.timer) clearInterval(this.timer);
      var hasPending = false;
      for (var i = 0; i < this.list.length; i++) {
        if (this.list[i].status === 'PENDING_CONFIRM') hasPending = true;
      }
      if (!hasPending) {
        if (this.pollTimer) { clearInterval(this.pollTimer); this.pollTimer = null; }
        return;
      }
      if (!this.pollTimer) {
        this.pollTimer = setInterval(function () {
          self.load();
        }, 30000);
      }
      this.timer = setInterval(function () {
        for (var j = 0; j < self.list.length; j++) {
          var t = self.list[j];
          if (t.status === 'PENDING_CONFIRM' && t.remainingSeconds !== null && t.remainingSeconds > 0) {
            t.remainingSeconds = t.remainingSeconds - 1;
          }
        }
      }, 1000);
    },
    completeTask: function (task) {
      var self = this;
      this.$confirm('确认接单同学已按约定完成？确认后 ' + task.points + ' 积分将结算给对方，且不可撤销。', '确认完成交付', {
        confirmButtonText: '确认完成', cancelButtonText: '再看看', type: 'warning'
      }).then(function () {
        return CQ.api.post('/tasks/' + task.id + '/complete', {});
      }).then(function () {
        ElementUI.Message.success('已确认完成，积分已结算给接单同学');
        self.refreshMe();
        self.refresh();
      }).catch(function () {});
    },
    openReview: function (task) {
      this.currentTask = task;
      this.reviewVisible = true;
    },
    submitReview: function (payload) {
      var self = this;
      if (!this.currentTask) return;
      CQ.api.post('/tasks/' + this.currentTask.id + '/review', payload).then(function (data) {
        self.reviewVisible = false;
        ElementUI.Message.success('评价已提交，对方信用分变动 ' + (data.change > 0 ? '+' : '') + data.change);
        self.refresh();
      }).catch(function () {});
    },
    cancelTask: function (task) {
      var self = this;
      this.$confirm('取消后任务将从大厅移除，预算 ' + task.points + ' 积分会全额退回给你。确定取消吗？', '取消任务', {
        confirmButtonText: '确定取消', cancelButtonText: '再想想', type: 'warning'
      }).then(function () {
        return CQ.api.post('/tasks/' + task.id + '/cancel', {});
      }).then(function (data) {
        ElementUI.Message.success('任务已取消，退回 ' + data.refund + ' 积分');
        self.refreshMe();
        self.refresh();
      }).catch(function () {});
    },
    deliverTask: function (task) {
      var self = this;
      this.$confirm('确认已经按约定完成任务并送达？发布者有 24 小时确认，超时系统会自动确认。', '标记已送达', {
        confirmButtonText: '已送达', cancelButtonText: '取消', type: 'warning'
      }).then(function () {
        return CQ.api.post('/tasks/' + task.id + '/deliver', {});
      }).then(function () {
        ElementUI.Message.success('已通知发布者确认，等待确认期间请保持联系');
        self.refresh();
      }).catch(function () {});
    },
    abandonTask: function (task) {
      var self = this;
      this.$confirm('放弃后任务会重新回到大厅，接单超过 30 分钟放弃会扣 2 点信用分。确定放弃吗？', '放弃任务', {
        confirmButtonText: '确定放弃', cancelButtonText: '继续完成', type: 'warning'
      }).then(function () {
        return CQ.api.post('/tasks/' + task.id + '/abandon', {});
      }).then(function (data) {
        if (data.penalty > 0) {
          ElementUI.Message.warning('已放弃任务，信用分扣 2 分，当前信用 ' + data.credit + ' 分');
        } else {
          ElementUI.Message.success('已放弃任务，任务已回到大厅，未扣信用分');
        }
        self.refreshMe();
        self.refresh();
      }).catch(function () {});
    }
  }
});
