/* 管理端 · 申诉处理 */
(function () {
  'use strict';

  new Vue({
    el: '#app',
    data: {
      adminName: '管理员',
      status: 'PENDING',
      counts: { pending: 0, approved: 0, rejected: 0 },
      list: [],
      page: 1,
      size: 8,
      total: 0,
      detail: null,
      rejectVisible: false,
      rejectReason: '',
      handling: false
    },
    computed: {
      shotUrl: function () {
        return this.detail && this.detail.screenshotPath ? '../' + this.detail.screenshotPath : '';
      }
    },
    created: function () {
      this.loadMe();
      this.loadCounts();
      this.loadList();
    },
    methods: {
      loadMe: function () {
        var self = this;
        CQ.api.get('/admin/me').then(function (data) {
          self.adminName = data.username || '管理员';
        }).catch(function () {
          location.href = '../login.html';
        });
      },
      loadCounts: function () {
        var self = this;
        CQ.api.get('/admin/appeals/counts').then(function (data) {
          self.counts = data;
        });
      },
      loadList: function () {
        var self = this;
        return CQ.api.get('/admin/appeals', {
          params: { status: self.status, page: self.page, size: self.size }
        }).then(function (data) {
          self.list = data.list || [];
          self.total = data.total || 0;
          var keep = self.detail && self.list.some(function (row) { return row.id === self.detail.id; });
          if (keep) return;
          if (self.list.length > 0) self.select(self.list[0]);
          else self.detail = null;
        });
      },
      switchStatus: function (status) {
        if (this.status === status) return;
        this.status = status;
        this.page = 1;
        this.detail = null;
        this.loadList();
      },
      changePage: function (page) {
        this.page = page;
        this.loadList();
      },
      select: function (row) {
        var self = this;
        CQ.api.get('/admin/appeals/' + row.id).then(function (data) {
          self.detail = data;
        });
      },
      approve: function () {
        var self = this;
        ElementUI.MessageBox.confirm('通过后申诉人信用分退回 2 分，确定通过该申诉？', '通过申诉', {
          type: 'warning', confirmButtonText: '确定通过', cancelButtonText: '取消'
        }).then(function () {
          self.handling = true;
          CQ.api.post('/admin/appeals/' + self.detail.id + '/approve', {}).then(function () {
            self.handling = false;
            ElementUI.Message.success('申诉已通过，信用分已退回');
            self.detail = null;
            self.loadCounts();
            self.loadList();
          }).catch(function () {
            self.handling = false;
          });
        }).catch(function () {});
      },
      openReject: function () {
        this.rejectReason = '';
        this.rejectVisible = true;
      },
      doReject: function () {
        var self = this;
        var reason = (this.rejectReason || '').trim();
        if (!reason) {
          ElementUI.Message.warning('请填写驳回理由');
          return;
        }
        this.handling = true;
        CQ.api.post('/admin/appeals/' + this.detail.id + '/reject', { reason: reason }).then(function () {
          self.handling = false;
          self.rejectVisible = false;
          ElementUI.Message.success('申诉已驳回');
          self.detail = null;
          self.loadCounts();
          self.loadList();
        }).catch(function () {
          self.handling = false;
        });
      },
      appealText: function (status) {
        return CQ.dict.appealStatus[status] || status;
      },
      appealCls: function (status) {
        if (status === 'APPROVED') return 'st-COMPLETED';
        if (status === 'REJECTED') return 'st-OFFLINE';
        return 'st-WAITING';
      },
      taskStatusText: function (status) {
        return CQ.dict.taskStatus[status] || status || '';
      },
      creditReasonText: function (reason) {
        return CQ.dict.creditReason[reason] || reason || '';
      },
      logout: function () {
        CQ.api.post('/auth/logout', {}).then(function () {
          location.href = '../login.html';
        });
      }
    }
  });
})();
