new Vue({
  el: '#app',
  mixins: [CQ.pageMixin],
  data: function () {
    return {
      list: [],
      mine: null,
      loading: false
    };
  },
  computed: {
    top: function () {
      return this.list.slice(0, 3);
    }
  },
  created: function () {
    this.load();
  },
  methods: {
    load: function () {
      var self = this;
      this.loading = true;
      CQ.api.get('/leaderboard', { params: { limit: 20 } }).then(function (data) {
        self.list = data.list || [];
        self.mine = data.me || null;
      }).finally(function () {
        self.loading = false;
      });
    }
  }
});
