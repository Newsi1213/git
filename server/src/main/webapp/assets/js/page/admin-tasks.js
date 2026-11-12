/* 管理端 · 任务管理 */
(function () {
  'use strict';

  new Vue({
    el: '#app',
    data: {
      adminName: '管理员',
      keyword: '',
      offline: '',
      list: [],
      page: 1,
      size: 10,
      total: 0
    },
    created: function () {
      this.loadMe();
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
      loadList: function () {
        var self = this;
        return CQ.api.get('/admin/tasks', {
          params: { keyword: self.keyword, offline: self.offline, page: self.page, size: self.size }
        }).then(function (data) {
          self.list = data.list || [];
          self.total = data.total || 0;
        });
      },
      search: function () {
        this.page = 1;
        this.loadList();
      },
      changePage: function (page) {
        this.page = page;
        this.loadList();
      },
      categoryName: function (code) {
        return CQ.dict.categoryName(code);
      },
      offlineTask: function (row) {
        var self = this;
        ElementUI.MessageBox.prompt('下架后学生端不再展示该任务，已接单的任务状态不受影响。请填写下架理由：',
          '下架任务《' + row.title + '》', {
            inputType: 'textarea',
            inputPlaceholder: '例如：任务描述含违规内容，请修改后联系管理员恢复',
            inputValidator: function (value) {
              return !!(value && value.trim()) || '下架理由不能为空';
            },
            confirmButtonText: '确认下架',
            cancelButtonText: '取消'
          }).then(function (res) {
            CQ.api.post('/admin/tasks/' + row.id + '/offline', { reason: res.value.trim() }).then(function () {
              ElementUI.Message.success('任务已下架');
              self.loadList();
            });
          }).catch(function () {});
      },
      onlineTask: function (row) {
        var self = this;
        ElementUI.MessageBox.confirm('恢复后该任务将重新在学生端展示，原任务状态保持不变。确定恢复？',
          '恢复任务', {
            type: 'warning', confirmButtonText: '确认恢复', cancelButtonText: '取消'
          }).then(function () {
            CQ.api.post('/admin/tasks/' + row.id + '/online', {}).then(function () {
              ElementUI.Message.success('任务已恢复');
              self.loadList();
            });
          }).catch(function () {});
      },
      logout: function () {
        CQ.api.post('/auth/logout', {}).then(function () {
          location.href = '../login.html';
        });
      }
    }
  });
})();
