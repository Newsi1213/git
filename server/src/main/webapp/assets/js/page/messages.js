new Vue({
  el: '#app',
  mixins: [CQ.pageMixin],
  data: function () {
    return {
      tab: 'notify',
      notices: [],
      noticePage: 1,
      noticeSize: 10,
      noticeTotal: 0,
      noticeOnlyUnread: false,
      noticeLoading: false,
      conversations: [],
      convLoading: false,
      activeConvId: 0
    };
  },
  computed: {
    activeConv: function () {
      for (var i = 0; i < this.conversations.length; i++) {
        if (this.conversations[i].id === this.activeConvId) return this.conversations[i];
      }
      return null;
    }
  },
  created: function () {
    this.loadNotices();
    this.loadConversations();
  },
  methods: {
    loadNotices: function () {
      var self = this;
      this.noticeLoading = true;
      CQ.api.get('/notifications', {
        params: {
          page: this.noticePage,
          size: this.noticeSize,
          onlyUnread: this.noticeOnlyUnread ? 1 : 0
        }
      }).then(function (data) {
        self.notices = (data && data.list) || [];
        self.noticeTotal = (data && data.total) || 0;
      }).catch(function () {
        self.notices = [];
      }).finally(function () {
        self.noticeLoading = false;
      });
    },
    changeNoticeFilter: function (onlyUnread) {
      this.noticeOnlyUnread = onlyUnread;
      this.noticePage = 1;
      this.loadNotices();
    },
    changeNoticePage: function (page) {
      this.noticePage = page;
      this.loadNotices();
    },
    noticeIcon: function (type) {
      var map = {
        APPLICATION_RECEIVED: '申请',
        APPLICATION_CONFIRMED: '确认',
        REVIEW_RECEIVED: '评价',
        SETTLEMENT: '到账',
        APPEAL_RESULT: '申诉'
      };
      return map[type] || '通知';
    },
    noticeTag: function (type) {
      return CQ.dict.notifyType[type] || type;
    },
    noticeLinkText: function (type) {
      if (type === 'APPLICATION_RECEIVED') return '查看申请';
      if (type === 'APPLICATION_CONFIRMED') return '查看任务';
      if (type === 'REVIEW_RECEIVED') return '查看评价';
      if (type === 'SETTLEMENT') return '查看明细';
      return '查看申诉';
    },
    noticeTarget: function (n) {
      if ((n.type === 'APPLICATION_RECEIVED' || n.type === 'APPLICATION_CONFIRMED') && n.taskId) {
        return 'task-detail.html?id=' + n.taskId;
      }
      return 'profile.html';
    },
    openNotice: function (n) {
      var self = this;
      var target = this.noticeTarget(n);
      var done = function () { location.href = target; };
      if (n.isRead === 0) {
        CQ.api.post('/notifications/' + n.id + '/read', {}).then(function (data) {
          n.isRead = 1;
          self.me.unreadCount = data.unreadCount;
          done();
        }).catch(function () {
          done();
        });
      } else {
        done();
      }
    },
    switchChat: function () {
      this.tab = 'chat';
      this.loadConversations();
    },
    loadConversations: function () {
      var self = this;
      this.convLoading = true;
      CQ.api.get('/conversations').then(function (data) {
        self.conversations = data || [];
        if (!self.activeConvId && self.conversations.length > 0) {
          self.activeConvId = self.conversations[0].id;
        }
      }).catch(function () {
        self.conversations = [];
      }).finally(function () {
        self.convLoading = false;
      });
    },
    selectConv: function (c) {
      this.activeConvId = c.id;
    },
    shortTime: function (s) {
      return CQ.fmtShort(s);
    },
    taskStatusName: function (s) {
      return CQ.dict.taskStatus[s] || s;
    }
  }
});
