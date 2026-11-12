new Vue({
  el: '#app',
  mixins: [CQ.pageMixin],
  data: function () {
    return {
      categories: CQ.categories,
      submitting: false,
      errorText: '',
      form: {
        category: '',
        title: '',
        description: '',
        points: 20,
        locationDesc: '',
        timeDesc: ''
      }
    };
  },
  computed: {
    creditText: function () {
      return CQ.creditText(this.me.credit || 0);
    }
  },
  methods: {
    submit: function () {
      var self = this;
      var f = this.form;
      this.errorText = '';
      if (!f.category) { this.errorText = '请选择任务分类'; return; }
      if (!f.title) { this.errorText = '标题不能为空'; return; }
      if (!f.description) { this.errorText = '描述不能为空'; return; }
      if (!f.points || f.points < 1 || f.points > 999) { this.errorText = '积分预算需在 1 到 999 之间'; return; }
      if (!f.locationDesc) { this.errorText = '地点说明不能为空'; return; }
      if (!f.timeDesc) { this.errorText = '时间说明不能为空'; return; }
      if (f.points > this.me.points) {
        this.errorText = '积分不足，当前余额 ' + this.me.points + '，本次需要 ' + f.points;
        return;
      }
      this.submitting = true;
      CQ.api.post('/tasks', f).then(function (data) {
        ElementUI.Message.success('发布成功，已扣除 ' + f.points + ' 积分');
        location.href = 'task-detail.html?id=' + data.taskId;
      }).catch(function (err) {
        self.errorText = (err && err.message) || '发布失败，请稍后再试';
      }).finally(function () {
        self.submitting = false;
      });
    }
  }
});
