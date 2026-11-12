new Vue({
  el: '#app',
  mixins: [CQ.pageMixin],
  data: function () {
    return {
      loading: false,
      summary: { inProgress: 0, toConfirm: 0, publishedActive: 0, publishedWaiting: 0, unreadCount: 0 },
      personal: { published: 0, completed: 0, accepted: 0, income: 0, expense: 0 },
      overview: { days: [], categories: [], top: [], totalTasks: 0 }
    };
  },
  computed: {
    greetingText: function () {
      return CQ.greeting() + '，' + (this.me.nickname || '同学') + '。';
    },
    summaryText: function () {
      var parts = [];
      if (this.summary.inProgress > 0) parts.push('今天有 ' + this.summary.inProgress + ' 个任务正在进行');
      if (this.summary.toConfirm > 0) parts.push(this.summary.toConfirm + ' 个任务等待你确认完成');
      if (parts.length === 0) return '今天暂时没有进行中的任务，去接单大厅看看有没有顺路的活儿。';
      return parts.join('，') + '。';
    },
    creditHint: function () {
      return CQ.creditText(this.me.credit);
    },
    pieStyle: function () {
      var segs = [];
      var start = 0;
      var categories = this.overview.categories || [];
      for (var i = 0; i < categories.length; i++) {
        var c = categories[i];
        if (c.percent <= 0) continue;
        var end = start + c.percent;
        segs.push(this.categoryColor(c.category) + ' ' + start + '% ' + end + '%');
        start = end;
      }
      if (segs.length === 0) return {};
      return { background: 'conic-gradient(' + segs.join(',') + ')' };
    }
  },
  created: function () {
    this.fetchAll();
  },
  methods: {
    fetchAll: function () {
      var self = this;
      this.loading = true;
      Promise.all([
        CQ.api.get('/stats/summary'),
        CQ.api.get('/stats/personal'),
        CQ.api.get('/stats/overview')
      ]).then(function (res) {
        self.summary = res[0];
        self.personal = res[1];
        self.overview = res[2];
      }).catch(function () {
        ElementUI.Message.error('看板数据加载失败，请刷新重试');
      }).finally(function () {
        self.loading = false;
      });
    },
    categoryName: function (code) {
      return CQ.dict.categoryName(code);
    },
    categoryColor: function (code) {
      return CQ.dict.categoryColor(code);
    }
  }
});
