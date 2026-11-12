/* CampusQuest 前端公共基础：axios 封装、字典、格式化、页面 mixin */
(function (window) {
  'use strict';

  /* Element UI 的 UMD 全局名是 ELEMENT，统一别名供业务代码使用 */
  if (!window.ElementUI && window.ELEMENT) { window.ElementUI = window.ELEMENT; }

  var api = axios.create({
    baseURL: '/campusquest/api',
    withCredentials: true,
    timeout: 15000
  });

  api.interceptors.response.use(function (resp) {
    var body = resp.data;
    if (!body || typeof body.code === 'undefined') {
      ElementUI.Message.error('响应格式异常');
      return Promise.reject(body);
    }
    if (body.code !== 0) {
      ElementUI.Message.error(body.message || '操作失败');
      return Promise.reject(body);
    }
    return body.data;
  }, function (err) {
    if (err.response && err.response.status === 401) {
      location.href = '/campusquest/login.html';
      return Promise.reject(err);
    }
    var msg = (err.response && err.response.data && err.response.data.message) || '网络异常，请稍后再试';
    ElementUI.Message.error(msg);
    return Promise.reject(err);
  });

  var categories = [
    { code: 'EXPRESS', name: '取快递', color: '#5b8ff9' },
    { code: 'TAKEOUT', name: '代取外卖', color: '#f6bd16' },
    { code: 'ERRAND', name: '代替服务', color: '#5ad8a6' },
    { code: 'MOVING', name: '搬运服务', color: '#e8684a' },
    { code: 'CLEANING', name: '宿舍打扫', color: '#9270ca' },
    { code: 'OTHER', name: '其他', color: '#7f8b99' }
  ];

  var dict = {
    categories: categories,
    categoryName: function (code) {
      for (var i = 0; i < categories.length; i++) {
        if (categories[i].code === code) return categories[i].name;
      }
      return code || '';
    },
    categoryColor: function (code) {
      for (var i = 0; i < categories.length; i++) {
        if (categories[i].code === code) return categories[i].color;
      }
      return '#7f8b99';
    },
    taskStatus: {
      WAITING: '待接单', IN_PROGRESS: '进行中', PENDING_CONFIRM: '待确认完成',
      COMPLETED: '已完成', CANCELLED: '已取消'
    },
    applicationStatus: {
      PENDING: '待确认', SELECTED: '已选中', REJECTED: '未选中', ABANDONED: '已放弃'
    },
    appealStatus: { PENDING: '待处理', APPROVED: '已通过', REJECTED: '已驳回' },
    pointType: {
      REGISTER: '注册赠送', PUBLISH: '发布任务', REFUND: '取消退回', INCOME: '任务收入', RECHARGE: '模拟充值'
    },
    creditReason: {
      INIT: '初始信用', GOOD_REVIEW: '收到好评', BAD_REVIEW: '收到差评',
      ABANDON: '超时放弃', APPEAL_REFUND: '申诉退回'
    },
    notifyType: {
      APPLICATION_RECEIVED: '收到申请', APPLICATION_CONFIRMED: '被确认', REVIEW_RECEIVED: '收到评价',
      SETTLEMENT: '结算到账', APPEAL_RESULT: '申诉结果'
    }
  };

  var menus = [
    { key: 'dashboard', name: '工作台', href: 'dashboard.html', ico: '台' },
    { key: 'lobby', name: '接单大厅', href: 'lobby.html', ico: '厅' },
    { key: 'publish', name: '发布任务', href: 'publish.html', ico: '发' },
    { key: 'my-tasks', name: '我的任务', href: 'my-tasks.html', ico: '任' },
    { key: 'messages', name: '消息中心', href: 'messages.html', ico: '信' },
    { key: 'profile', name: '个人中心', href: 'profile.html', ico: '我' },
    { key: 'leaderboard', name: '排行榜', href: 'leaderboard.html', ico: '榜' }
  ];

  function pad(n) { return n < 10 ? '0' + n : '' + n; }

  var CQ = {
    api: api,
    dict: dict,
    menus: menus,
    categories: categories,

    fmtTime: function (s) {
      if (!s) return '';
      return String(s).replace('T', ' ');
    },

    fmtShort: function (s) {
      if (!s) return '';
      var t = String(s).replace('T', ' ');
      return t.length > 16 ? t.substring(5, 16) : t;
    },

    creditText: function (credit) {
      if (credit >= 100) return '信用满分';
      if (credit >= 90) return '信用良好，可正常发布';
      if (credit >= 85) return '信用偏低，暂不能发布任务';
      return '信用过低，不能发布也不能接单';
    },

    greeting: function () {
      var h = new Date().getHours();
      if (h < 12) return '上午好';
      if (h < 18) return '下午好';
      return '晚上好';
    },

    remainText: function (seconds) {
      if (seconds === null || typeof seconds === 'undefined') return '';
      if (seconds <= 0) return '已到期';
      var h = Math.floor(seconds / 3600);
      var m = Math.floor((seconds % 3600) / 60);
      var s = seconds % 60;
      return h + ' 小时 ' + pad(m) + ' 分 ' + pad(s) + ' 秒';
    },

    pageMixin: {
      data: function () {
        return {
          me: { nickname: '', points: 0, credit: 0, unreadCount: 0 },
          meLoaded: false
        };
      },
      created: function () {
        if (this.loadMe) this.loadMe();
      },
      methods: {
        loadMe: function () {
          var self = this;
          return api.get('/auth/me').then(function (data) {
            self.me = data;
            self.meLoaded = true;
            return data;
          });
        },
        refreshMe: function () {
          return this.loadMe();
        },
        go: function (page) {
          location.href = page;
        },
        logout: function () {
          api.post('/auth/logout', {}).then(function () {
            location.href = '/campusquest/login.html';
          });
        }
      }
    }
  };

  window.CQ = CQ;
})(window);
