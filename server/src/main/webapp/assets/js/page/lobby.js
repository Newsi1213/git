new Vue({
  el: '#app',
  mixins: [CQ.pageMixin],
  data: function () {
    return {
      categories: CQ.categories,
      category: '',
      keyword: '',
      sort: 'latest',
      onlyWaiting: true,
      page: 1,
      size: 12,
      total: 0,
      list: [],
      loading: false
    };
  },
  watch: {
    onlyWaiting: function () { this.page = 1; this.load(); }
  },
  created: function () {
    this.load();
  },
  methods: {
    categoryName: function (code) { return CQ.dict.categoryName(code); },
    selectCategory: function (code) {
      this.category = code;
      this.page = 1;
      this.load();
    },
    search: function () {
      this.page = 1;
      this.load();
    },
    reload: function () {
      this.page = 1;
      this.load();
    },
    changePage: function (p) {
      this.page = p;
      this.load();
    },
    load: function () {
      var self = this;
      this.loading = true;
      CQ.api.get('/tasks', {
        params: {
          category: this.category,
          keyword: this.keyword,
          sort: this.sort,
          onlyWaiting: this.onlyWaiting ? 1 : 0,
          page: this.page,
          size: this.size
        }
      }).then(function (data) {
        self.list = data.list || [];
        self.total = data.total || 0;
      }).finally(function () {
        self.loading = false;
      });
    }
  }
});
